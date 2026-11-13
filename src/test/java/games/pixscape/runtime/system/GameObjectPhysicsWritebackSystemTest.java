package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.hierarchy.GameObjectPlacement;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.physics.box2d.Joint;
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

/**
 * Runtime P1 coverage for authored hierarchy push and native parent-first writeback.
 */
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
    public void pulleyReplacementFailureKeepsOriginalResourcesAndReusablePlacementCanRetry() {
        int root = entity(1, true, -1, true);
        int child = entity(2, false, 1, true);
        transform(child).x = 200;
        int[] joints = {distanceJoint(root, child, 2), distanceJoint(root, child, 2)};
        for (int joint : joints) {
            world.getMapper(PhysicsDistanceJointComponent.class).remove(joint);
            world.getMapper(PhysicsJointComponent.class).get(joint).type = PhysicsJointComponent.TYPE_PULLEY;
            var pulley = world.getMapper(PhysicsPulleyJointComponent.class).create(joint);
            pulley.groundAy = pulley.groundBy = 2;
            pulley.groundBx = 2;
            pulley.lengthAM = pulley.lengthBM = 2;
            pulley.ratio = 1;
        }
        identities.rebuild();
        world.process();
        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        Joint first = world.getMapper(PhysicsRuntimeJointComponent.class).get(joints[0]).joint;
        Joint second = world.getMapper(PhysicsRuntimeJointComponent.class).get(joints[1]).joint;
        float rootX = nativeBody(root).getPosition().x, childX = nativeBody(child).getPosition().x;
        float localX = transform(child).x;
        var hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        hierarchy.reservePlacement(2, 2);
        SpawnPhysicsProbe probe = new SpawnPhysicsProbe(sync);
        probe.failJoint = 2;
        Assert.assertThrows(IllegalStateException.class,
                () -> hierarchy.placeComposition(IntArray.with(root), 0, 0, 100, 0, 0, false));
        Assert.assertEquals(2, box2d.world.getJointCount());
        Assert.assertEquals(rootX, nativeBody(root).getPosition().x, 0f);
        Assert.assertEquals(childX, nativeBody(child).getPosition().x, 0f);
        Assert.assertEquals(localX, transform(child).x, 0f);
        Assert.assertSame(first, world.getMapper(PhysicsRuntimeJointComponent.class).get(joints[0]).joint);
        Assert.assertSame(second, world.getMapper(PhysicsRuntimeJointComponent.class).get(joints[1]).joint);
        for (int joint : joints) {
            Assert.assertEquals(0, world.getMapper(PhysicsPulleyJointComponent.class).get(joint).groundAx, 0f);
        }
        hierarchy.placeComposition(IntArray.with(root), 0, 0, 100, 0, 0, false);
        Assert.assertEquals(rootX + 1, nativeBody(root).getPosition().x, EPSILON);
        Assert.assertEquals(childX + 1, nativeBody(child).getPosition().x, EPSILON);
        Assert.assertEquals(2, box2d.world.getJointCount());
    }

    @Test
    public void reusablePlacementUsesCurrentTopologyAndRecoversAfterRefusal() {
        int root = entity(1, true, -1, false);
        int first = entity(2, false, 1, true);
        int outside = entity(3, false, -1, true);
        transform(outside).x = 300f;
        identities.rebuild();
        world.process();
        var hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        hierarchy.reservePlacement(1, 0); // Deliberately exceed these capacities below.
        var roots = IntArray.with(root);
        hierarchy.placeComposition(roots, 0, 0, 100, 0, 0, false);
        float firstX = world.getMapper(PhysicsRuntimeBodyComponent.class).get(first).body.getPosition().x;
        int hitch = world.create();
        var base = world.getMapper(PhysicsJointComponent.class).create(hitch);
        base.aEid = first;
        base.bEid = outside;
        world.getMapper(PhysicsDistanceJointComponent.class).create(hitch).lengthM = 2f;
        world.process();
        Assert.assertThrows(IllegalArgumentException.class,
                () -> hierarchy.placeComposition(roots, 0, 0, 50, 0, 0, false));
        Assert.assertEquals(firstX, world.getMapper(PhysicsRuntimeBodyComponent.class).get(first).body.getPosition().x, EPSILON);
        // Explicitly change the instance after its first placement, then publish through the normal boundary.
        world.getMapper(GameObjectMemberComponent.class).create(outside).parentStableId = 1;
        world.process();
        float outsideX = world.getMapper(PhysicsRuntimeBodyComponent.class).get(outside).body.getPosition().x;
        firstX = world.getMapper(PhysicsRuntimeBodyComponent.class).get(first).body.getPosition().x;
        hierarchy.placeComposition(roots, 0, 0, 50, 0, 0, false);
        Assert.assertEquals(firstX + .5f, world.getMapper(PhysicsRuntimeBodyComponent.class).get(first).body.getPosition().x, EPSILON);
        Assert.assertEquals(outsideX + .5f, world.getMapper(PhysicsRuntimeBodyComponent.class).get(outside).body.getPosition().x, EPSILON);
        world.delete(hitch);
        world.getMapper(GameObjectMemberComponent.class).remove(outside);
        world.process();
        outsideX = nativeBody(outside).getPosition().x;
        hierarchy.placeComposition(roots, 0, 0, 50, 0, 0, false);
        Assert.assertEquals(outsideX, nativeBody(outside).getPosition().x, EPSILON);
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
        TransformComponent rootTransform = transform(root);
        rootTransform.originX = 7f;
        rootTransform.originY = 5f;
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
        childTransform.scaleX = childTransform.scaleY = 0.75f;
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
        Assert.assertEquals(0.75f, childTransform.scaleX, 0f);
        Assert.assertEquals(0.75f, childTransform.scaleY, 0f);

        for (int i = 0; i < 3; i++) world.process();

        Assert.assertEquals(250f, worldState().x[child], EPSILON);
        Assert.assertEquals(70f, worldState().y[child], EPSILON);
        Assert.assertEquals(-0.25f, worldState().rotationRad[child], 0.001f);
        Assert.assertEquals(4f, childTransform.originX, 0f);
        Assert.assertEquals(2f, childTransform.originY, 0f);
        Assert.assertEquals(0.75f, childTransform.scaleX, 0f);
        Assert.assertEquals(0.75f, childTransform.scaleY, 0f);
    }

    @Test
    public void ordinaryMemberWritebackPreservesGeometryOnlyOriginAndVisualScaleWithoutDrift() {
        int root = entity(1, true, -1, false);
        TransformComponent rootTransform = transform(root);
        rootTransform.x = 100f;
        rootTransform.y = 20f;
        rootTransform.rotationRad = 0.6f;
        rootTransform.originX = 11f;
        rootTransform.originY = 7f;

        int child = entity(2, false, 1, true);
        TransformComponent childTransform = transform(child);
        childTransform.x = 13f;
        childTransform.y = -9f;
        childTransform.originX = 6f;
        childTransform.originY = 4f;
        childTransform.scaleX = -1f;
        childTransform.scaleY = 2f;
        identities.rebuild();
        world.process();

        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        Body body = nativeBody(child);
        body.setTransform(2.5f, -0.7f, -0.25f);
        world.process();

        float worldX = 250f;
        float worldY = -70f;
        float cos = MathUtils.cos(rootTransform.rotationRad);
        float sin = MathUtils.sin(rootTransform.rotationRad);
        float parentFrameX = rootTransform.x + rootTransform.originX
                - cos * rootTransform.originX + sin * rootTransform.originY;
        float parentFrameY = rootTransform.y + rootTransform.originY
                - sin * rootTransform.originX - cos * rootTransform.originY;
        float dx = worldX - parentFrameX;
        float dy = worldY - parentFrameY;
        float expectedLocalX = cos * dx + sin * dy;
        float expectedLocalY = -sin * dx + cos * dy;

        Assert.assertEquals(worldX, worldState().x[child], EPSILON);
        Assert.assertEquals(worldY, worldState().y[child], EPSILON);
        Assert.assertEquals(-0.25f, worldState().rotationRad[child], EPSILON);
        Assert.assertEquals(expectedLocalX, childTransform.x, EPSILON);
        Assert.assertEquals(expectedLocalY, childTransform.y, EPSILON);
        Assert.assertEquals(-0.25f - rootTransform.rotationRad, childTransform.rotationRad, EPSILON);
        Assert.assertEquals(6f, childTransform.originX, 0f);
        Assert.assertEquals(4f, childTransform.originY, 0f);
        Assert.assertEquals(-1f, childTransform.scaleX, 0f);
        Assert.assertEquals(2f, childTransform.scaleY, 0f);

        for (int i = 0; i < 3; i++) world.process();

        Assert.assertEquals(worldX, worldState().x[child], EPSILON);
        Assert.assertEquals(worldY, worldState().y[child], EPSILON);
        Assert.assertEquals(expectedLocalX, childTransform.x, EPSILON);
        Assert.assertEquals(expectedLocalY, childTransform.y, EPSILON);
        Assert.assertEquals(-1f, childTransform.scaleX, 0f);
        Assert.assertEquals(2f, childTransform.scaleY, 0f);
    }

    @Test
    public void ordinaryMemberZeroVisualScaleDoesNotAffectPhysicsWriteback() {
        int root = entity(1, true, -1, false);
        int child = entity(2, false, 1, true);
        TransformComponent childTransform = transform(child);
        childTransform.originX = 3f;
        childTransform.originY = 2f;
        childTransform.scaleX = 0f;
        childTransform.scaleY = 0f;
        identities.rebuild();
        world.process();

        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        nativeBody(child).setTransform(1.1f, -0.4f, 0.9f);
        world.process();

        Assert.assertEquals(110f, worldState().x[child], EPSILON);
        Assert.assertEquals(-40f, worldState().y[child], EPSILON);
        Assert.assertEquals(0.9f, worldState().rotationRad[child], EPSILON);
        Assert.assertEquals(0f, childTransform.scaleX, 0f);
        Assert.assertEquals(0f, childTransform.scaleY, 0f);
        Assert.assertEquals(3f, childTransform.originX, 0f);
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

    @Test
    public void movingAuthoredParentPreservesNestedNativeBoundariesAndTheirVisualChildren() {
        int root = entity(1, true, -1, false);
        transform(root).originX = 11f;
        transform(root).originY = -4f;
        int body = entity(2, true, 1, true);
        transform(body).originX = 7f;
        int visual = entity(3, false, 2, false);
        transform(visual).x = 25f;
        int nested = entity(4, true, 2, true);
        int nestedVisual = entity(5, false, 4, false);
        transform(nestedVisual).y = 12f;
        identities.rebuild();
        world.process();
        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        nativeBody(body).setTransform(2f, -1f, 0.4f);
        nativeBody(nested).setTransform(-1f, 3f, -0.7f);
        world.process();
        float visualX = worldState().x[visual];
        float visualY = worldState().y[visual];
        float nestedX = worldState().x[nestedVisual];
        float nestedY = worldState().y[nestedVisual];

        for (int i = 0; i < 30; i++) {
            transform(root).x += 13f;
            transform(root).rotationRad += 0.13f;
            world.process();
            Assert.assertEquals(200f, worldState().x[body], 0.001f);
            Assert.assertEquals(-100f, worldState().y[body], 0.001f);
            Assert.assertEquals(-100f, worldState().x[nested], 0.001f);
            Assert.assertEquals(300f, worldState().y[nested], 0.001f);
            Assert.assertEquals(visualX, worldState().x[visual], 0.001f);
            Assert.assertEquals(visualY, worldState().y[visual], 0.001f);
            Assert.assertEquals(nestedX, worldState().x[nestedVisual], 0.001f);
            Assert.assertEquals(nestedY, worldState().y[nestedVisual], 0.001f);
        }
    }

    @Test
    public void unrealizedBodyComposesUntilItsFixturesAreMaterialized() {
        int root = entity(1, true, -1, false);
        int child = entity(2, false, 1, true);
        PhysicsCompiledFixturesComponent compiled = world.getMapper(PhysicsCompiledFixturesComponent.class).get(child);
        compiled.fixtures.clear();
        transform(child).x = 20f;
        identities.rebuild();
        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        world.process();
        transform(root).x = 100f;
        world.process();
        Assert.assertEquals(120f, worldState().x[child], EPSILON);
        PhysicsRuntimeBodyComponent unrealized = world.getMapper(PhysicsRuntimeBodyComponent.class).getSafe(child, null);
        Assert.assertTrue(unrealized == null || unrealized.body == null);

        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(child);
        PhysicsService.publishPreparedCandidate(shapes, compiled, PhysicsService.prepareBodyCandidate(shapes.shapes));
        world.getSystem(DirtyTrackerSystem.class).physics(child, PhysicsDirtyBits.ALL);
        world.process();
        Assert.assertEquals(120f, px(nativeBody(child).getPosition().x), EPSILON);
        transform(root).x = 300f;
        world.process();
        Assert.assertEquals(120f, worldState().x[child], EPSILON);
        Assert.assertEquals(-180f, transform(child).x, EPSILON);
    }

    @Test
    public void articulatedBodiesAndIndependentTrailerKeepTheirMembershipWhileStepping() {
        int tractor = entity(1, true, -1, true);
        int arm = entity(2, true, 1, true);
        transform(arm).x = 100f;
        int trailer = entity(3, true, -1, true);
        transform(trailer).x = 200f;
        int articulation = distanceJoint(tractor, arm, 1f);
        int hitch = distanceJoint(arm, trailer, 1f);
        identities.rebuild();
        world.process();
        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        sync.setStepEnabled(true);
        world.setDelta(1f / 60f);
        nativeBody(trailer).applyLinearImpulse(0f, 0.05f, 2f, 0f, true);
        for (int i = 0; i < 120; i++) {
            world.process();
        }
        Assert.assertFalse(world.getMapper(GameObjectMemberComponent.class).has(trailer));
        Assert.assertEquals(1, world.getMapper(GameObjectMemberComponent.class).get(arm).parentStableId);
        Assert.assertEquals(1f, nativeBody(tractor).getPosition().dst(nativeBody(arm).getPosition()), 0.02f);
        Assert.assertEquals(1f, nativeBody(arm).getPosition().dst(nativeBody(trailer).getPosition()), 0.02f);
        Assert.assertNotNull(world.getMapper(PhysicsRuntimeJointComponent.class).get(articulation).joint);
        Assert.assertNotNull(world.getMapper(PhysicsRuntimeJointComponent.class).get(hitch).joint);
    }

    private int distanceJoint(int a, int b, float length) {
        int entity = world.create();
        identities.ensureStableId(entity);
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(entity);
        joint.aEid = a;
        joint.bEid = b;
        world.getMapper(PhysicsDistanceJointComponent.class).create(entity).lengthM = length;
        return entity;
    }

    @Test
    public void placementNormalizesRootsPreservesVelocitiesAndRejectsExternalHitchAtomically() {
        int root = entity(1, true, -1, true);
        int child = entity(2, true, 1, true);
        transform(child).x = 100f;
        transform(root).originX = 7f;
        int trailer = entity(3, true, -1, true);
        transform(trailer).x = 300f;
        int internal = distanceJoint(root, child, 1f);
        int external = distanceJoint(child, trailer, 2f);
        identities.rebuild();
        world.process();
        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        nativeBody(root).setLinearVelocity(2f, 3f);
        nativeBody(root).setAngularVelocity(0.7f);
        Joint original = world.getMapper(PhysicsRuntimeJointComponent.class).get(internal).joint;
        Assert.assertThrows(IllegalArgumentException.class, () -> GameObjectPlacement.prepare(world,
                new IntArray(new int[]{root}), 0f, 0f, 100f, 20f, 0f, false));
        Assert.assertEquals(0f, nativeBody(root).getPosition().x, EPSILON);
        Assert.assertEquals(0f, transform(root).x, EPSILON);
        GameObjectPlacement placement = GameObjectPlacement.prepare(world,
                new IntArray(new int[]{root, child, root, trailer}), 0f, 0f, 100f, 20f,
                (float) (Math.PI / 2), false);
        placement.applyAfter();
        Assert.assertEquals(1f, nativeBody(root).getPosition().x, EPSILON);
        Assert.assertEquals(0.2f, nativeBody(root).getPosition().y, EPSILON);
        Assert.assertEquals(1f, nativeBody(child).getPosition().x, EPSILON);
        Assert.assertEquals(1.2f, nativeBody(child).getPosition().y, EPSILON);
        Assert.assertEquals(-3f, nativeBody(root).getLinearVelocity().x, EPSILON);
        Assert.assertEquals(2f, nativeBody(root).getLinearVelocity().y, EPSILON);
        Assert.assertEquals(0.7f, nativeBody(root).getAngularVelocity(), EPSILON);
        Assert.assertSame(original, world.getMapper(PhysicsRuntimeJointComponent.class).get(internal).joint);
        world.process();
        Assert.assertEquals(1f, world.getMapper(PhysicsDistanceJointComponent.class).get(internal).lengthM, EPSILON);
        Assert.assertEquals(2f, world.getMapper(PhysicsDistanceJointComponent.class).get(external).lengthM, EPSILON);
        placement.applyBefore();
        Assert.assertEquals(0f, nativeBody(root).getPosition().x, EPSILON);
        Assert.assertEquals(2f, nativeBody(root).getLinearVelocity().x, EPSILON);
        GameObjectPlacement.prepare(world, new IntArray(new int[]{root, trailer}),
                0f, 0f, 0f, 0f, 0f, true).applyAfter();
        Assert.assertEquals(0f, nativeBody(root).getLinearVelocity().len2(), 0f);
        Assert.assertEquals(0f, nativeBody(root).getAngularVelocity(), 0f);
    }

    @Test
    public void placementTransformsPulleyAnchorsAndPreservesItsAuthoredLengths() {
        int root = entity(1, true, -1, true);
        int child = entity(2, false, 1, true);
        transform(child).x = 200f;
        int joint = distanceJoint(root, child, 2f);
        world.getMapper(PhysicsDistanceJointComponent.class).remove(joint);
        world.getMapper(PhysicsJointComponent.class).get(joint).type = PhysicsJointComponent.TYPE_PULLEY;
        PhysicsPulleyJointComponent pulley = world.getMapper(PhysicsPulleyJointComponent.class).create(joint);
        pulley.groundAx = 0f;
        pulley.groundAy = 2f;
        pulley.groundBx = 2f;
        pulley.groundBy = 2f;
        pulley.lengthAM = 2f;
        pulley.lengthBM = 2f;
        pulley.ratio = 1f;
        identities.rebuild();
        world.process();
        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        GameObjectPlacement placement = GameObjectPlacement.prepare(world, new IntArray(new int[]{root}),
                0f, 0f, 100f, 0f, (float) (Math.PI / 2), false);
        placement.applyAfter();
        Assert.assertEquals(-1f, pulley.groundAx, EPSILON);
        Assert.assertEquals(0f, pulley.groundAy, EPSILON);
        Assert.assertEquals(-1f, pulley.groundBx, EPSILON);
        Assert.assertEquals(2f, pulley.groundBy, EPSILON);
        com.badlogic.gdx.physics.box2d.joints.PulleyJoint nativeJoint =
                (com.badlogic.gdx.physics.box2d.joints.PulleyJoint) world.getMapper(PhysicsRuntimeJointComponent.class).get(joint).joint;
        Assert.assertEquals(pulley.groundAx, nativeJoint.getGroundAnchorA().x, EPSILON);
        Assert.assertEquals(2f, pulley.lengthAM, 0f);
        Assert.assertEquals(2f, pulley.lengthBM, 0f);
        placement.applyBefore();
        Assert.assertEquals(0f, pulley.groundAx, EPSILON);
        Assert.assertEquals(2f, pulley.groundAy, EPSILON);
    }

    @Test
    public void placementRejectsUnrealizedPhysicsBeforeChangingAnyNode() {
        int root = entity(1, true, -1, false);
        int child = entity(2, false, 1, true);
        world.getMapper(PhysicsCompiledFixturesComponent.class).get(child).fixtures.clear();
        identities.rebuild();
        world.process();
        Assert.assertThrows(IllegalStateException.class, () -> GameObjectPlacement.prepare(world,
                new IntArray(new int[]{root}), 0f, 0f, 50f, 20f, 0.5f, false));
        Assert.assertEquals(0f, transform(root).x, 0f);
        Assert.assertEquals(0f, transform(child).x, 0f);
    }

    @Test
    public void authoringPlacementPreservesGearSourcesAndReferenceAnglesAcrossFollowingFrames() {
        int frame = entity(1, true, -1, true);
        int wheel = entity(2, true, 1, true);
        int trailer = entity(3, true, -1, true);
        int wheel2 = entity(4, false, 3, true);
        world.getMapper(PhysicsBodyComponent.class).get(frame).type = PhysicsBodyComponent.STATIC;
        world.getMapper(PhysicsBodyComponent.class).get(trailer).type = PhysicsBodyComponent.KINEMATIC;
        transform(frame).rotationRad = .31f;
        transform(frame).originX = 13f;
        transform(wheel).x = 137f;
        transform(wheel).rotationRad = .47f;
        transform(wheel).originY = 9f;
        transform(trailer).x = 400f;
        transform(wheel2).x = 111f;
        int source1 = distanceJoint(frame, wheel, 1f);
        int source2 = distanceJoint(trailer, wheel2, 1f);
        for (int source : new int[]{source1, source2}) {
            world.getMapper(PhysicsDistanceJointComponent.class).remove(source);
            world.getMapper(PhysicsJointComponent.class).get(source).type = PhysicsJointComponent.TYPE_REVOLUTE;
            world.getMapper(PhysicsRevoluteJointComponent.class).create(source).enableLimit = true;
        }
        int gearEntity = distanceJoint(wheel, wheel2, 1f);
        world.getMapper(PhysicsDistanceJointComponent.class).remove(gearEntity);
        world.getMapper(PhysicsJointComponent.class).get(gearEntity).type = PhysicsJointComponent.TYPE_GEAR;
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).create(gearEntity);
        gear.joint1Eid = source1;
        gear.joint2Eid = source2;
        gear.ratio = 2.3f;
        identities.rebuild();
        world.process();
        world.process(); // Publish dependent Gear after its source joints are realized.
        Joint[] before = new Joint[3];
        int[] jointIds = {source1, source2, gearEntity};
        for (int j = 0; j < 3; j++) {
            before[j] = world.getMapper(PhysicsRuntimeJointComponent.class).get(jointIds[j]).joint;
            Assert.assertNotNull(before[j]);
        }
        float reference = ((com.badlogic.gdx.physics.box2d.joints.RevoluteJoint) before[0]).getReferenceAngle();
        Assert.assertThrows(IllegalArgumentException.class, () -> GameObjectPlacement.prepare(world,
                IntArray.with(frame), 0f, 0f, 10f, 0f, .17f, false));
        GameObjectPlacement placement = GameObjectPlacement.prepare(world, IntArray.with(frame, trailer),
                27f, -13f, 213f, 117f, .713f, false);
        placement.applyAfter();
        Vector2 placed = new Vector2(nativeBody(wheel).getPosition());
        for (int i = 0; i < 3; i++) {
            world.process();
            Assert.assertEquals(placed, nativeBody(wheel).getPosition());
            for (int j = 0; j < 3; j++) {
                Assert.assertSame(before[j], world.getMapper(PhysicsRuntimeJointComponent.class).get(jointIds[j]).joint);
            }
        }
        Assert.assertEquals(reference, ((com.badlogic.gdx.physics.box2d.joints.RevoluteJoint) before[0]).getReferenceAngle(), 0f);
        Assert.assertEquals(2.3f, gear.ratio, 0f);
        placement.applyBefore();
        world.process();
        Assert.assertSame(before[2], world.getMapper(PhysicsRuntimeJointComponent.class).get(gearEntity).joint);
        // A later explicit design move is still detected, rather than masked by placement provenance.
        transform(frame).x += 30f;
        world.process();
        Assert.assertEquals(.3f, nativeBody(frame).getPosition().x, EPSILON);
    }

    @Test
    public void preparedPlacementRejectsChangedMembershipAndFreshPlacementUsesCurrentChildren() {
        int root = entity(1, true, -1, false);
        int child = entity(2, false, 1, false);
        int other = entity(3, true, -1, false);
        identities.rebuild();
        world.process();
        GameObjectPlacement stale = GameObjectPlacement.prepare(world, IntArray.with(root), 0f, 0f, 10f, 0f, 0f, false);
        world.getMapper(GameObjectMemberComponent.class).get(child).parentStableId = 3;
        int fresh = entity(4, false, 1, false);
        identities.rebuild();
        world.process();
        Assert.assertThrows(IllegalStateException.class, stale::applyAfter);
        Assert.assertEquals(0f, transform(root).x, 0f);
        GameObjectPlacement.prepare(world, IntArray.with(root), 0f, 0f, 10f, 0f, 0f, false).applyAfter();
        Assert.assertEquals(10f, worldState().x[fresh], EPSILON);
        Assert.assertEquals(0f, worldState().x[child], EPSILON);
        Assert.assertEquals(0f, transform(other).x, 0f);
    }

    @Test
    public void ordinaryUnindexedJointCanBePlacedButStaleNativeJointCannotBeReused() {
        int first = entity(1, true, -1, true);
        int second = entity(2, true, -1, true);
        transform(second).x = 100f;
        int joint = distanceJoint(first, second, 1f);
        IdentityRegistry.unindexEntityImmediately(world, joint);
        world.getMapper(PixscapeIdentityComponent.class).remove(joint);
        identities.rebuild();
        world.process();
        GameObjectPlacement placement = GameObjectPlacement.prepare(world, IntArray.with(first, second),
                0f, 0f, 30f, 20f, .2f, false);
        placement.applyAfter();
        placement.applyBefore();
        Assert.assertEquals(0f, world.getMapper(PhysicsRuntimeBodyComponent.class).get(first).body.getPosition().x, EPSILON);
        world.delete(joint);
        world.process();
        Assert.assertThrows(IllegalStateException.class, placement::applyAfter);
        Assert.assertEquals(0f, world.getMapper(PhysicsRuntimeBodyComponent.class).get(first).body.getPosition().x, EPSILON);
    }

    @Test
    public void placementRejectsDuringNativeContactWithoutWritingAuthoredState() {
        int first = entity(1, true, -1, true);
        int second = entity(2, true, -1, true);
        transform(second).x = 300f;
        identities.rebuild();
        world.process();
        GameObjectPlacement placement = GameObjectPlacement.prepare(world, IntArray.with(first, second),
                0f, 0f, 30f, 20f, .2f, false);
        boolean[] observed = {false};
        box2d.world.setContactListener(new com.badlogic.gdx.physics.box2d.ContactListener() {
            @Override
            public void beginContact(com.badlogic.gdx.physics.box2d.Contact contact) {
                observed[0] = true;
                float beforeX = transform(first).x;
                Assert.assertTrue(box2d.world.isLocked());
                Assert.assertThrows(IllegalStateException.class, placement::applyAfter);
                Assert.assertEquals(beforeX, transform(first).x, 0f);
            }

            @Override
            public void endContact(com.badlogic.gdx.physics.box2d.Contact contact) {
            }

            @Override
            public void preSolve(com.badlogic.gdx.physics.box2d.Contact contact, com.badlogic.gdx.physics.box2d.Manifold manifold) {
            }

            @Override
            public void postSolve(com.badlogic.gdx.physics.box2d.Contact contact, com.badlogic.gdx.physics.box2d.ContactImpulse impulse) {
            }
        });
        authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        nativeBody(second).setTransform(0f, 0f, 0f);
        sync.setStepEnabled(true);
        world.setDelta(1f / 60f);
        world.process();
        Assert.assertTrue(observed[0]);
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
