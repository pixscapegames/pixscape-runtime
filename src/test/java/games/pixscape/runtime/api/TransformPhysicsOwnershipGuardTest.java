package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.GameObjectPhysicsWritebackSystem;
import com.badlogic.gdx.math.Vector2;
import com.artemis.Aspect;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.PhysicsPoseAuthority;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 * Runtime P2 contract coverage for high-level transform ownership protection.
 */
public class TransformPhysicsOwnershipGuardTest {
    private final java.util.List<Harness> harnesses = new java.util.ArrayList<>();

    @org.junit.After
    public void disposeHarnesses() {
        for (Harness harness : harnesses) {
            harness.engine.getIdentityRegistry().bind(null, null);
            harness.world.dispose();
            harness.box2d.dispose();
        }
    }
    @Test
    public void runtimePhysicsPreservesPoseAndOriginGuardsForEachBodyType() throws Exception {
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
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    transform.setPosition(10f, 11f);
                }
            });
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    transform.setX(12f);
                }
            });
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    transform.setY(13f);
                }
            });
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    transform.moveBy(1f, 2f);
                }
            });
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    transform.setRotationRad(0.5f);
                }
            });
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    transform.rotateByRad(0.5f);
                }
            });
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    transform.setOrigin(2f, 3f);
                }
            });
        }
        Assert.assertEquals(3f, authored.x, 0f);
        Assert.assertEquals(4f, authored.y, 0f);
        Assert.assertEquals(0f, authored.rotationRad, 0f);
        Assert.assertEquals(0f, authored.originX, 0f);
        Assert.assertEquals(0f, authored.originY, 0f);
    }

    @Test
    public void ownBodyAllowsEveryScaleSetterAndSpriteAnimationShortcut() throws Exception {
        Harness harness = new Harness();
        int entity = harness.entity(1, false, -1);
        PhysicsBodyComponent physics = harness.world.getMapper(PhysicsBodyComponent.class).create(entity);
        harness.world.getMapper(TextureRegionComponent.class).create(entity);
        harness.activateRuntimePhysics();
        EntityRef ref = harness.engine.api().entities().ofEntityId(entity);
        TransformFacade transform = ref.transform();
        SpriteRef sprite = new PixscapeApiImpl.SpriteRefImpl(ref);
        AnimationRef animation = new PixscapeApiImpl.AnimationRefImpl(ref);
        for (int type = PhysicsBodyComponent.STATIC; type <= PhysicsBodyComponent.DYNAMIC; type++) {
            physics.type = type;
            transform.setScale(1f).setScaleX(-1f);
            assertScale(transform, -1f, 1f);
            transform.setScaleY(-2f);
            assertScale(transform, -1f, -2f);
            transform.setScale(3f);
            assertScale(transform, 3f, 3f);
            transform.setScale(-2f, 4f);
            assertScale(transform, -2f, 4f);
            Assert.assertSame(sprite, sprite.scale(2f));
            assertScale(transform, 2f, 2f);
            Assert.assertSame(sprite, sprite.scale(-3f, 4f));
            assertScale(transform, -3f, 4f);
            Assert.assertSame(animation, animation.scale(-2f));
            assertScale(transform, -2f, -2f);
            animation.transform().setScale(0f, 2f);
            assertScale(transform, 0f, 2f);
        }
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            Assert.assertThrows(IllegalArgumentException.class, () -> transform.setScale(invalid));
            Assert.assertThrows(IllegalArgumentException.class, () -> transform.setScale(5f, invalid));
            Assert.assertThrows(IllegalArgumentException.class, () -> transform.setScaleX(invalid));
            Assert.assertThrows(IllegalArgumentException.class, () -> transform.setScaleY(invalid));
            assertScale(transform, 0f, 2f);
        }
    }

    @Test
    public void gameObjectOwnBodyRejectsGlobalScaleBeforeMutation() throws Exception {
        Harness harness = new Harness();
        int root = harness.entity(1, true, -1);
        harness.world.getMapper(PhysicsBodyComponent.class).create(root);
        harness.activateRuntimePhysics();
        TransformFacade transform = harness.engine.api().entities().ofEntityId(root).transform();
        Assert.assertThrows(IllegalArgumentException.class, () -> transform.setScale(2f));
        Assert.assertThrows(IllegalArgumentException.class, () -> transform.setScale(-2f));
        Assert.assertThrows(IllegalArgumentException.class, () -> transform.setScale(0f));
        transform.setScale(1f);
        assertScale(transform, 1f, 1f);
        assertRejected(() -> transform.setPosition(1f, 2f));
        assertRejected(() -> transform.setRotationRad(1f));
        assertRejected(() -> transform.setOrigin(1f, 2f));
        harness.world.process();
    }

    private static void assertScale(TransformFacade transform, float sx, float sy) {
        Assert.assertEquals(sx, transform.scaleX(), 0f);
        Assert.assertEquals(sy, transform.scaleY(), 0f);
    }

    @Test
    public void lightRadiusCannotBypassPhysicsOriginOwnership() throws Exception {
        Harness harness = new Harness();
        int entity = harness.entity(1, false, -1);
        harness.world.getMapper(PhysicsBodyComponent.class).create(entity);
        harness.world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).create(entity);
        DimensionsComponent dimensions = harness.world.getMapper(DimensionsComponent.class).create(entity);
        dimensions.width = dimensions.height = 400;
        harness.activateRuntimePhysics();
        LightFacade light = harness.engine.api().entities().ofEntityId(entity).light();
        assertRejected(new Runnable() {
            @Override
            public void run() {
                light.setRadius(100);
            }
        });
        Assert.assertEquals(200, light.radius(), 0);
        Assert.assertEquals(400, dimensions.width, 0);
        light.setRadius(200); // Equal values do not request any mutation.
    }

    @Test
    public void runtimePhysicsAllowsBodylessParentsButRejectsTheirGlobalScaling() throws Exception {
        Harness harness = new Harness();
        int root = harness.entity(1, true, -1);
        int middle = harness.entity(2, true, 1);
        int child = harness.entity(3, false, 2);
        harness.world.getMapper(PhysicsBodyComponent.class).create(child)
                .type = PhysicsBodyComponent.KINEMATIC;
        harness.world.getMapper(PhysicsBodyComponent.class).create(root);
        int visual = harness.entity(5, false, 1);
        int sibling = harness.entity(4, false, -1);
        harness.activateRuntimePhysics();

        TransformFacade rootPose = harness.engine.api().entities().ofEntityId(root).transform();
        Assert.assertThrows(IllegalArgumentException.class, () -> rootPose.setScale(2f));
        assertRejected(() -> rootPose.setPosition(1f, 2f));
        assertRejected(() -> rootPose.setRotationRad(1f));
        TransformFacade middlePose = harness.engine.api().entities().ofEntityId(middle).transform();
        Assert.assertThrows(IllegalArgumentException.class, () -> middlePose.setScale(2f));
        middlePose.setPosition(1f, 2f).setRotationRad(1f).setOrigin(5f, 6f);
        harness.world.process();
        Assert.assertEquals(0f, harness.world.getMapper(games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent.class)
                .get(child).body.getPosition().x, 0.0001f);
        TransformFacade visualTransform = harness.engine.api().entities().ofEntityId(visual).transform();
        visualTransform.setScale(-2f, 3f).setPosition(4f, 5f);
        assertScale(visualTransform, -2f, 3f);
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

        TransformFacade rootTransform = harness.engine.api().entities().ofEntityId(root).transform();
        Assert.assertThrows(IllegalArgumentException.class, () -> rootTransform.setScale(2f));
        assertScale(rootTransform, 1f, 1f);

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

    private final class Harness {
        final World world;
        final PixscapeEngine engine;
        final PhysicsPoseAuthority authority;
        final Box2dWorldService box2d;
        final SceneMetaRuntime meta;

        Harness() throws Exception {
            com.badlogic.gdx.utils.GdxNativesLoader.load();
            box2d = new Box2dWorldService(100f, new Vector2());
            authority = new PhysicsPoseAuthority();
            world = new World(new WorldConfigurationBuilder()
                    .with(new DirtyTrackerSystem(16), authority, new GameObjectHierarchySystem(16),
                            new Box2dSyncSystem(box2d), new GameObjectPhysicsWritebackSystem())
                    .build());
            engine = new PixscapeEngine();
            Field field = PixscapeEngine.class.getDeclaredField("world");
            field.setAccessible(true);
            field.set(engine, world);
            meta = new SceneMetaRuntime();
            world.getSystem(Box2dSyncSystem.class).setSceneMeta(meta);
            world.getSystem(Box2dSyncSystem.class).setEnabled(false);
            meta.nextEntityStableId = 100;
            engine.getIdentityRegistry().bind(world, meta);
            engine.getTagRegistry().bind(world);
            harnesses.add(this);
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
            world.getSystem(Box2dSyncSystem.class).setEnabled(false);
            prepareHierarchy();
            PhysicsService service = new PhysicsService(world, box2d);
            service.setPhysicsShapeIdState(meta);
            var bodies = world.getAspectSubscriptionManager().get(Aspect.all(PhysicsBodyComponent.class)).getEntities();
            for (int i = 0; i < bodies.size(); i++) {
                service.ensurePhysics(bodies.get(i));
            }
            world.getSystem(Box2dSyncSystem.class).setEnabled(true);
            world.process();
            authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
        }

        void prepareHierarchy() {
            engine.getIdentityRegistry().rebuild();
            world.process();
        }
    }
}
