package games.pixscape.runtime.hud;

import com.badlogic.gdx.utils.Json;
import org.junit.Assert;
import org.junit.Test;

public class HudScreenAssetTest {
    @Test
    public void defaultsUseSupportedSchemaAndReferenceResolution() {
        HudScreenAsset asset = new HudScreenAsset();

        asset.validate();

        Assert.assertEquals(HudScreenAsset.CURRENT_SCHEMA_VERSION, asset.schemaVersion);
        Assert.assertEquals(1920, asset.referenceWidth);
        Assert.assertEquals(1080, asset.referenceHeight);
        Assert.assertNull(asset.skinId);
        Assert.assertNull(asset.atlasId);
        Assert.assertEquals(HudTextureProfile.DEFAULT_ID, asset.textureProfileId);
    }

    @Test
    public void customPositiveReferenceResolutionRoundTrips() {
        HudScreenAsset source = new HudScreenAsset();
        source.referenceWidth = 1080;
        source.referenceHeight = 1920;

        Json json = new Json();
        json.setUsePrototypes(false);
        HudScreenAsset restored = json.fromJson(
                HudScreenAsset.class, json.toJson(source));
        restored.validate();

        Assert.assertEquals(1080, restored.referenceWidth);
        Assert.assertEquals(1920, restored.referenceHeight);
    }

    @Test
    public void invalidSchemaAndReferenceDimensionsAreRejected() {
        HudScreenAsset asset = new HudScreenAsset();
        asset.schemaVersion = 2;
        rejected(asset, "schemaVersion 1");

        asset.schemaVersion = HudScreenAsset.CURRENT_SCHEMA_VERSION;
        asset.referenceWidth = 0;
        rejected(asset, "referenceWidth");

        asset.referenceWidth = 1920;
        asset.referenceHeight = -1;
        rejected(asset, "referenceHeight");
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
