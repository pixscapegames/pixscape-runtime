package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.hierarchy.GameObjectCompositionState;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.IdentityRegistry;
import org.junit.Assert;
import org.junit.Test;

public class GameObjectRenderCompositionTest {

    @Test
    public void rootOrdersOneAtomicBlockAndNestedRootsExpandInTheirLocalSlot() {
        Fixture fixture = new Fixture(64);
        int bottom = fixture.standalone(1, 0, -1);
        int root = fixture.gameObject(10, -1, 1, 0);
        int high = fixture.member(11, 10, 1, 20);
        fixture.gameObject(12, 10, 3, 0);
        int nestedLeaf = fixture.member(13, 12, 3, -999);
        int low = fixture.member(14, 10, 2, -20);
        int vfx = fixture.vfx(1, -10);
        int[] tiled = fixture.tiledMap(30, 1, 10, 80L, 20L);
        int top = fixture.standalone(31, 2, -100);

        fixture.process();

        Assert.assertEquals(8, fixture.drawList.size);
        fixture.assertEntry(0, RenderSourceDomain.SOURCE_ECS, bottom);
        fixture.assertEntry(1, RenderSourceDomain.SOURCE_VFX, vfx);
        fixture.assertEntry(2, RenderSourceDomain.SOURCE_ECS, low);
        fixture.assertEntry(3, RenderSourceDomain.SOURCE_ECS, nestedLeaf);
        fixture.assertEntry(4, RenderSourceDomain.SOURCE_ECS, high);
        fixture.assertEntry(5, RenderSourceDomain.SOURCE_TILED, tiled[1]);
        fixture.assertEntry(6, RenderSourceDomain.SOURCE_TILED, tiled[0]);
        fixture.assertEntry(7, RenderSourceDomain.SOURCE_ECS, top);

        int gameObjectDescriptors = 0;
        for (int i = 0; i < fixture.drawList.composition().size; i++) {
            if (fixture.drawList.composition().sourceDomain[i]
                    == RenderSourceDomain.SOURCE_GAME_OBJECT) {
                gameObjectDescriptors++;
                Assert.assertEquals(root, fixture.drawList.composition().sourceIndex[i]);
            }
        }
        Assert.assertEquals(1, gameObjectDescriptors);
        GameObjectCompositionState composition = fixture.composition.state();
        Assert.assertEquals(1, composition.effectiveLayer[fixture.entityForSlot(low)]);
        Assert.assertEquals(1, composition.effectiveLayer[fixture.entityForSlot(nestedLeaf)]);
    }

    @Test
    public void mutationsVisibilityAndNestedBoundsResolveEveryFrameWithoutAuthoredRootBounds() {
        Fixture fixture = new Fixture(32);
        int before = fixture.standalone(1, 0, 5);
        int root = fixture.gameObject(10, -1, 1, 10);
        int first = fixture.member(11, 10, 7, 20);
        int nested = fixture.gameObject(12, 10, 9, 0);
        int second = fixture.member(13, 12, 8, -20);
        fixture.bounds(first, 1f, 2f, 4f, 6f);
        fixture.bounds(second, -3f, -2f, 2f, 3f);

        fixture.process();

        fixture.assertEcsOrder(before, second, first);
        GameObjectCompositionState state = fixture.composition.state();
        Assert.assertTrue(state.boundsResolved[root]);
        Assert.assertEquals(-3f, state.minX[root], 0f);
        Assert.assertEquals(-2f, state.minY[root], 0f);
        Assert.assertEquals(4f, state.maxX[root], 0f);
        Assert.assertEquals(6f, state.maxY[root], 0f);
        Assert.assertFalse(fixture.world.getMapper(AABBComponent.class).has(root));
        Assert.assertTrue(fixture.composition.contributesDrawableBounds(
                fixture.entityForSlot(first)));
        Assert.assertTrue(fixture.composition.contributesDrawableBounds(
                fixture.entityForSlot(second)));

        fixture.indexForEntity(fixture.entityForSlot(first)).zIndex = -30;
        fixture.process();
        fixture.assertEcsOrder(before, first, second);

        fixture.indexForEntity(root).layerIndex = 0;
        fixture.indexForEntity(root).zIndex = 0;
        fixture.process();
        fixture.assertEcsOrder(first, second, before);

        fixture.world.getMapper(VisibilityComponent.class).create(nested).visible = false;
        fixture.process();
        fixture.assertEcsOrder(first, before);
        Assert.assertFalse(fixture.composition.contributesDrawableBounds(
                fixture.entityForSlot(second)));
        Assert.assertEquals(1f, state.minX[root], 0f);
        Assert.assertEquals(2f, state.minY[root], 0f);
        Assert.assertEquals(4f, state.maxX[root], 0f);
        Assert.assertEquals(6f, state.maxY[root], 0f);

        fixture.world.getMapper(VisibilityComponent.class).create(root).visible = false;
        fixture.process();
        fixture.assertEcsOrder(before);
    }

