package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.joints.DistanceJoint;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;

public class VisualScalePhysicsIntegrationTest {
    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void visualScalePreservesNativeGeometryAndSpatialFootprintForEveryBodyType() throws Exception {
        verifyVisualScale(false);
    }

    @Test
    public void visualScalePreservesExistingJointAndItsParametersForEveryBodyType() throws Exception {
        verifyVisualScale(true);
    }

    private void verifyVisualScale(boolean withJoint) throws Exception {
        for (int type = PhysicsBodyComponent.STATIC; type <= PhysicsBodyComponent.DYNAMIC; type++) {
            try (Harness h = new Harness()) {
                int sprite = h.entity(1, type);
                TransformComponent authored = h.world.getMapper(TransformComponent.class).get(sprite);
                authored.originX = 10f;
                authored.originY = 12f;
                h.world.getMapper(TextureRegionComponent.class).create(sprite);
                DimensionsComponent dimensions = h.world.getMapper(DimensionsComponent.class).create(sprite);
                dimensions.width = 80f;
                dimensions.height = 40f;
                h.world.getMapper(OrientedBoundsComponent.class).create(sprite);
                h.world.getMapper(AABBComponent.class).create(sprite);
                int jointEntity = -1;
                if (withJoint) {
                    int other = h.entity(2, PhysicsBodyComponent.DYNAMIC);
                    h.world.getMapper(TransformComponent.class).get(other).x = 300f;
                    jointEntity = h.world.create();
                    PhysicsJointComponent joint = h.world.getMapper(PhysicsJointComponent.class).create(jointEntity);
                    joint.aEid = sprite;
                    joint.bEid = other;
                    joint.anchorAx = 0.4f;
                    joint.anchorAy = -0.2f;
                    joint.anchorBx = -0.3f;
                    joint.anchorBy = 0.1f;
                    PhysicsDistanceJointComponent distance = h.world.getMapper(PhysicsDistanceJointComponent.class)
                            .create(jointEntity);
                    distance.lengthM = 2.4f;
                    distance.frequencyHz = 3f;
                    distance.dampingRatio = 0.25f;
                }
                h.activate();
                Body body = h.body(sprite);
                int generation = h.world.getMapper(PhysicsRuntimeBodyComponent.class).get(sprite).gen;
                Fixture polygonFixture = body.getFixtureList().get(0);
                Fixture circleFixture = body.getFixtureList().get(1);
                float[] vertices = vertices((PolygonShape) polygonFixture.getShape());
                CircleShape circle = (CircleShape) circleFixture.getShape();
                Vector2 circleOffset = circle.getPosition().cpy();
                float circleRadius = circle.getRadius();
                float mass = body.getMass();
                SpatialPhysicsFootprintComponent footprint = h.world.getMapper(SpatialPhysicsFootprintComponent.class)
                        .get(sprite);
                int footprintGeneration = footprint.physicsGeneration;
                float footprintRadius = footprint.radiusPx;
                float footprintX = footprint.localOffsetXPx;
                float footprintY = footprint.localOffsetYPx;
                DistanceJoint nativeJoint = withJoint ? (DistanceJoint) h.world
                        .getMapper(PhysicsRuntimeJointComponent.class).get(jointEntity).joint : null;
                float length = withJoint ? nativeJoint.getLength() : 0f;
                Vector2 anchorA = withJoint ? nativeJoint.getLocalAnchorA().cpy() : null;
                Vector2 anchorB = withJoint ? nativeJoint.getLocalAnchorB().cpy() : null;
                TransformFacade transform = h.engine.api().entities().ofEntityId(sprite).transform();
                for (float[] scale : new float[][]{{-1f, 1f}, {2f, 2f}, {-2f, 3f}}) {
                    if (scale[0] == -1f) transform.setScaleX(-1f);
                    else transform.setScale(scale[0], scale[1]);
                    body.setTransform(1f, 2f, 0f);
                    h.world.process();
                    Assert.assertEquals(100f, transform.x(), 0f);
                    Assert.assertEquals(200f, transform.y(), 0f);
                    Assert.assertEquals(scale[0], transform.scaleX(), 0f);
                    Assert.assertEquals(scale[1], transform.scaleY(), 0f);
                    OrientedBoundsComponent bounds = h.world.getMapper(OrientedBoundsComponent.class).get(sprite);
                    Assert.assertEquals(40f * Math.abs(scale[0]), bounds.hx, 0f);
                    Assert.assertEquals(20f * Math.abs(scale[1]), bounds.hy, 0f);
                    Assert.assertEquals(transform.x() + 30f * scale[0], bounds.cx, 0.0001f);
                    Assert.assertEquals(transform.y() + 8f * scale[1], bounds.cy, 0.0001f);
                    Assert.assertSame(body, h.body(sprite));
                    Assert.assertEquals(generation, h.world.getMapper(PhysicsRuntimeBodyComponent.class).get(sprite).gen);
                    Assert.assertEquals(2, body.getFixtureList().size);
                    Assert.assertSame(polygonFixture, body.getFixtureList().get(0));
                    Assert.assertSame(circleFixture, body.getFixtureList().get(1));
                    Assert.assertArrayEquals(vertices, vertices((PolygonShape) polygonFixture.getShape()), 0f);
                    Assert.assertEquals(circleOffset, circle.getPosition());
                    Assert.assertEquals(circleRadius, circle.getRadius(), 0f);
                    Assert.assertEquals(mass, body.getMass(), 0f);
                    Assert.assertEquals(footprintGeneration, footprint.physicsGeneration);
                    Assert.assertEquals(footprintRadius, footprint.radiusPx, 0f);
                    Assert.assertEquals(footprintX, footprint.localOffsetXPx, 0f);
                    Assert.assertEquals(footprintY, footprint.localOffsetYPx, 0f);
                    if (withJoint) {
                        Assert.assertSame(nativeJoint, h.world.getMapper(PhysicsRuntimeJointComponent.class)
                                .get(jointEntity).joint);
                        Assert.assertEquals(1, h.box2d.world.getJointCount());
                        Assert.assertEquals(length, nativeJoint.getLength(), 0f);
                        Assert.assertEquals(anchorA, nativeJoint.getLocalAnchorA());
                        Assert.assertEquals(anchorB, nativeJoint.getLocalAnchorB());
                        Assert.assertEquals(3f, nativeJoint.getFrequency(), 0f);
                        Assert.assertEquals(0.25f, nativeJoint.getDampingRatio(), 0f);
                    }
                }
            }
        }
    }

