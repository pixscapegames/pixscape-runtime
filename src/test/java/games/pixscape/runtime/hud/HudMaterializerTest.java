package games.pixscape.runtime.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudContainerData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageButtonData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudTextFieldData;
import games.pixscape.runtime.hud.document.HudSelectBoxData;
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
    private Application previousApp;
    private HudResources resources;
    private HudSelectedResources selectedResources;
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
        previousApp = Gdx.app;
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
        Gdx.app = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(), new Class<?>[]{Application.class},
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
        asset.documentId = "hud/test.json";
        asset.skinId = "ui/game.json";
        asset.atlasId = "ui/game.atlas";
        resources = HudResources.prepareStandalone(asset, root, HudResourcesTest.fullRequirements());
        selectedResources = resources.select(asset.skinId);
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
        Gdx.app = previousApp;
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
        HudScreenAsset emptyAsset = new HudScreenAsset();
        emptyAsset.documentId = "hud/layout-only.json";
        HudResources emptyResources = HudResources.prepareStandalone(emptyAsset, root, requirements);
        try {
            MaterializedHud hud = new HudMaterializer().materialize(
                    validation.validatedDocument(), emptyResources.select(null));
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
                new HudDocumentV1(empty), selectedResources);
        Assert.assertTrue(emptyValidation.issues().toString(), emptyValidation.isValid());

        MaterializedHud emptyHud = new HudMaterializer().materialize(
                emptyValidation.validatedDocument(), selectedResources);
        Container<?> emptyActor = (Container<?>) emptyHud.actor("empty");
        Assert.assertNull(emptyActor.getActor());
        Assert.assertEquals(1, emptyHud.actorById().size());

        HudNode occupied = new HudNode("occupied", HudNodeKind.CONTAINER);
        occupied.container = new HudContainerData();
        occupied.children.add(HudChild.direct(new HudNode("group", HudNodeKind.GROUP)));
        HudValidationResult occupiedValidation = new HudDocumentValidator().validate(
                new HudDocumentV1(occupied), selectedResources);
        Assert.assertTrue(occupiedValidation.issues().toString(), occupiedValidation.isValid());

        MaterializedHud occupiedHud = new HudMaterializer().materialize(
                occupiedValidation.validatedDocument(), selectedResources);
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
    public void tableKeepsExplicitWidthAndNativePreferredHeightIndependent() {
        HudNode root = new HudNode("root", HudNodeKind.TABLE);
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
        button.textButton.text = "One line";
        HudCellConstraints constraints = new HudCellConstraints();
        constraints.prefWidth = 220f;
        root.children.add(HudChild.cell(button, constraints));

        MaterializedHud first = materialize(new HudDocumentV1(root));
        Table firstTable = (Table) first.root();
        firstTable.pack();
        firstTable.validate();
        TextButton firstButton = (TextButton) first.actor("button");
        float firstHeight = firstButton.getHeight();
        Assert.assertEquals(220f, firstButton.getWidth(), 0.01f);
        Assert.assertEquals(firstButton.getPrefHeight(), firstHeight, 0.01f);

        root.children.get(0).node.textButton.text = "One line\nSecond line";
        MaterializedHud second = materialize(new HudDocumentV1(root));
        Table secondTable = (Table) second.root();
        secondTable.pack();
        secondTable.validate();
        TextButton secondButton = (TextButton) second.actor("button");
        Assert.assertEquals(220f, secondButton.getWidth(), 0.01f);
        Assert.assertEquals(secondButton.getPrefHeight(), secondButton.getHeight(), 0.01f);
        Assert.assertTrue(secondButton.getHeight() > firstHeight);
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
        HudVisualResources visualResources = selectedResources;
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
        Assert.assertSame(selectedResources.skin().getDrawable("inventory-panel"),
                drawableImage.getDrawable());
        Assert.assertSame(selectedResources.skin().get(
                "hud-body-bitmap", Label.LabelStyle.class), label.getStyle());
        Assert.assertSame(selectedResources.skin().get(
                "hud-primary", TextButton.TextButtonStyle.class), button.getStyle());
        Assert.assertEquals(texturesBefore, generatedTextures);
    }

    @Test
    public void materializesBorrowedVisualResourcesWithoutOwningTheProvider() {
        TextureRegion plainRegion = new TextureRegion();
        FakeVisualResources visualResources = new FakeVisualResources(
                plainRegion,
                selectedResources.skin().getDrawable("inventory-panel"),
                selectedResources.skin().get("hud-body-bitmap", Label.LabelStyle.class),
                selectedResources.skin().get("hud-primary", TextButton.TextButtonStyle.class));
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
    public void materializesDefaultTextButtonFromBuiltInStyle() {
        TextButton.TextButtonStyle style = selectedResources.builtInTextButtonStyle();
        FakeVisualResources visualResources = new FakeVisualResources(
                new TextureRegion(), selectedResources.skin().getDrawable("inventory-panel"),
                selectedResources.skin().get("hud-body-bitmap", Label.LabelStyle.class), style);
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
        button.textButton.text = "Button";
        HudValidationResult validation = new HudDocumentValidator().validate(
                new games.pixscape.runtime.hud.document.HudDocumentV1(button));

        MaterializedHud hud = new HudMaterializer().materialize(
                validation.validatedDocument(), visualResources);

        Assert.assertSame(style, ((TextButton) hud.actor("button")).getStyle());
    }

    @Test
    public void materializesNativeTextFieldWithAuthoredPropertiesAndUsableDefaultStyle() {
        HudNode node = new HudNode("field", HudNodeKind.TEXT_FIELD);
        node.textField = new HudTextFieldData();
        node.textField.text = "abcdef";
        node.textField.messageText = "Enter value";
        node.textField.maxLength = 3;
        node.textField.passwordMode = true;

        MaterializedHud hud = materialize(new HudDocumentV1(node));
        TextField field = (TextField) hud.actor("field");

        Assert.assertEquals("abc", field.getText());
        Assert.assertEquals("Enter value", field.getMessageText());
        Assert.assertEquals(3, field.getMaxLength());
        Assert.assertTrue(field.isPasswordMode());
        Assert.assertSame(selectedResources.builtInTextFieldStyle(), field.getStyle());
        Assert.assertNotNull(field.getStyle().font);
        Assert.assertNotNull(field.getStyle().cursor);
        Assert.assertNotNull(field.getStyle().selection);
        Assert.assertNotNull(field.getStyle().background);
        Assert.assertNotNull(field.getStyle().messageFontColor);
        Assert.assertEquals(field.getPrefWidth(), field.getWidth(), 0.01f);
        Assert.assertEquals(field.getPrefHeight(), field.getHeight(), 0.01f);

        Assert.assertEquals("abcdef", node.textField.text);

        HudNode unlimitedNode = new HudNode("unlimited", HudNodeKind.TEXT_FIELD);
        unlimitedNode.textField = new HudTextFieldData();
        unlimitedNode.textField.text = "abcdef";
        unlimitedNode.textField.maxLength = 0;
        TextField unlimited = (TextField) materialize(
                new HudDocumentV1(unlimitedNode)).actor("unlimited");
        Assert.assertEquals("abcdef", unlimited.getText());
    }

    @Test
    public void textFieldUsesAValidCustomNativeStyleAndRejectsAMissingOne() {
        TextField.TextFieldStyle custom = new TextField.TextFieldStyle();
        custom.font = selectedResources.builtInTextFieldStyle().font;
        custom.fontColor = selectedResources.builtInTextFieldStyle().fontColor;
        selectedResources.skin().add("compact-field", custom, TextField.TextFieldStyle.class);
        TextField.TextFieldStyle missingColor = new TextField.TextFieldStyle();
        missingColor.font = selectedResources.builtInTextFieldStyle().font;
        selectedResources.skin().add(
                "missing-color", missingColor, TextField.TextFieldStyle.class);
        HudNode node = new HudNode("field", HudNodeKind.TEXT_FIELD);
        node.textField = new HudTextFieldData();
        node.textField.styleName = "compact-field";

        MaterializedHud hud = materialize(new HudDocumentV1(node));
        Assert.assertSame(custom, ((TextField) hud.actor("field")).getStyle());

        node.textField.styleName = "missing-color";
        HudValidationResult missingColorValidation = new HudDocumentValidator().validate(
                new HudDocumentV1(node), selectedResources);
        Assert.assertFalse(missingColorValidation.isValid());
        Assert.assertTrue(missingColorValidation.issues().get(0).message()
                .contains("TEXT_FIELD Skin style 'missing-color' is missing or unusable."));

        node.textField.styleName = "missing-field";
        HudValidationResult invalid = new HudDocumentValidator().validate(
                new HudDocumentV1(node), selectedResources);
        Assert.assertFalse(invalid.isValid());
    }

    @Test
    public void materializesNativeSelectBoxWithoutMutatingItsAuthoredSelection() {
        HudNode node = new HudNode("choice", HudNodeKind.SELECT_BOX);
        node.selectBox = new HudSelectBoxData();
        node.selectBox.items.add("North");
        node.selectBox.items.add("South");
        node.selectBox.selectedIndex = 1;
        node.selectBox.maxListCount = 1;

        SelectBox<String> box = (SelectBox<String>) materialize(
                new HudDocumentV1(node)).actor("choice");
        Assert.assertEquals("South", box.getSelected());
        Assert.assertEquals(1, box.getMaxListCount());
        Assert.assertSame(selectedResources.builtInSelectBoxStyle(), box.getStyle());
        box.setSelected("North");
        Assert.assertEquals(1, node.selectBox.selectedIndex);
    }

    @Test
    public void materializesNativeCheckBoxWithoutMutatingItsAuthoredState() {
        HudNode node = new HudNode("check", HudNodeKind.CHECK_BOX);
        node.checkBox = new games.pixscape.runtime.hud.document.HudCheckBoxData();
        node.checkBox.text = "Enabled";
        node.checkBox.checked = true;
        node.checkBox.disabled = false;

        CheckBox box = (CheckBox) materialize(new HudDocumentV1(node)).actor("check");
        Assert.assertEquals("Enabled", box.getText().toString());
        Assert.assertTrue(box.isChecked());
        Assert.assertFalse(box.isDisabled());
        Assert.assertSame(selectedResources.builtInCheckBoxStyle(), box.getStyle());
        box.setChecked(false);
        Assert.assertTrue(node.checkBox.checked);
        box.setDisabled(true);
        Assert.assertFalse(node.checkBox.disabled);
    }

    @Test
    public void imageButtonUsesNativeStyleStatesWithoutMutatingSharedStyle() {
        ImageButton.ImageButtonStyle shared = selectedResources.builtInImageButtonStyle();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode first = imageButton("first", "inventory-art");
        HudNode second = new HudNode("second", HudNodeKind.IMAGE_BUTTON);
        second.imageButton = new HudImageButtonData();
        root.children.add(HudChild.direct(first));
        root.children.add(HudChild.direct(second));

        MaterializedHud hud = materialize(new HudDocumentV1(root));
        ImageButton overridden = (ImageButton) hud.actor("first");
        ImageButton defaultButton = (ImageButton) hud.actor("second");

        Assert.assertNotSame(shared, overridden.getStyle());
        Assert.assertSame(shared, defaultButton.getStyle());
        Assert.assertNull(shared.imageUp);
        Assert.assertNotNull(overridden.getStyle().imageUp);
        Assert.assertEquals(overridden.getPrefWidth(), overridden.getWidth(), 0.01f);
        Assert.assertEquals(overridden.getPrefHeight(), overridden.getHeight(), 0.01f);
        Assert.assertTrue(overridden.getPrefWidth() > 0f);
        Assert.assertTrue(overridden.getPrefHeight() > 0f);
    }

    private static HudNode imageButton(String id, String imageUp) {
        HudNode button = new HudNode(id, HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.imageUp = new HudImageData();
        button.imageButton.imageUp.source = HudImageSource.REGION;
        button.imageButton.imageUp.resourceName = imageUp;
        return button;
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
                new HudDocumentV1(root), selectedResources);
        Assert.assertTrue(validation.issues().toString(), validation.isValid());

        MaterializedHud hud = new HudMaterializer().materialize(
                validation.validatedDocument(), selectedResources);

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
                        validation.validatedDocument(), selectedResources));

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

        Label.LabelStyle builtIn = selectedResources.labelStyle("hud-body-bitmap");
        FakeVisualResources visual = new FakeVisualResources(null, null, builtIn, null);
        Label label = (Label) new HudMaterializer().materialize(
                validation.validatedDocument(), visual).root();

        Assert.assertSame(builtIn, label.getStyle());
    }

    @Test
    public void labelFontOverrideCopiesSharedStyleAndChangesOnlyFont() {
        HudNode root = new HudNode("label", HudNodeKind.LABEL);
        root.label = new games.pixscape.runtime.hud.document.HudLabelData();
        root.label.text = "Label";
        root.label.fontAssetId = 42;
        Label.LabelStyle shared = selectedResources.labelStyle("hud-body-bitmap");
        BitmapFont override = selectedResources.builtInLabelStyle().font;
        FakeVisualResources visual = new FakeVisualResources(
                null, null, shared, null, override);
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentV1(root));
        Label label = (Label) new HudMaterializer().materialize(
                validation.validatedDocument(), visual).root();

        Assert.assertNotSame(shared, label.getStyle());
        Assert.assertSame(override, label.getStyle().font);
        Assert.assertNotSame(override, shared.font);
    }

    @Test
    public void publicMaterializerBoundaryAcceptsOnlyValidatedDocuments() throws Exception {
        Method visualMaterialize = HudMaterializer.class.getMethod(
                "materialize", ValidatedHudDocument.class, HudVisualResources.class);
        Assert.assertEquals(MaterializedHud.class, visualMaterialize.getReturnType());
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
        private final BitmapFont bitmapFont;
        private boolean disposed;

        private FakeVisualResources(TextureRegion region, Drawable drawable,
                                    Label.LabelStyle labelStyle,
                                    TextButton.TextButtonStyle textButtonStyle) {
            this(region, drawable, labelStyle, textButtonStyle, null);
        }

        private FakeVisualResources(TextureRegion region, Drawable drawable,
                                    Label.LabelStyle labelStyle,
                                    TextButton.TextButtonStyle textButtonStyle,
                                    BitmapFont bitmapFont) {
            this.region = region;
            this.drawable = drawable;
            this.labelStyle = labelStyle;
            this.textButtonStyle = textButtonStyle;
            this.bitmapFont = bitmapFont;
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
        @Override public BitmapFont bitmapFont(int assetId) {
            return assetId == 42 ? bitmapFont : null;
        }

        @Override
        public TextButton.TextButtonStyle textButtonStyle(String name) {
            return "hud-primary".equals(name) ? textButtonStyle : null;
        }

        @Override public TextButton.TextButtonStyle builtInTextButtonStyle() {
            return textButtonStyle;
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
        return new HudMaterializer().materialize(validated(fixture), selectedResources);
    }

    private MaterializedHud materialize(HudDocumentV1 document) {
        HudValidationResult result = new HudDocumentValidator().validate(document, selectedResources);
        Assert.assertTrue(result.issues().toString(), result.isValid());
        return new HudMaterializer().materialize(result.validatedDocument(), selectedResources);
    }

    private ValidatedHudDocument validated(String fixture) {
        HudValidationResult result = new HudDocumentValidator().validate(
                new HudDocumentCodec().read(new FileHandle(
                        "src/test/resources/" + FIXTURE_ROOT + fixture)), selectedResources);
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