    @Test
    public void representativeSceneKeepsHundredsOfBlocksAtomicWithoutTopologyRebuilds() {
        Fixture fixture = new Fixture(4096);
        final int rootCount = 300;
        final int childrenPerRoot = 8;
        int stableId = 1;
        for (int rootIndex = 0; rootIndex < rootCount; rootIndex++) {
            int rootStableId = stableId++;
            fixture.gameObject(rootStableId, -1, rootIndex & 1, rootIndex);
            for (int child = 0; child < childrenPerRoot; child++) {
                fixture.member(stableId++, rootStableId, child, childrenPerRoot - child);
            }
        }

        fixture.process();
        int rebuilds = fixture.hierarchy.rebuildCount();

        Assert.assertEquals(rootCount * childrenPerRoot, fixture.drawList.size);
        Assert.assertEquals(rootCount, fixture.drawList.composition().size);
        for (int i = 0; i < fixture.drawList.size; i += childrenPerRoot) {
            int firstEntity = fixture.entityForSlot(fixture.drawList.get(i));
            int root = fixture.hierarchy.topology().rootEntityId[firstEntity];
            for (int child = 1; child < childrenPerRoot; child++) {
                int entity = fixture.entityForSlot(fixture.drawList.get(i + child));
                Assert.assertEquals(root, fixture.hierarchy.topology().rootEntityId[entity]);
            }
        }

        fixture.process();
        Assert.assertEquals(rebuilds, fixture.hierarchy.rebuildCount());
        Assert.assertEquals(rootCount * childrenPerRoot, fixture.drawList.size);
    }

    @Test
    public void spatialPostPassCannotLeaveAnExternalEntryInsideAFlattenedBlock() {
        Fixture fixture = new Fixture(32);
        fixture.gameObject(10, -1, 0, 0);
        int first = fixture.member(11, 10, 0, -1);
        int second = fixture.member(12, 10, 0, 1);
        int external = fixture.standalone(20, 0, 10);
        fixture.process();

        fixture.drawList.set(0, RenderSourceDomain.SOURCE_ECS, first);
        fixture.drawList.set(1, RenderSourceDomain.SOURCE_ECS, external);
        fixture.drawList.set(2, RenderSourceDomain.SOURCE_ECS, second);
        fixture.spatial.restoreGameObjectAtomicity();

        fixture.assertEcsOrder(first, second, external);
    }

    @Test
    public void rootTranslationAndRotationUpdateTheDerivedDescendantUnion() {
        Fixture fixture = new Fixture(32);
        int root = fixture.gameObject(10, -1, 0, 0);
        int childSlot = fixture.member(11, 10, 0, 0);
        int child = fixture.entityForSlot(childSlot);
        TransformComponent rootTransform = fixture.world.getMapper(TransformComponent.class).get(root);
        rootTransform.x = 10f;
        rootTransform.y = 20f;
        rootTransform.rotationRad = (float) (Math.PI * 0.5);
        fixture.world.getMapper(TransformComponent.class).get(child).x = 2f;
        DimensionsComponent dimensions = fixture.world.getMapper(DimensionsComponent.class).create(child);
        dimensions.width = 2f;
        dimensions.height = 1f;
        fixture.world.getMapper(OrientedBoundsComponent.class).create(child);
        fixture.world.getMapper(AABBComponent.class).create(child);

        fixture.process();

        GameObjectCompositionState state = fixture.composition.state();
        Assert.assertEquals(9f, state.minX[root], 0.0001f);
        Assert.assertEquals(22f, state.minY[root], 0.0001f);
        Assert.assertEquals(10f, state.maxX[root], 0.0001f);
        Assert.assertEquals(24f, state.maxY[root], 0.0001f);

        rootTransform.x = 20f;
        rootTransform.y = 5f;
        rootTransform.rotationRad = 0f;
        fixture.dirty.markDirty().transform(root).position().rotation().commit();
        fixture.process();

        Assert.assertEquals(22f, state.minX[root], 0.0001f);
        Assert.assertEquals(5f, state.minY[root], 0.0001f);
        Assert.assertEquals(24f, state.maxX[root], 0.0001f);
        Assert.assertEquals(6f, state.maxY[root], 0.0001f);
    }

    private static final class Fixture {
        final DynamicEntityRenderState ecsState;
        final TiledMapRenderState tiledState;
        final VfxRenderState vfxState;
        final LayerStateSOA layerState;
        final DrawList drawList;
        final GameObjectHierarchySystem hierarchy;
        final GameObjectCompositionSystem composition;
        final SpatialRenderOrderSystem spatial;
        final DirtyTrackerSystem dirty;
        final World world;
        final IdentityRegistry identities = new IdentityRegistry();

