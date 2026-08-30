package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
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
    public void emptyHierarchyBuildsAnEmptyTraversal() {
        world.process();

        Assert.assertEquals(0, hierarchy.topology().traversal.size);
    }

    @Test
    public void oneRootAndMultipleChildrenHaveDeterministicParentFirstTopology() {
        int root = entity(10, true, -1);
        int high = entity(30, false, 10);
        int low = entity(20, false, 10);

        world.process();

        GameObjectTopologyState state = hierarchy.topology();
        Assert.assertEquals(3, state.traversal.size);
        Assert.assertEquals(root, state.traversal.get(0));
        Assert.assertEquals(low, state.traversal.get(1));
        Assert.assertEquals(high, state.traversal.get(2));
        Assert.assertEquals(root, state.parentEntityId[low]);
        Assert.assertEquals(root, state.rootEntityId[high]);
        Assert.assertEquals(1, state.depth[low]);

        int rebuilds = hierarchy.rebuildCount();
        world.process();
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
        Assert.assertEquals(low, state.traversal.get(1));
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
    public void representativeHierarchyUsesOneDenseResolveWithoutFrameRebuilds() {
        entity(1, true, -1);
        for (int stableId = 2; stableId <= 1001; stableId++) {
            entity(stableId, false, 1);
        }
        world.process();
        int rebuilds = hierarchy.rebuildCount();

        for (int frame = 0; frame < 10; frame++) world.process();

        Assert.assertEquals(1001, hierarchy.topology().traversal.size);
        Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
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
}
