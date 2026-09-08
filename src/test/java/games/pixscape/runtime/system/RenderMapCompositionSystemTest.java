package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderMapCompositionSystemTest {

    @Test
    public void spriteMapSpriteUsesMapZAndKeepsAllRefsContiguous() {
        Fixture fixture = new Fixture();
        int topSprite = fixture.addSprite(30, 30, BlendMode.ALPHA, 3);
        int bottomSprite = fixture.addSprite(10, 10, BlendMode.ALPHA, 1);
        int[] mapRefs = fixture.addMap(20, 20, 90L, 10L, 50L);

        fixture.process();

        fixture.assertEntry(0, RenderSourceDomain.SOURCE_ECS, bottomSprite);
        fixture.assertEntry(1, RenderSourceDomain.SOURCE_TILED, mapRefs[1]);
        fixture.assertEntry(2, RenderSourceDomain.SOURCE_TILED, mapRefs[2]);
        fixture.assertEntry(3, RenderSourceDomain.SOURCE_TILED, mapRefs[0]);
        fixture.assertEntry(4, RenderSourceDomain.SOURCE_ECS, topSprite);
    }

    @Test
    public void twoMapsOrderAsCompleteBlocksInBothZDirections() {
        Fixture fixture = new Fixture();
        int[] mapA = fixture.addMap(10, 10, 80L, 20L);
        int[] mapB = fixture.addMap(20, 20, 70L, 10L);

        fixture.process();
        fixture.assertTiledOrder(mapA[1], mapA[0], mapB[1], mapB[0]);

        fixture.tiledState.clearVisibleSlots();
        mapA = fixture.addMap(10, 30, 80L, 20L);
        mapB = fixture.addMap(20, 5, 70L, 10L);
        fixture.process();
        fixture.assertTiledOrder(mapB[1], mapB[0], mapA[1], mapA[0]);
    }

    @Test
    public void sameZMapsUseEntityTieAndExactCollisionsRemainStableByGroup() {
        Fixture fixture = new Fixture();
        int[] highTie = fixture.addMap(7, 20, 1L, 2L);
        int[] lowTie = fixture.addMap(3, 20, 3L, 4L);

        fixture.process();
        fixture.assertTiledOrder(lowTie[0], lowTie[1], highTie[0], highTie[1]);

        fixture.tiledState.clearVisibleSlots();
        int[] firstCollision = fixture.addMap(1, 20, 9L, 10L);
        int[] secondCollision = fixture.addMap(1 + SortKey64.MAX_TIE + 1, 20, 1L, 2L);
        fixture.process();
        fixture.assertTiledOrder(firstCollision[0], firstCollision[1],
                secondCollision[0], secondCollision[1]);
    }

    @Test
    public void materialFirstPassesCannotSplitMapBlock() {
        assertMaterialPathCannotSplitMap(BlendMode.OPAQUE);
        assertMaterialPathCannotSplitMap(BlendMode.CUTOUT);
        assertMaterialPathCannotSplitMap(BlendMode.ADDITIVE);
        assertMaterialPathCannotSplitMap(BlendMode.ALPHA);
    }

    @Test
    public void hiddenLayerSuppressesCompleteMapGroup() {
        Fixture fixture = new Fixture();
        fixture.layerState.enabled[0] = false;
        fixture.addMap(5, 20, 2L, 1L);

        fixture.process();

        Assert.assertEquals(0, fixture.drawList.size);
    }

    @Test
    public void changingInternalMaterialOrOrderDoesNotMoveMapGroup() {
        Fixture fixture = new Fixture();
        int below = fixture.addSprite(1, 10, BlendMode.ALPHA, 1);
        int[] refs = fixture.addMap(2, 20, 2L, 1L);
        int above = fixture.addSprite(3, 30, BlendMode.ALPHA, 3);
        fixture.tiledState.textureHandle[refs[0]] = SortKey64.MAX_TEXTURE_HANDLE;
        fixture.tiledState.sortKey[refs[0]] = Long.MAX_VALUE;

        fixture.process();

        fixture.assertEntry(0, RenderSourceDomain.SOURCE_ECS, below);
        fixture.assertEntry(1, RenderSourceDomain.SOURCE_TILED, refs[1]);
        fixture.assertEntry(2, RenderSourceDomain.SOURCE_TILED, refs[0]);
        fixture.assertEntry(3, RenderSourceDomain.SOURCE_ECS, above);
    }

    private static void assertMaterialPathCannotSplitMap(BlendMode mode) {
        Fixture fixture = new Fixture();
        int below = fixture.addSprite(1, 10, mode, SortKey64.MAX_TEXTURE_HANDLE);
        int[] refs = fixture.addMap(2, 20, Long.MAX_VALUE, 0L, 100L);
        int above = fixture.addSprite(3, 30, mode, 1);

        fixture.process();

        fixture.assertEntry(0, RenderSourceDomain.SOURCE_ECS, below);
        fixture.assertEntry(1, RenderSourceDomain.SOURCE_TILED, refs[1]);
        fixture.assertEntry(2, RenderSourceDomain.SOURCE_TILED, refs[2]);
        fixture.assertEntry(3, RenderSourceDomain.SOURCE_TILED, refs[0]);
        fixture.assertEntry(4, RenderSourceDomain.SOURCE_ECS, above);
    }

    private static final class Fixture {
        final DynamicEntityRenderState ecsState = new DynamicEntityRenderState(16);
        final TiledMapRenderState tiledState = new TiledMapRenderState(16);
        final LayerStateSOA layerState = new LayerStateSOA(2);
        final DrawList drawList = new DrawList(32);
        final World world;

        Fixture() {
            layerState.enabled[0] = true;
            world = new World(new WorldConfigurationBuilder()
                    .with(
                            new RenderBuildDrawListSystem(
                                    ecsState, tiledState, layerState, drawList,
                                    new RenderStats(), 64, -1, -1),
                            new RenderSortSystem(ecsState, tiledState, drawList)
                    )
                    .build());
        }

        int addSprite(int entityId, int zIndex, BlendMode blendMode, int texture) {
            int slot = ecsState.acquireSlotForEntity(entityId);
            ecsState.kind[slot] = RenderKind.SPRITE;
            ecsState.enabled[slot] = true;
            ecsState.visible[slot] = true;
            ecsState.textureHandle[slot] = texture;
            ecsState.shader[slot] = 1;
            ecsState.blend[slot] = blendMode.id;
            ecsState.layerIndex[slot] = 0;
            ecsState.z[slot] = zIndex;
            ecsState.runtimeOrder[slot] = entityId;
            ecsState.sortKey[slot] = SortKey64.packForBlend(
                    1, blendMode.id, texture, 0, zIndex, entityId);
            return slot;
        }

        int[] addMap(int mapEntityId, int zIndex, long... internalKeys) {
            int start = tiledState.getVisibleRefCount();
            int[] refs = new int[internalKeys.length];
            for (int i = 0; i < internalKeys.length; i++) {
                int ref = tiledState.registerRef();
                refs[i] = ref;
                tiledState.setRenderDataForRef(
                        ref, i + 1, 1, BlendMode.ALPHA.id, 0, 0, 0, internalKeys[i],
                        0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f,
                        0f, 0f, 1f, 1f, 1f, 1f, RenderRepeatFlags.NONE);
                tiledState.addVisibleRef(ref);
            }
            long groupKey = SortKey64.packForBlend(
                    1, BlendMode.ALPHA.id, 0, 0, zIndex, mapEntityId);
            tiledState.addVisibleMap(mapEntityId, 0, zIndex, groupKey, start, refs.length);
            return refs;
        }

        void process() {
            world.process();
        }

        void assertEntry(int index, byte domain, int source) {
            Assert.assertEquals(domain, drawList.getDomain(index));
            Assert.assertEquals(source, drawList.get(index));
        }

        void assertTiledOrder(int... refs) {
            Assert.assertEquals(refs.length, drawList.size);
            for (int i = 0; i < refs.length; i++) {
                assertEntry(i, RenderSourceDomain.SOURCE_TILED, refs[i]);
            }
        }
    }
}
