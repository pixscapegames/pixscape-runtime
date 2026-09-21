package games.pixscape.runtime.hud.document;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class HudDocumentCodecTest {
    private static final String FIXTURES =
            "src/test/resources/games/pixscape/runtime/hud/document/v1/";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final HudDocumentCodec codec = new HudDocumentCodec();

    @Test
    public void readsPositiveFixture() {
        HudDocumentV1 document = codec.read(fixture("managed-table.json"));

        Assert.assertEquals(HudDocumentV1.CURRENT_SCHEMA_VERSION, document.schemaVersion);
        Assert.assertEquals("screen", document.root.id);
        Assert.assertEquals(HudNodeKind.TABLE, document.root.kind);
    }

    @Test
    public void canonicalJsonRoundTripPreservesStableTokensAndOrder() {
        HudDocumentV1 source = codec.read(fixture("free-layout.json"));

        String first = codec.write(source);
        String second = codec.write(source);
        HudDocumentV1 restored = codec.read(first);

        Assert.assertEquals(first, second);
        Assert.assertTrue(first.contains("\"kind\": \"GROUP\""));
        Assert.assertTrue(first.contains("\"placementKind\": \"FREE\""));
        Assert.assertFalse(first.contains("\"class\""));
        Assert.assertEquals("top-left", restored.root.children.get(0).node.id);
        Assert.assertEquals("bottom-right", restored.root.children.get(2).node.id);
    }

    @Test
    public void fileWriterAndReaderRoundTrip() throws Exception {
        HudDocumentV1 source = codec.read(fixture("stack.json"));
        File target = temporaryFolder.newFolder("nested").toPath()
                .resolve("screen.json").toFile();

        codec.write(new FileHandle(target), source);
        HudDocumentV1 restored = codec.read(new FileHandle(target));

        Assert.assertEquals("overlay", restored.root.id);
        Assert.assertEquals(2, restored.root.children.size());
    }

    @Test
    public void imageButtonRoundTripPreservesOptionalNativeStateReferences() {
        HudNode button = new HudNode("button", HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.imageUp = image("up");
        button.imageButton.imageCheckedOver = image("checked-over");

        HudDocumentV1 restored = codec.read(codec.write(new HudDocumentV1(button)));

        Assert.assertEquals(HudNodeKind.IMAGE_BUTTON, restored.root.kind);
        Assert.assertEquals("up", restored.root.imageButton.imageUp.resourceName);
        Assert.assertNull(restored.root.imageButton.imageDown);
        Assert.assertEquals("checked-over", restored.root.imageButton.imageCheckedOver.resourceName);
    }

    @Test
    public void imageTextButtonRoundTripPreservesTypedTextFontStyleAndStateReferences() {
        HudNode button = new HudNode("button", HudNodeKind.IMAGE_TEXT_BUTTON);
        button.imageTextButton = new HudImageTextButtonData();
        button.imageTextButton.text = "Play";
        button.imageTextButton.styleName = "primary";
        button.imageTextButton.fontAssetId = 24;
        button.imageTextButton.imageUp = image("up");
        button.imageTextButton.imageCheckedOver = image("checked-over");

        HudNode restored = codec.read(codec.write(new HudDocumentV1(button))).root;

        Assert.assertEquals(HudNodeKind.IMAGE_TEXT_BUTTON, restored.kind);
        Assert.assertEquals("Play", restored.imageTextButton.text);
        Assert.assertEquals("primary", restored.imageTextButton.styleName);
        Assert.assertEquals(Integer.valueOf(24), restored.imageTextButton.fontAssetId);
        Assert.assertEquals("up", restored.imageTextButton.imageUp.resourceName);
        Assert.assertEquals("checked-over", restored.imageTextButton.imageCheckedOver.resourceName);
    }

    @Test
    public void textFieldRoundTripPreservesAuthoredInitialValues() {
        HudNode field = new HudNode("name", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        field.textField.text = "Ada";
        field.textField.messageText = "Name";
        field.textField.styleName = "compact";
        field.textField.maxLength = 24;
        field.textField.passwordMode = true;

        HudNode restored = codec.read(codec.write(new HudDocumentV1(field))).root;

        Assert.assertEquals(HudNodeKind.TEXT_FIELD, restored.kind);
        Assert.assertEquals("Ada", restored.textField.text);
        Assert.assertEquals("Name", restored.textField.messageText);
        Assert.assertEquals("compact", restored.textField.styleName);
        Assert.assertEquals(24, restored.textField.maxLength);
        Assert.assertTrue(restored.textField.passwordMode);
    }

    @Test
    public void labelRoundTripPreservesOptionalBitmapFontAssetIdentity() {
        HudNode label = new HudNode("title", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "Title";
        label.label.fontAssetId = 73;

        HudNode restored = codec.read(codec.write(new HudDocumentV1(label))).root;

        Assert.assertEquals(Integer.valueOf(73), restored.label.fontAssetId);
    }

    @Test
    public void textraLabelRoundTripPreservesAuthoredBitmapStateOnly() {
        HudNode label = new HudNode("dialogue", HudNodeKind.TEXTRA_LABEL);
        label.textraLabel = new HudTextraLabelData();
        label.textraLabel.text = "{WAVE}Hello{ENDWAVE}";
        label.textraLabel.styleName = "dialogue";
        label.textraLabel.fontAssetId = 75;
        label.textraLabel.typingEnabled = true;

        String serialized = codec.write(new HudDocumentV1(label));
        HudNode restored = codec.read(serialized).root;

        Assert.assertEquals(HudNodeKind.TEXTRA_LABEL, restored.kind);
        Assert.assertEquals("{WAVE}Hello{ENDWAVE}", restored.textraLabel.text);
        Assert.assertEquals("dialogue", restored.textraLabel.styleName);
        Assert.assertEquals(Integer.valueOf(75), restored.textraLabel.fontAssetId);
        Assert.assertTrue(restored.textraLabel.typingEnabled);
        Assert.assertFalse(serialized.contains("elapsed"));
    }

    @Test
    public void nativeTextWidgetRoundTripPreservesBitmapFontOverrides() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "Button";
        button.textButton.fontAssetId = 71;
        HudNode check = new HudNode("check", HudNodeKind.CHECK_BOX);
        check.checkBox = new HudCheckBoxData();
        check.checkBox.fontAssetId = 72;
        HudNode field = new HudNode("field", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        field.textField.fontAssetId = 73;
        HudNode select = new HudNode("select", HudNodeKind.SELECT_BOX);
        select.selectBox = new HudSelectBoxData();
        select.selectBox.fontAssetId = 74;
        root.children.add(HudChild.direct(button));
        root.children.add(HudChild.direct(check));
        root.children.add(HudChild.direct(field));
        root.children.add(HudChild.direct(select));

        HudNode restored = codec.read(codec.write(new HudDocumentV1(root))).root;

        Assert.assertEquals(Integer.valueOf(71), restored.children.get(0).node.textButton.fontAssetId);
        Assert.assertEquals(Integer.valueOf(72), restored.children.get(1).node.checkBox.fontAssetId);
        Assert.assertEquals(Integer.valueOf(73), restored.children.get(2).node.textField.fontAssetId);
        Assert.assertEquals(Integer.valueOf(74), restored.children.get(3).node.selectBox.fontAssetId);
    }

    @Test
    public void sliderRoundTripPreservesAuthoredNativeState() {
        HudNode slider = new HudNode("volume", HudNodeKind.SLIDER);
        slider.slider = new HudSliderData();
        slider.slider.orientation = HudSliderOrientation.VERTICAL;
        slider.slider.min = -10f;
        slider.slider.max = 10f;
        slider.slider.stepSize = 0.5f;
        slider.slider.value = 3.25f;
        slider.slider.styleName = "compact";
        slider.slider.disabled = true;

        HudNode restored = codec.read(codec.write(new HudDocumentV1(slider))).root;

        Assert.assertEquals(HudNodeKind.SLIDER, restored.kind);
        Assert.assertEquals(HudSliderOrientation.VERTICAL, restored.slider.orientation);
        Assert.assertEquals(-10f, restored.slider.min, 0f);
        Assert.assertEquals(10f, restored.slider.max, 0f);
        Assert.assertEquals(0.5f, restored.slider.stepSize, 0f);
        Assert.assertEquals(3.25f, restored.slider.value, 0f);
        Assert.assertEquals("compact", restored.slider.styleName);
        Assert.assertTrue(restored.slider.disabled);
    }

    @Test
    public void progressBarRoundTripPreservesAuthoredNativeState() {
        HudNode progressBar = new HudNode("progress", HudNodeKind.PROGRESS_BAR);
        progressBar.progressBar = new HudProgressBarData();
        progressBar.progressBar.orientation = HudSliderOrientation.VERTICAL;
        progressBar.progressBar.min = -10f;
        progressBar.progressBar.max = 10f;
        progressBar.progressBar.stepSize = .125f;
        progressBar.progressBar.value = 3.25f;
        progressBar.progressBar.styleName = "compact";
        progressBar.progressBar.disabled = true;

        HudNode restored = codec.read(codec.write(new HudDocumentV1(progressBar))).root;

        Assert.assertEquals(HudNodeKind.PROGRESS_BAR, restored.kind);
        Assert.assertEquals(HudSliderOrientation.VERTICAL, restored.progressBar.orientation);
        Assert.assertEquals(-10f, restored.progressBar.min, 0f);
        Assert.assertEquals(10f, restored.progressBar.max, 0f);
        Assert.assertEquals(.125f, restored.progressBar.stepSize, 0f);
        Assert.assertEquals(3.25f, restored.progressBar.value, 0f);
        Assert.assertEquals("compact", restored.progressBar.styleName);
        Assert.assertTrue(restored.progressBar.disabled);
    }

    private static HudImageData image(String resourceName) {
        HudImageData image = new HudImageData();
        image.resourceName = resourceName;
        return image;
    }

    @Test
    public void unsupportedSchemaHasTypedLoadFailure() {
        rejected(fixture("invalid-unsupported-schema.json"),
                HudDocumentLoadCode.UNSUPPORTED_SCHEMA_VERSION);
    }

    @Test
    public void missingSchemaHasTypedLoadFailure() {
        rejected("{\"root\":null}", HudDocumentLoadCode.MISSING_SCHEMA_VERSION);
    }

    @Test
    public void nonIntegerSchemaHasTypedDeserializationFailure() {
        rejected("{\"schemaVersion\":1.5,\"root\":null}",
                HudDocumentLoadCode.DESERIALIZATION_FAILURE);
    }

    @Test
    public void malformedJsonHasTypedLoadFailure() {
        rejected("{\"schemaVersion\":1,", HudDocumentLoadCode.MALFORMED_JSON);
    }

    @Test
    public void nonObjectJsonHasTypedLoadFailure() {
        rejected("[]", HudDocumentLoadCode.DESERIALIZATION_FAILURE);
    }

    @Test
    public void unknownNodeKindHasTypedDeserializationFailure() {
        rejected(fixture("invalid-unknown-node-kind.json"),
                HudDocumentLoadCode.DESERIALIZATION_FAILURE);
    }

    @Test
    public void unknownPlacementKindHasTypedDeserializationFailure() {
        String json = "{\"schemaVersion\":1,\"root\":{"
                + "\"id\":\"root\",\"kind\":\"STACK\",\"children\":[{"
                + "\"placementKind\":\"MAGIC\",\"node\":{"
                + "\"id\":\"label\",\"kind\":\"LABEL\","
                + "\"label\":{\"text\":\"x\",\"styleName\":\"hud-body\"}}}]}}";

        rejected(json, HudDocumentLoadCode.DESERIALIZATION_FAILURE);
    }

    private void rejected(FileHandle file, HudDocumentLoadCode expectedCode) {
        try {
            codec.read(file);
            Assert.fail("Expected HUD load failure " + expectedCode + ".");
        } catch (HudDocumentLoadException expected) {
            Assert.assertEquals(expectedCode, expected.code());
            Assert.assertEquals(file.path(), expected.source());
        }
    }

    private void rejected(String json, HudDocumentLoadCode expectedCode) {
        try {
            codec.read(json);
            Assert.fail("Expected HUD load failure " + expectedCode + ".");
        } catch (HudDocumentLoadException expected) {
            Assert.assertEquals(expectedCode, expected.code());
            Assert.assertEquals("<memory>", expected.source());
        }
    }

    private static FileHandle fixture(String name) {
        return new FileHandle(FIXTURES + name);
    }
}
