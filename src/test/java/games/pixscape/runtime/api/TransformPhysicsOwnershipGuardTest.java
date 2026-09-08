package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.PhysicsPoseAuthority;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

/** Runtime P2 contract coverage for high-level transform ownership protection. */
public class TransformPhysicsOwnershipGuardTest {
    @Test
    public void runtimePhysicsRejectsEveryGenericTransformMutationForEachBodyType() throws Exception {
        Harness harness = new Harness();
        int body = harness.entity(1, false, -1);
        PhysicsBodyComponent physics = harness.world.getMapper(PhysicsBodyComponent.class).create(body);
        harness.activateRuntimePhysics();
        TransformFacade transform = harness.engine.api().entities().ofEntityId(body).transform();
        TransformComponent authored = harness.world.getMapper(TransformComponent.class).get(body);
        authored.x = 3f;
        authored.y = 4f;

        for (int type = PhysicsBodyComponent.STATIC; type <= PhysicsBodyComponent.DYNAMIC; type++) {
            physics.type = type;
            assertRejected(new Runnable() { @Override public void run() { transform.setPosition(10f, 11f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.setX(12f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.setY(13f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.moveBy(1f, 2f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.setRotationRad(0.5f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.rotateByRad(0.5f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.setScale(2f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.setScaleX(2f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.setScaleY(2f); } });
            assertRejected(new Runnable() { @Override public void run() { transform.setOrigin(2f, 3f); } });
        }
        Assert.assertEquals(3f, authored.x, 0f);
        Assert.assertEquals(4f, authored.y, 0f);
    }

    @Test
    public void runtimePhysicsRejectsAncestorsButLeavesUnrelatedSiblingMutable() throws Exception {
        Harness harness = new Harness();
        int root = harness.entity(1, true, -1);
        int middle = harness.entity(2, true, 1);
        int child = harness.entity(3, false, 2);
        harness.world.getMapper(PhysicsBodyComponent.class).create(child)
                .type = PhysicsBodyComponent.KINEMATIC;
        int sibling = harness.entity(4, false, -1);
        harness.activateRuntimePhysics();

        assertRejected(new Runnable() {
            @Override public void run() {
                harness.engine.api().entities().ofEntityId(root).transform().setScale(2f);
            }
        });
        assertRejected(new Runnable() {
            @Override public void run() {
                harness.engine.api().entities().ofEntityId(middle).transform().setOrigin(5f, 6f);
            }
        });

        harness.engine.api().entities().ofEntityId(sibling).transform().setPosition(9f, 10f);
        Assert.assertEquals(9f, harness.world.getMapper(TransformComponent.class).get(sibling).x, 0f);
    }

    @Test
    public void authoringAndExpertEcsRemainPermitted() throws Exception {
        Harness harness = new Harness();
        int root = harness.entity(1, true, -1);
        int child = harness.entity(2, false, 1);
        harness.world.getMapper(PhysicsBodyComponent.class).create(child);
        harness.prepareHierarchy();

        harness.engine.api().entities().ofEntityId(root).transform().setPosition(7f, 8f);
        Assert.assertEquals(7f, harness.world.getMapper(TransformComponent.class).get(root).x, 0f);

        harness.authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        harness.world.getMapper(TransformComponent.class).get(root).x = 21f;
        Assert.assertEquals(21f, harness.world.getMapper(TransformComponent.class).get(root).x, 0f);
    }

    private static void assertRejected(Runnable action) {
        IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, action::run);
        Assert.assertTrue(failure.getMessage().contains("Runtime Physics owns"));
    }

    private static final class Harness {
        final World world;
        final PixscapeEngine engine;
        final PhysicsPoseAuthority authority;

        Harness() throws Exception {
            authority = new PhysicsPoseAuthority();
            world = new World(new WorldConfigurationBuilder()
                    .with(new DirtyTrackerSystem(16), authority, new GameObjectHierarchySystem(16))
                    .build());
            engine = new PixscapeEngine();
            Field field = PixscapeEngine.class.getDeclaredField("world");
            field.setAccessible(true);
            field.set(engine, world);
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 100;
            engine.getIdentityRegistry().bind(world, meta);
            engine.getTagRegistry().bind(world);
        }

        int entity(int stableId, boolean gameObject, int parentStableId) {
            int entityId = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entityId).stableId = stableId;
            world.getMapper(EntityIndexComponent.class).create(entityId);
            world.getMapper(TransformComponent.class).create(entityId);
            if (gameObject) world.getMapper(GameObjectComponent.class).create(entityId);
            if (parentStableId > 0) {
                world.getMapper(GameObjectMemberComponent.class).create(entityId)
                        .parentStableId = parentStableId;
            }
            return entityId;
        }

        void activateRuntimePhysics() {
            prepareHierarchy();
            authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        }

        void prepareHierarchy() {
            engine.getIdentityRegistry().rebuild();
            world.process();
        }
    }
}
