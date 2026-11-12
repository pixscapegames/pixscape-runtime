package games.pixscape.runtime.engine;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.*;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.GameObjectInstance;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.runtime.loading.SceneLoadingInvocationStrategy;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.SpawnPhysicsProbe;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PixscapeEngineGameObjectSpawnTest {
    @Test public void readyBeforeFirstRenderAndBetweenFramesWithoutSimulationOrWorldProcessing() throws Exception {
        try (Harness h = new Harness(asset())) {
            int[] systemCalls = {0};
            h.engine.setConfigurationCustomizer(b -> b.with(new BaseSystem() {
                @Override protected void processSystem() { systemCalls[0]++; }
            }));
            h.ready();
            Body sentinel = h.rawBody(10, 10, true, false);
            sentinel.setLinearVelocity(3, 4);
            int subscriptions = h.engine.getWorld().getAspectSubscriptionManager().get(Aspect.all()).getEntities().size();
            int rebuilds = h.hierarchy().rebuildCount();
            GameObjectInstance first = h.spawn();
            h.assertComplete(first, 3, 2);
            assertEquals(1.05f, h.player(first).getPosition().x, .00001f);
            assertEquals(2.07f, h.player(first).getPosition().y, .00001f);
            assertEquals(0f, h.player(first).getLinearVelocity().y, 0f);
            assertEquals(10f, sentinel.getPosition().x, 0f);
            assertEquals(10f, sentinel.getPosition().y, 0f);
            assertEquals(0, systemCalls[0]);
            assertEquals(rebuilds, h.hierarchy().rebuildCount());
            assertEquals(subscriptions, h.engine.getWorld().getAspectSubscriptionManager().get(Aspect.all()).getEntities().size());
            assertTrue(h.engine.getBox2dSyncSystem().isStepEnabled());
            h.frame(0);
            GameObjectInstance second = h.spawn();
            h.assertComplete(second, 5, 4);
            assertEquals(1, systemCalls[0]);
            assertEquals(10f, sentinel.getPosition().x, 0f);
        }
    }

    @Test public void delayedNotificationsKeepBodiesJointsAndImmediatelyAppliedVelocity() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); SpawnPhysicsProbe probe = new SpawnPhysicsProbe(h.engine.getBox2dSyncSystem());
            GameObjectInstance instance = h.spawn();
            Body body = h.player(instance);
            int generation = h.engine.getWorld().getMapper(PhysicsRuntimeBodyComponent.class)
                    .get(instance.requireMember("playerAnimation").entityId()).gen;
            Joint joint = body.getJointList().first().joint;
            body.setLinearVelocity(7, 11); body.setAngularVelocity(2);
            h.frame(0);
            assertSame(body, h.player(instance));
            assertSame(joint, body.getJointList().first().joint);
            assertEquals(generation, h.engine.getWorld().getMapper(PhysicsRuntimeBodyComponent.class)
                    .get(instance.requireMember("playerAnimation").entityId()).gen);
            assertEquals(7f, body.getLinearVelocity().x, 0f);
            assertEquals(11f, body.getLinearVelocity().y, 0f);
            assertEquals(2f, body.getAngularVelocity(), 0f);
            assertEquals(2, probe.bodies); assertEquals(2, probe.fixtures); assertEquals(2, probe.joints);
            int checks = probe.eligibility + probe.cacheChecks;
            h.frame(0);
            assertEquals(checks, probe.eligibility + probe.cacheChecks);
        }
    }

    @Test public void composedHierarchyPosesAreReadyImmediately() throws Exception {
        GameObjectAsset asset = asset();
        asset.entities.get(0).transform.rotationRad = MathUtils.PI / 2;
        GameObjectAsset.GameObjectEntityData nested = entity(6, 1, "nested", 10, 20);
        nested.gameObject = new GameObjectAsset.GameObjectData();
        nested.transform.rotationRad = -MathUtils.PI / 2;
        asset.entities.add(nested);
        asset.entities.get(1).parentSourceEntityId = 6;
        asset.entities.get(1).transform.rotationRad = .3f;
        try (Harness h = new Harness(asset)) {
            h.ready(); Body body = h.player(h.spawn());
            assertEquals(.85f, body.getPosition().x, .0001f);
            assertEquals(2.17f, body.getPosition().y, .0001f);
            assertEquals(.3f, body.getAngle(), .0001f);
        }
    }

    @Test public void gearWaitsForBothSourceJointsEvenWhenListedFirst() throws Exception {
        GameObjectAsset asset = asset();
        physical(asset.entities.get(3), true); asset.entities.get(3).physicsBody.type = 0;
        asset.joints.clear();
        GameObjectAsset.GameObjectJointData gear = joint(3, 2, 3);
        gear.type = PhysicsJointComponent.TYPE_GEAR; gear.distance = null;
        gear.gear = new GameObjectAsset.GearJointData();
        gear.gear.jointALocalId = 1; gear.gear.jointBLocalId = 2; gear.gear.ratio = 1;
        asset.joints.add(gear);
        for (int i = 1; i <= 2; i++) {
            GameObjectAsset.GameObjectJointData source = joint(i, 4, i + 1);
            source.type = PhysicsJointComponent.TYPE_REVOLUTE; source.distance = null;
            source.revolute = new GameObjectAsset.RevoluteJointData(); asset.joints.add(source);
        }
        try (Harness h = new Harness(asset)) {
            h.ready(); GameObjectInstance instance = h.spawn();
            h.assertComplete(instance, 3, 3); h.frame(0); h.assertComplete(instance, 3, 3);
            instance.despawn(); h.barrier();
            assertEquals(0, h.nativeWorld().getBodyCount()); assertEquals(0, h.nativeWorld().getJointCount());
        }
    }

    @Test public void synchronousSpawnInsideGameplaySystemIsAlreadyComplete() throws Exception {
        try (Harness h = new Harness(asset())) {
            GameObjectInstance[] result = {null};
            h.engine.setConfigurationCustomizer(b -> b.with(new BaseSystem() {
                @Override protected void processSystem() {
                    if (result[0] != null) return;
                    result[0] = h.spawn(); h.assertComplete(result[0], 2, 2);
                    h.player(result[0]).setLinearVelocity(9, 8);
                }
            }));
            h.ready(); h.frame(0); Body body = h.player(result[0]); h.frame(0);
            assertSame(body, h.player(result[0])); assertEquals(9f, body.getLinearVelocity().x, 0f);
        }
    }

    @Test public void lockedSynchronousSpawnRefusesBeforeMutationAndDeferredSpawnCompletesLater() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); h.rawBody(-10, -10, false, true); h.rawBody(-10, -10, true, true);
            int[] contacts = {0}; GameObjectInstance[] result = {null}; List<Throwable> failures = new ArrayList<>();
            h.nativeWorld().setContactListener(new ContactListener() {
                @Override public void beginContact(Contact contact) {
                    if (contacts[0]++ != 0) return;
                    assertTrue(h.nativeWorld().isLocked());
                    int nextEntity = h.fixture.sceneA.nextEntityStableId;
                    int nextShape = h.fixture.sceneA.nextPhysicsShapeId;
                    IllegalStateException error = assertThrows(IllegalStateException.class, h::spawn);
                    assertTrue(error.getMessage().contains("locked"));
                    assertEquals(nextEntity, h.fixture.sceneA.nextEntityStableId);
                    assertEquals(nextShape, h.fixture.sceneA.nextPhysicsShapeId);
                    assertEquals(2, h.nativeWorld().getBodyCount());
                    h.engine.api().gameObjects().spawnDeferred("declared", 100, 200, instance -> {
                        assertFalse(h.nativeWorld().isLocked());
                        h.assertComplete(instance, 4, 2); result[0] = instance;
                    }, failures::add);
                    assertNull(result[0]);
                }
                @Override public void endContact(Contact c) { }
                @Override public void preSolve(Contact c, Manifold m) { }
                @Override public void postSolve(Contact c, ContactImpulse i) { }
            });
            h.frame(1f / 60); assertTrue(contacts[0] > 0); assertNull(result[0]);
            h.frame(0); assertNotNull(result[0]); assertTrue(failures.toString(), failures.isEmpty());
        }
    }

    @Test public void nonPhysicalSpawnRemainsAllowedInsideContactCallback() throws Exception {
        GameObjectAsset visual = asset(); visual.joints.clear();
        for (GameObjectAsset.GameObjectEntityData e : visual.entities) { e.physicsBody = null; e.physicsShapes.clear(); }
        try (Harness h = new Harness(visual)) {
            h.ready(); h.rawBody(-10, -10, false, true); h.rawBody(-10, -10, true, true);
            GameObjectInstance[] result = {null};
            h.nativeWorld().setContactListener(new ContactListener() {
                @Override public void beginContact(Contact c) {
                    assertTrue(h.nativeWorld().isLocked()); result[0] = h.spawn();
                    assertNull(h.player(result[0])); assertEquals(2, h.nativeWorld().getBodyCount());
                }
                @Override public void endContact(Contact c) { }
                @Override public void preSolve(Contact c, Manifold m) { }
                @Override public void postSolve(Contact c, ContactImpulse i) { }
            });
            h.frame(1f / 60); assertNotNull(result[0]);
        }
    }

    @Test public void emptyPhysicsDefinitionDoesNotRequireNativeCreationUnderLock() throws Exception {
        GameObjectAsset empty = asset(); empty.joints.clear();
        for (GameObjectAsset.GameObjectEntityData e : empty.entities) e.physicsShapes.clear();
        try (Harness h = new Harness(empty)) {
            h.ready(); h.rawBody(-10, -10, false, true); h.rawBody(-10, -10, true, true);
            GameObjectInstance[] result = {null};
            h.nativeWorld().setContactListener(new ContactListener() {
                @Override public void beginContact(Contact c) {
                    assertTrue(h.nativeWorld().isLocked()); result[0] = h.spawn(); assertNull(h.player(result[0]));
                }
                @Override public void endContact(Contact c) { }
                @Override public void preSolve(Contact c, Manifold m) { }
                @Override public void postSolve(Contact c, ContactImpulse i) { }
            });
            h.frame(1f / 60); assertNotNull(result[0]); assertEquals(2, h.nativeWorld().getBodyCount());
        }
    }

    @Test public void invalidPositionAndDisabledPhysicsFailBeforeAllocation() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); int next = h.fixture.sceneA.nextEntityStableId;
            assertThrows(IllegalArgumentException.class, () -> h.engine.api().gameObjects().spawn("declared", Float.NaN, 0));
            assertEquals(next, h.fixture.sceneA.nextEntityStableId); assertEquals(0, h.nativeWorld().getBodyCount());
        }
        try (Harness h = new Harness(asset())) {
            h.fixture.sceneA.physicsEnabled = false; h.ready(); int next = h.fixture.sceneA.nextEntityStableId;
            assertThrows(IllegalStateException.class, h::spawn);
            assertEquals(next, h.fixture.sceneA.nextEntityStableId); assertNull(h.engine.api().physics().box2dWorld());
        }
    }

    @Test public void removingBodyBeforeObservationRejectsSurvivingAuthoredJoint() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); GameObjectInstance instance = h.spawn();
            int player = instance.requireMember("playerAnimation").entityId();
            h.engine.getWorld().getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).remove(player);
            h.engine.getWorld().getMapper(games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent.class).remove(player);
            // Removing only the body leaves the instance's authored joint dangling.
            // This must fail at publication, not silently disappear from native Physics.
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> h.frame(0));
            assertTrue(failure.getMessage(), failure.getMessage().contains("Joint publication"));
        }
    }

    @Test public void failureBeforeSceneRetirementLeavesRequestsAndCallbackExceptionsDoNotRedeliver() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); List<String> events = new ArrayList<>();
            h.engine.api().gameObjects().spawnDeferred("declared", 0, 0, instance -> {
                events.add("success"); throw new IllegalStateException("User callback failed");
            }, error -> events.add("failure"));
            assertThrows(IllegalArgumentException.class, () -> h.engine.beginLoadScene("unknown"));
            h.frame(0); h.frame(0); assertEquals(List.of("success"), events);
            assertEquals(2, h.nativeWorld().getBodyCount());
        }
    }

    @Test public void bodyFailureRollsBackOnlyAttemptAndCanRetry() throws Exception { failureAndRetry(1); }
    @Test public void fixtureFailureRollsBackCandidateAndEarlierBodiesAndCanRetry() throws Exception { failureAndRetry(2); }
    @Test public void jointFailureRollsBackEarlierJointAndAllBodiesAndCanRetry() throws Exception { failureAndRetry(3); }

    private void failureAndRetry(int kind) throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); GameObjectInstance existing = h.spawn(); Body body = h.player(existing);
            body.setLinearVelocity(8, 9); Joint joint = body.getJointList().first().joint;
            int traversal = h.hierarchy().topology().traversalSize;
            SpawnPhysicsProbe probe = new SpawnPhysicsProbe(h.engine.getBox2dSyncSystem());
            if (kind == 1) probe.failBody = 2;
            if (kind == 2) probe.failFixture = 2;
            if (kind == 3) probe.failJoint = 2;
            assertThrows(IllegalStateException.class, h::spawn);
            assertEquals(2, h.nativeWorld().getBodyCount()); assertEquals(2, h.nativeWorld().getJointCount());
            assertEquals(traversal, h.hierarchy().topology().traversalSize);
            assertSame(body, h.player(existing)); assertSame(joint, body.getJointList().first().joint);
            probe.failBody = probe.failFixture = probe.failJoint = 0;
            GameObjectInstance retry = h.spawn(); h.assertComplete(retry, 4, 4);
            h.frame(0);
            assertSame(body, h.player(existing)); assertEquals(8f, body.getLinearVelocity().x, 0f);
            existing.despawn(); retry.despawn(); h.barrier();
            assertEquals(0, h.nativeWorld().getBodyCount()); assertEquals(0, h.nativeWorld().getJointCount());
        }
    }

    @Test public void unmaterializableAuthoredJointFailsEntireSpawn() throws Exception {
        GameObjectAsset asset = asset(); asset.joints.get(1).bodyBLocalEntityId = 4;
        try (Harness h = new Harness(asset)) {
            h.ready(); assertThrows(IllegalStateException.class, h::spawn);
            assertEquals(0, h.nativeWorld().getBodyCount()); assertEquals(0, h.nativeWorld().getJointCount());
            h.frame(0); assertEquals(0, h.nativeWorld().getBodyCount());
        }
    }

    @Test public void deferredSuccessFailureAndCallbackSubmissionsHaveSeparateDelivery() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); List<String> events = new ArrayList<>();
            h.engine.api().gameObjects().spawnDeferred("declared", 100, 200, instance -> {
                h.assertComplete(instance, 2, 2); events.add("success");
                h.engine.api().gameObjects().spawnDeferred("missing", 0, 0,
                        unused -> fail("Missing asset succeeded"), error -> events.add("missing"));
            }, error -> fail(error.toString()));
            assertTrue(events.isEmpty()); h.frame(0); assertEquals(List.of("success"), events);
            h.frame(0); assertEquals(List.of("success", "missing"), events);
            SpawnPhysicsProbe probe = new SpawnPhysicsProbe(h.engine.getBox2dSyncSystem()); probe.failFixture = 2;
            h.engine.api().gameObjects().spawnDeferred("declared", 0, 0,
                    unused -> fail("Injected failure succeeded"), error -> events.add("fixture"));
            h.frame(0); assertEquals(List.of("success", "missing", "fixture"), events);
            assertEquals(2, h.nativeWorld().getBodyCount()); assertEquals(2, h.nativeWorld().getJointCount());
        }
    }

    @Test public void sceneReplacementAndDisposalCancelQueuedRequestsExactlyOnce() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); List<Throwable> failures = new ArrayList<>();
            h.engine.api().gameObjects().spawnDeferred("declared", 0, 0, unused -> fail(), failures::add);
            h.engine.loadScene("A");
            assertEquals(1, failures.size()); assertTrue(failures.get(0).getMessage().contains("cancelled"));
            h.frame(0); assertEquals(0, h.nativeWorld().getBodyCount());
            h.engine.api().gameObjects().spawnDeferred("declared", 0, 0, unused -> fail(), failures::add);
            h.engine.dispose(); assertEquals(2, failures.size());
        }
    }

    @Test public void sceneChangeInSuccessCallbackCancelsRemainingBatch() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); List<String> events = new ArrayList<>();
            h.engine.api().gameObjects().spawnDeferred("declared", 0, 0,
                    instance -> { events.add("first"); h.engine.loadScene("A"); }, error -> fail());
            h.engine.api().gameObjects().spawnDeferred("declared", 0, 0,
                    instance -> fail("Retired request succeeded"), error -> events.add("cancelled"));
            h.frame(0); assertEquals(List.of("first", "cancelled"), events);
            assertEquals(0, h.nativeWorld().getBodyCount());
        }
    }

    @Test public void nullRemainsLegitimateForNonPhysicalAndEmptyMembersAndDeletedHandles() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); GameObjectInstance instance = h.spawn();
            assertNull(h.body(null)); assertNull(h.body(instance.root()));
            assertNull(h.body(instance.requireMember("visualOnly")));
            assertNull(h.body(instance.requireMember("emptyPhysics")));
            EntityRef player = instance.requireMember("playerAnimation"); assertNotNull(h.body(player));
            instance.despawn(); h.barrier(); assertNull(h.body(player));
            assertEquals(0, h.nativeWorld().getBodyCount());
        }
    }

    @Test public void spawnWorkDoesNotGrowWithExistingBodies() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready(); for (int i = 0; i < 50; i++) h.spawn(); h.frame(0);
            SpawnPhysicsProbe probe = new SpawnPhysicsProbe(h.engine.getBox2dSyncSystem());
            h.spawn(); assertEquals(2, probe.bodies); assertEquals(2, probe.joints);
            assertTrue(probe.eligibility < 20); assertTrue(probe.cacheChecks < 20);
            h.frame(0); assertEquals(2, probe.bodies); assertEquals(2, probe.joints);
        }
    }

    static final class Harness implements AutoCloseable {
        final PixscapeEnginePhysicsLifecycleTest fixtureSupport = new PixscapeEnginePhysicsLifecycleTest();
        final PixscapeEnginePhysicsLifecycleTest.EngineFixture fixture;
        final PixscapeEngine engine;
        Harness(GameObjectAsset asset) throws Exception {
            fixtureSupport.installApplication();
            fixture = PixscapeEnginePhysicsLifecycleTest.createEngineFixture(); engine = fixture.engine;
            fixture.sceneA.physicsEnabled = true; fixture.sceneA.pixelsPerMeter = 100;
            fixture.sceneA.gravityX = 0; fixture.sceneA.gravityY = -10;
            new GameObjectAssetLoader().save(fixture.projectDir.child("gameobjects/declared.gameobject"), asset);
        }
        void ready() { engine.loadScene("A"); }
        void frame(float delta) { engine.update(delta); engine.render(); }
        GameObjectInstance spawn() { return engine.api().gameObjects().spawn("declared", 100, 200); }
        Body body(EntityRef entity) { return engine.api().physics().body(entity); }
        Body player(GameObjectInstance instance) { return body(instance.requireMember("playerAnimation")); }
        World nativeWorld() { return engine.api().physics().box2dWorld(); }
        GameObjectHierarchySystem hierarchy() { return engine.getWorld().getSystem(GameObjectHierarchySystem.class); }
        void barrier() { ((SceneLoadingInvocationStrategy) engine.getWorld().getInvocationStrategy()).synchronizeEntitySubscriptions(); }
        void assertComplete(GameObjectInstance instance, int bodies, int joints) {
            assertNotNull(player(instance)); assertNotNull(body(instance.requireMember("otherBody")));
            assertEquals(bodies, nativeWorld().getBodyCount()); assertEquals(joints, nativeWorld().getJointCount());
            assertTrue(hierarchy().worldTransforms().isResolved(instance.requireMember("playerAnimation").entityId()));
        }
        Body rawBody(float x, float y, boolean dynamic, boolean fixture) {
            BodyDef def = new BodyDef(); def.type = dynamic ? BodyDef.BodyType.DynamicBody : BodyDef.BodyType.StaticBody;
            def.position.set(x, y); Body body = nativeWorld().createBody(def);
            if (fixture) { PolygonShape shape = new PolygonShape(); shape.setAsBox(.5f, .5f); body.createFixture(shape, 1); shape.dispose(); }
            return body;
        }
        @Override public void close() { engine.dispose(); fixtureSupport.restoreApplication(); }
    }

    static GameObjectAsset asset() {
        GameObjectAsset asset = new GameObjectAsset(); asset.rootSourceEntityId = 1;
        GameObjectAsset.GameObjectEntityData root = entity(1, -1, "root", 0, 0);
        root.gameObject = new GameObjectAsset.GameObjectData(); asset.entities.add(root);
        GameObjectAsset.GameObjectEntityData player = entity(2, 1, "playerAnimation", 5, 7);
        physical(player, true); asset.entities.add(player);
        GameObjectAsset.GameObjectEntityData other = entity(3, 1, "otherBody", 105, 7);
        physical(other, true); asset.entities.add(other);
        GameObjectAsset.GameObjectEntityData empty = entity(4, 1, "emptyPhysics", 0, 0);
        physical(empty, false); asset.entities.add(empty);
        asset.entities.add(entity(5, 1, "visualOnly", 0, 0));
        asset.joints.add(joint(1, 2, 3)); asset.joints.add(joint(2, 2, 3)); return asset;
    }
    static GameObjectAsset.GameObjectJointData joint(int id, int a, int b) {
        GameObjectAsset.GameObjectJointData joint = new GameObjectAsset.GameObjectJointData();
        joint.jointLocalId = id; joint.type = PhysicsJointComponent.TYPE_DISTANCE;
        joint.bodyALocalEntityId = a; joint.bodyBLocalEntityId = b;
        joint.distance = new GameObjectAsset.DistanceJointData(); joint.distance.lengthM = 1; return joint;
    }
    static GameObjectAsset.GameObjectEntityData entity(int id, int parent, String name, float x, float y) {
        GameObjectAsset.GameObjectEntityData e = new GameObjectAsset.GameObjectEntityData();
        e.sourceEntityId = id; e.parentSourceEntityId = parent;
        e.transform = new GameObjectAsset.TransformData(); e.transform.x = x; e.transform.y = y;
        e.transform.scaleX = e.transform.scaleY = 1;
        e.entityIndex = new GameObjectAsset.EntityIndexData();
        e.identity = new GameObjectAsset.IdentityData(); e.identity.name = name; return e;
    }
    static void physical(GameObjectAsset.GameObjectEntityData e, boolean fixture) {
        e.physicsBody = new GameObjectAsset.PhysicsBodyData(); e.physicsBody.type = 2;
        if (fixture) {
            GameObjectAsset.PhysicsShapeData shape = new GameObjectAsset.PhysicsShapeData();
            shape.localShapeId = e.sourceEntityId; shape.geometry = new PhysicsGeometryData();
            shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX; e.physicsShapes.add(shape);
        }
    }
}
