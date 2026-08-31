package games.pixscape.runtime.gameobject;

import games.pixscape.runtime.property.PropertySet;
import org.junit.Assert;
import org.junit.Test;

public class GameObjectAssetLoaderTest {
    private final GameObjectAssetLoader loader = new GameObjectAssetLoader();

    @Test
    public void logicalAssetIdIsCanonicalAndProjectRelative() {
        Assert.assertEquals("gameobjects/enemy.gameobject",
                GameObjectAssetId.normalize("enemy"));
        Assert.assertEquals("gameobjects/enemy.gameobject",
                GameObjectAssetId.normalize("gameobjects\\enemy.gameobject"));
        Assert.assertEquals("gameobjects/enemy.pixfragment.json",
                GameObjectAssetId.runtimeFragmentRelativePath("enemy"));
    }

    @Test
    public void schemaHierarchyAndAuthoredStateRoundTrip() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(2).tags = new GameObjectAsset.TagsData();
        asset.entities.get(2).tags.values.add("enemy");
        asset.entities.get(2).customProperties = new PropertySet()
                .putString("role", "guard")
                .putObjectStableId("target", 2);
        asset.entities.get(2).animation = new GameObjectAsset.AnimationData();
        asset.entities.get(2).animation.currentClip = "idle";
        asset.entities.get(2).pointLight = new GameObjectAsset.PointLightData();

        String json = loader.toJson(asset);
        GameObjectAsset restored = loader.fromJson(json);

        Assert.assertEquals(1, restored.schemaVersion);
        Assert.assertEquals(1, restored.rootSourceEntityId);
        Assert.assertEquals(1, restored.entities.get(1).parentSourceEntityId);
        Assert.assertEquals(2, restored.entities.get(2).parentSourceEntityId);
        Assert.assertEquals(4f, restored.entities.get(1).transform.x, 0f);
        Assert.assertEquals(7, restored.entities.get(1).entityIndex.zIndex);
        Assert.assertEquals("enemy", restored.entities.get(2).tags.values.get(0));
        Assert.assertEquals("guard", restored.entities.get(2).customProperties
                .getString("role", ""));
        Assert.assertEquals(2, restored.entities.get(2).customProperties
                .getObjectStableId("target", -1));
        Assert.assertEquals("idle", restored.entities.get(2).animation.currentClip);
        Assert.assertNotNull(restored.entities.get(2).pointLight);
        Assert.assertNotNull(restored.entities.get(1).gameObject);
    }

    @Test
    public void derivedAndSceneIdentityStateIsAbsentFromJson() {
        String json = loader.toJson(validNestedAsset());
        Assert.assertFalse(json.contains("parentStableId"));
        Assert.assertFalse(json.contains("stableId"));
        Assert.assertFalse(json.contains("entityId"));
        Assert.assertFalse(json.contains("WorldTransformState"));
        Assert.assertFalse(json.contains("sourceAssetId"));
        Assert.assertFalse(json.contains("layerIndex"));
    }

    @Test public void missingRootIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.rootSourceEntityId = 99;
        rejected(asset, "rootSourceEntityId 99 is missing");
    }

    @Test public void duplicateSourceIdIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(1).sourceEntityId = 1;
        rejected(asset, "duplicate sourceEntityId 1");
    }

    @Test public void missingParentIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(1).parentSourceEntityId = 99;
        rejected(asset, "missing parentSourceEntityId 99");
    }

    @Test public void directCycleIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(1).parentSourceEntityId = 2;
        rejected(asset, "cycle");
    }

    @Test public void indirectCycleIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(1).parentSourceEntityId = 3;
        asset.entities.get(2).parentSourceEntityId = 2;
        asset.entities.get(2).gameObject = new GameObjectAsset.GameObjectData();
        rejected(asset, "cycle");
    }

    @Test public void unsupportedSchemaIsRejected() {
        try {
            loader.fromJson("{\"schemaVersion\":2,\"rootSourceEntityId\":1,\"entities\":[]}");
            Assert.fail("Expected rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("unsupported schemaVersion 2"));
        }
    }

    @Test public void externalObjectReferenceIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(1).customProperties = new PropertySet()
                .putObjectStableId("external", 500);
        rejected(asset, "unsupported external OBJECT reference 500");
    }

    @Test public void nonFiniteTransformIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(1).transform.x = Float.NaN;
        rejected(asset, "non-finite transform.x");
    }

    @Test public void nonUniformGameObjectParentScaleIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(1).transform.scaleY = 2f;
        rejected(asset, "positive uniform authored scale");
    }

    private void rejected(GameObjectAsset asset, String diagnostic) {
        try {
            loader.toJson(asset);
            Assert.fail("Expected rejection containing: " + diagnostic);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(diagnostic));
        }
    }

    private static GameObjectAsset validNestedAsset() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        asset.entities.add(entity(1, -1, true, 0, 0f));
        asset.entities.add(entity(2, 1, true, 7, 4f));
        asset.entities.add(entity(3, 2, false, 3, 2f));
        return asset;
    }

    private static GameObjectAsset.GameObjectEntityData entity(
            int id, int parent, boolean root, int localZ, float x) {
        GameObjectAsset.GameObjectEntityData entity =
                new GameObjectAsset.GameObjectEntityData();
        entity.sourceEntityId = id;
        entity.parentSourceEntityId = parent;
        entity.transform = new GameObjectAsset.TransformData();
        entity.transform.x = x;
        entity.transform.scaleX = 1f;
        entity.transform.scaleY = 1f;
        entity.entityIndex = new GameObjectAsset.EntityIndexData();
        entity.entityIndex.zIndex = localZ;
        if (root) entity.gameObject = new GameObjectAsset.GameObjectData();
        return entity;
    }
}
