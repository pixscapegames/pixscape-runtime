package games.pixscape.runtime.hud;

import com.badlogic.gdx.utils.Json;
import org.junit.Assert;
import org.junit.Test;

public class HudScreenAssetTest {
    @Test
    public void defaultsUseSupportedSchemaWithoutResolution() {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/default.json";

        asset.validate();

        Assert.assertEquals(HudScreenAsset.CURRENT_SCHEMA_VERSION, asset.schemaVersion);
        Assert.assertNull(asset.skinId);
        Assert.assertNull(asset.atlasId);
        Assert.assertEquals("hud/default.json", asset.documentId);
        Assert.assertEquals(HudTextureProfile.DEFAULT_ID, asset.textureProfileId);
    }

    @Test
    public void descriptorRoundTripsWithoutResolution() {
        HudScreenAsset source = new HudScreenAsset();
        source.documentId = "hud/portrait.json";

        Json json = new Json();
        json.setUsePrototypes(false);
        HudScreenAsset restored = json.fromJson(
                HudScreenAsset.class, json.toJson(source));
        restored.validate();

        Assert.assertEquals("hud/portrait.json", restored.documentId);
        Assert.assertFalse(json.toJson(restored).contains("referenceWidth"));
    }

    @Test
    public void documentReferenceRoundTripsAndIsRequired() {
        Json json = new Json();
        json.setUsePrototypes(false);
        HudScreenAsset source = new HudScreenAsset();
        source.documentId = "hud/game.json";

        HudScreenAsset restored = json.fromJson(
                HudScreenAsset.class, json.toJson(source));
        restored.validate();
        HudScreenAsset incomplete = json.fromJson(
                HudScreenAsset.class, "{\"schemaVersion\":1}");
        rejected(incomplete, "documentId is required");

        Assert.assertEquals("hud/game.json", restored.documentId);
    }

    @Test
    public void invalidSchemaIsRejected() {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/invalid.json";
        asset.schemaVersion = 2;
        rejected(asset, "schemaVersion 1");

    }

    @Test
    public void logicalIdsAreCanonicalProjectRelativeReferences() {
        Assert.assertEquals("hud/game", HudScreenAssetId.normalize("game"));
        Assert.assertEquals("hud/game", HudScreenAssetId.normalize("hud\\game"));
        Assert.assertNull(HudScreenAssetId.normalizeOptional(null));
        Assert.assertNull(HudScreenAssetId.normalizeOptional(""));
        Assert.assertNull(HudScreenAssetId.normalizeOptional("   "));
    }

    @Test
    public void resourceReferencesRoundTripAndValidationDoesNotMutateThem() {
        HudScreenAsset source = new HudScreenAsset();
        source.documentId = "hud/resources.json";
        source.skinId = "  ui\\game.json  ";
        source.atlasId = "  ui\\game.atlas  ";
        source.textureProfileId = "  " + HudTextureProfile.DEFAULT_ID + "  ";

        Json json = new Json();
        json.setUsePrototypes(false);
        HudScreenAsset restored = json.fromJson(
                HudScreenAsset.class, json.toJson(source));
        restored.validate();

        Assert.assertEquals(source.skinId, restored.skinId);
        Assert.assertEquals(source.atlasId, restored.atlasId);
        Assert.assertEquals(source.textureProfileId, restored.textureProfileId);
        Assert.assertEquals("ui/game.json",
                HudResourceId.normalizeOptional(restored.skinId, "Skin"));
        Assert.assertEquals("ui/game.atlas",
                HudResourceId.normalizeOptional(restored.atlasId, "TextureAtlas"));
    }

    @Test
    public void absoluteAndParentTraversalResourceReferencesAreRejected() {
        HudScreenAsset asset = new HudScreenAsset();
        asset.skinId = "C:\\ui\\game.json";
        rejected(asset, "project-relative");

        asset.skinId = "ui/../game.json";
        rejected(asset, "project-relative");

        asset.skinId = "ui/game.json";
        asset.atlasId = "/ui/game.atlas";
        rejected(asset, "project-relative");

        asset.atlasId = "ui/game.atlas";
        asset.documentId = "../outside.json";
        rejected(asset, "project-relative");
    }

    private static void rejected(HudScreenAsset asset, String diagnostic) {
        try {
            asset.validate();
            Assert.fail("Expected rejection containing: " + diagnostic);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains(diagnostic));
        }
    }
}
