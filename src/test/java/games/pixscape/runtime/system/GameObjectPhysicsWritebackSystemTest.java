package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Runtime P1 coverage for authored hierarchy push and native parent-first writeback. */
public class GameObjectPhysicsWritebackSystemTest {
    private static final float EPSILON = 0.0001f;

    private World world;
    private IdentityRegistry identities;
    private Box2dWorldService box2d;
    private Box2dSyncSystem sync;
    private PhysicsPoseAuthority authority;

    @Before
    public void setUp() {
        GdxNativesLoader.load();
        box2d = new Box2dWorldService(100f, new Vector2());
        sync = new Box2dSyncSystem(box2d);
        authority = new PhysicsPoseAuthority();
        world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(32), authority,
                        new GameObjectHierarchySystem(32), sync,
                        new GameObjectPhysicsWritebackSystem())
                .build());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.gravityX = 0f;
        meta.gravityY = 0f;
        meta.nextEntityStableId = 1000;
        sync.setSceneMeta(meta);
        identities = new IdentityRegistry();
        identities.bind(world, meta);
    }

    @After
    public void tearDown() {
        identities.bind(null, null);
        world.dispose();
        box2d.dispose();
    }

    @Test
    public void authoringPushUsesResolvedParentWorldPoseForEveryBodyType() {
        int root = entity(1, true, -1, false);
        TransformComponent rootTransform = transform(root);
        rootTransform.x = 100f;
        rootTransform.y = 20f;
        rootTransform.rotationRad = (float) (Math.PI * 0.5);
        rootTransform.originX = 10f;

        int child = entity(2, false, 1, true);
        TransformComponent childTransform = transform(child);
        childTransform.x = 30f;
        childTransform.y = 0f;
        world.getMapper(PhysicsBodyComponent.class).get(child).type = PhysicsBodyComponent.STATIC;
        int kinematic = entity(3, false, 1, true);
        transform(kinematic).x = 30f;
        world.getMapper(PhysicsBodyComponent.class).get(kinematic).type = PhysicsBodyComponent.KINEMATIC;
        int dynamic = entity(4, false, 1, true);
        transform(dynamic).x = 30f;

        identities.rebuild();
        world.process();

        Body nativeBody = nativeBody(child);
        Assert.assertEquals(110f, px(nativeBody.getPosition().x), EPSILON);
        Assert.assertEquals(40f, px(nativeBody.getPosition().y), EPSILON);
        Assert.assertEquals((float) (Math.PI * 0.5), nativeBody.getAngle(), EPSILON);
        Assert.assertEquals(110f, px(nativeBody(kinematic).getPosition().x), EPSILON);
        Assert.assertEquals(110f, px(nativeBody(dynamic).getPosition().x), EPSILON);

        rootTransform.x = 150f;
        rootTransform.rotationRad = (float) Math.PI;
        world.process();

        Assert.assertEquals(140f, px(nativeBody.getPosition().x), EPSILON);
        Assert.assertEquals(20f, px(nativeBody.getPosition().y), EPSILON);
        Assert.assertEquals((float) Math.PI, nativeBody.getAngle(), EPSILON);
        Assert.assertEquals(140f, px(nativeBody(kinematic).getPosition().x), EPSILON);
        Assert.assertEquals(140f, px(nativeBody(dynamic).getPosition().x), EPSILON);
        Assert.assertEquals(30f, childTransform.x, 0f);
        Assert.assertEquals(0f, childTransform.y, 0f);
    }

    @Test
    public void physicalParentDoesNotCarryPhysicalChildAndChildLocalCompensates() {
        int root = entity(1, true, -1, true);
        int child = entity(2, true, 1, true);
        transform(child).x = 10f;
        identities.rebuild();
        world.process();

        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        Body rootBody = nativeBody(root);
        Body childBody = nativeBody(child);
        rootBody.setTransform(1f, 0f, 0f);
        childBody.setTransform(0.5f, 0f, 0f);
        world.process();

        Assert.assertEquals(100f, transform(root).x, EPSILON);
        Assert.assertEquals(-50f, transform(child).x, EPSILON);
        Assert.assertEquals(50f, px(childBody.getPosition().x), EPSILON);
        Assert.assertEquals(50f, worldState().x[child], EPSILON);

        rootBody.setTransform(2f, 0f, 0f);
        world.process();

        Assert.assertEquals(200f, transform(root).x, EPSILON);
        Assert.assertEquals(-150f, transform(child).x, EPSILON);
        Assert.assertEquals(50f, px(childBody.getPosition().x), EPSILON);
        Assert.assertEquals(50f, worldState().x[child], EPSILON);
    }

    @Test
    public void nestedGameObjectBodyUsesPivotAwareParentFirstWriteback() {
        int root = entity(1, true, -1, true);
        int middle = entity(2, true, 1, false);
        TransformComponent middleTransform = transform(middle);
        middleTransform.x = 15f;
        middleTransform.originX = 5f;
        middleTransform.originY = 3f;
        middleTransform.rotationRad = 0.3f;
        int child = entity(3, true, 2, true);
        TransformComponent childTransform = transform(child);
        childTransform.originX = 4f;
        childTransform.originY = 2f;
        childTransform.scaleX = childTransform.scaleY = 1f;
        identities.rebuild();
        world.process();

        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        nativeBody(root).setTransform(0.8f, -0.2f, 0.4f);
        nativeBody(child).setTransform(2.5f, 0.7f, -0.25f);
        world.process();

        Assert.assertEquals(250f, worldState().x[child], EPSILON);
        Assert.assertEquals(70f, worldState().y[child], EPSILON);
        Assert.assertEquals(-0.25f, worldState().rotationRad[child], 0.001f);
        Assert.assertEquals(4f, childTransform.originX, 0f);
        Assert.assertEquals(2f, childTransform.originY, 0f);
    }

    @Test
    public void pausedRuntimeKeepsNativeBodyPoseAuthoritative() {
        int root = entity(1, true, -1, true);
        identities.rebuild();
        world.process();

        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        sync.setStepEnabled(false);
        nativeBody(root).setTransform(1.25f, -0.5f, 0.75f);
        world.process();

        Assert.assertEquals(125f, transform(root).x, EPSILON);
        Assert.assertEquals(-50f, transform(root).y, EPSILON);
        Assert.assertEquals(0.75f, transform(root).rotationRad, EPSILON);
        Assert.assertEquals(125f, worldState().x[root], EPSILON);
    }

    @Test
    public void runtimeNativePoseWinsForStaticAndKinematicBodies() {
        int staticBody = entity(1, true, -1, true);
        world.getMapper(PhysicsBodyComponent.class).get(staticBody).type = PhysicsBodyComponent.STATIC;
        int kinematicBody = entity(2, true, -1, true);
        world.getMapper(PhysicsBodyComponent.class).get(kinematicBody).type = PhysicsBodyComponent.KINEMATIC;
        identities.rebuild();
        world.process();

        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        nativeBody(staticBody).setTransform(0.4f, 0.2f, 0.1f);
        nativeBody(kinematicBody).setTransform(-0.3f, 0.7f, -0.2f);
        world.process();

        Assert.assertEquals(40f, transform(staticBody).x, EPSILON);
        Assert.assertEquals(20f, transform(staticBody).y, EPSILON);
        Assert.assertEquals(0.1f, transform(staticBody).rotationRad, EPSILON);
        Assert.assertEquals(-30f, transform(kinematicBody).x, EPSILON);
        Assert.assertEquals(70f, transform(kinematicBody).y, EPSILON);
        Assert.assertEquals(-0.2f, transform(kinematicBody).rotationRad, EPSILON);
    }

    @Test
    public void availabilityResolvesHierarchyBeforeAuthoringBodyMaterialization() {
        sync.setEnabled(false);
        int root = entity(1, true, -1, false);
        int child = entity(2, false, 1, true);
        transform(root).x = 100f;
        transform(child).x = 20f;
        identities.rebuild();
        world.process();

        transform(root).x = 300f;
        sync.prepareRuntimeAvailability();

        Assert.assertEquals(320f, px(nativeBody(child).getPosition().x), EPSILON);
        Assert.assertEquals(320f, worldState().x[child], EPSILON);
    }

    @Test
    public void runtimeBodyRebuildPreservesTheCurrentNativePose() {
        int root = entity(1, true, -1, true);
        identities.rebuild();
        world.process();

        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        Body previous = nativeBody(root);
        previous.setTransform(1.4f, -0.6f, 0.35f);
        world.getSystem(DirtyTrackerSystem.class).physics(root, PhysicsDirtyBits.ALL);
        world.process();

        Body rebuilt = nativeBody(root);
        Assert.assertNotSame(previous, rebuilt);
        Assert.assertEquals(140f, px(rebuilt.getPosition().x), EPSILON);
        Assert.assertEquals(-60f, px(rebuilt.getPosition().y), EPSILON);
        Assert.assertEquals(0.35f, rebuilt.getAngle(), EPSILON);
    }

    @Test
    public void hierarchyMemberBodiesMaterializeJointAndRefreshDistanceFromNativeWorldPoses() {
        int firstRoot = entity(1, true, -1, false);
        transform(firstRoot).x = 100f;
        int firstBody = entity(2, false, 1, true);

        int secondRoot = entity(3, true, -1, false);
        transform(secondRoot).x = 500f;
        int secondBody = entity(4, false, 3, true);

        int jointEntity = world.create();
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(jointEntity);
        joint.type = PhysicsJointComponent.TYPE_DISTANCE;
        joint.aEid = firstBody;
        joint.bEid = secondBody;
        world.getMapper(PhysicsDistanceJointComponent.class).create(jointEntity).lengthM = 0.1f;

        identities.rebuild();
        world.process();

        Assert.assertNotNull(world.getMapper(PhysicsRuntimeJointComponent.class).get(jointEntity).joint);

        transform(secondRoot).x = 600f;
        world.process();

        Assert.assertEquals(5f,
                world.getMapper(PhysicsDistanceJointComponent.class).get(jointEntity).lengthM,
                EPSILON);
    }

    private int entity(int stableId, boolean gameObject, int parentStableId, boolean physics) {
        int entityId = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entityId).stableId = stableId;
        world.getMapper(EntityIndexComponent.class).create(entityId);
        world.getMapper(TransformComponent.class).create(entityId);
        if (gameObject) world.getMapper(GameObjectComponent.class).create(entityId);
        if (parentStableId > 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entityId).parentStableId = parentStableId;
        }
        if (physics) {
            world.getMapper(PhysicsBodyComponent.class).create(entityId).type = PhysicsBodyComponent.DYNAMIC;
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(entityId);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = stableId;
            shape.geometry = new PhysicsGeometryData();
            shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
            shapes.shapes.add(shape);
            PhysicsService.publishPreparedCandidate(
                    shapes,
                    world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId),
                    PhysicsService.prepareBodyCandidate(shapes.shapes));
        }
        return entityId;
    }

    private TransformComponent transform(int entityId) {
        return world.getMapper(TransformComponent.class).get(entityId);
    }

    private Body nativeBody(int entityId) {
        return world.getMapper(PhysicsRuntimeBodyComponent.class).get(entityId).body;
    }

    private WorldTransformState worldState() {
        return world.getSystem(GameObjectHierarchySystem.class).worldTransforms();
    }

    private float px(float meters) {
        return box2d.mToPx(meters);
    }
}
