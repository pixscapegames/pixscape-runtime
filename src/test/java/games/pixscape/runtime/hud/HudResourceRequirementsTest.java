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
        assertRequirements(node("textra", "TEXTRA_LABEL", "\"textraLabel\":{\"text\":\"Title\","
                + "\"styleName\":\"title\",\"typingEnabled\":true},\"children\":[]"), true, true);
        assertRequirements(node("button", "TEXT_BUTTON", "\"textButton\":{\"text\":\"Go\","
                + "\"styleName\":\"primary\"},\"children\":[]"), true, true);
        assertRequirements(node("button", "IMAGE_BUTTON", "\"imageButton\":{\"styleName\":\"primary\","
                + "\"imageOver\":{\"source\":\"DRAWABLE\",\"resourceName\":\"over\"}},"
                + "\"children\":[]"), true, true);
        assertRequirements(node("field", "TEXT_FIELD", "\"textField\":{\"text\":\"\","
                + "\"messageText\":\"Name\",\"styleName\":\"compact\"},\"children\":[]"),
                true, true);
    }

    @Test
    public void labelWithoutCustomStyleRequiresBuiltInStyleAndAtlasButNoSkin() {
        HudResourceRequirements requirements = requirements(node("label", "LABEL",
                "\"label\":{\"text\":\"Label\"},\"children\":[]"));

        Assert.assertFalse(requirements.requiresSkin());
        Assert.assertTrue(requirements.requiresAtlas());
        Assert.assertTrue(requirements.requiresBuiltInLabelStyle());
    }

    @Test
    public void textraLabelWithoutCustomStyleRequiresBuiltInLabelResources() {
        HudResourceRequirements requirements = requirements(node("textra", "TEXTRA_LABEL",
                "\"textraLabel\":{\"text\":\"Text\",\"typingEnabled\":true},"
                        + "\"children\":[]"));

        Assert.assertFalse(requirements.requiresSkin());
        Assert.assertTrue(requirements.requiresAtlas());
        Assert.assertTrue(requirements.requiresBuiltInLabelStyle());
    }

    @Test
    public void textButtonWithoutCustomStyleRequiresBuiltInResourcesButNoSkin() {
        HudResourceRequirements requirements = requirements(node("button", "TEXT_BUTTON",
                "\"textButton\":{\"text\":\"Button\"},\"children\":[]"));

        Assert.assertFalse(requirements.requiresSkin());
        Assert.assertTrue(requirements.requiresAtlas());
        Assert.assertTrue(requirements.requiresBuiltInLabelStyle());
        Assert.assertTrue(requirements.requiresBuiltInTextButtonStyle());
    }

    @Test
    public void imageButtonWithoutCustomStyleRequiresItsBuiltInResourcesButNoFont() {
        HudResourceRequirements requirements = requirements(node("button", "IMAGE_BUTTON",
                "\"imageButton\":{},\"children\":[]"));

        Assert.assertFalse(requirements.requiresSkin());
        Assert.assertTrue(requirements.requiresAtlas());
        Assert.assertFalse(requirements.requiresBuiltInLabelStyle());
        Assert.assertFalse(requirements.requiresBuiltInTextButtonStyle());
        Assert.assertTrue(requirements.requiresBuiltInImageButtonStyle());
    }

    @Test
    public void imageTextButtonWithoutCustomStyleRequiresBuiltInTextAndImageButtonResources() {
        HudResourceRequirements requirements = requirements(node("button", "IMAGE_TEXT_BUTTON",
                "\"imageTextButton\":{\"text\":\"Button\"},\"children\":[]"));

        Assert.assertFalse(requirements.requiresSkin());
        Assert.assertTrue(requirements.requiresAtlas());
        Assert.assertTrue(requirements.requiresBuiltInLabelStyle());
        Assert.assertTrue(requirements.requiresBuiltInTextButtonStyle());
        Assert.assertTrue(requirements.requiresBuiltInImageTextButtonStyle());
    }

    @Test
    public void textFieldWithoutCustomStyleRequiresBuiltInFontAndGraphicsButNoSkin() {
        HudResourceRequirements requirements = requirements(node("field", "TEXT_FIELD",
                "\"textField\":{\"text\":\"\",\"messageText\":\"Name\"},\"children\":[]"));

        Assert.assertFalse(requirements.requiresSkin());
        Assert.assertTrue(requirements.requiresAtlas());
        Assert.assertTrue(requirements.requiresBuiltInLabelStyle());
        Assert.assertTrue(requirements.requiresBuiltInTextFieldStyle());
    }

    @Test
    public void selectBoxWithoutCustomStyleRequiresCompleteBuiltInNativeStyle() {
        HudResourceRequirements requirements = requirements(node("choice", "SELECT_BOX",
                "\"selectBox\":{\"items\":[\"One\"],\"selectedIndex\":0},\"children\":[]"));

        Assert.assertFalse(requirements.requiresSkin());
        Assert.assertTrue(requirements.requiresAtlas());
        Assert.assertTrue(requirements.requiresBuiltInLabelStyle());
        Assert.assertTrue(requirements.requiresBuiltInSelectBoxStyle());
    }

    @Test
    public void sliderUsesAtlasOnlyForDefaultAndSkinForCustomStyle() {
        HudResourceRequirements builtIn = requirements(node("slider", "SLIDER",
                "\"slider\":{},\"children\":[]"));
        Assert.assertFalse(builtIn.requiresSkin());
        Assert.assertTrue(builtIn.requiresAtlas());
        Assert.assertTrue(builtIn.requiresBuiltInSliderStyle());
        Assert.assertFalse(builtIn.requiresBuiltInLabelStyle());
        Assert.assertTrue(builtIn.bitmapFontAssetIds().isEmpty());

        HudResourceRequirements custom = requirements(node("slider", "SLIDER",
                "\"slider\":{\"styleName\":\"compact\"},\"children\":[]"));
        Assert.assertTrue(custom.requiresSkin());
        Assert.assertFalse(custom.requiresBuiltInSliderStyle());
        Assert.assertTrue(custom.bitmapFontAssetIds().isEmpty());
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

    @Test
    public void everyNativeTextPayloadContributesItsFontAssetDependency() {
        String button = node("button", "TEXT_BUTTON", "\"textButton\":{\"text\":\"Go\"," +
                "\"fontAssetId\":41},\"children\":[]");
        String check = node("check", "CHECK_BOX", "\"checkBox\":{\"fontAssetId\":42}," +
                "\"children\":[]");
        String field = node("field", "TEXT_FIELD", "\"textField\":{\"fontAssetId\":43}," +
                "\"children\":[]");
        String select = node("select", "SELECT_BOX", "\"selectBox\":{\"items\":[]," +
                "\"selectedIndex\":-1,\"fontAssetId\":44},\"children\":[]");
        String textra = node("textra", "TEXTRA_LABEL", "\"textraLabel\":{\"text\":\"Text\"," +
                "\"fontAssetId\":45,\"typingEnabled\":true},\"children\":[]");
        HudResourceRequirements requirements = requirements(node("root", "GROUP",
                "\"children\":[{\"placementKind\":\"DIRECT\",\"node\":" + button + "},{" +
                        "\"placementKind\":\"DIRECT\",\"node\":" + check + "},{" +
                        "\"placementKind\":\"DIRECT\",\"node\":" + field + "},{" +
                        "\"placementKind\":\"DIRECT\",\"node\":" + select + "},{" +
                        "\"placementKind\":\"DIRECT\",\"node\":" + textra + "}]"));

        Assert.assertEquals(5, requirements.bitmapFontAssetIds().size());
        Assert.assertTrue(requirements.bitmapFontAssetIds().contains(41));
        Assert.assertTrue(requirements.bitmapFontAssetIds().contains(42));
        Assert.assertTrue(requirements.bitmapFontAssetIds().contains(43));
        Assert.assertTrue(requirements.bitmapFontAssetIds().contains(44));
        Assert.assertTrue(requirements.bitmapFontAssetIds().contains(45));
    }

    private static void assertRequirements(
            String root, boolean expectedSkin, boolean expectedAtlas) {
        HudResourceRequirements requirements = requirements(root);
        Assert.assertEquals(expectedSkin, requirements.requiresSkin());
        Assert.assertEquals(expectedAtlas, requirements.requiresAtlas());
    }

    private static HudResourceRequirements requirements(String root) {
        HudValidationResult validation = new HudDocumentValidator().validate(
                new HudDocumentCodec().read("{\"schemaVersion\":1,\"root\":" + root + "}"));
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        return HudResourceRequirements.from(validation.validatedDocument());
    }

    private static String node(String id, String kind, String fields) {
        return "{\"id\":\"" + id + "\",\"kind\":\""
                + kind + "\"," + fields + "}";
    }
}
