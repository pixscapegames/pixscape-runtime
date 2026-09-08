package games.pixscape.runtime.gameobject;

import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
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

        Assert.assertEquals(GameObjectAsset.SCHEMA_VERSION, restored.schemaVersion);
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
            loader.fromJson("{\"schemaVersion\":1,\"rootSourceEntityId\":1,\"entities\":[]}");
            Assert.fail("Expected rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("unsupported schemaVersion 1"));
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

    @Test
    public void v2PhysicsBodyAndManualShapesRoundTripWithoutSceneIds() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(0).physicsBody = new GameObjectAsset.PhysicsBodyData();
        asset.entities.get(0).physicsBody.type = PhysicsBodyComponent.STATIC;
        asset.entities.get(1).physicsBody = new GameObjectAsset.PhysicsBodyData();
        asset.entities.get(1).physicsBody.type = PhysicsBodyComponent.DYNAMIC;
        GameObjectAsset.GameObjectEntityData physical = asset.entities.get(2);
        physical.physicsBody = body();
        physical.physicsShapes.add(shape(11, box()));
        physical.physicsShapes.add(shape(17, circle()));
        physical.physicsShapes.add(shape(23, polygon()));

        GameObjectAsset restored = loader.fromJson(loader.toJson(asset));
        GameObjectAsset.GameObjectEntityData restoredPhysical = restored.entities.get(2);
        Assert.assertEquals(PhysicsBodyComponent.STATIC, restored.entities.get(0).physicsBody.type);
        Assert.assertEquals(PhysicsBodyComponent.DYNAMIC, restored.entities.get(1).physicsBody.type);
        Assert.assertEquals(PhysicsBodyComponent.KINEMATIC, restoredPhysical.physicsBody.type);
        Assert.assertTrue(restoredPhysical.physicsBody.fixedRotation);
        Assert.assertEquals(3, restoredPhysical.physicsShapes.size());
        GameObjectAsset.PhysicsShapeData restoredShape = restoredPhysical.physicsShapes.get(2);
        Assert.assertEquals(23, restoredShape.localShapeId);
        Assert.assertEquals(PhysicsGeometryData.SHAPE_POLYGON, restoredShape.geometry.shapeType);
        Assert.assertArrayEquals(new float[] {0f, 0f, 2f, 0f, 0f, 3f},
                restoredShape.geometry.polygonVertices, 0f);
        Assert.assertEquals((short) 0x0002, restoredShape.categoryBits);
        Assert.assertEquals((short) 0x00F0, restoredShape.maskBits);
        Assert.assertEquals((short) -3, restoredShape.groupIndex);
        Assert.assertFalse(restoredShape.enabled);
        Assert.assertFalse(loader.toJson(restored).contains("physicsShapeId"));
        Assert.assertFalse(loader.toJson(restored).contains("spatialBlockId"));
    }

    @Test
    public void v3SpatialHeightAndAutonomousFootprintRoundTrip() {
        GameObjectAsset asset = validNestedAsset();
        GameObjectAsset.GameObjectEntityData physical = asset.entities.get(2);
        physical.physicsBody = body();
        GameObjectAsset.PhysicsShapeData footprint = shape(1, circle());
        footprint.enabled = true;
        footprint.spatialFootprint = true;
        physical.physicsShapes.add(footprint);
        physical.spatialHeight = new GameObjectAsset.SpatialHeightData();
        physical.spatialHeight.altitude = 2.5f;
        physical.spatialHeight.height = 7.5f;

        GameObjectAsset restored = loader.fromJson(loader.toJson(asset));

        Assert.assertEquals(3, restored.schemaVersion);
        Assert.assertTrue(restored.entities.get(2).physicsShapes.get(0).spatialFootprint);
        Assert.assertEquals(2.5f, restored.entities.get(2).spatialHeight.altitude, 0f);
        Assert.assertEquals(7.5f, restored.entities.get(2).spatialHeight.height, 0f);
    }

    @Test
    public void negativeSpatialHeightIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(2).spatialHeight = new GameObjectAsset.SpatialHeightData();
        asset.entities.get(2).spatialHeight.height = -1f;
        rejected(asset, "negative Spatial height");
    }

    @Test public void physicsShapesRequireBody() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(2).physicsShapes.add(shape(1, box()));
        rejected(asset, "Physics Shapes without a Physics Body");
    }

    @Test public void malformedPhysicsIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(2).physicsBody = body();
        GameObjectAsset.PhysicsShapeData invalid = shape(1, polygon());
        invalid.geometry.polygonVertices[4] = Float.NaN;
        asset.entities.get(2).physicsShapes.add(invalid);
        rejected(asset, "must be finite");
    }

    @Test public void malformedPhysicsBodyIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(2).physicsBody = body();
        asset.entities.get(2).physicsBody.gravityScale = Float.NaN;
        rejected(asset, "non-finite physicsBody.gravityScale");
    }

    @Test public void physicalDescendantCannotHaveScaledGameObjectAncestor() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(1).transform.scaleX = 2f;
        asset.entities.get(1).transform.scaleY = 2f;
        asset.entities.get(2).physicsBody = body();
        rejected(asset, "Physics ancestor scale to be (1,1)");
    }

    @Test public void v3InternalJointsRoundTripWithAssetLocalEndpoints() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(0).physicsBody = body();
        asset.entities.get(1).physicsBody = body();
        GameObjectAsset.GameObjectJointData joint = distanceJoint(7, 1, 2);
        asset.joints.add(joint);

        GameObjectAsset restored = loader.fromJson(loader.toJson(asset));

        Assert.assertEquals(3, restored.schemaVersion);
        Assert.assertEquals(1, restored.joints.size());
        GameObjectAsset.GameObjectJointData restoredJoint = restored.joints.get(0);
        Assert.assertEquals(7, restoredJoint.jointLocalId);
        Assert.assertEquals(PhysicsJointComponent.TYPE_DISTANCE, restoredJoint.type);
        Assert.assertEquals(1, restoredJoint.bodyALocalEntityId);
        Assert.assertEquals(2, restoredJoint.bodyBLocalEntityId);
        Assert.assertEquals(3.5f, restoredJoint.distance.lengthM, 0f);
    }

    @Test public void v3JointEndpointOutsideAssetIsRejected() {
        GameObjectAsset asset = validNestedAsset();
        asset.entities.get(0).physicsBody = body();
        asset.entities.get(1).physicsBody = body();
        asset.joints.add(distanceJoint(1, 1, 99));
        rejected(asset, "requires two asset-local entity endpoints");
    }

    @Test public void v2AssetsAreRejectedRatherThanSilentlyMigrated() {
        GameObjectAsset asset = validNestedAsset();
        asset.schemaVersion = 2;
        rejected(asset, "schemaVersion must be 3");
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

    private static GameObjectAsset.PhysicsBodyData body() {
        GameObjectAsset.PhysicsBodyData body = new GameObjectAsset.PhysicsBodyData();
        body.type = PhysicsBodyComponent.KINEMATIC;
        body.fixedRotation = true;
        body.bullet = true;
        body.allowSleep = false;
        body.awake = false;
        body.gravityScale = .75f;
        body.linearDamping = .5f;
        body.angularDamping = .25f;
        return body;
    }

    private static GameObjectAsset.GameObjectJointData distanceJoint(
            int localId, int bodyA, int bodyB) {
        GameObjectAsset.GameObjectJointData joint = new GameObjectAsset.GameObjectJointData();
        joint.jointLocalId = localId;
        joint.type = PhysicsJointComponent.TYPE_DISTANCE;
        joint.bodyALocalEntityId = bodyA;
        joint.bodyBLocalEntityId = bodyB;
        joint.anchorAx = .25f;
        joint.anchorAy = .5f;
        joint.anchorBx = .75f;
        joint.anchorBy = 1f;
        joint.distance = new GameObjectAsset.DistanceJointData();
        joint.distance.lengthM = 3.5f;
        joint.distance.frequencyHz = 2f;
        joint.distance.dampingRatio = .4f;
        return joint;
    }

    private static GameObjectAsset.PhysicsShapeData shape(
            int localShapeId, PhysicsGeometryData geometry) {
        GameObjectAsset.PhysicsShapeData shape = new GameObjectAsset.PhysicsShapeData();
        shape.localShapeId = localShapeId;
        shape.geometry = geometry;
        shape.density = 2f;
        shape.friction = .6f;
        shape.restitution = .4f;
        shape.sensor = true;
        shape.categoryBits = 0x0002;
        shape.maskBits = 0x00F0;
        shape.groupIndex = -3;
        shape.enabled = false;
        return shape;
    }

    private static PhysicsGeometryData box() {
        PhysicsGeometryData geometry = new PhysicsGeometryData();
        geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
        geometry.halfWidth = 2f;
        geometry.halfHeight = 3f;
        return geometry;
    }

    private static PhysicsGeometryData circle() {
        PhysicsGeometryData geometry = new PhysicsGeometryData();
        geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        geometry.radius = 4f;
        return geometry;
    }

    private static PhysicsGeometryData polygon() {
        PhysicsGeometryData geometry = new PhysicsGeometryData();
        geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        geometry.polygonVertexCount = 3;
        geometry.polygonVertices = new float[] {0f, 0f, 2f, 0f, 0f, 3f};
        return geometry;
    }
}
