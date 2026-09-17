package games.pixscape.runtime.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudContainerData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.runtime.hud.document.ValidatedHudDocument;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.Map;

public class HudMaterializerTest {
    private static final String FIXTURE_ROOT =
            "games/pixscape/runtime/hud/document/v1/";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;
    private Graphics previousGraphics;
    private Files previousFiles;
    private HudResources resources;
    private int generatedTextures;

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void prepareResources() throws Exception {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;
        previousGraphics = Gdx.graphics;
        previousFiles = Gdx.files;
        int[] nextHandle = {1};
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(), new Class<?>[]{GL30.class},
                (proxy, method, args) -> glValue(
                        method.getName(), args, method.getReturnType(), nextHandle));
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        FileHandle builtInDescriptor = new FileHandle(temporaryFolder.newFile("lsans-15.fnt"));
        try (java.io.InputStream source = HudMaterializerTest.class.getClassLoader()
                .getResourceAsStream(HudBuiltInLabelStyle.FONT_DESCRIPTOR)) {
            java.nio.file.Files.copy(source, builtInDescriptor.file().toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Gdx.files = (Files) Proxy.newProxyInstance(
                Files.class.getClassLoader(), new Class<?>[]{Files.class},
                (proxy, method, args) -> "classpath".equals(method.getName())
                        ? builtInDescriptor
                        : defaultValue(method.getReturnType()));
        TextureRegistry.clear();
        InternalTextures.dispose();

        FileHandle root = new FileHandle(temporaryFolder.newFolder());
        HudResourcesTest.writeHudFiles(root);
        HudScreenAsset asset = new HudScreenAsset();
        asset.skinId = "ui/game.json";
        asset.atlasId = "ui/game.atlas";
        resources = HudResources.prepare(asset, root, HudResourcesTest.fullRequirements());
    }

    @After
    public void disposeResources() {
        if (resources != null && !resources.isDisposed()) resources.dispose();
        InternalTextures.dispose();
        TextureRegistry.clear();
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
        Gdx.graphics = previousGraphics;
        Gdx.files = previousFiles;
    }

    @Test
    public void materializesLayoutOnlyTreeWithoutSkinOrAtlas() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("layout-only"));
        String serialized = "{\"schemaVersion\":1,\"root\":{\"id\":\"root\","
                + "\"kind\":\"GROUP\",\"children\":[{\"placementKind\":\"DIRECT\","
                + "\"node\":{\"id\":\"stack\",\"kind\":\"STACK\","
                + "\"children\":[]}}]}}";
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentCodec().read(serialized));
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        HudResourceRequirements requirements =
                HudResourceRequirements.from(validation.validatedDocument());
        HudResources emptyResources = HudResources.prepare(
                new HudScreenAsset(), root, requirements);
        try {
            MaterializedHud hud = new HudMaterializer().materialize(
                    validation.validatedDocument(), emptyResources);
            Assert.assertTrue(hud.root() instanceof HudFreeGroup);
            Assert.assertTrue(hud.actor("stack") instanceof Stack);
            Assert.assertEquals(2, hud.actorById().size());
        } finally {
            emptyResources.dispose();
        }
    }

    @Test
    public void materializesEmptyAndOccupiedContainersWithoutSyntheticChildren() {
        HudNode empty = new HudNode("empty", HudNodeKind.CONTAINER);
        empty.container = new HudContainerData();
        HudValidationResult emptyValidation = new HudDocumentValidator().validate(
                new HudDocumentV1(empty), resources);
        Assert.assertTrue(emptyValidation.issues().toString(), emptyValidation.isValid());

        MaterializedHud emptyHud = new HudMaterializer().materialize(
                emptyValidation.validatedDocument(), resources);
        Container<?> emptyActor = (Container<?>) emptyHud.actor("empty");
        Assert.assertNull(emptyActor.getActor());
        Assert.assertEquals(1, emptyHud.actorById().size());

        HudNode occupied = new HudNode("occupied", HudNodeKind.CONTAINER);
        occupied.container = new HudContainerData();
        occupied.children.add(HudChild.direct(new HudNode("group", HudNodeKind.GROUP)));
        HudValidationResult occupiedValidation = new HudDocumentValidator().validate(
                new HudDocumentV1(occupied), resources);
        Assert.assertTrue(occupiedValidation.issues().toString(), occupiedValidation.isValid());

        MaterializedHud occupiedHud = new HudMaterializer().materialize(
                occupiedValidation.validatedDocument(), resources);
        Container<?> occupiedActor = (Container<?>) occupiedHud.actor("occupied");
        Assert.assertSame(occupiedHud.actor("group"), occupiedActor.getActor());
        Assert.assertEquals(2, occupiedHud.actorById().size());
    }

    @Test
    public void allPositiveFixturesUseNativeTypesNamesAndCompleteImmutableIndexes() {
        assertMapping("managed-table.json", "screen", Table.class);
        assertMapping("free-layout.json", "free-root", HudFreeGroup.class);
        assertMapping("stack.json", "overlay", Stack.class);
        assertMapping("clipped-container.json", "clipped-panel", Container.class);
        MaterializedHud resourcesHud = materialize("resources.json");
        assertActor(resourcesHud, "scene-art", Image.class);
        assertActor(resourcesHud, "resource-label", Label.class);
        assertActor(resourcesHud, "resource-button", TextButton.class);

        for (Map.Entry<String, Actor> entry : resourcesHud.actorById().entrySet()) {
            Assert.assertEquals(entry.getKey(), entry.getValue().getName());
        }
        Assert.assertThrows(UnsupportedOperationException.class,
                () -> resourcesHud.actorById().put("extra", new Actor()));
    }

    @Test
    public void managedTableAppliesExactNativeCellConstraintsAndRows() {
        MaterializedHud hud = materialize("managed-table.json");
        Table table = (Table) hud.root();
        Cell<?> title = table.getCell(hud.actor("title"));
        Cell<?> actions = table.getCell(hud.actor("actions"));

        Assert.assertEquals(0f, title.getMinWidth(), 0f);
        Assert.assertEquals(96f, title.getPrefHeight(), 0f);
        Assert.assertEquals(16f, title.getPadTop(), 0f);
        Assert.assertEquals(24f, title.getPadRight(), 0f);
        Assert.assertEquals(16f, title.getPadBottom(), 0f);
        Assert.assertEquals(24f, title.getPadLeft(), 0f);
        Assert.assertEquals(1f, title.getFillX(), 0f);
        Assert.assertEquals(0f, title.getFillY(), 0f);
        Assert.assertEquals(Integer.valueOf(1), title.getExpandX());
        Assert.assertEquals(Integer.valueOf(0), title.getExpandY());
        Assert.assertEquals(Integer.valueOf(Align.left), title.getAlign());
        Assert.assertTrue(title.isEndRow());

        Assert.assertEquals(320f, actions.getMinWidth(), 0f);
        Assert.assertEquals(120f, actions.getMinHeight(), 0f);
        Assert.assertEquals(640f, actions.getPrefWidth(), 0f);
        Assert.assertEquals(240f, actions.getPrefHeight(), 0f);
        Assert.assertEquals(1f, actions.getFillX(), 0f);
        Assert.assertEquals(1f, actions.getFillY(), 0f);
        Assert.assertEquals(Integer.valueOf(1), actions.getExpandX());
        Assert.assertEquals(Integer.valueOf(1), actions.getExpandY());
        Assert.assertTrue(actions.isEndRow());

        Table nested = (Table) hud.actor("actions");
        Assert.assertEquals(Integer.valueOf(Align.right),
                nested.getCell(hud.actor("status")).getAlign());
        Assert.assertFalse(nested.getCell(hud.actor("status")).isEndRow());
    }

    @Test
    public void stackPreservesDocumentChildOrder() {
        MaterializedHud hud = materialize("stack.json");
        Stack stack = (Stack) hud.root();
        Assert.assertSame(hud.actor("overlay-background"), stack.getChild(0));
        Assert.assertSame(hud.actor("overlay-title"), stack.getChild(1));
    }

    @Test
    public void containerUsesCommittedChildAndClippingWithoutOwningResources() {
        MaterializedHud hud = materialize("clipped-container.json");
        Container<?> container = (Container<?>) hud.root();
        Assert.assertSame(hud.actor("clipped-content"), container.getActor());
        Assert.assertTrue(container.getClip());
        Assert.assertFalse(resources.isDisposed());
        Assert.assertFalse(com.badlogic.gdx.utils.Disposable.class
                .isAssignableFrom(MaterializedHud.class));
    }

    @Test
    public void freePlacementUsesBottomLeftCoordinatesAndRespondsToParentResize() {
        MaterializedHud hud = materialize("free-layout.json");
        HudFreeGroup root = (HudFreeGroup) hud.root();
        root.validate();

        assertPosition(hud.actor("top-left"), 24f, 1008f);
        assertPosition(hud.actor("center"), 936f, 516f);
        assertPosition(hud.actor("bottom-right"), 1716f, 24f);

        root.setSize(1000f, 500f);
        root.validate();
        assertPosition(hud.actor("top-left"), 24f, 428f);
        assertPosition(hud.actor("center"), 476f, 226f);
        assertPosition(hud.actor("bottom-right"), 796f, 24f);
    }

    @Test
    public void nativeTableAllocatesMixedFreeGroupAndAnchorsFollowAllocatedSize() {
        MaterializedHud hud = materialize("mixed-layout.json");
        Table root = (Table) hud.root();
        root.validate();
        Actor freeSurface = hud.actor("free-surface");
        Assert.assertEquals(1920f, freeSurface.getWidth(), 0f);
        Assert.assertEquals(1080f, freeSurface.getHeight(), 0f);
        assertPosition(hud.actor("objective"), 1528f, 976f);

        root.setSize(1000f, 500f);
        root.invalidateHierarchy();
        root.validate();
        Assert.assertEquals(1000f, freeSurface.getWidth(), 0f);
        Assert.assertEquals(500f, freeSurface.getHeight(), 0f);
        assertPosition(hud.actor("objective"), 608f, 396f);
    }

    @Test
    public void resourcesResolveFromPreparedSnapshotWithoutCreatingTextures() {
        int texturesBefore = generatedTextures;
        HudVisualResources visualResources = resources;
        MaterializedHud hud = new HudMaterializer().materialize(
                validated("resources.json"), visualResources);
        Image regionImage = (Image) hud.actor("scene-art");
        Image drawableImage = (Image) hud.actor("ninepatch-panel");
        Label label = (Label) hud.actor("resource-label");
        TextButton button = (TextButton) hud.actor("resource-button");

        Texture atlasTexture = resources.atlas().findRegion("inventory-art").getTexture();
        Assert.assertSame(atlasTexture,
                ((com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable)
                        regionImage.getDrawable()).getRegion().getTexture());
        Assert.assertSame(resources.skin().getDrawable("inventory-panel"),
                drawableImage.getDrawable());
        Assert.assertSame(resources.skin().get(
                "hud-body-bitmap", Label.LabelStyle.class), label.getStyle());
        Assert.assertSame(resources.skin().get(
                "hud-primary", TextButton.TextButtonStyle.class), button.getStyle());
        Assert.assertEquals(texturesBefore, generatedTextures);
    }

    @Test
    public void materializesBorrowedVisualResourcesWithoutOwningTheProvider() {
        TextureRegion plainRegion = new TextureRegion();
        FakeVisualResources visualResources = new FakeVisualResources(
                plainRegion,
                resources.skin().getDrawable("inventory-panel"),
                resources.skin().get("hud-body-bitmap", Label.LabelStyle.class),
                resources.skin().get("hud-primary", TextButton.TextButtonStyle.class));
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentCodec().read(new FileHandle(
                        "src/test/resources/" + FIXTURE_ROOT + "resources.json")));
        Assert.assertTrue(validation.issues().toString(), validation.isValid());

        MaterializedHud hud = new HudMaterializer().materialize(
                validation.validatedDocument(), visualResources);

        Image image = (Image) hud.actor("scene-art");
        Assert.assertSame(plainRegion,
                ((com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable)
                        image.getDrawable()).getRegion());
        Assert.assertSame(visualResources.drawable,
                ((Image) hud.actor("ninepatch-panel")).getDrawable());
        Assert.assertSame(visualResources.labelStyle,
                ((Label) hud.actor("resource-label")).getStyle());
        Assert.assertSame(visualResources.textButtonStyle,
                ((TextButton) hud.actor("resource-button")).getStyle());
        Assert.assertFalse(visualResources.disposed);
    }

    @Test
    public void callersCanExtendNativeTreeWithoutChangingPersistedIndex() {
        MaterializedHud hud = materialize("free-layout.json");
        Group root = (Group) hud.root();
        Actor custom = new Actor();
        custom.setName("runtime-only");
        custom.addListener(new EventListener() {
            @Override
            public boolean handle(Event event) {
                return false;
            }
        });
        custom.addAction(Actions.delay(1f));
        root.addActor(custom);

        Assert.assertSame(custom, root.findActor("runtime-only"));
        Assert.assertEquals(1, custom.getListeners().size);
        Assert.assertEquals(1, custom.getActions().size);
        Assert.assertNull(hud.actor("runtime-only"));
        Assert.assertEquals(4, hud.actorById().size());
    }

    @Test
    public void groupDirectPlacementRemainsAnOrdinaryNativeChildRelationship() {
        HudNode root = new HudNode("direct-root", HudNodeKind.GROUP);
        HudNode child = new HudNode("direct-child", HudNodeKind.GROUP);
        root.children.add(HudChild.direct(child));
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentV1(root), resources);
        Assert.assertTrue(validation.issues().toString(), validation.isValid());

        MaterializedHud hud = new HudMaterializer().materialize(
                validation.validatedDocument(), resources);

        Assert.assertSame(hud.actor("direct-child"), ((Group) hud.root()).getChild(0));
    }

    @Test
    public void missingPreparedResourceFailsBeforeAnyTreeIsPublished() {
        HudNode root = new HudNode("missing-region", HudNodeKind.IMAGE);
        root.image = new HudImageData();
        root.image.resourceName = "absent-region";
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentV1(root));
        Assert.assertTrue(validation.issues().toString(), validation.isValid());

        IllegalStateException failure = Assert.assertThrows(IllegalStateException.class,
                () -> new HudMaterializer().materialize(
                        validation.validatedDocument(), resources));

        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("missing-region"));
        Assert.assertFalse(resources.isDisposed());
    }

    @Test
    public void labelWithoutCustomStyleUsesThePreparedBuiltInStyle() {
        HudNode root = new HudNode("label", HudNodeKind.LABEL);
        root.label = new games.pixscape.runtime.hud.document.HudLabelData();
        root.label.text = "Label";
        HudValidationResult validation = new HudDocumentValidator().validate(new HudDocumentV1(root));
        Assert.assertTrue(validation.issues().toString(), validation.isValid());

        Label.LabelStyle builtIn = resources.labelStyle("hud-body-bitmap");
        FakeVisualResources visual = new FakeVisualResources(null, null, builtIn, null);
        Label label = (Label) new HudMaterializer().materialize(
                validation.validatedDocument(), visual).root();

        Assert.assertSame(builtIn, label.getStyle());
    }

    @Test
    public void publicMaterializerBoundaryAcceptsOnlyValidatedDocuments() throws Exception {
        Method visualMaterialize = HudMaterializer.class.getMethod(
                "materialize", ValidatedHudDocument.class, HudVisualResources.class);
        Assert.assertEquals(MaterializedHud.class, visualMaterialize.getReturnType());
        Method compatibilityMaterialize = HudMaterializer.class.getMethod(
                "materialize", ValidatedHudDocument.class, HudResources.class);
        Assert.assertEquals(MaterializedHud.class, compatibilityMaterialize.getReturnType());
        for (Method method : HudMaterializer.class.getMethods()) {
            if (!"materialize".equals(method.getName())) continue;
            Assert.assertEquals(ValidatedHudDocument.class, method.getParameterTypes()[0]);
        }
    }

    private static final class FakeVisualResources implements HudVisualResources, Disposable {
        private final TextureRegion region;
        private final Drawable drawable;
        private final Label.LabelStyle labelStyle;
        private final TextButton.TextButtonStyle textButtonStyle;
        private boolean disposed;

        private FakeVisualResources(TextureRegion region, Drawable drawable,
                                    Label.LabelStyle labelStyle,
                                    TextButton.TextButtonStyle textButtonStyle) {
            this.region = region;
            this.drawable = drawable;
            this.labelStyle = labelStyle;
            this.textButtonStyle = textButtonStyle;
        }

        @Override
        public TextureRegion region(String name) {
            return "inventory-art".equals(name) ? region : null;
        }

        @Override
        public Drawable drawable(String name) {
            return "inventory-panel".equals(name) ? drawable : null;
        }

        @Override
        public Label.LabelStyle labelStyle(String name) {
            return "hud-body-bitmap".equals(name) ? labelStyle : null;
        }

        @Override public Label.LabelStyle builtInLabelStyle() { return labelStyle; }

        @Override
        public TextButton.TextButtonStyle textButtonStyle(String name) {
            return "hud-primary".equals(name) ? textButtonStyle : null;
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }

    private void assertMapping(String fixture, String id, Class<?> type) {
        MaterializedHud hud = materialize(fixture);
        assertActor(hud, id, type);
        Assert.assertEquals(validated(fixture).nodeIndex().size(), hud.actorById().size());
        for (Map.Entry<String, Actor> entry : hud.actorById().entrySet()) {
            Assert.assertEquals(entry.getKey(), entry.getValue().getName());
        }
    }

    private static void assertActor(MaterializedHud hud, String id, Class<?> type) {
        Assert.assertTrue(type.isInstance(hud.actor(id)));
        Assert.assertEquals(id, hud.actor(id).getName());
    }

    private MaterializedHud materialize(String fixture) {
        return new HudMaterializer().materialize(validated(fixture), resources);
    }

    private ValidatedHudDocument validated(String fixture) {
        HudValidationResult result = new HudDocumentValidator().validate(
                new HudDocumentCodec().read(new FileHandle(
                        "src/test/resources/" + FIXTURE_ROOT + fixture)), resources);
        Assert.assertTrue(result.issues().toString(), result.isValid());
        return result.validatedDocument();
    }

    private static void assertPosition(Actor actor, float x, float y) {
        Assert.assertEquals(x, actor.getX(), 0.001f);
        Assert.assertEquals(y, actor.getY(), 0.001f);
    }

    private Object glValue(String name, Object[] args, Class<?> returnType, int[] nextHandle) {
        if ("glGenTexture".equals(name)) {
            generatedTextures++;
            return nextHandle[0]++;
        }
        if ("glGetIntegerv".equals(name) && args != null && args.length >= 2) {
            int parameter = (Integer) args[0];
            int value = parameter == GL30.GL_MAX_ARRAY_TEXTURE_LAYERS ? 16 : 4096;
            ((IntBuffer) args[1]).put(0, value);
            return null;
        }
        return defaultValue(returnType);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
