package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import org.junit.Assert;
import org.junit.Test;

public class GameObjectSpatialOwnershipTest {
    @Test
    public void nearestOwnerCrossesEmptyAndPhysicalNodesAndSurvivesRemoval() {
        try (Fixture f = new Fixture()) {
            int root = f.node(1, 0);
            int group = f.node(2, 1);
            int child = f.node(3, 2);
            f.world.getMapper(SpatialFootprintComponent.class).create(root);
            f.world.getMapper(PhysicsBodyComponent.class).create(group);
            f.process();

            Assert.assertEquals(root, f.composition.spatialOwner(child));
            Assert.assertTrue(f.composition.usesPhysicsParallax(root));
            Assert.assertTrue(f.composition.usesPhysicsParallax(child));
            f.world.getMapper(SpatialFootprintComponent.class).create(group);
            f.process();
            Assert.assertEquals(group, f.composition.spatialOwner(child));
            f.world.getMapper(SpatialFootprintComponent.class).remove(group);
            f.process();
            Assert.assertEquals(root, f.composition.spatialOwner(child));
            f.world.getMapper(SpatialFootprintComponent.class).remove(root);
            f.world.getMapper(PhysicsBodyComponent.class).remove(group);
            f.process();
            Assert.assertEquals(-1, f.composition.spatialOwner(child));
            Assert.assertFalse(f.composition.usesPhysicsParallax(root));
        }
    }

    @Test
    public void reparentUpdatesBothCompositionContextsAndOwner() {
        try (Fixture f = new Fixture()) {
            int a = f.node(1, 0);
            int b = f.node(2, 0);
            int child = f.node(3, 1);
            f.world.getMapper(SpatialFootprintComponent.class).create(a);
            f.world.getMapper(SpatialFootprintComponent.class).create(b);
            f.world.getMapper(PhysicsBodyComponent.class).create(child);
            f.process();
            Assert.assertTrue(f.composition.usesPhysicsParallax(a));
            Assert.assertFalse(f.composition.usesPhysicsParallax(b));

            f.world.getMapper(GameObjectMemberComponent.class).get(child).parentStableId = 2;
            f.process();
            Assert.assertEquals(b, f.composition.spatialOwner(child));
            Assert.assertFalse(f.composition.usesPhysicsParallax(a));
            Assert.assertTrue(f.composition.usesPhysicsParallax(b));
        }
    }

    @Test
    public void preparedSpawnPublishesBeforeFrameAndRollbackClearsIt() {
        try (Fixture f = new Fixture()) {
            int root = f.node(1, 0);
            f.world.getMapper(SpatialFootprintComponent.class).create(root);
            f.process();
            int rebuilds = f.hierarchy.rebuildCount();
            int child = f.node(2, 1);
            f.world.getMapper(PhysicsBodyComponent.class).create(child);
            f.identities.indexEntityImmediately(child);
            IntBag created = new IntBag();
            created.add(child);
            f.hierarchy.registerPreparedSpawn(created);
            f.composition.registerPreparedSpawn(created);

            Assert.assertEquals(root, f.composition.spatialOwner(child));
            Assert.assertTrue(f.composition.usesPhysicsParallax(root));
            Assert.assertEquals(rebuilds, f.hierarchy.rebuildCount());
            f.hierarchy.discardPreparedSpawn(child);
            Assert.assertEquals(-1, f.composition.spatialOwner(child));
            Assert.assertFalse(f.composition.usesPhysicsParallax(root));
        }
    }

    @Test
    public void deletionAndIdReuseCannotRetainOwnerOrPhysics() {
        try (Fixture f = new Fixture()) {
            int first = f.node(1, 0);
            f.world.getMapper(SpatialFootprintComponent.class).create(first);
            f.world.getMapper(PhysicsBodyComponent.class).create(first);
            f.process();
            f.world.delete(first);
            f.process();
            Assert.assertEquals(-1, f.composition.spatialOwner(first));
            int replacement = f.node(2, 0);
            f.process();
            Assert.assertEquals(first, replacement);
            Assert.assertEquals(-1, f.composition.spatialOwner(replacement));
            Assert.assertFalse(f.composition.usesPhysicsParallax(replacement));
            int rebuilds = f.hierarchy.rebuildCount();
            for (int i = 0; i < 100; i++) {
                Assert.assertEquals(-1, f.composition.spatialOwner(replacement));
            }
            Assert.assertEquals(rebuilds, f.hierarchy.rebuildCount());
        }
    }

