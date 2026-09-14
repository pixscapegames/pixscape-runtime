package games.pixscape.runtime.hud;

import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudValidationResult;
import org.junit.Assert;
import org.junit.Test;

public class HudResourceRequirementsTest {
    @Test
    public void layoutKindsRequireNoResources() {
        assertRequirements(node("group", "GROUP", "\"children\":[]"), false, false);
        assertRequirements(node("table", "TABLE", "\"children\":[]"), false, false);
        assertRequirements(node("stack", "STACK", "\"children\":[]"), false, false);
        assertRequirements(node("container", "CONTAINER", "\"container\":{},\"children\":[{"
                + "\"placementKind\":\"DIRECT\",\"node\":"
                + node("child", "GROUP", "\"children\":[]") + "}]"), false, false);
    }

    @Test
    public void nestedLayoutOnlyTreeRequiresNoResources() {
        String stack = node("stack", "STACK", "\"children\":[{\"placementKind\":\"DIRECT\","
                + "\"node\":" + node("leaf-layout", "GROUP", "\"children\":[]") + "}]");
        String table = node("table", "TABLE", "\"children\":[{\"placementKind\":\"CELL\","
                + "\"cell\":{},\"node\":" + stack + "}]");
        assertRequirements(node("root", "GROUP", "\"children\":[{"
                + "\"placementKind\":\"DIRECT\",\"node\":" + table + "}]"),
                false, false);
    }

    @Test
    public void actorPayloadsDeriveExactResourceUnion() {
        assertRequirements(node("image", "IMAGE", "\"image\":{\"source\":\"REGION\","
                + "\"resourceName\":\"art\"},\"children\":[]"), false, true);
        assertRequirements(node("image", "IMAGE", "\"image\":{\"source\":\"DRAWABLE\","
                + "\"resourceName\":\"panel\"},\"children\":[]"), true, true);
        assertRequirements(node("label", "LABEL", "\"label\":{\"text\":\"Title\","
                + "\"styleName\":\"title\"},\"children\":[]"), true, true);
        assertRequirements(node("button", "TEXT_BUTTON", "\"textButton\":{\"text\":\"Go\","
                + "\"styleName\":\"primary\"},\"children\":[]"), true, true);
    }

    @Test
    public void mixedTreeUsesUnionOfActualRequirements() {
        String image = node("image", "IMAGE", "\"image\":{\"source\":\"REGION\","
                + "\"resourceName\":\"art\"},\"children\":[]");
        String label = node("label", "LABEL", "\"label\":{\"text\":\"Title\","
                + "\"styleName\":\"title\"},\"children\":[]");
        assertRequirements(node("root", "STACK", "\"children\":[{"
                + "\"placementKind\":\"DIRECT\",\"node\":" + image + "},{"
                + "\"placementKind\":\"DIRECT\",\"node\":" + label + "}]"),
                true, true);
    }

    private static void assertRequirements(
            String root, boolean expectedSkin, boolean expectedAtlas) {
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentCodec().read("{\"schemaVersion\":1,\"root\":" + root + "}"));
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        HudResourceRequirements requirements =
                HudResourceRequirements.from(validation.validatedDocument());
        Assert.assertEquals(expectedSkin, requirements.requiresSkin());
        Assert.assertEquals(expectedAtlas, requirements.requiresAtlas());
    }

    private static String node(String id, String kind, String fields) {
        return "{\"id\":\"" + id + "\",\"kind\":\""
                + kind + "\"," + fields + "}";
    }
}
