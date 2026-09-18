package games.pixscape.runtime.hud.document;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;
import org.junit.Assert;
import org.junit.Test;

public class HudDocumentV1Test {
    private static final String FIXTURES =
            "src/test/resources/games/pixscape/runtime/hud/document/v1/";

    @Test
    public void minimalDocumentCanBeConstructedWithoutRuntimeObjects() {
        HudNode root = new HudNode("root", HudNodeKind.STACK);
        HudNode label = new HudNode("message", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "Ready";
        label.label.styleName = "hud-body";
        root.children.add(HudChild.direct(label));

        HudDocumentV1 document = new HudDocumentV1(root);

        Assert.assertEquals(HudDocumentV1.CURRENT_SCHEMA_VERSION, document.schemaVersion);
        Assert.assertSame(root, document.root);
        Assert.assertEquals("message", document.root.children.get(0).node.id);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructedDocumentRequiresItsSingleRoot() {
        new HudDocumentV1(null);
    }

    @Test
    public void orderedHierarchyAndKindTokensSurviveJsonRoundTrip() {
        HudDocumentV1 source = read("stack.json");

        String serialized = json().prettyPrint(source);
        HudDocumentV1 restored = json().fromJson(HudDocumentV1.class, serialized);

        Assert.assertEquals(HudNodeKind.STACK, restored.root.kind);
        Assert.assertEquals(2, restored.root.children.size());
        Assert.assertEquals("overlay-background", restored.root.children.get(0).node.id);
        Assert.assertEquals("overlay-title", restored.root.children.get(1).node.id);
        Assert.assertTrue(serialized.contains("\"kind\": \"STACK\""));
        Assert.assertFalse(serialized.contains(HudNode.class.getName()));
        Assert.assertFalse(serialized.contains("\"class\""));
    }

    @Test
    public void freeAnchorPivotAndOffsetDataSurvivesJsonRoundTrip() {
        HudDocumentV1 restored = roundTrip(read("free-layout.json"));

        HudFreePlacement topLeft = restored.root.children.get(0).free;
        Assert.assertEquals(HudHorizontalAnchor.LEFT, topLeft.horizontalAnchor);
        Assert.assertEquals(HudVerticalAnchor.TOP, topLeft.verticalAnchor);
        Assert.assertEquals(0f, topLeft.pivotX, 0f);
        Assert.assertEquals(1f, topLeft.pivotY, 0f);
        Assert.assertEquals(24f, topLeft.offsetX, 0f);
        Assert.assertEquals(-24f, topLeft.offsetY, 0f);

        HudFreePlacement center = restored.root.children.get(1).free;
        Assert.assertEquals(HudHorizontalAnchor.CENTER, center.horizontalAnchor);
        Assert.assertEquals(HudVerticalAnchor.CENTER, center.verticalAnchor);
        Assert.assertEquals(.5f, center.pivotX, 0f);
        Assert.assertEquals(.5f, center.pivotY, 0f);

        HudFreePlacement bottomRight = restored.root.children.get(2).free;
        Assert.assertEquals(HudHorizontalAnchor.RIGHT, bottomRight.horizontalAnchor);
        Assert.assertEquals(HudVerticalAnchor.BOTTOM, bottomRight.verticalAnchor);
        Assert.assertEquals(1f, bottomRight.pivotX, 0f);
        Assert.assertEquals(0f, bottomRight.pivotY, 0f);
        Assert.assertEquals(-24f, bottomRight.offsetX, 0f);
        Assert.assertEquals(24f, bottomRight.offsetY, 0f);
    }

    @Test
    public void nativeCellConstructionDataSurvivesJsonRoundTrip() {
        HudDocumentV1 restored = roundTrip(read("managed-table.json"));

        HudCellConstraints title = restored.root.children.get(0).cell;
        Assert.assertTrue(title.fillX);
        Assert.assertTrue(title.expandX);
        Assert.assertTrue(title.rowAfter);
        Assert.assertEquals(HudHorizontalAlign.LEFT, title.horizontalAlign);
        Assert.assertEquals(24f, title.padLeft, 0f);
        Assert.assertNull(title.minWidth);
        Assert.assertNotNull(title.prefHeight);

        HudCellConstraints actions = restored.root.children.get(1).cell;
        Assert.assertEquals(320f, actions.minWidth, 0f);
        Assert.assertEquals(640f, actions.prefWidth, 0f);
        Assert.assertTrue(actions.fillY);
        Assert.assertTrue(actions.expandY);
    }

    @Test
    public void allPositiveContractFixturesUseSchemaOneAndOneRoot() {
        String[] fixtureNames = {
                "managed-table.json",
                "stack.json",
                "free-layout.json",
                "mixed-layout.json",
                "resources.json",
                "clipped-container.json"
        };

        for (int i = 0; i < fixtureNames.length; i++) {
            HudDocumentV1 document = read(fixtureNames[i]);
            Assert.assertEquals(fixtureNames[i], HudDocumentV1.CURRENT_SCHEMA_VERSION,
                    document.schemaVersion);
            Assert.assertNotNull(fixtureNames[i], document.root);
            Assert.assertNotNull(fixtureNames[i], document.root.id);
        }
    }

    @Test
    public void negativeIdentityFixtureRemainsReadableForStepTwoValidation() {
        HudDocumentV1 document = read("invalid-duplicate-node-id.json");

        Assert.assertEquals("duplicate", document.root.children.get(0).node.id);
        Assert.assertEquals("duplicate", document.root.children.get(1).node.id);
    }

    @Test
    public void unknownKindCannotBecomeAnArbitraryJavaType() {
        try {
            read("invalid-unknown-node-kind.json");
            Assert.fail("Expected the bounded HudNodeKind token to reject an unknown kind.");
        } catch (SerializationException expected) {
            Assert.assertTrue(expected.getMessage(),
                    containsInChain(expected, "ARBITRARY_JAVA_ACTOR"));
        }
    }

    private static HudDocumentV1 roundTrip(HudDocumentV1 source) {
        Json json = json();
        return json.fromJson(HudDocumentV1.class, json.toJson(source));
    }

    private static HudDocumentV1 read(String fixtureName) {
        return json().fromJson(HudDocumentV1.class,
                new FileHandle(FIXTURES + fixtureName));
    }

    private static Json json() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(false);
        json.setUsePrototypes(false);
        return json;
    }

    private static boolean containsInChain(Throwable failure, String text) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(text)) return true;
            current = current.getCause();
        }
        return false;
    }
}
