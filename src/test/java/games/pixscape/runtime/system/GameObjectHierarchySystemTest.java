package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.hierarchy.GameObjectTopologyState;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GameObjectHierarchySystemTest {
    private static final float EPSILON = 0.0001f;

    private World world;
    private IdentityRegistry identities;
    private GameObjectHierarchySystem hierarchy;

    @Before
    public void setUp() {
        hierarchy = new GameObjectHierarchySystem(4);
        world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(16), hierarchy, new DirtyFlushSystem())
                .build());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 20000;
        identities = new IdentityRegistry();
        identities.bind(world, meta);
    }

    @After
    public void tearDown() {
        identities.bind(null, null);
        world.dispose();
    }

    @Test
    public void directReparentBetweenTwoSynchronizationPhasesIsObservedWithoutDirtySignal() {
        int first = entity(10, true, -1);
        int second = entity(20, true, -1);
        int child = entity(30, false, 10);
        world.process();
        hierarchy.ensureCurrentTopology();
        Assert.assertEquals(first, hierarchy.topology().parentEntityId[child]);
        world.getMapper(GameObjectMemberComponent.class).get(child).parentStableId = 20;
        hierarchy.ensureCurrentTopology();
        Assert.assertEquals(second, hierarchy.topology().parentEntityId[child]);
    }

    @Test
    public void worldPreservingReparentAndDetachKeepDistantDescendants() {
        int outer = entity(1, true, -1), group = entity(2, true, 1);
        int leaf = entity(3, false, 2), destination = entity(4, true, -1);
        TransformComponent rootPose = world.getMapper(TransformComponent.class).get(outer);
        rootPose.x = 21;
        rootPose.originX = 7;
        rootPose.rotationRad = .3f;
        TransformComponent groupPose = world.getMapper(TransformComponent.class).get(group);
        groupPose.originY = 9;
        groupPose.rotationRad = .3f;
        TransformComponent leafPose = world.getMapper(TransformComponent.class).get(leaf);
        leafPose.x = 10000;
        leafPose.scaleX = -2;
        leafPose.scaleY = 3;
        TransformComponent destinationPose = world.getMapper(TransformComponent.class).get(destination);
        destinationPose.x = -53;
        destinationPose.originY = 11;
        destinationPose.rotationRad = -.7f;
        destinationPose.scaleX = destinationPose.scaleY = 2;
        identities.rebuild();
        world.process();
        float x = hierarchy.worldTransforms().x[leaf], y = hierarchy.worldTransforms().y[leaf];
        for (int cycle = 0; cycle < 12; cycle++) {
            games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.reparent(world, group, destination);
            assertResolvedPosition(leaf, x, y, .008f);
            world.process();
            assertResolvedPosition(leaf, x, y, .008f);
            games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.reparent(world, group, -1);
            assertResolvedPosition(leaf, x, y, .008f);
            world.process();
            assertResolvedPosition(leaf, x, y, .008f);
            Assert.assertEquals(-2, leafPose.scaleX, 0);
            Assert.assertEquals(3, leafPose.scaleY, 0);
            Assert.assertEquals(2, world.getMapper(GameObjectMemberComponent.class).get(leaf).parentStableId);
        }
    }

    private void assertResolvedPosition(int entity, float x, float y, float tolerance) {
        Assert.assertEquals(x, hierarchy.worldTransforms().x[entity], tolerance);
        Assert.assertEquals(y, hierarchy.worldTransforms().y[entity], tolerance);
    }

    @Test
    public void preparedLocalMutationKeepsLocalWhileWorldPreservingOperationConvertsIt() {
        int first = entity(1, true, -1), second = entity(2, true, -1), child = entity(3, false, 1);
        world.getMapper(TransformComponent.class).get(first).x = 10;
        world.getMapper(TransformComponent.class).get(second).x = 30;
        TransformComponent local = world.getMapper(TransformComponent.class).get(child);
        local.x = 2;
        identities.rebuild();
        world.process();
        games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.applyPreparedLocalPose(world, child, 2, local);
        Assert.assertEquals(2, local.x, 0);
        // Reads the pending relationship, not the still-published x=12 pose.
        Assert.assertEquals(32, games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation
                .captureWorldPose(world, child).x, 0);
        world.process();
        games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.reparent(world, child, first);
        Assert.assertEquals(22, local.x, 0);
        assertResolvedPosition(child, 32, 0, 0);
    }

    @Test
    public void incompatibleProspectiveParentsRejectWithoutPromotionOrLocalMutation() {
        int child = entity(1, false, -1), parent = entity(2, false, -1);
        TransformComponent local = world.getMapper(TransformComponent.class).get(child);
        local.x = 17;
        TransformComponent parentPose = world.getMapper(TransformComponent.class).get(parent);
        parentPose.rotationRad = .7f;
        identities.rebuild();
        for (float[] scale : new float[][]{{0, 0}, {-1, -1}, {2, 3}}) {
            parentPose.scaleX = scale[0];
            parentPose.scaleY = scale[1];
            world.process();
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation.reparent(world, child, parent));
            Assert.assertEquals(17, local.x, 0);
            Assert.assertFalse(world.getMapper(GameObjectComponent.class).has(parent));
            Assert.assertFalse(world.getMapper(GameObjectMemberComponent.class).has(child));
            assertResolvedPosition(child, 17, 0, 0);
        }
    }

    @Test
    public void navigationExcludesTiledOwnershipButKeepsOrdinaryObjectsInObjectLayers() throws Exception {
        int map = entity(1, true, -1);
        TiledMapComponent mapData = world.getMapper(TiledMapComponent.class).create(map);
        mapData.projection = games.pixscape.runtime.tiled.TiledProjection.ORTHO;
        mapData.width = mapData.height = 1;
        mapData.tileWidth = mapData.tileHeight = 32;
        int objects = entity(2, true, 1);
        world.getMapper(TiledMapLayerComponent.class).create(objects).kind = TiledMapLayerComponent.Kind.OBJECT;
        int object = entity(3, false, 2);
        world.process();
        games.pixscape.runtime.engine.PixscapeEngine engine = new games.pixscape.runtime.engine.PixscapeEngine();
        java.lang.reflect.Field field = engine.getClass().getDeclaredField("world");
        field.setAccessible(true);
        field.set(engine, world);
        var entities = engine.api().entities();
        var mapRef = entities.ofEntityId(map);
        var layerRef = entities.ofEntityId(objects);
        Assert.assertNull(mapRef.parent());
        Assert.assertEquals(0, mapRef.children().size);
        Assert.assertEquals(0, mapRef.descendants().size);
        Assert.assertNull(layerRef.parent());
        Assert.assertEquals(object, layerRef.children().first().entityId());
        Assert.assertEquals(1, layerRef.descendants().size);
        Assert.assertEquals(objects, entities.ofEntityId(object).parent().entityId());
    }

    @Test
    public void emptyHierarchyBuildsAnEmptyTraversal() {
        world.process();

        Assert.assertEquals(0, hierarchy.topology().traversalSize);
    }

    @Test
    public void oneRootAndMultipleChildrenHaveDeterministicParentFirstTopology() {
        int root = entity(10, true, -1);
        int high = entity(30, false, 10);
        int low = entity(20, false, 10);

        world.process();

        GameObjectTopologyState state = hierarchy.topology();
        Assert.assertEquals(3, state.traversalSize);
        Assert.assertEquals(root, state.firstTraversalEntityId);
        Assert.assertEquals(low, state.nextTraversalEntityId[root]);
        Assert.assertEquals(high, state.nextTraversalEntityId[low]);
        Assert.assertEquals(root, state.parentEntityId[low]);
        Assert.assertEquals(root, state.rootEntityId[high]);
        Assert.assertEquals(1, state.depth[low]);

        int rebuilds = hierarchy.rebuildCount();
        world.process();
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
        Assert.assertEquals(low, state.nextTraversalEntityId[root]);
    }

    @Test
    public void nestedRootsResolveImmediateParentTopRootAndDepth() {
        int root = entity(1, true, -1);
        int nestedRoot = entity(2, true, 1);
        int child = entity(3, false, 2);

        world.process();

        GameObjectTopologyState state = hierarchy.topology();
        Assert.assertEquals(root, state.rootEntityId[nestedRoot]);
        Assert.assertEquals(root, state.rootEntityId[child]);
        Assert.assertEquals(nestedRoot, state.parentEntityId[child]);
        Assert.assertEquals(2, state.depth[child]);
    }

    @Test
    public void deepHierarchyIsResolvedWithoutRecursiveTraversal() {
        int previousStableId = 1;
        entity(previousStableId, true, -1);
        int leaf = -1;
        for (int stableId = 2; stableId <= 512; stableId++) {
            leaf = entity(stableId, stableId < 512, previousStableId);
            world.getMapper(TransformComponent.class).get(leaf).x = 1f;
            previousStableId = stableId;
        }

        world.process();

        Assert.assertEquals(511, hierarchy.topology().depth[leaf]);
        Assert.assertEquals(511f, hierarchy.worldTransforms().x[leaf], EPSILON);
    }

    @Test
    public void structuralMembershipMutationsRebuildTopology() {
        int firstRoot = entity(1, true, -1);
        int secondRoot = entity(2, true, -1);
        int child = entity(3, false, -1);
        world.process();
        int initial = hierarchy.rebuildCount();

        world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 1;
        world.process();
        Assert.assertEquals(initial + 1, hierarchy.rebuildCount());
        Assert.assertEquals(firstRoot, hierarchy.topology().parentEntityId[child]);

        world.getMapper(GameObjectMemberComponent.class).get(child).parentStableId = 2;
        world.process();
        Assert.assertEquals(initial + 2, hierarchy.rebuildCount());
        Assert.assertEquals(secondRoot, hierarchy.topology().parentEntityId[child]);

        world.getMapper(GameObjectMemberComponent.class).remove(child);
        world.process();
        Assert.assertEquals(initial + 3, hierarchy.rebuildCount());
        Assert.assertFalse(hierarchy.topology().parented[child]);
    }

    @Test
    public void deletedEntityStateIsClearedAndEcsIdReuseDoesNotLeakOwnership() {
        entity(1, true, -1);
        int child = entity(2, false, 1);
        world.process();
        Assert.assertTrue(hierarchy.worldTransforms().isResolved(child));

        world.delete(child);
        world.process();
        Assert.assertFalse(hierarchy.worldTransforms().isResolved(child));

        int replacement = entity(3, false, -1);
        world.getMapper(TransformComponent.class).get(replacement).x = 77f;
        world.process();
        Assert.assertEquals(child, replacement);
        Assert.assertFalse(hierarchy.topology().parented[replacement]);
        Assert.assertEquals(77f, hierarchy.worldTransforms().x[replacement], EPSILON);
    }

    @Test
    public void standaloneWorldStateEqualsAuthoredTransformAndSurvivesGrowth() {
        int standalone = entity(1, false, -1);
        TransformComponent authored = world.getMapper(TransformComponent.class).get(standalone);
        authored.x = 4f;
        authored.y = -3f;
        authored.rotationRad = 0.25f;
        authored.scaleX = 2f;
        authored.scaleY = -1f;
        for (int i = 2; i < 80; i++) entity(i, false, -1);

        world.process();

        WorldTransformState state = hierarchy.worldTransforms();
        Assert.assertEquals(authored.x, state.x[standalone], 0f);
        Assert.assertEquals(authored.y, state.y[standalone], 0f);
        Assert.assertEquals(authored.rotationRad, state.rotationRad[standalone], 0f);
        Assert.assertEquals(authored.scaleX, state.scaleX[standalone], 0f);
        Assert.assertTrue(state.getEntityCapacity() >= 80);
    }

    @Test
    public void deletionUnlinksNestedSubtreesAndPreservesSurvivingTraversalWithoutRebuild() {
        int root = entity(10, true, -1);
        int first = entity(20, false, 10);
        int nested = entity(30, true, 10);
        int nestedChild = entity(40, false, 30);
        int last = entity(50, false, 10);
        int other = entity(60, true, -1);
        world.getMapper(TransformComponent.class).get(root).x = 10;
        world.getMapper(TransformComponent.class).get(last).x = 7;
        world.process();
        int rebuilds = hierarchy.rebuildCount();

        world.delete(nestedChild);
        world.delete(first);
        world.delete(nested);
        world.process();

        GameObjectTopologyState state = hierarchy.topology();
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
        Assert.assertEquals(3, state.traversalSize);
        Assert.assertEquals(root, state.firstTraversalEntityId);
        Assert.assertEquals(last, state.nextTraversalEntityId[root]);
        Assert.assertEquals(other, state.nextTraversalEntityId[last]);
        Assert.assertEquals(other, state.lastTraversalEntityId);
        Assert.assertEquals(last, state.previousTraversalEntityId[other]);
        Assert.assertEquals(last, state.firstChildEntityId[root]);
        Assert.assertEquals(-1, state.previousSiblingEntityId[last]);
        Assert.assertEquals(-1, state.nextSiblingEntityId[last]);
        Assert.assertEquals(17f, hierarchy.worldTransforms().x[last], EPSILON);
        Assert.assertFalse(hierarchy.worldTransforms().isResolved(nestedChild));
        Assert.assertEquals(-1, identities.findByStableId(30));

        world.delete(root);
        world.delete(last);
        world.process();
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
        Assert.assertEquals(1, state.traversalSize);
        Assert.assertEquals(other, state.firstTraversalEntityId);
        Assert.assertEquals(-1, state.previousTraversalEntityId[other]);
        world.delete(other);
        world.process();
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
        Assert.assertEquals(0, state.traversalSize);
        Assert.assertEquals(-1, state.firstTraversalEntityId);
        Assert.assertEquals(-1, state.lastTraversalEntityId);
    }

    @Test
    public void deletingAParentAloneStillRejectsSurvivingOrphans() {
        int root = entity(1, true, -1);
        entity(2, false, 1);
        world.process();

        world.delete(root);

        Assert.assertThrows(RuntimeException.class, () -> world.process());
    }

    @Test
    public void preparedCreateDeleteBeforeSynchronizationClearsTraversalAndAllowsIdReuse() {
        world.process();
        int rebuilds = hierarchy.rebuildCount();
        int root = entity(1, true, -1);
        int child = entity(2, false, 1);
        identities.indexEntityImmediately(root);
        identities.indexEntityImmediately(child);
        hierarchy.registerPreparedSpawn(root);
        hierarchy.registerPreparedSpawn(child);
        world.delete(root);
        world.delete(child);
        world.process();
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
        Assert.assertEquals(0, hierarchy.topology().traversalSize);

        int replacement = entity(3, false, -1);
        Assert.assertEquals(root, replacement);
        identities.indexEntityImmediately(replacement);
        hierarchy.registerPreparedSpawn(replacement);
        world.process();
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
        Assert.assertEquals(1, hierarchy.topology().traversalSize);
        Assert.assertFalse(hierarchy.topology().parented[replacement]);
        Assert.assertEquals(-1, hierarchy.topology().firstChildEntityId[replacement]);
        Assert.assertTrue(hierarchy.worldTransforms().isResolved(replacement));
    }

    @Test
    public void combinedChainResolvesWorldWithoutRewritingAuthoredLocalTransforms() {
        int root = entity(1, true, -1);
        int nested = entity(2, true, 1);
        int child = entity(3, false, 2);
        TransformComponent rootTransform = world.getMapper(TransformComponent.class).get(root);
        rootTransform.x = 10f;
        rootTransform.y = 20f;
        rootTransform.rotationRad = (float) (Math.PI * 0.5);
        rootTransform.scaleX = rootTransform.scaleY = 2f;
        TransformComponent nestedLocal = world.getMapper(TransformComponent.class).get(nested);
        nestedLocal.x = 3f;
        nestedLocal.y = 0f;
        nestedLocal.rotationRad = (float) (Math.PI * 0.5);
        nestedLocal.scaleX = nestedLocal.scaleY = 0.5f;
        TransformComponent childLocal = world.getMapper(TransformComponent.class).get(child);
        childLocal.x = 4f;
        childLocal.y = 0f;
        childLocal.rotationRad = 0.2f;
        childLocal.scaleX = 3f;
        childLocal.scaleY = -2f;
        childLocal.originX = 7f;
        childLocal.originY = 8f;

        world.process();

        WorldTransformState state = hierarchy.worldTransforms();
        Assert.assertEquals(6f, state.x[child], EPSILON);
        Assert.assertEquals(26f, state.y[child], EPSILON);
        Assert.assertEquals((float) Math.PI + 0.2f, state.rotationRad[child], EPSILON);
        Assert.assertEquals(3f, state.scaleX[child], EPSILON);
        Assert.assertEquals(-2f, state.scaleY[child], EPSILON);
        Assert.assertEquals(4f, childLocal.x, 0f);
        Assert.assertEquals(0f, childLocal.y, 0f);
        Assert.assertEquals(0.2f, childLocal.rotationRad, 0f);
        Assert.assertEquals(7f, childLocal.originX, 0f);
        Assert.assertEquals(8f, childLocal.originY, 0f);
    }

    @Test
    public void rootOriginControlsChildrenAndNestedOriginUsesTheSameContract() {
        int root = entity(1, true, -1);
        int nested = entity(2, true, 1);
        int child = entity(3, false, 2);
        TransformComponent rootTransform = world.getMapper(TransformComponent.class).get(root);
        rootTransform.x = 10f;
        rootTransform.y = 20f;
        rootTransform.originX = 5f;
        rootTransform.originY = 7f;
        rootTransform.rotationRad = (float) (Math.PI * 0.5);
        TransformComponent nestedTransform = world.getMapper(TransformComponent.class).get(nested);
        nestedTransform.x = 5f;
        nestedTransform.y = 7f;
        nestedTransform.originX = 2f;
        nestedTransform.originY = 3f;
        nestedTransform.rotationRad = (float) (Math.PI * 0.5);
        TransformComponent childTransform = world.getMapper(TransformComponent.class).get(child);
        childTransform.x = 2f;
        childTransform.y = 3f;

        world.process();

        WorldTransformState state = hierarchy.worldTransforms();
        Assert.assertEquals(10f, state.x[nested], EPSILON);
        Assert.assertEquals(26f, state.y[nested], EPSILON);
        Assert.assertEquals(12f, state.x[child], EPSILON);
        Assert.assertEquals(29f, state.y[child], EPSILON);
        Assert.assertEquals(5f, nestedTransform.x, 0f);
        Assert.assertEquals(7f, nestedTransform.y, 0f);
        Assert.assertEquals(2f, nestedTransform.originX, 0f);
        Assert.assertEquals(3f, nestedTransform.originY, 0f);
    }

    @Test
    public void rootAndLeafMutationsUpdateOnlyExpectedResolvedValues() {
        int root = entity(1, true, -1);
        int first = entity(2, false, 1);
        int second = entity(3, false, 1);
        world.getMapper(TransformComponent.class).get(first).x = 2f;
        world.getMapper(TransformComponent.class).get(second).x = 5f;
        world.process();

        world.getMapper(TransformComponent.class).get(root).x = 10f;
        world.process();
        Assert.assertEquals(12f, hierarchy.worldTransforms().x[first], EPSILON);
        Assert.assertEquals(15f, hierarchy.worldTransforms().x[second], EPSILON);

        world.getMapper(TransformComponent.class).get(first).x = 7f;
        world.process();
        Assert.assertEquals(17f, hierarchy.worldTransforms().x[first], EPSILON);
        Assert.assertEquals(15f, hierarchy.worldTransforms().x[second], EPSILON);
    }

    @Test
    public void representativeHierarchyResolvesWithoutFrameRebuilds() {
        entity(1, true, -1);
        for (int stableId = 2; stableId <= 1001; stableId++) {
            entity(stableId, false, 1);
        }
        world.process();
        int rebuilds = hierarchy.rebuildCount();

        for (int frame = 0; frame < 10; frame++) world.process();

        Assert.assertEquals(1001, hierarchy.topology().traversalSize);
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
    }

    @Test
    public void physicsDescendantQueryExcludesOwnBodyAndTraversesOnlyTheTargetSubtree() {
        int root = entity(1, true, -1);
        int ownBodyRoot = entity(2, true, -1);
        int directBody = entity(3, false, 1);
        int nested = entity(4, true, 1);
        int deepBody = entity(5, false, 4);
        int unrelated = entity(6, true, -1);
        world.getMapper(PhysicsBodyComponent.class).create(ownBodyRoot);
        world.getMapper(PhysicsBodyComponent.class).create(directBody);
        world.getMapper(PhysicsBodyComponent.class).create(deepBody);

        world.process();

        Assert.assertTrue(hierarchy.containsPhysicsInSubtree(ownBodyRoot));
        Assert.assertFalse(hierarchy.containsPhysicsInDescendants(ownBodyRoot));
        Assert.assertTrue(hierarchy.containsPhysicsInDescendants(root));
        Assert.assertTrue(hierarchy.containsPhysicsInDescendants(nested));
        Assert.assertFalse(hierarchy.containsPhysicsInDescendants(unrelated));
    }

    @Test
    public void resolvedWorldPoseFeedsGeometryInTheSameFrameAndPreservesOriginContract() {
        identities.bind(null, null);
        world.dispose();
        hierarchy = new GameObjectHierarchySystem(8);
        world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(16), hierarchy,
                        new UpdateWorldGeometrySystem(), new DirtyFlushSystem())
                .build());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        identities = new IdentityRegistry();
        identities.bind(world, meta);

        int root = entity(1, true, -1);
        int child = entity(2, false, 1);
        TransformComponent rootTransform = world.getMapper(TransformComponent.class).get(root);
        rootTransform.x = 10f;
        rootTransform.y = 20f;
        rootTransform.rotationRad = (float) (Math.PI * 0.5);
        rootTransform.scaleX = rootTransform.scaleY = 2f;
        TransformComponent local = world.getMapper(TransformComponent.class).get(child);
        local.x = 3f;
        local.originX = 1f;
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(child);
        dimensions.width = 4f;
        dimensions.height = 2f;
        world.getMapper(OrientedBoundsComponent.class).create(child);
        AABBComponent aabb = world.getMapper(AABBComponent.class).create(child);

        world.process();

        Assert.assertEquals(6f, aabb.minX, EPSILON);
        Assert.assertEquals(24f, aabb.minY, EPSILON);
        Assert.assertEquals(10f, aabb.maxX, EPSILON);
        Assert.assertEquals(32f, aabb.maxY, EPSILON);
        Assert.assertEquals(3f, local.x, 0f);
        Assert.assertEquals(1f, local.originX, 0f);
    }

    private int entity(int stableId, boolean gameObject, int parentStableId) {
        int entityId = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entityId).stableId = stableId;
        world.getMapper(TransformComponent.class).create(entityId);
        world.getMapper(EntityIndexComponent.class).create(entityId);
        if (gameObject) world.getMapper(GameObjectComponent.class).create(entityId);
        if (parentStableId > 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entityId)
                    .parentStableId = parentStableId;
        }
        return entityId;
    }

    @Test
    public void structuralRebuildPreservesUnchangedPosesAndPropagatesReparenting() {
        int root = entity(1, true, -1), other = entity(2, true, -1);
        int nested = entity(3, true, 1), leaf = entity(4, false, 3);
        int unchanged = entity(5, false, -1);
        world.getMapper(TransformComponent.class).get(root).x = 10;
        world.getMapper(TransformComponent.class).get(other).x = 30;
        world.getMapper(TransformComponent.class).get(leaf).x = 2;
        world.process();
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        world.getSystem(DirtyFlushSystem.class).setEnabled(false);
        int rebuilds = hierarchy.rebuildCount();

        world.getMapper(GameObjectMemberComponent.class).get(nested).parentStableId = 2;
        world.process();
        Assert.assertEquals(rebuilds + 1, hierarchy.rebuildCount());
        Assert.assertEquals(32f, hierarchy.worldTransforms().x[leaf], EPSILON);
        Assert.assertNotEquals(0, dirty.geomSub(nested));
        Assert.assertNotEquals(0, dirty.geomSub(leaf));
        Assert.assertEquals(0, dirty.geomSub(root));
        Assert.assertEquals(0, dirty.geomSub(other));
        Assert.assertEquals(0, dirty.geomSub(unchanged));

        dirty.clearFrame();
        world.getMapper(TransformComponent.class).get(other).x = 40;
        dirty.geometry(other, games.pixscape.runtime.render.GeometryDirty.POSITION);
        world.process();
        Assert.assertEquals(42f, hierarchy.worldTransforms().x[leaf], EPSILON);
        Assert.assertNotEquals(0, dirty.geomSub(leaf));
        Assert.assertEquals(0, dirty.geomSub(unchanged));
    }

    @Test
    public void removedTransformCannotSupplyAResolvedPoseOnReintroduction() {
        int entity = entity(1, false, -1);
        world.getMapper(TransformComponent.class).get(entity).x = 17;
        world.process();
        world.getMapper(TransformComponent.class).remove(entity);
        world.process();
        Assert.assertFalse(hierarchy.worldTransforms().isResolved(entity));
        Assert.assertEquals(0, hierarchy.topology().traversalSize);

        world.getSystem(DirtyFlushSystem.class).setEnabled(false);
        // Even an equal numeric pose is newly resolved after the component's absence.
        world.getMapper(TransformComponent.class).create(entity).x = 17;
        world.process();
        Assert.assertTrue(hierarchy.worldTransforms().isResolved(entity));
        Assert.assertNotEquals(0, world.getSystem(DirtyTrackerSystem.class).geomSub(entity));
        Assert.assertEquals(17f, hierarchy.worldTransforms().x[entity], EPSILON);
    }

    @Test
    public void recycledIdAndSameFramePreparedDeletionDiscardResolvedState() {
        int old = entity(1, false, -1);
        world.getMapper(TransformComponent.class).get(old).x = 17;
        world.process();
        world.delete(old);
        world.process();
        Assert.assertFalse(hierarchy.worldTransforms().isResolved(old));
        int replacement = entity(2, false, -1);
        Assert.assertEquals(old, replacement);
        identities.indexEntityImmediately(replacement);
        hierarchy.registerPreparedSpawn(replacement);
        hierarchy.resolveEntityFromCurrentAuthoredTransform(replacement);
        Assert.assertEquals(0f, hierarchy.worldTransforms().x[replacement], EPSILON);
        world.delete(replacement);
        world.process();
        Assert.assertFalse(hierarchy.worldTransforms().isResolved(replacement));
        Assert.assertEquals(0, hierarchy.topology().traversalSize);
        int finalEntity = entity(3, false, -1);
        Assert.assertEquals(old, finalEntity);
        world.getMapper(TransformComponent.class).get(finalEntity).x = 81;
        world.process();
        Assert.assertEquals(81f, hierarchy.worldTransforms().x[finalEntity], EPSILON);
    }
}