    @Test
    public void preparedReparentPromotesVisualKeepsPoseAndRejectsCyclesBeforeMutation() {
        try (Fixture f = new Fixture()) {
            int parent = f.node(1, 0), child = f.node(2, 0);
            f.world.getMapper(GameObjectComponent.class).remove(parent);
            f.world.getMapper(GameObjectComponent.class).remove(child);
            TransformComponent p = f.world.getMapper(TransformComponent.class).get(parent);
            p.x = 20;
            p.y = 30;
            p.originX = 12;
            p.originY = 7;
            p.rotationRad = .4f;
            TransformComponent c = f.world.getMapper(TransformComponent.class).get(child);
            c.x = 80;
            c.y = 60;
            f.world.getMapper(SpatialFootprintComponent.class).create(parent);
            f.world.getMapper(games.pixscape.runtime.component.TextureRegionComponent.class).create(child);
            var representations = f.world.getMapper(games.pixscape.runtime.component.spatial.SpatialRepresentationComponent.class);
            f.process();
            Assert.assertFalse(representations.has(child));
            games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.reparent(f.world, child, parent);
            Assert.assertNotNull(representations.get(child).rectangles.staticRectangle);
            Assert.assertFalse(representations.has(parent));
            Assert.assertTrue(f.world.getMapper(GameObjectComponent.class).has(parent));
            Assert.assertEquals(20, p.x, 0);
            Assert.assertEquals(80, f.hierarchy.worldTransforms().x[child], .0001f);
            Assert.assertEquals(60, f.hierarchy.worldTransforms().y[child], .0001f);
            Assert.assertEquals(parent, f.composition.spatialOwner(child));
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.reparent(f.world, parent, child));
            Assert.assertFalse(f.world.getMapper(GameObjectComponent.class).has(child));
            games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.reparent(f.world, child, -1);
            Assert.assertEquals(80, c.x, .0001f);
            Assert.assertEquals(-1, f.composition.spatialOwner(child));
        }
    }

    @Test
    public void incompatibleVisualParentIsRejectedWithoutPromotionOrLocalChanges() {
        try (Fixture f = new Fixture()) {
            int parent = f.node(1, 0), child = f.node(2, 0);
            f.world.getMapper(GameObjectComponent.class).remove(parent);
            TransformComponent p = f.world.getMapper(TransformComponent.class).get(parent);
            p.scaleX = -1;
            f.process();
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.reparent(f.world, child, parent));
            Assert.assertFalse(f.world.getMapper(GameObjectComponent.class).has(parent));
            Assert.assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(child));
            Assert.assertEquals(-1, p.scaleX, 0);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameObjectHierarchySystem hierarchy = new GameObjectHierarchySystem();
        final GameObjectCompositionSystem composition = new GameObjectCompositionSystem(null);
        final World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(32), hierarchy, composition).build());
        final IdentityRegistry identities = new IdentityRegistry();

        Fixture() {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 100;
            identities.bind(world, meta);
        }

        int node(int stableId, int parentStableId) {
            int entity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
            world.getMapper(TransformComponent.class).create(entity);
            world.getMapper(EntityIndexComponent.class).create(entity);
            world.getMapper(GameObjectComponent.class).create(entity);
            if (parentStableId > 0) {
                world.getMapper(GameObjectMemberComponent.class).create(entity).parentStableId = parentStableId;
            }
            return entity;
        }

        void process() {
            identities.rebuild();
            world.process();
        }

        @Override
        public void close() {
            world.dispose();
        }
    }
}