        Fixture(int capacity) {
            ecsState = new DynamicEntityRenderState(capacity);
            tiledState = new TiledMapRenderState(capacity);
            vfxState = new VfxRenderState(capacity);
            layerState = new LayerStateSOA(4);
            drawList = new DrawList(capacity);
            for (int i = 0; i < layerState.capacity(); i++) layerState.enabled[i] = true;
            hierarchy = new GameObjectHierarchySystem(capacity);
            composition = new GameObjectCompositionSystem(ecsState, capacity);
            spatial = new SpatialRenderOrderSystem(ecsState, tiledState, drawList);
            dirty = new DirtyTrackerSystem(capacity);
            world = new World(new WorldConfigurationBuilder()
                    .with(dirty, hierarchy, new UpdateWorldGeometrySystem(), composition,
                            new RenderBuildDrawListSystem(ecsState, tiledState, vfxState,
                                    layerState, drawList, new RenderStats(), capacity,
                                    0, capacity),
                            new RenderSortSystem(ecsState, tiledState, vfxState, drawList,
                                    0, capacity),
                            spatial,
                            new DirtyFlushSystem())
                    .build());
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = capacity * 4;
            identities.bind(world, meta);
        }

        int gameObject(int stableId, int parentStableId, int layer, int z) {
            int entityId = baseEntity(stableId, layer, z);
            world.getMapper(GameObjectComponent.class).create(entityId);
            if (parentStableId > 0) {
                world.getMapper(GameObjectMemberComponent.class).create(entityId)
                        .parentStableId = parentStableId;
            }
            return entityId;
        }

        int member(int stableId, int parentStableId, int layer, int z) {
            int entityId = baseEntity(stableId, layer, z);
            world.getMapper(GameObjectMemberComponent.class).create(entityId)
                    .parentStableId = parentStableId;
            return spriteSlot(entityId, layer, z, stableId);
        }

        int standalone(int stableId, int layer, int z) {
            int entityId = baseEntity(stableId, layer, z);
            return spriteSlot(entityId, layer, z, stableId);
        }

        private int baseEntity(int stableId, int layer, int z) {
            int entityId = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entityId).stableId = stableId;
            world.getMapper(TransformComponent.class).create(entityId);
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entityId);
            index.layerIndex = layer;
            index.zIndex = z;
            return entityId;
        }

        private int spriteSlot(int entityId, int layer, int z, int tie) {
            int slot = ecsState.acquireSlotForEntity(entityId);
            ecsState.kind[slot] = RenderKind.SPRITE;
            ecsState.enabled[slot] = true;
            ecsState.visible[slot] = true;
            ecsState.layerIndex[slot] = layer;
            ecsState.z[slot] = z;
            ecsState.blend[slot] = BlendMode.ALPHA.id;
            ecsState.textureHandle[slot] = tie;
            ecsState.sortKey[slot] = SortKey64.packOrdered(
                    BlendMode.PASS_ORDERED, 0, 0, 0, layer, z, tie);
            return slot;
        }

        int vfx(int layer, int z) {
            return vfxState.addParticleQuad(1, 0, BlendMode.ALPHA.id, layer, z,
                    0, 0, SortKey64.packOrdered(BlendMode.PASS_ORDERED, 0, 0, 0,
                            layer, z, 1),
                    0, 0, 1, 0, 1, 1, 0, 1,
                    0, 0, 1, 1, 1, RenderRepeatFlags.NONE, 0);
        }

        int[] tiledMap(int entityId, int layer, int z, long... internalKeys) {
            int start = tiledState.getVisibleRefCount();
            int[] refs = new int[internalKeys.length];
            for (int i = 0; i < refs.length; i++) {
                int ref = tiledState.registerRef();
                refs[i] = ref;
                tiledState.setRenderDataForRef(ref, i + 1, 0, BlendMode.ALPHA.id,
                        0, 0, 0, internalKeys[i],
                        0, 0, 1, 0, 1, 1, 0, 1,
                        0, 0, 1, 1, 1, 1, RenderRepeatFlags.NONE);
                tiledState.addVisibleRef(ref);
            }
            long key = SortKey64.packOrdered(BlendMode.PASS_ORDERED, 0, 0, 0,
                    layer, z, entityId);
            tiledState.addVisibleMap(entityId, layer, z, key, start, refs.length);
            return refs;
        }

        void bounds(int slot, float minX, float minY, float maxX, float maxY) {
            int entityId = entityForSlot(slot);
            AABBComponent bounds = world.getMapper(AABBComponent.class).create(entityId);
            bounds.minX = minX;
            bounds.minY = minY;
            bounds.maxX = maxX;
            bounds.maxY = maxY;
        }

        EntityIndexComponent indexForEntity(int entityId) {
            return world.getMapper(EntityIndexComponent.class).get(entityId);
        }

        int entityForSlot(int slot) {
            return ecsState.entityIdForSlot(slot);
        }

        void process() {
            world.process();
        }

        void assertEntry(int index, byte domain, int source) {
            Assert.assertEquals(domain, drawList.getDomain(index));
            Assert.assertEquals(source, drawList.get(index));
        }

        void assertEcsOrder(int... slots) {
            Assert.assertEquals(slots.length, drawList.size);
            for (int i = 0; i < slots.length; i++) {
                assertEntry(i, RenderSourceDomain.SOURCE_ECS, slots[i]);
            }
        }
    }
}
