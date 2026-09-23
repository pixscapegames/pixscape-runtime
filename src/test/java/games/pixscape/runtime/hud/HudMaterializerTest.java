package games.pixscape.runtime.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.github.tommyettinger.textra.Font;
import com.github.tommyettinger.textra.TypingLabel;
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
import games.pixscape.runtime.hud.document.HudImageTextButtonData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudTextFieldData;
import games.pixscape.runtime.hud.document.HudTextraLabelData;
import games.pixscape.runtime.hud.document.HudSelectBoxData;
import games.pixscape.runtime.hud.document.HudSliderData;
import games.pixscape.runtime.hud.document.HudProgressBarData;
import games.pixscape.runtime.hud.document.HudScrollPaneData;
import games.pixscape.runtime.hud.document.HudSliderOrientation;
import games.pixscape.runtime.hud.document.HudTooltipData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudDialogData;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudTextButtonData;
import games.pixscape.runtime.hud.document.HudWindowAction;
import games.pixscape.runtime.hud.document.HudWindowActionKind;
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
        hud.dispose();
        Assert.assertFalse(resources.isDisposed());
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
    public void materializesDefaultImageTextButtonFromBuiltInStyleWithoutMutatingIt() {
        HudNode button = new HudNode("button", HudNodeKind.IMAGE_TEXT_BUTTON);
        button.imageTextButton = new HudImageTextButtonData();
        button.imageTextButton.text = "Button";
        HudValidationResult validation = new HudDocumentValidator().validate(new HudDocumentV1(button));

        MaterializedHud hud = new HudMaterializer().materialize(
                validation.validatedDocument(), selectedResources);

        ImageTextButton materialized = (ImageTextButton) hud.actor("button");
        Assert.assertSame(selectedResources.builtInImageTextButtonStyle(), materialized.getStyle());
        Assert.assertEquals("Button", materialized.getText().toString());
    }

    @Test
    public void builtInImageTextButtonStyleCopiesNativeButtonOffsetsWithoutMutatingSource() {
        TextButton.TextButtonStyle source = new TextButton.TextButtonStyle(
                selectedResources.builtInTextButtonStyle());
        source.pressedOffsetX = 3f;
        source.pressedOffsetY = -2f;
        source.unpressedOffsetX = 1f;
        source.unpressedOffsetY = 4f;
        source.checkedOffsetX = -5f;
        source.checkedOffsetY = 6f;

        ImageTextButton.ImageTextButtonStyle copy = HudBuiltInImageTextButtonStyle.create(source);

        Assert.assertEquals(3f, copy.pressedOffsetX, 0f);
        Assert.assertEquals(-2f, copy.pressedOffsetY, 0f);
        Assert.assertEquals(1f, copy.unpressedOffsetX, 0f);
        Assert.assertEquals(4f, copy.unpressedOffsetY, 0f);
        Assert.assertEquals(-5f, copy.checkedOffsetX, 0f);
        Assert.assertEquals(6f, copy.checkedOffsetY, 0f);
        copy.pressedOffsetX = 99f;
        Assert.assertEquals(3f, source.pressedOffsetX, 0f);
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
    public void textFieldWithoutOverridePreservesItsDistinctPlaceholderFont() {
        BitmapFont messageFont = new BitmapFont(new BitmapFont.BitmapFontData(),
                new TextureRegion(), false);
        TextField.TextFieldStyle shared = new TextField.TextFieldStyle(
                selectedResources.builtInTextFieldStyle());
        shared.messageFont = messageFont;
        selectedResources.skin().add("distinct-placeholder", shared,
                TextField.TextFieldStyle.class);
        HudNode node = new HudNode("field", HudNodeKind.TEXT_FIELD);
        node.textField = new HudTextFieldData();
        node.textField.styleName = "distinct-placeholder";
        try {
            TextField field = (TextField) materialize(new HudDocumentV1(node)).actor("field");
            Assert.assertSame(shared, field.getStyle());
            Assert.assertSame(messageFont, field.getStyle().messageFont);
            Assert.assertNotSame(field.getStyle().font, field.getStyle().messageFont);
        } finally {
            messageFont.dispose();
        }
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
    public void materializesNativeSliderWithNativeSnappingAndChangeEvents() {
        HudNode node = new HudNode("slider", HudNodeKind.SLIDER);
        node.slider = new HudSliderData();
        node.slider.min = 0f;
        node.slider.max = 100f;
        node.slider.stepSize = 10f;
        node.slider.value = 53f;
        node.slider.disabled = true;
        Slider.SliderStyle shared = selectedResources.builtInSliderStyle();
        Drawable background = shared.background;

        Slider slider = (Slider) materialize(new HudDocumentV1(node)).actor("slider");

        Assert.assertEquals(0f, slider.getMinValue(), 0f);
        Assert.assertEquals(100f, slider.getMaxValue(), 0f);
        Assert.assertEquals(10f, slider.getStepSize(), 0f);
        Assert.assertEquals(50f, slider.getValue(), 0f);
        Assert.assertEquals(53f, node.slider.value, 0f);
        Assert.assertTrue(slider.isDisabled());
        Assert.assertFalse(slider.isVertical());
        Assert.assertEquals(140f, slider.getPrefWidth(), 0f);
        Assert.assertSame(shared, slider.getStyle());
        Assert.assertSame(background, shared.background);

        final int[] changes = {0};
        slider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { changes[0]++; }
        });
        slider.setDisabled(false);
        slider.setValue(80f);
        Assert.assertEquals(1, changes[0]);
        Assert.assertEquals(80f, slider.getValue(), 0f);
    }

    @Test
    public void materializesNativeProgressBarWithNativeSnappingWithoutMutatingTheDocument() {
        HudNode node = new HudNode("progress", HudNodeKind.PROGRESS_BAR);
        node.progressBar = new HudProgressBarData();
        node.progressBar.min = 0f;
        node.progressBar.max = 100f;
        node.progressBar.stepSize = 10f;
        node.progressBar.value = 53f;
        node.progressBar.orientation = HudSliderOrientation.VERTICAL;
        ProgressBar.ProgressBarStyle shared = selectedResources.builtInProgressBarStyle();

        ProgressBar progressBar = (ProgressBar) materialize(new HudDocumentV1(node)).actor("progress");

        Assert.assertEquals(50f, progressBar.getValue(), 0f);
        Assert.assertEquals(53f, node.progressBar.value, 0f);
        Assert.assertTrue(progressBar.isVertical());
        Assert.assertSame(shared, progressBar.getStyle());
        Assert.assertNotNull(progressBar.getStyle().background);
        Assert.assertNotNull(progressBar.getStyle().knobBefore);
        Assert.assertNull(progressBar.getStyle().knob);
    }

    @Test
    public void materializesEveryNativeNonNullCustomProgressBarStyleWithoutMutation() {
        ProgressBar.ProgressBarStyle backgroundAndKnob = new ProgressBar.ProgressBarStyle();
        backgroundAndKnob.background = new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable();
        backgroundAndKnob.knob = new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable();
        ProgressBar.ProgressBarStyle knobAfterOnly = new ProgressBar.ProgressBarStyle();
        knobAfterOnly.knobAfter = new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable();
        ProgressBar.ProgressBarStyle withoutBackground = new ProgressBar.ProgressBarStyle();
        withoutBackground.knob = new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable();
        ProgressBar.ProgressBarStyle empty = new ProgressBar.ProgressBarStyle();
        Object background = backgroundAndKnob.background;
        Object knob = backgroundAndKnob.knob;
        Object knobAfter = knobAfterOnly.knobAfter;
        Object knobWithoutBackground = withoutBackground.knob;

        for (ProgressBar.ProgressBarStyle custom : new ProgressBar.ProgressBarStyle[]{
                backgroundAndKnob, knobAfterOnly, withoutBackground, empty}) {
            HudNode node = new HudNode("progress", HudNodeKind.PROGRESS_BAR);
            node.progressBar = new HudProgressBarData();
            node.progressBar.styleName = "custom";
            MaterializedHud hud = new HudMaterializer().materialize(
                    new HudDocumentValidator().validate(new HudDocumentV1(node)).validatedDocument(),
                    new HudVisualResources() {
                        @Override public TextureRegion region(String name) { return null; }
                        @Override public Drawable drawable(String name) { return null; }
                        @Override public Label.LabelStyle labelStyle(String name) { return null; }
                        @Override public TextButton.TextButtonStyle textButtonStyle(String name) { return null; }
                        @Override public ProgressBar.ProgressBarStyle progressBarStyle(String name) {
                            return "custom".equals(name) ? custom : null;
                        }
                    });
            Assert.assertSame(custom, ((ProgressBar) hud.actor("progress")).getStyle());
            hud.dispose();
        }
        Assert.assertSame(background, backgroundAndKnob.background);
        Assert.assertSame(knob, backgroundAndKnob.knob);
        Assert.assertSame(knobAfter, knobAfterOnly.knobAfter);
        Assert.assertSame(knobWithoutBackground, withoutBackground.knob);
        Assert.assertNull(backgroundAndKnob.knobBefore);
        Assert.assertNull(knobAfterOnly.knobBefore);
        Assert.assertNull(withoutBackground.background);
    }

    @Test
    public void materializesNativeScrollablePaneAndPreservesSharedStyle() {
        HudNode paneNode = new HudNode("pane", HudNodeKind.SCROLL_PANE);
        paneNode.scrollPane = new HudScrollPaneData();
        paneNode.actor.width = 100f;
        paneNode.actor.height = 80f;
        HudNode content = new HudNode("content", HudNodeKind.GROUP);
        content.actor.width = 100f;
        content.actor.height = 240f;
        paneNode.children.add(HudChild.direct(content));
        ScrollPane.ScrollPaneStyle shared = selectedResources.builtInScrollPaneStyle();
        Drawable background = shared.background;

        MaterializedHud hud = materialize(new HudDocumentV1(paneNode));
        ScrollPane pane = (ScrollPane) hud.actor("pane");
        pane.validate();

        Assert.assertEquals(content.id, pane.getActor().getName());
        Assert.assertTrue(pane.getMaxY() > 0f);
        pane.setScrollPercentY(1f);
        pane.updateVisualScroll();
        Assert.assertEquals(pane.getMaxY(), pane.getVisualScrollY(), .001f);
        Assert.assertSame(shared, pane.getStyle());
        Assert.assertSame(background, shared.background);
        Assert.assertNotNull(shared.hScroll);
        Assert.assertNotNull(shared.vScroll);
        Assert.assertNotNull(shared.hScrollKnob);
        Assert.assertNotNull(shared.vScrollKnob);
    }

    @Test
    public void materializesEmptyCustomScrollPaneStyleWithoutMutation() {
        HudNode node = new HudNode("pane", HudNodeKind.SCROLL_PANE);
        node.scrollPane = new HudScrollPaneData();
        node.scrollPane.styleName = "empty";
        ScrollPane.ScrollPaneStyle shared = new ScrollPane.ScrollPaneStyle();
        MaterializedHud hud = new HudMaterializer().materialize(
                new HudDocumentValidator().validate(new HudDocumentV1(node)).validatedDocument(),
                new HudVisualResources() {
                    @Override public TextureRegion region(String name) { return null; }
                    @Override public Drawable drawable(String name) { return null; }
                    @Override public Label.LabelStyle labelStyle(String name) { return null; }
                    @Override public TextButton.TextButtonStyle textButtonStyle(String name) { return null; }
                    @Override public ScrollPane.ScrollPaneStyle scrollPaneStyle(String name) {
                        return "empty".equals(name) ? shared : null;
                    }
                });

        Assert.assertSame(shared, ((ScrollPane) hud.actor("pane")).getStyle());
        Assert.assertNull(shared.background);
        Assert.assertNull(shared.hScrollKnob);
        Assert.assertNull(shared.vScrollKnob);
        hud.dispose();
    }

    @Test
    public void verticalSliderUsesNativeVerticalNaturalSize() {
        HudNode node = new HudNode("slider", HudNodeKind.SLIDER);
        node.slider = new HudSliderData();
        node.slider.orientation = HudSliderOrientation.VERTICAL;

        Slider slider = (Slider) materialize(new HudDocumentV1(node)).actor("slider");

        Assert.assertTrue(slider.isVertical());
        Assert.assertEquals(140f, slider.getPrefHeight(), 0f);
        Assert.assertTrue(slider.getPrefWidth() < slider.getPrefHeight());
    }

    @Test
    public void customSliderUsesTheSharedSkinStyleWithoutMutation() {
        HudNode node = new HudNode("slider", HudNodeKind.SLIDER);
        node.slider = new HudSliderData();
        node.slider.styleName = "custom";
        Slider.SliderStyle custom = new Slider.SliderStyle();
        custom.background = new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable();
        custom.knob = new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable();
        Drawable background = custom.background;
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentV1(node));

        MaterializedHud hud = new HudMaterializer().materialize(
                validation.validatedDocument(), new HudVisualResources() {
                    @Override public TextureRegion region(String name) { return null; }
                    @Override public Drawable drawable(String name) { return null; }
                    @Override public Label.LabelStyle labelStyle(String name) { return null; }
                    @Override public TextButton.TextButtonStyle textButtonStyle(String name) { return null; }
                    @Override public Slider.SliderStyle sliderStyle(String name) {
                        return "custom".equals(name) ? custom : null;
                    }
                });
        Slider slider = (Slider) hud.actor("slider");

        Assert.assertSame(custom, slider.getStyle());
        Assert.assertSame(background, custom.background);
        hud.dispose();
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
    public void textraLabelUsesNativeActorIsolatedFontAndPreservesSharedSources() {
        BitmapFont source = selectedResources.builtInLabelStyle().font;
        int sourceRegionCount = source.getRegions().size;
        BitmapFont.Glyph sourceBlock = source.getData().getGlyph('\u2588');
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode firstNode = textraLabel("first", true);
        HudNode secondNode = textraLabel("second", true);
        root.children.add(HudChild.direct(firstNode));
        root.children.add(HudChild.direct(secondNode));

        MaterializedHud hud = materialize(new HudDocumentV1(root));
        TypingLabel first = (TypingLabel) hud.actor("first");
        TypingLabel second = (TypingLabel) hud.actor("second");
        Font prepared = selectedResources.textraFont(source);

        Assert.assertNotSame(prepared, first.getFont());
        Assert.assertNotSame(first.getFont(), second.getFont());
        Assert.assertSame(first.getFont().mapping.get('T').getTexture(),
                second.getFont().mapping.get('T').getTexture());
        Assert.assertNull(first.getFont().whiteBlock);
        Assert.assertEquals(sourceRegionCount, source.getRegions().size);
        Assert.assertSame(sourceBlock, source.getData().getGlyph('\u2588'));

        float secondBold = second.getFont().getBoldStrength();
        first.getFont().setBoldStrength(secondBold + 0.5f);
        Assert.assertEquals(secondBold, second.getFont().getBoldStrength(), 0f);
        second.pause();
        first.skipToTheEnd(true, false);
        Assert.assertTrue(first.hasEnded());
        Assert.assertFalse(second.hasEnded());
        Assert.assertTrue(second.isPaused());
        hud.dispose();
    }

    @Test
    public void textraPreparationAdaptsWithoutMutatingAnExistingSolidBlockGlyph() {
        BitmapFont source = selectedResources.builtInLabelStyle().font;
        BitmapFont.Glyph previous = source.getData().getGlyph('\u2588');
        BitmapFont.Glyph block = new BitmapFont.Glyph();
        block.id = '\u2588';
        block.page = 0;
        block.width = 2;
        block.height = 1;
        block.xadvance = 1;
        source.getData().setGlyph('\u2588', block);
        Font prepared = null;
        try {
            prepared = HudTextraFontFactory.prepare(source, source.getRegion());
            Assert.assertNull(prepared.whiteBlock);
            Assert.assertSame(block, source.getData().getGlyph('\u2588'));
        } finally {
            if (prepared != null) prepared.dispose();
            source.getData().setGlyph('\u2588', previous);
        }
    }

    @Test
    public void textraPreparationUsesUnscaledPaddingForTheWhiteGlyph() {
        assertPreparedWhiteGlyph(1f, false);
        assertPreparedWhiteGlyph(2f, false);
        assertPreparedWhiteGlyph(0.5f, false);
        assertPreparedWhiteGlyph(2f, true);
    }

    @Test
    public void textraLabelPreservesEnabledIntegerPositionsInItsNativeFontCopy() {
        assertTextraLabelIntegerPositions(true);
    }

    @Test
    public void textraLabelPreservesDisabledIntegerPositionsInItsNativeFontCopy() {
        assertTextraLabelIntegerPositions(false);
    }

    @Test
    public void disabledTextraTypingRevealsTextWithoutDiscardingEffects() {
        HudNode node = textraLabel("textra", false);
        node.textraLabel.text = "{WAVE}Text{ENDWAVE}{EVENT=ignored}";

        MaterializedHud hud = materialize(new HudDocumentV1(node));
        TypingLabel label = (TypingLabel) hud.root();

        Assert.assertTrue(label.hasEnded());
        Assert.assertEquals(4, label.getWorkingLayout().countGlyphs());
        Assert.assertTrue(label.getPrefWidth() > 0f);
        Assert.assertTrue(label.getPrefHeight() > 0f);
        hud.dispose();
    }

    @Test
    public void nativeTextWidgetOverridesCopyStylesAndShareOnlyTheChosenFont() {
        BitmapFont override = new BitmapFont(new BitmapFont.BitmapFontData(),
                new TextureRegion(), false);
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
        button.textButton.text = "Button";
        button.textButton.fontAssetId = 42;
        HudNode inheritedButton = new HudNode("inherited-button", HudNodeKind.TEXT_BUTTON);
        inheritedButton.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
        inheritedButton.textButton.text = "Inherited";
        HudNode check = new HudNode("check", HudNodeKind.CHECK_BOX);
        check.checkBox = new games.pixscape.runtime.hud.document.HudCheckBoxData();
        check.checkBox.fontAssetId = 42;
        HudNode field = new HudNode("field", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        field.textField.fontAssetId = 42;
        HudNode select = new HudNode("select", HudNodeKind.SELECT_BOX);
        select.selectBox = new HudSelectBoxData();
        select.selectBox.fontAssetId = 42;
        select.selectBox.selectedIndex = -1;
        root.children.add(HudChild.direct(button));
        root.children.add(HudChild.direct(inheritedButton));
        root.children.add(HudChild.direct(check));
        root.children.add(HudChild.direct(field));
        root.children.add(HudChild.direct(select));
        TextButton.TextButtonStyle sharedButton = selectedResources.builtInTextButtonStyle();
        CheckBox.CheckBoxStyle sharedCheck = selectedResources.builtInCheckBoxStyle();
        TextField.TextFieldStyle sharedField = selectedResources.builtInTextFieldStyle();
        SelectBox.SelectBoxStyle sharedSelect = selectedResources.builtInSelectBoxStyle();
        com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle sharedList = sharedSelect.listStyle;

        MaterializedHud hud;
        try {
            HudValidationResult validation = new HudDocumentValidator().validate(
                    new HudDocumentV1(root));
            hud = new HudMaterializer().materialize(validation.validatedDocument(),
                    overrideVisualResources(override));
        } finally {
            // The test owns only the standalone override, never the shared resource styles.
            override.dispose();
        }

        TextButton materializedButton = (TextButton) hud.actor("button");
        TextButton materializedInheritedButton = (TextButton) hud.actor("inherited-button");
        CheckBox materializedCheck = (CheckBox) hud.actor("check");
        TextField materializedField = (TextField) hud.actor("field");
        @SuppressWarnings("unchecked") SelectBox<String> materializedSelect =
                (SelectBox<String>) hud.actor("select");
        Assert.assertNotSame(sharedButton, materializedButton.getStyle());
        Assert.assertSame(override, materializedButton.getStyle().font);
        Assert.assertSame(sharedButton, materializedInheritedButton.getStyle());
        Assert.assertNotSame(sharedCheck, materializedCheck.getStyle());
        Assert.assertSame(override, materializedCheck.getStyle().font);
        Assert.assertNotSame(sharedField, materializedField.getStyle());
        Assert.assertSame(override, materializedField.getStyle().font);
        Assert.assertSame(override, materializedField.getStyle().messageFont);
        Assert.assertNotSame(sharedSelect, materializedSelect.getStyle());
        Assert.assertSame(override, materializedSelect.getStyle().font);
        Assert.assertNotSame(sharedList, materializedSelect.getStyle().listStyle);
        Assert.assertSame(override, materializedSelect.getStyle().listStyle.font);
        Assert.assertNotNull(materializedSelect.getStyle().scrollStyle);
        Assert.assertNotSame(override, sharedButton.font);
        Assert.assertNotSame(override, sharedCheck.font);
        Assert.assertNotSame(override, sharedField.font);
        Assert.assertNotSame(override, sharedField.messageFont);
        Assert.assertNotSame(override, sharedSelect.font);
        Assert.assertNotSame(override, sharedList.font);
    }

    private HudVisualResources overrideVisualResources(final BitmapFont override) {
        return new HudVisualResources() {
            @Override public TextureRegion region(String name) { return selectedResources.region(name); }
            @Override public Drawable drawable(String name) { return selectedResources.drawable(name); }
            @Override public Label.LabelStyle labelStyle(String name) { return selectedResources.labelStyle(name); }
            @Override public Label.LabelStyle builtInLabelStyle() { return selectedResources.builtInLabelStyle(); }
            @Override public BitmapFont bitmapFont(int assetId) { return assetId == 42 ? override : null; }
            @Override public TextButton.TextButtonStyle textButtonStyle(String name) { return selectedResources.textButtonStyle(name); }
            @Override public TextButton.TextButtonStyle builtInTextButtonStyle() { return selectedResources.builtInTextButtonStyle(); }
            @Override public TextField.TextFieldStyle textFieldStyle(String name) { return selectedResources.textFieldStyle(name); }
            @Override public TextField.TextFieldStyle builtInTextFieldStyle() { return selectedResources.builtInTextFieldStyle(); }
            @Override public SelectBox.SelectBoxStyle selectBoxStyle(String name) { return selectedResources.selectBoxStyle(name); }
            @Override public SelectBox.SelectBoxStyle builtInSelectBoxStyle() { return selectedResources.builtInSelectBoxStyle(); }
            @Override public CheckBox.CheckBoxStyle checkBoxStyle(String name) { return selectedResources.checkBoxStyle(name); }
            @Override public CheckBox.CheckBoxStyle builtInCheckBoxStyle() { return selectedResources.builtInCheckBoxStyle(); }
        };
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

    @Test
    public void windowIsNativeTableWithAuthoredCellsAndSeparateTitleActors() {
        HudNode root = new HudNode("window", HudNodeKind.WINDOW);
        root.window = new HudWindowData();
        root.window.title = "Inventory";
        root.actor.width = 240f;
        root.actor.height = 160f;
        HudNode content = new HudNode("content", HudNodeKind.TABLE);
        root.children.add(HudChild.cell(content, new HudCellConstraints()));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        try {
            Assert.assertTrue(hud.root() instanceof Window);
            Window window = (Window) hud.root();
            Assert.assertEquals("Inventory", window.getTitleLabel().getText().toString());
            Assert.assertSame(content.id, hud.actor("content").getName());
            Assert.assertNotNull(window.getCell(hud.actor("content")));
            Assert.assertNull(hud.actor(window.getTitleLabel().getName()));
            Assert.assertSame(selectedResources.builtInWindowStyle(), window.getStyle());
            Assert.assertTrue(window.isMovable());
            Assert.assertFalse(window.isResizable());
            Assert.assertFalse(window.isModal());
        } finally {
            hud.dispose();
        }
    }

    @Test
    public void windowFontOverrideCopiesNativeStyleWithoutMutatingSource() {
        HudNode root = new HudNode("window", HudNodeKind.WINDOW);
        root.window = new HudWindowData();
        root.window.styleName = "custom";
        root.window.fontAssetId = 42;
        Window.WindowStyle shared = new Window.WindowStyle();
        BitmapFont override = selectedResources.builtInLabelStyle().font;
        HudVisualResources visual = new HudVisualResources() {
            @Override public TextureRegion region(String name) { return null; }
            @Override public Drawable drawable(String name) { return null; }
            @Override public Label.LabelStyle labelStyle(String name) { return null; }
            @Override public TextButton.TextButtonStyle textButtonStyle(String name) { return null; }
            @Override public Window.WindowStyle windowStyle(String name) {
                return "custom".equals(name) ? shared : null;
            }
            @Override public BitmapFont bitmapFont(int assetId) { return assetId == 42 ? override : null; }
        };
        HudValidationResult validation = new HudDocumentValidator().validate(new HudDocumentV1(root));
        MaterializedHud hud = new HudMaterializer().materialize(validation.validatedDocument(), visual);
        try {
            Window window = (Window) hud.root();
            Assert.assertNotSame(shared, window.getStyle());
            Assert.assertSame(override, window.getStyle().titleFont);
            Assert.assertNull(shared.titleFont);
        } finally {
            hud.dispose();
        }
    }

    @Test
    public void dialogUsesNativeSkinWindowStyleAndCopiesFontOverride() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.dialog.styleName = "custom";
        dialogNode.dialog.fontAssetId = 42;
        dialogNode.actor.width = 200f;
        dialogNode.actor.height = 140f;
        root.children.add(HudChild.free(dialogNode, new HudFreePlacement()));
        Window.WindowStyle shared = new Window.WindowStyle();
        BitmapFont override = selectedResources.builtInLabelStyle().font;
        HudVisualResources visual = new HudVisualResources() {
            @Override public TextureRegion region(String name) { return null; }
            @Override public Drawable drawable(String name) { return null; }
            @Override public Label.LabelStyle labelStyle(String name) { return null; }
            @Override public TextButton.TextButtonStyle textButtonStyle(String name) { return null; }
            @Override public Window.WindowStyle windowStyle(String name) {
                return "custom".equals(name) ? shared : null;
            }
            @Override public BitmapFont bitmapFont(int assetId) {
                return assetId == 42 ? override : null;
            }
        };
        HudValidationResult validation = new HudDocumentValidator().validate(new HudDocumentV1(root));
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        MaterializedHud hud = new HudMaterializer().materialize(validation.validatedDocument(), visual);
        try {
            Assert.assertTrue(hud.actor("dialog") instanceof Dialog);
            Window.WindowStyle resolved = ((Dialog) hud.actor("dialog")).getStyle();
            Assert.assertNotSame(shared, resolved);
            Assert.assertSame(override, resolved.titleFont);
            Assert.assertNull(shared.titleFont);
        } finally {
            hud.dispose();
        }
    }

    @Test
    public void dialogOpensClosesAndReopensThroughStageButtonsWithoutGeometryDrift() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode opener = new HudNode("opener", HudNodeKind.TEXT_BUTTON);
        opener.textButton = new HudTextButtonData();
        opener.textButton.text = "Open";
        opener.actor.width = 90f;
        opener.actor.height = 35f;
        opener.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.SHOW));
        HudFreePlacement openerPlacement = new HudFreePlacement();
        openerPlacement.offsetX = 20f;
        openerPlacement.offsetY = 20f;
        root.children.add(HudChild.free(opener, openerPlacement));
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.dialog.resizable = true;
        dialogNode.visible = false;
        dialogNode.actor.width = 200f;
        dialogNode.actor.height = 140f;
        HudNode closer = new HudNode("closer", HudNodeKind.TEXT_BUTTON);
        closer.textButton = new HudTextButtonData();
        closer.textButton.text = "Close";
        closer.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.HIDE));
        dialogNode.children.add(HudChild.cell(closer, new HudCellConstraints()));
        HudFreePlacement dialogPlacement = new HudFreePlacement();
        dialogPlacement.offsetX = 130f;
        dialogPlacement.offsetY = 60f;
        root.children.add(HudChild.free(dialogNode, dialogPlacement));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(400f, 300f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            Dialog dialog = (Dialog) hud.actor("dialog");
            Assert.assertFalse(dialog.isVisible());
            Assert.assertNotSame(stage.getRoot(), dialog.getParent());
            ((TextButton) hud.actor("opener")).setChecked(true);
            Assert.assertFalse("programmatic checked must not show a dialog", dialog.isVisible());
            stage.setKeyboardFocus(hud.actor("opener"));
            clickActor(stage, hud.actor("opener"), 300);
            Assert.assertSame(stage.getRoot(), dialog.getParent());
            Assert.assertEquals(130f, dialog.getX(), .001f);
            Assert.assertEquals(60f, dialog.getY(), .001f);
            Assert.assertEquals(200f, dialog.getWidth(), .001f);
            Assert.assertEquals(140f, dialog.getHeight(), .001f);
            Assert.assertSame(dialog, stage.getKeyboardFocus());
            Assert.assertSame(dialog, stage.getScrollFocus());
            int listenerCount = dialog.getListeners().size;
            ((HudDialog) dialog).open();
            Assert.assertEquals(2, stage.getRoot().getChildren().size);
            Assert.assertEquals(listenerCount, dialog.getListeners().size);
            Assert.assertNotNull(dialog.getContentTable().getCell(hud.actor("closer")));
            Assert.assertEquals(4, hud.actorById().size());
            final int[] underlyingTouches = {0};
            InputMultiplexer routed = new InputMultiplexer(stage, new InputAdapter() {
                @Override public boolean touchDown(int x, int y, int pointer, int button) {
                    underlyingTouches[0]++;
                    return true;
                }
            });
            Assert.assertTrue(routed.touchDown(390, 290, 0, 0));
            routed.touchUp(390, 290, 0, 0);
            Assert.assertEquals(0, underlyingTouches[0]);
            int titleX = Math.round(dialog.getX() + 50f);
            int titleY = Math.round(300f - dialog.getTop() + 10f);
            Assert.assertTrue(stage.touchDown(titleX, titleY, 0, 0));
            stage.touchDragged(titleX + 20, titleY - 10, 0);
            stage.touchUp(titleX + 20, titleY - 10, 0, 0);
            Assert.assertTrue(dialog.getX() > 130f);
            float beforeResize = dialog.getWidth();
            int resizeX = Math.round(dialog.getRight() - 10f);
            int resizeY = Math.round(300f - dialog.getY() - 10f);
            Assert.assertTrue(stage.touchDown(resizeX, resizeY, 0, 0));
            stage.touchDragged(resizeX + 20, resizeY, 0);
            stage.touchUp(resizeX + 20, resizeY, 0, 0);
            Assert.assertTrue(dialog.getWidth() > beforeResize);
            dialog.validate();
            clickActor(stage, hud.actor("closer"), 300);
            Assert.assertNull(dialog.getStage());
            Assert.assertSame(hud.actor("opener"), stage.getKeyboardFocus());
            Assert.assertNull(stage.getScrollFocus());
            clickActor(stage, hud.actor("opener"), 300);
            Assert.assertSame(stage.getRoot(), dialog.getParent());
            Assert.assertEquals(130f, dialog.getX(), .001f);
            Assert.assertEquals(200f, dialog.getWidth(), .001f);
            Assert.assertEquals(2, stage.getRoot().getChildren().size);
            hud.actor("opener").setVisible(false);
            dialog.validate();
            clickActor(stage, hud.actor("closer"), 300);
            Assert.assertNull("native restore must not focus a hidden opener",
                    stage.getKeyboardFocus());
            Assert.assertFalse("authored initial visibility is unchanged", dialogNode.visible);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void closedDialogSlotDoesNotShadowOverlappingOpenButton() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode opener = new HudNode("opener", HudNodeKind.TEXT_BUTTON);
        opener.textButton = new HudTextButtonData();
        opener.textButton.text = "Open";
        opener.actor.width = 90f;
        opener.actor.height = 35f;
        opener.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.SHOW));
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = 20f;
        placement.offsetY = 20f;
        root.children.add(HudChild.free(opener, placement));
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.visible = false;
        dialogNode.actor.width = 200f;
        dialogNode.actor.height = 140f;
        root.children.add(HudChild.free(dialogNode, placement));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(400f, 300f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            Assert.assertTrue(stage.hit(65f, 37.5f, true)
                    .isDescendantOf(hud.actor("opener")));
            clickActor(stage, hud.actor("opener"), 300);
            Assert.assertSame(stage.getRoot(), hud.actor("dialog").getParent());
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void dialogStartsClosedForEitherAuthoredVisibilityWithoutFocusOrModalInput() {
        for (boolean authoredVisible : new boolean[]{true, false}) {
            HudNode root = new HudNode("root", HudNodeKind.GROUP);
            HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
            dialogNode.dialog = new HudDialogData();
            dialogNode.visible = authoredVisible;
            dialogNode.actor.width = 200f;
            dialogNode.actor.height = 140f;
            root.children.add(HudChild.free(dialogNode, new HudFreePlacement()));
            MaterializedHud hud = materialize(new HudDocumentV1(root));
            Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
            Graphics graphics = Gdx.graphics;
            try {
                Gdx.graphics = logicalGraphics(400, 300);
                stage.getViewport().update(400, 300, true);
                hud.root().setSize(400f, 300f);
                stage.addActor(hud.root());
                ((Layout) hud.root()).validate();
                Dialog dialog = (Dialog) hud.actor("dialog");
                Assert.assertFalse(dialog.isVisible());
                Assert.assertNotSame(stage.getRoot(), dialog.getParent());
                Assert.assertNull(stage.getKeyboardFocus());
                Assert.assertNull(stage.getScrollFocus());
                final int[] underlyingTouches = {0};
                InputMultiplexer inputs = new InputMultiplexer(stage, new InputAdapter() {
                    @Override public boolean touchDown(int x, int y, int pointer, int button) {
                        underlyingTouches[0]++;
                        return true;
                    }
                });
                Assert.assertTrue(inputs.touchDown(300, 100, 0, 0));
                inputs.touchUp(300, 100, 0, 0);
                Assert.assertEquals(1, underlyingTouches[0]);
            } finally {
                hud.dispose();
                stage.dispose();
                Gdx.graphics = graphics;
            }
        }
    }

    @Test
    public void dialogUsesCurrentRightAndTopAnchorsAtFirstShowAndAfterResize() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode opener = new HudNode("opener", HudNodeKind.TEXT_BUTTON);
        opener.textButton = new HudTextButtonData();
        opener.textButton.text = "Open";
        opener.actor.width = 90f;
        opener.actor.height = 35f;
        opener.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.SHOW));
        HudFreePlacement openerPlacement = new HudFreePlacement();
        openerPlacement.offsetX = 20f;
        openerPlacement.offsetY = 20f;
        root.children.add(HudChild.free(opener, openerPlacement));
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.actor.width = 200f;
        dialogNode.actor.height = 140f;
        HudNode closer = new HudNode("closer", HudNodeKind.TEXT_BUTTON);
        closer.textButton = new HudTextButtonData();
        closer.textButton.text = "Close";
        closer.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.HIDE));
        dialogNode.children.add(HudChild.cell(closer, new HudCellConstraints()));
        HudFreePlacement dialogPlacement = new HudFreePlacement();
        dialogPlacement.horizontalAnchor = games.pixscape.runtime.hud.document.HudHorizontalAnchor.RIGHT;
        dialogPlacement.verticalAnchor = games.pixscape.runtime.hud.document.HudVerticalAnchor.TOP;
        dialogPlacement.offsetX = -210f;
        dialogPlacement.offsetY = -150f;
        root.children.add(HudChild.free(dialogNode, dialogPlacement));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(400f, 300f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            Dialog dialog = (Dialog) hud.actor("dialog");
            clickActor(stage, hud.actor("opener"), 300);
            Assert.assertEquals(190f, dialog.getX(), .001f);
            Assert.assertEquals(150f, dialog.getY(), .001f);
            dialog.validate();
            clickActor(stage, hud.actor("closer"), 300);
            Assert.assertFalse(dialog.isVisible());
            Assert.assertNull(dialog.getStage());
            Gdx.graphics = logicalGraphics(600, 400);
            stage.getViewport().update(600, 400, true);
            hud.root().setSize(600f, 400f);
            ((Layout) hud.root()).invalidateHierarchy();
            ((Layout) hud.root()).validate();
            Actor slot = ((Group) hud.root()).getChildren().get(1);
            Assert.assertEquals(390f, slot.getX(), .001f);
            Assert.assertEquals(250f, slot.getY(), .001f);
            clickActor(stage, hud.actor("opener"), 400);
            Assert.assertEquals(390f, dialog.getX(), .001f);
            Assert.assertEquals(250f, dialog.getY(), .001f);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void dialogUsesCenteredAuthoredPlacementAtFirstShow() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode opener = new HudNode("opener", HudNodeKind.TEXT_BUTTON);
        opener.textButton = new HudTextButtonData();
        opener.textButton.text = "Open";
        opener.actor.width = 90f;
        opener.actor.height = 35f;
        opener.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.SHOW));
        root.children.add(HudChild.free(opener, new HudFreePlacement()));
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.actor.width = 200f;
        dialogNode.actor.height = 140f;
        HudFreePlacement centered = new HudFreePlacement();
        centered.horizontalAnchor = games.pixscape.runtime.hud.document.HudHorizontalAnchor.CENTER;
        centered.verticalAnchor = games.pixscape.runtime.hud.document.HudVerticalAnchor.CENTER;
        root.children.add(HudChild.free(dialogNode, centered));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(400f, 300f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            clickActor(stage, hud.actor("opener"), 300);
            Dialog dialog = (Dialog) hud.actor("dialog");
            Assert.assertEquals(200f, dialog.getX(), .001f);
            Assert.assertEquals(150f, dialog.getY(), .001f);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void nativeDialogShowAndHideRemainUsableFromApplicationCode() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.actor.width = 200f;
        dialogNode.actor.height = 140f;
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = 130f;
        placement.offsetY = 60f;
        root.children.add(HudChild.free(dialogNode, placement));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(400f, 300f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            Dialog dialog = (Dialog) hud.actor("dialog");
            Assert.assertFalse(dialog.isVisible());
            dialog.show(stage, null);
            Assert.assertTrue(dialog.isVisible());
            Assert.assertSame(stage.getRoot(), dialog.getParent());
            Assert.assertEquals(130f, dialog.getX(), .001f);
            Assert.assertEquals(60f, dialog.getY(), .001f);
            dialog.hide(null);
            Assert.assertFalse(dialog.isVisible());
            Assert.assertNull(dialog.getStage());
            Assert.assertNull(stage.getKeyboardFocus());
            dialog.show(stage);
            Assert.assertTrue(dialog.isVisible());
            Assert.assertEquals(130f, dialog.getX(), .001f);
            Assert.assertEquals(60f, dialog.getY(), .001f);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void authoringDialogStaysVisibleAndIgnoresRuntimeCloseAction() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.visible = false;
        dialogNode.actor.width = 200f;
        dialogNode.actor.height = 140f;
        HudNode closer = new HudNode("closer", HudNodeKind.TEXT_BUTTON);
        closer.textButton = new HudTextButtonData();
        closer.textButton.text = "Close";
        closer.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.HIDE));
        dialogNode.children.add(HudChild.cell(closer, new HudCellConstraints()));
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = 130f;
        placement.offsetY = 60f;
        root.children.add(HudChild.free(dialogNode, placement));
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentV1(root), selectedResources);
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        MaterializedHud hud = new HudMaterializer().materialize(
                validation.validatedDocument(), selectedResources, false);
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(400f, 300f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            Dialog dialog = (Dialog) hud.actor("dialog");
            dialog.validate();
            Assert.assertTrue(dialog.isVisible());
            Assert.assertFalse(dialog.isModal());
            Assert.assertSame(stage, dialog.getStage());
            Assert.assertEquals(200f, dialog.getWidth(), .001f);
            Assert.assertEquals(140f, dialog.getHeight(), .001f);
            Assert.assertNotSame(stage.getRoot(), dialog.getParent());
            clickActor(stage, hud.actor("closer"), 300);
            Assert.assertTrue(dialog.isVisible());
            Assert.assertSame(stage, dialog.getStage());
            Assert.assertFalse(dialogNode.visible);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void windowButtonAssociationStillTogglesNativeWindow() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "Toggle";
        button.actor.width = 90f;
        button.actor.height = 35f;
        button.windowActions.add(new HudWindowAction("window", HudWindowActionKind.TOGGLE));
        HudFreePlacement buttonPlacement = new HudFreePlacement();
        buttonPlacement.offsetX = 20f;
        buttonPlacement.offsetY = 20f;
        root.children.add(HudChild.free(button, buttonPlacement));
        HudNode windowNode = new HudNode("window", HudNodeKind.WINDOW);
        windowNode.window = new HudWindowData();
        windowNode.visible = false;
        windowNode.actor.width = 160f;
        windowNode.actor.height = 100f;
        HudFreePlacement windowPlacement = new HudFreePlacement();
        windowPlacement.offsetX = 200f;
        windowPlacement.offsetY = 100f;
        root.children.add(HudChild.free(windowNode, windowPlacement));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(400f, 300f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            Window window = (Window) hud.actor("window");
            Assert.assertFalse(window.isVisible());
            TextButton toggle = (TextButton) hud.actor("button");
            toggle.setChecked(true);
            Assert.assertFalse("programmatic checked must not toggle a window", window.isVisible());
            toggle.setDisabled(true);
            clickActor(stage, toggle, 300);
            Assert.assertFalse("disabled button must not toggle a window", window.isVisible());
            toggle.setDisabled(false);
            Vector2 buttonPoint = toggle.localToStageCoordinates(new Vector2(45f, 17f));
            int buttonX = Math.round(buttonPoint.x);
            int buttonY = Math.round(300f - buttonPoint.y);
            Assert.assertTrue(stage.touchDown(buttonX, buttonY, 0, 0));
            stage.cancelTouchFocus();
            stage.touchUp(buttonX, buttonY, 0, 0);
            Assert.assertFalse("cancelled gesture must not toggle a window", window.isVisible());
            clickActor(stage, hud.actor("button"), 300);
            Assert.assertTrue(window.isVisible());
            clickActor(stage, hud.actor("button"), 300);
            Assert.assertFalse(window.isVisible());
            Assert.assertFalse(windowNode.visible);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void nonmodalDialogStaysInScrolledAuthoredParentAfterNativeShow() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode paneNode = new HudNode("pane", HudNodeKind.SCROLL_PANE);
        paneNode.scrollPane = new HudScrollPaneData();
        paneNode.actor.width = 280f;
        paneNode.actor.height = 180f;
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.dialog.modal = false;
        dialogNode.actor.width = 220f;
        dialogNode.actor.height = 260f;
        paneNode.children.add(HudChild.direct(dialogNode));
        root.children.add(HudChild.free(paneNode, new HudFreePlacement()));
        HudNode toggle = new HudNode("toggle", HudNodeKind.TEXT_BUTTON);
        toggle.textButton = new HudTextButtonData();
        toggle.textButton.text = "Toggle";
        toggle.actor.width = 80f;
        toggle.actor.height = 30f;
        toggle.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.TOGGLE));
        HudFreePlacement togglePlacement = new HudFreePlacement();
        togglePlacement.offsetX = 300f;
        togglePlacement.offsetY = 20f;
        root.children.add(HudChild.free(toggle, togglePlacement));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(400f, 300f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            Dialog dialog = (Dialog) hud.actor("dialog");
            ScrollPane pane = (ScrollPane) hud.actor("pane");
            Assert.assertFalse(dialog.isVisible());
            clickActor(stage, hud.actor("toggle"), 300);
            Assert.assertSame(pane.getActor(), dialog.getParent());
            Assert.assertSame(stage, dialog.getStage());
            Assert.assertFalse(dialog.isModal());
            Assert.assertEquals(220f, dialog.getWidth(), .001f);
            Assert.assertEquals(260f, dialog.getHeight(), .001f);
            pane.validate();
            float beforeScroll = dialog.localToStageCoordinates(new Vector2()).y;
            pane.setScrollY(50f);
            pane.updateVisualScroll();
            pane.layout();
            Assert.assertNotEquals(beforeScroll,
                    dialog.localToStageCoordinates(new Vector2()).y, .001f);
            clickActor(stage, hud.actor("toggle"), 300);
            Assert.assertFalse(dialog.isVisible());
            clickActor(stage, hud.actor("toggle"), 300);
            Assert.assertSame(pane.getActor(), dialog.getParent());
            Assert.assertTrue(dialog.isVisible());
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void dialogDirectlyInScrollRootDoesNotEscapeItsClippingParent() {
        HudNode root = new HudNode("scroll-root", HudNodeKind.SCROLL_PANE);
        root.scrollPane = new HudScrollPaneData();
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        dialogNode.dialog.modal = false;
        dialogNode.actor.width = 220f;
        dialogNode.actor.height = 260f;
        root.children.add(HudChild.direct(dialogNode));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            hud.root().setSize(280f, 180f);
            stage.addActor(hud.root());
            ((Layout) hud.root()).validate();
            Dialog dialog = (Dialog) hud.actor("dialog");
            Assert.assertFalse(dialog.isVisible());
            ((HudDialog) dialog).open();
            Assert.assertSame(((ScrollPane) hud.root()).getActor(), dialog.getParent());
            Assert.assertNotSame(stage.getRoot(), dialog.getParent());
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    private static void clickActor(Stage stage, Actor actor, int screenHeight) {
        Vector2 point = actor.localToStageCoordinates(
                new Vector2(actor.getWidth() * .5f, actor.getHeight() * .5f));
        int x = Math.round(point.x);
        int y = Math.round(screenHeight - point.y);
        Assert.assertTrue(stage.touchDown(x, y, 0, 0));
        stage.touchUp(x, y, 0, 0);
    }

    @Test
    public void stageRoutesNativeWindowMoveResizeAndModalWithoutEditingDocument() {
        HudNode root = new HudNode("window", HudNodeKind.WINDOW);
        root.window = new HudWindowData();
        root.window.resizable = true;
        root.window.modal = true;
        root.actor.width = 200f;
        root.actor.height = 140f;
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Window window = (Window) hud.root();
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Stage stage = new Stage(new ScreenViewport(), batch);
        Graphics testGraphics = Gdx.graphics;
        try {
            Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                    new Class<?>[]{Graphics.class},
                    (proxy, method, args) -> "getWidth".equals(method.getName()) ? 400
                            : "getHeight".equals(method.getName()) ? 300
                            : defaultValue(method.getReturnType()));
            stage.getViewport().update(400, 300, true);
            window.setPosition(40f, 40f);
            stage.addActor(window);
            Assert.assertTrue(stage.touchDown(60, 130, 0, 0)); // Title: stage (60,170).
            stage.touchDragged(90, 110, 0);
            stage.touchUp(90, 110, 0, 0);
            Assert.assertTrue(window.getX() > 40f);
            Assert.assertTrue(window.getY() > 40f);
            float width = window.getWidth();
            int resizeX = Math.round(window.getRight() - 10f);
            int resizeY = Math.round(300f - (window.getY() + 10f));
            Assert.assertTrue(stage.touchDown(resizeX, resizeY, 0, 0));
            stage.touchDragged(resizeX + 20, resizeY, 0);
            stage.touchUp(resizeX + 20, resizeY, 0, 0);
            Assert.assertTrue(window.getWidth() > width);
            Assert.assertTrue(stage.touchDown(390, 290, 0, 0)); // Modal outside its bounds.
            stage.touchUp(390, 290, 0, 0);
            final int[] worldTouches = {0};
            InputMultiplexer routed = new InputMultiplexer(stage, new InputAdapter() {
                @Override public boolean touchDown(int x, int y, int pointer, int button) {
                    worldTouches[0]++;
                    return true;
                }
            });
            Assert.assertTrue(routed.touchDown(390, 290, 0, 0));
            Assert.assertEquals(0, worldTouches[0]);
            routed.touchUp(390, 290, 0, 0);
            Assert.assertEquals(200f, root.actor.width, 0f);
            Assert.assertEquals(140f, root.actor.height, 0f);
            Assert.assertTrue(root.window.modal);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = testGraphics;
        }
    }

    @Test
    public void freeGroupDoesNotOverwriteNativeWindowPreviewBoundsAfterInitialPlacement() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.actor.width = 400f;
        root.actor.height = 300f;
        HudNode node = new HudNode("window", HudNodeKind.WINDOW);
        node.window = new HudWindowData();
        node.actor.width = 200f;
        node.actor.height = 140f;
        games.pixscape.runtime.hud.document.HudFreePlacement free =
                new games.pixscape.runtime.hud.document.HudFreePlacement();
        free.offsetX = 40f;
        free.offsetY = 50f;
        root.children.add(HudChild.free(node, free));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        try {
            HudFreeGroup group = (HudFreeGroup) hud.root();
            Window window = (Window) hud.actor("window");
            group.validate();
            Assert.assertEquals(40f, window.getX(), 0f);
            Assert.assertEquals(50f, window.getY(), 0f);
            window.setBounds(75f, 85f, 220f, 150f);
            group.invalidate();
            group.validate();
            Assert.assertEquals(75f, window.getX(), 0f);
            Assert.assertEquals(85f, window.getY(), 0f);
            Assert.assertEquals(220f, window.getWidth(), 0f);
            Assert.assertEquals(150f, window.getHeight(), 0f);
            Assert.assertEquals(40f, free.offsetX, 0f);
            Assert.assertEquals(50f, free.offsetY, 0f);
        } finally {
            hud.dispose();
        }
    }

    @Test
    public void scrolledWindowDrawDoesNotOverrideItsParentLayout() {
        HudNode paneNode = new HudNode("pane", HudNodeKind.SCROLL_PANE);
        paneNode.scrollPane = new HudScrollPaneData();
        paneNode.actor.width = 120f;
        paneNode.actor.height = 100f;
        HudNode windowNode = new HudNode("window", HudNodeKind.WINDOW);
        windowNode.window = new HudWindowData();
        windowNode.actor.width = 240f;
        windowNode.actor.height = 180f;
        HudCellConstraints contentSize = new HudCellConstraints();
        contentSize.prefWidth = 240f;
        contentSize.prefHeight = 180f;
        windowNode.children.add(HudChild.cell(new HudNode("content", HudNodeKind.GROUP), contentSize));
        paneNode.children.add(HudChild.direct(windowNode));
        MaterializedHud hud = materialize(new HudDocumentV1(paneNode));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            ScrollPane pane = (ScrollPane) hud.root();
            Window window = (Window) hud.actor("window");
            stage.addActor(pane);
            pane.validate();
            Assert.assertTrue(pane.getMaxX() > 0f);
            pane.setScrollX(pane.getMaxX());
            pane.updateVisualScroll();
            pane.layout();
            float layoutX = window.getX();
            Assert.assertTrue("the native viewport has scrolled its content", layoutX < 0f);

            window.draw(stage.getBatch(), 1f);
            Assert.assertEquals("drawing must not undo the native scroll", layoutX, window.getX(), 0f);
            Assert.assertTrue(windowNode.window.keepWithinStage);
            window.setKeepWithinStage(true);
            window.draw(stage.getBatch(), 1f);
            Assert.assertTrue("the native clamp would contradict the ScrollPane", window.getX() > layoutX);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void rootWindowRetainsNativeStageConstraintOnDraw() {
        HudNode root = new HudNode("window", HudNodeKind.WINDOW);
        root.window = new HudWindowData();
        root.actor.width = 200f;
        root.actor.height = 140f;
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            Window window = (Window) hud.root();
            window.setPosition(-20f, -15f);
            stage.addActor(window);
            window.draw(stage.getBatch(), 1f);
            Assert.assertEquals(0f, window.getX(), 0f);
            Assert.assertEquals(0f, window.getY(), 0f);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void windowInsideOffsetFreeRootUsesParentCoordinatesOnDraw() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.actor.width = 300f;
        root.actor.height = 240f;
        HudNode child = new HudNode("window", HudNodeKind.WINDOW);
        child.window = new HudWindowData();
        child.actor.width = 100f;
        child.actor.height = 80f;
        root.children.add(HudChild.free(child, new games.pixscape.runtime.hud.document.HudFreePlacement()));
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        Stage stage = new Stage(new ScreenViewport(), inertDrawBatch());
        Graphics graphics = Gdx.graphics;
        try {
            Gdx.graphics = logicalGraphics(400, 300);
            stage.getViewport().update(400, 300, true);
            HudFreeGroup group = (HudFreeGroup) hud.root();
            group.setPosition(50f, 40f);
            stage.addActor(group);
            group.validate();
            Window window = (Window) hud.actor("window");
            window.setPosition(-10f, -5f); // Still inside the Stage in global coordinates.
            window.draw(stage.getBatch(), 1f);
            Assert.assertEquals(-10f, window.getX(), 0f);
            Assert.assertEquals(-5f, window.getY(), 0f);
            Assert.assertTrue(child.window.keepWithinStage);
        } finally {
            hud.dispose();
            stage.dispose();
            Gdx.graphics = graphics;
        }
    }

    @Test
    public void hiddenNodesRemainIndexedAndRuntimeVisibilityDoesNotEditAuthoredState() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.visible = false;
        root.actor.width = 400f;
        root.actor.height = 300f;
        HudNode child = new HudNode("child", HudNodeKind.WINDOW);
        child.window = new HudWindowData();
        child.actor.width = 120f;
        child.actor.height = 80f;
        root.children.add(HudChild.free(child, new games.pixscape.runtime.hud.document.HudFreePlacement()));
        HudDocumentV1 document = new HudDocumentV1(root);

        MaterializedHud hud = materialize(document);
        try {
            Assert.assertFalse(hud.root().isVisible());
            Assert.assertNotNull(hud.actor("child"));
            Assert.assertTrue(hud.actor("child").isVisible());
            hud.root().setVisible(true);
            hud.actor("child").setVisible(false);
            Assert.assertFalse(root.visible);
            Assert.assertTrue(child.visible);
        } finally {
            hud.dispose();
        }
        MaterializedHud rebuilt = materialize(document);
        try {
            Assert.assertFalse(rebuilt.root().isVisible());
            Assert.assertTrue(rebuilt.actor("child").isVisible());
        } finally {
            rebuilt.dispose();
        }
    }

    private static Batch inertDrawBatch() {
        Matrix4 transform = new Matrix4();
        Color color = new Color(Color.WHITE);
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
                (proxy, method, args) -> "getTransformMatrix".equals(method.getName())
                        || "getProjectionMatrix".equals(method.getName()) ? transform
                        : "getColor".equals(method.getName()) ? color
                        : defaultValue(method.getReturnType()));
    }

    private static Graphics logicalGraphics(int width, int height) {
        return (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class}, (proxy, method, args) ->
                        "getWidth".equals(method.getName()) || "getBackBufferWidth".equals(method.getName())
                                ? width : "getHeight".equals(method.getName())
                                || "getBackBufferHeight".equals(method.getName())
                                ? height : defaultValue(method.getReturnType()));
    }

    @Test
    public void tooltipUsesNativeListenerAndStageAndIsRemovedWithItsHud() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.actor.width = 100f;
        root.actor.height = 100f;
        root.tooltip = new HudTooltipData();
        root.tooltip.text = "Native tooltip";
        HudDocumentV1 document = new HudDocumentV1(root);
        HudValidationResult validation = new HudDocumentValidator().validate(document, selectedResources);
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        HudResourceRequirements requirements = HudResourceRequirements.from(validation.validatedDocument());
        Assert.assertTrue(requirements.requiresBuiltInTextTooltipStyle());
        Assert.assertTrue(requirements.requiresBuiltInLabelStyle());
        MaterializedHud hud = new HudMaterializer().materialize(validation.validatedDocument(), selectedResources);
        TextTooltip tooltip = null;
        for (EventListener listener : hud.root().getListeners()) {
            if (listener instanceof TextTooltip) tooltip = (TextTooltip) listener;
        }
        Assert.assertNotNull(tooltip);
        Assert.assertSame(selectedResources.builtInTextTooltipStyle(), tooltip.getStyle());
        Assert.assertEquals(0.5f, tooltip.getManager().initialTime, 0f);
        Assert.assertEquals(0f, tooltip.getManager().subsequentTime, 0f);
        Assert.assertEquals(1.5f, tooltip.getManager().resetTime, 0f);
        tooltip.setInstant(true);
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Stage stage = new Stage(new ScreenViewport(), batch);
        Application testApp = Gdx.app;
        Graphics testGraphics = Gdx.graphics;
        try {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> "getType".equals(method.getName())
                            ? Application.ApplicationType.Desktop
                            : defaultValue(method.getReturnType()));
            Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                    new Class<?>[]{Graphics.class},
                    (proxy, method, args) -> "getWidth".equals(method.getName()) ? 300
                            : "getHeight".equals(method.getName()) ? 200
                            : defaultValue(method.getReturnType()));
            stage.getViewport().update(300, 200, true);
            stage.addActor(hud.root());
            stage.mouseMoved(20, 180);
            stage.act(0f);
            Assert.assertSame(stage, tooltip.getContainer().getStage());
            hud.dispose();
            Assert.assertNull(tooltip.getContainer().getStage());
            Assert.assertFalse(hud.root().getListeners().contains(tooltip, true));
        } finally {
            stage.dispose();
            Gdx.app = testApp;
            Gdx.graphics = testGraphics;
        }
    }

    @Test
    public void tooltipFontOverrideCopiesNestedStyleWithoutChangingSharedFont() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.tooltip = new HudTooltipData();
        root.tooltip.styleName = "custom";
        root.tooltip.fontAssetId = 42;
        BitmapFont override = selectedResources.builtInLabelStyle().font;
        TextTooltip.TextTooltipStyle shared = new TextTooltip.TextTooltipStyle(
                new Label.LabelStyle(), null);
        HudVisualResources visual = new HudVisualResources() {
            @Override public TextureRegion region(String name) { return null; }
            @Override public Drawable drawable(String name) { return null; }
            @Override public Label.LabelStyle labelStyle(String name) { return null; }
            @Override public TextButton.TextButtonStyle textButtonStyle(String name) { return null; }
            @Override public TextTooltip.TextTooltipStyle textTooltipStyle(String name) {
                return "custom".equals(name) ? shared : null;
            }
            @Override public BitmapFont bitmapFont(int assetId) { return assetId == 42 ? override : null; }
        };
        HudValidationResult validation = new HudDocumentValidator().validate(new HudDocumentV1(root));
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        MaterializedHud hud = new HudMaterializer().materialize(validation.validatedDocument(), visual);
        TextTooltip tooltip = null;
        for (EventListener listener : hud.root().getListeners()) {
            if (listener instanceof TextTooltip) tooltip = (TextTooltip) listener;
        }
        Assert.assertNotNull(tooltip);
        Assert.assertNotSame(shared, tooltip.getStyle());
        Assert.assertNotSame(shared.label, tooltip.getStyle().label);
        Assert.assertSame(override, tooltip.getStyle().label.font);
        Assert.assertNull(shared.label.font);
        hud.dispose();
    }

    @Test
    public void tooltipMakesOnlyItsLayoutSurfaceHittableAndAuthoringCanOmitListeners() {
        HudNode root = new HudNode("root", HudNodeKind.TABLE);
        root.actor.width = 100f;
        root.actor.height = 100f;
        root.tooltip = new HudTooltipData();
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentV1(root), selectedResources);
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        MaterializedHud interactive = new HudMaterializer().materialize(
                validation.validatedDocument(), selectedResources);
        Assert.assertEquals(Touchable.enabled, interactive.root().getTouchable());
        Assert.assertSame(interactive.root(), interactive.root().hit(50f, 50f, true));
        interactive.dispose();

        MaterializedHud authoring = new HudMaterializer().materialize(
                validation.validatedDocument(), selectedResources, false);
        Assert.assertEquals(Touchable.childrenOnly, authoring.root().getTouchable());
        for (EventListener listener : authoring.root().getListeners()) {
            Assert.assertFalse(listener instanceof TextTooltip);
        }
        authoring.dispose();
    }

    @Test
    public void disposingDuringNativeTooltipDelayCancelsThePendingShow() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.actor.width = 100f;
        root.actor.height = 100f;
        root.tooltip = new HudTooltipData();
        MaterializedHud hud = materialize(new HudDocumentV1(root));
        TextTooltip tooltip = null;
        for (EventListener listener : hud.root().getListeners()) {
            if (listener instanceof TextTooltip) tooltip = (TextTooltip) listener;
        }
        Assert.assertNotNull(tooltip);
        java.lang.reflect.Field activeDelayField = TooltipManager.class.getDeclaredField("time");
        activeDelayField.setAccessible(true);
        Assert.assertEquals(0.5f, activeDelayField.getFloat(tooltip.getManager()), 0f);
        java.lang.reflect.Field pendingField = TooltipManager.class.getDeclaredField("showTask");
        pendingField.setAccessible(true);
        Timer.Task pending = (Timer.Task) pendingField.get(tooltip.getManager());
        java.lang.reflect.Field targetField = TooltipManager.class.getDeclaredField("showTooltip");
        targetField.setAccessible(true);
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Stage stage = new Stage(new ScreenViewport(), batch);
        Application testApp = Gdx.app;
        Graphics testGraphics = Gdx.graphics;
        try {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> "getType".equals(method.getName())
                            ? Application.ApplicationType.Desktop
                            : defaultValue(method.getReturnType()));
            Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                    new Class<?>[]{Graphics.class},
                    (proxy, method, args) -> "getWidth".equals(method.getName()) ? 300
                            : "getHeight".equals(method.getName()) ? 200
                            : defaultValue(method.getReturnType()));
            stage.getViewport().update(300, 200, true);
            stage.addActor(hud.root());
            stage.mouseMoved(20, 180);
            stage.act(0f);
            Assert.assertTrue(pending.isScheduled());
            Assert.assertNull(tooltip.getContainer().getStage());
            hud.dispose();
            Assert.assertFalse(pending.isScheduled());
            Assert.assertNull(targetField.get(tooltip.getManager()));
        } finally {
            stage.dispose();
            Gdx.app = testApp;
            Gdx.graphics = testGraphics;
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

    private static HudNode textraLabel(String id, boolean typingEnabled) {
        HudNode node = new HudNode(id, HudNodeKind.TEXTRA_LABEL);
        node.textraLabel = new HudTextraLabelData();
        node.textraLabel.text = "Text";
        node.textraLabel.typingEnabled = typingEnabled;
        return node;
    }

    private void assertPreparedWhiteGlyph(float scale, boolean existingBlock) {
        BitmapFont source = selectedResources.builtInLabelStyle().font;
        BitmapFont.BitmapFontData data = source.getData();
        BitmapFont.Glyph previousBlock = data.getGlyph('\u2588');
        float previousScaleX = data.scaleX;
        float previousScaleY = data.scaleY;
        float previousPadLeft = data.padLeft;
        float previousPadTop = data.padTop;
        float previousPadRight = data.padRight;
        float previousPadBottom = data.padBottom;
        boolean previousIntegerPositions = source.usesIntegerPositions();
        Font prepared = null;
        try {
            data.setScale(1f);
            data.padLeft = 2f;
            data.padTop = 3f;
            data.padRight = 4f;
            data.padBottom = 5f;
            data.setScale(scale);
            BitmapFont.Glyph block = null;
            if (existingBlock) {
                block = new BitmapFont.Glyph();
                block.id = '\u2588';
                block.page = 0;
                block.srcX = -2;
                block.srcY = -3;
                block.width = 3;
                block.height = 4;
                block.xadvance = 1;
                block.yoffset = -1;
            }
            data.setGlyph('\u2588', block);

            float configuredScaleX = data.scaleX;
            float configuredScaleY = data.scaleY;
            float configuredPadLeft = data.padLeft;
            float configuredPadTop = data.padTop;
            float configuredPadRight = data.padRight;
            float configuredPadBottom = data.padBottom;
            int sourceRegionCount = source.getRegions().size;
            int texturesBefore = generatedTextures;
            TextureRegion whiteRegion = new TextureRegion(InternalTextures.whiteTexture());

            prepared = HudTextraFontFactory.prepare(source, whiteRegion);

            Assert.assertNull(prepared.whiteBlock);
            Assert.assertEquals(texturesBefore, generatedTextures);
            Assert.assertEquals(1, prepared.mapping.get('\u2588').getRegionWidth());
            Assert.assertSame(existingBlock ? source.getRegion().getTexture()
                            : whiteRegion.getTexture(),
                    prepared.mapping.get('\u2588').getTexture());
            Assert.assertEquals(configuredScaleX, prepared.scaleX, 0f);
            Assert.assertEquals(configuredScaleY, prepared.scaleY, 0f);
            Assert.assertEquals(configuredScaleX, data.scaleX, 0f);
            Assert.assertEquals(configuredScaleY, data.scaleY, 0f);
            Assert.assertEquals(configuredPadLeft, data.padLeft, 0f);
            Assert.assertEquals(configuredPadTop, data.padTop, 0f);
            Assert.assertEquals(configuredPadRight, data.padRight, 0f);
            Assert.assertEquals(configuredPadBottom, data.padBottom, 0f);
            Assert.assertEquals(previousIntegerPositions, source.usesIntegerPositions());
            Assert.assertSame(block, data.getGlyph('\u2588'));
            Assert.assertEquals(sourceRegionCount, source.getRegions().size);
        } finally {
            if (prepared != null) prepared.dispose();
            data.setGlyph('\u2588', previousBlock);
            data.setScale(previousScaleX, previousScaleY);
            data.padLeft = previousPadLeft;
            data.padTop = previousPadTop;
            data.padRight = previousPadRight;
            data.padBottom = previousPadBottom;
        }
    }

    private void assertTextraLabelIntegerPositions(boolean enabled) {
        BitmapFont source = selectedResources.builtInLabelStyle().font;
        source.setUseIntegerPositions(enabled);

        MaterializedHud hud = materialize(new HudDocumentV1(textraLabel("textra", true)));
        TypingLabel label = (TypingLabel) hud.root();
        Font prepared = selectedResources.textraFont(source);

        Assert.assertEquals(enabled, prepared.integerPosition);
        Assert.assertEquals(enabled, label.getFont().integerPosition);
        hud.dispose();
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
