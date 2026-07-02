package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderBuildDrawListSystemDoubleExtractionTest {

    @Test
    public void ecsScanIsBoundedWhenMaxEntityIdIsHigh() {
        Fixture fixture = new Fixture(400_000, 64);

        fixture.enableSprite(12, 0, 120L);
        fixture.enableSprite(320_000, 0, 200L);
        fixture.tiledState.addVisibleSlot(320_000);

        fixture.world.process();

        Assert.assertEquals("Only ECS low id + tiled visible candidate should be extracted", 2, fixture.drawList.size);
        Assert.assertEquals(64, fixture.stats.buildDrawListScannedEcsSlots);
        Assert.assertEquals(1, fixture.stats.buildDrawListScannedTiledSlots);
    }

    @Test
    public void tiledPhaseExtractsOnlyVisibleChunkSlots() {
        Fixture fixture = new Fixture(2_000, 64);

        fixture.enableSprite(900, 0, 100L);
        fixture.enableSprite(901, 0, 50L);
        fixture.enableSprite(950, 0, 5L);
        fixture.addVisibleTiledRange(900, 2);

        fixture.world.process();

        Assert.assertEquals(2, fixture.drawList.size);
        Assert.assertEquals(901, fixture.drawList.get(0));
        Assert.assertEquals(900, fixture.drawList.get(1));
    }

    @Test
    public void tiledMaskedOrOutOfViewSlotsAreNotExtracted() {
        Fixture fixture = new Fixture(2_000, 64);

        fixture.enableSprite(900, 0, 100L);
        fixture.enableSprite(901, 0, 120L);
        fixture.state.visible[901] = false;
        fixture.addVisibleTiledRange(900, 2);

        fixture.world.process();

        Assert.assertEquals(1, fixture.drawList.size);
        Assert.assertEquals(900, fixture.drawList.get(0));
    }

    @Test
    public void mixedEcsAndTiledKeepsCorrectSortedOutputWithoutDuplicate() {
        Fixture fixture = new Fixture(2_000, 64);

        fixture.enableSprite(10, 0, 40L);
        fixture.enableSprite(11, 0, 10L);
        fixture.enableSprite(900, 0, 30L);
        fixture.enableSprite(901, 0, 20L);
        fixture.addVisibleTiledRange(900, 2);

        fixture.world.process();

        Assert.assertEquals(4, fixture.drawList.size);
        Assert.assertArrayEquals(new int[]{11, 901, 900, 10}, snapshot(fixture.drawList));
    }

    @Test
    public void disabledLayerFiltersEcsAndTiledSlots() {
        Fixture fixture = new Fixture(2_000, 64);
        fixture.layerState.enabled[1] = false;

        fixture.enableSprite(5, 1, 10L);
        fixture.enableSprite(900, 1, 20L);
        fixture.tiledState.addVisibleSlot(900);

        fixture.world.process();

        Assert.assertEquals(0, fixture.drawList.size);
    }

    @Test
    public void reservedVfxRangeIsExtractedOutsideEcsBound() {
        Fixture fixture = new Fixture(2_000, 64, 1_500, 2_000);

        fixture.addVfx(12L);

        fixture.world.process();

        Assert.assertEquals(1, fixture.drawList.size);
        Assert.assertEquals(1_500, fixture.drawList.get(0));
        Assert.assertEquals(0, fixture.stats.buildDrawListScannedEcsSlots);
        Assert.assertEquals(0, fixture.stats.buildDrawListScannedTiledSlots);
        Assert.assertEquals(1, fixture.stats.vfxActiveParticles);
    }

    @Test
    public void mixedEcsTiledAndReservedVfxKeepCorrectSortedOutputWithoutDuplicate() {
        Fixture fixture = new Fixture(3_000, 64, 1_500, 2_000);

        fixture.enableSprite(10, 0, 40L);     // ECS
        fixture.enableSprite(900, 0, 30L);    // tiled
        fixture.enableSprite(901, 0, 20L);    // tiled
        fixture.addVfx(10L);
        fixture.addVisibleTiledRange(900, 2);

        fixture.world.process();

        Assert.assertEquals(4, fixture.drawList.size);
        Assert.assertArrayEquals(new int[]{1500, 901, 900, 10}, snapshot(fixture.drawList));
    }

    private static int[] snapshot(DrawList drawList) {
        int[] out = new int[drawList.size];
        System.arraycopy(drawList.data(), 0, out, 0, drawList.size);
        return out;
    }

    private static final class Fixture {
        final RenderStateSOA state;
        final TiledMapRenderState tiledState;
        final VfxRenderState vfxState;
        final LayerStateSOA layerState;
        final DrawList drawList;
        final RenderStats stats;
        final World world;

        Fixture(int capacity, int ecsEndExclusive) {
            this(capacity, ecsEndExclusive, -1, -1);
        }

        Fixture(int capacity, int ecsEndExclusive, int reservedStartInclusive, int reservedEndExclusive) {
            this.state = new RenderStateSOA(capacity);
            this.tiledState = new TiledMapRenderState(16);
            this.vfxState = new VfxRenderState(16);
            this.layerState = new LayerStateSOA(4);
            this.layerState.enabled[0] = true;
            this.layerState.enabled[1] = true;
            this.drawList = new DrawList(capacity);
            this.stats = new RenderStats();
            this.world = new World(new WorldConfigurationBuilder()
                    .with(
                            new RenderBuildDrawListSystem(
                                    state,
                                    tiledState,
                                    vfxState,
                                    layerState,
                                    drawList,
                                    stats,
                                    ecsEndExclusive,
                                    reservedStartInclusive,
                                    reservedEndExclusive
                            ),
                            new RenderSortSystem(
                                    state,
                                    vfxState,
                                    drawList,
                                    reservedStartInclusive,
                                    reservedEndExclusive
                            )
                    )
                    .build());
        }

        void addVisibleTiledRange(int startInclusive, int count) {
            for (int i = 0; i < count; i++) {
                tiledState.addVisibleSlot(startInclusive + i);
            }
        }

        void enableSprite(int slot, int layerIdx, long sortKey) {
            state.kind[slot] = RenderStateSOA.KIND_SPRITE;
            state.enabled[slot] = true;
            state.visible[slot] = true;
            state.layerIndex[slot] = layerIdx;
            state.sortKey[slot] = sortKey;
            state.touch(slot);
        }

        void addVfx(long sortKey) {
            vfxState.addParticleQuad(
                    1,
                    2,
                    3,
                    0,
                    0,
                    0,
                    0,
                    sortKey,
                    0f,
                    0f,
                    1f,
                    0f,
                    1f,
                    1f,
                    0f,
                    1f,
                    0f,
                    0f,
                    1f,
                    1f,
                    1f,
                    RenderRepeatFlags.NONE,
                    -1
            );
        }
    }
}