    @Test
    public void physicalGameObjectScalePropagatesToVisualChildWithoutChangingItsBody() throws Exception {
        try (Harness h = new Harness()) {
            int root = h.entity(1, PhysicsBodyComponent.DYNAMIC);
            h.world.getMapper(GameObjectComponent.class).create(root);
            int visual = h.world.create();
            h.world.getMapper(PixscapeIdentityComponent.class).create(visual).stableId = 2;
            h.world.getMapper(EntityIndexComponent.class).create(visual);
            h.world.getMapper(GameObjectMemberComponent.class).create(visual).parentStableId = 1;
            h.world.getMapper(TransformComponent.class).create(visual).x = 10f;
            h.activate();
            Body body = h.body(root);
            h.engine.api().entities().ofEntityId(root).transform().setScale(2f);
            h.engine.api().entities().ofEntityId(visual).transform().setScale(-1f, 3f);
            h.world.process();
            Assert.assertSame(body, h.body(root));
            Assert.assertEquals(2f, h.engine.api().entities().ofEntityId(root).transform().scaleX(), 0f);
            Assert.assertEquals(20f, h.world.getSystem(GameObjectHierarchySystem.class).worldTransforms().x[visual], 0f);
            Assert.assertEquals(-2f, h.world.getSystem(GameObjectHierarchySystem.class).worldTransforms().scaleX[visual], 0f);
            Assert.assertEquals(6f, h.world.getSystem(GameObjectHierarchySystem.class).worldTransforms().scaleY[visual], 0f);
        }
    }

    private static float[] vertices(PolygonShape shape) {
        float[] result = new float[shape.getVertexCount() * 2];
        Vector2 vertex = new Vector2();
        for (int i = 0; i < shape.getVertexCount(); i++) {
            shape.getVertex(i, vertex);
            result[i * 2] = vertex.x;
            result[i * 2 + 1] = vertex.y;
        }
        return result;
    }

    private static final class Harness implements AutoCloseable {
        final SceneMetaRuntime meta = new SceneMetaRuntime();
        final PhysicsShapeIdAllocator shapeIds = new PhysicsShapeIdAllocator(meta);
        final Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2());
        final PhysicsPoseAuthority authority = new PhysicsPoseAuthority();
        final Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        final World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(32), authority, new GameObjectHierarchySystem(32), sync,
                        new GameObjectPhysicsWritebackSystem(), new PhysicsSpatialFootprintSyncSystem(100f),
                        new UpdateWorldGeometrySystem()).build());
        final PixscapeEngine engine = new PixscapeEngine();

        Harness() throws Exception {
            Field field = PixscapeEngine.class.getDeclaredField("world");
            field.setAccessible(true);
            field.set(engine, world);
            meta.gravityX = meta.gravityY = 0f;
            meta.nextEntityStableId = 100;
            sync.setSceneMeta(meta);
            engine.getIdentityRegistry().bind(world, meta);
            engine.getTagRegistry().bind(world);
        }

        int entity(int stableId, int type) {
            int entity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
            world.getMapper(EntityIndexComponent.class).create(entity);
            world.getMapper(TransformComponent.class).create(entity);
            world.getMapper(PhysicsBodyComponent.class).create(entity).type = type;
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(entity);
            PhysicsShapeData polygon = new PhysicsShapeData();
            polygon.physicsShapeId = shapeIds.allocateNewPhysicsShapeId();
            polygon.geometry = new PhysicsGeometryData();
            polygon.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
            polygon.geometry.polygonVertices = new float[]{0f, 0f, 1f, 0f, 0.2f, 0.6f};
            polygon.geometry.polygonVertexCount = 3;
            polygon.geometry.offsetX = 0.3f;
            polygon.geometry.offsetY = -0.2f;
            shapes.shapes.add(polygon);
            PhysicsShapeData circle = new PhysicsShapeData();
            circle.physicsShapeId = shapeIds.allocateNewPhysicsShapeId();
            circle.spatialFootprint = true;
            circle.geometry = new PhysicsGeometryData();
            circle.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
            circle.geometry.offsetX = 0.35f;
            circle.geometry.offsetY = -0.15f;
            circle.geometry.radius = 0.2f;
            shapes.shapes.add(circle);
            PhysicsService.publishPreparedCandidate(shapes,
                    world.getMapper(PhysicsCompiledFixturesComponent.class).create(entity),
                    PhysicsService.prepareBodyCandidate(shapes.shapes));
            return entity;
        }

        void activate() {
            engine.getIdentityRegistry().rebuild();
            world.process();
            authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        }

        Body body(int entity) {
            return world.getMapper(PhysicsRuntimeBodyComponent.class).get(entity).body;
        }

        @Override public void close() {
            engine.getIdentityRegistry().bind(null, null);
            world.dispose();
            box2d.dispose();
        }
    }
}
