package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderExtractFrameQueueSystemTest {

    @Test
    public void canKeepTiledAndParticleQuadsInAuthoredCoordinatesForAnAuthoringWorld() {
        DynamicEntityRenderState ecsState = new DynamicEntityRenderState(1);
        TiledMapRenderState tiledState = new TiledMapRenderState(1);
        VfxRenderState vfxState = new VfxRenderState(1);
        DrawList drawList = new DrawList(2);
        FrameRenderQueue queue = new FrameRenderQueue(2);
        LayerStateSOA layers = new LayerStateSOA(2);
        layers.enabled[1] = true;
        layers.parallaxX[1] = .2f;
        layers.parallaxY[1] = .3f;
        OrthographicCamera camera = new OrthographicCamera();
        camera.position.set(100f, 200f, 0f);
        int tiledRef = tiledState.registerRef();
        writeTiled(tiledState, tiledRef, 10);
        tiledState.layerIndex[tiledRef] = 1;
        writeVfx(vfxState, 20);
        vfxState.layerIndex[0] = 1;
        drawList.addTiledSlot(tiledRef);
        drawList.addVfxSlot(0);

        World world = new World(new WorldConfigurationBuilder().with(
                new RenderExtractFrameQueueSystem(
                        ecsState, tiledState, vfxState, new IdentityLayerDisplayOffsetResolver(),
                        drawList, queue, new RenderStats(), 1, 0, 1)).build());
        world.process();

        Assert.assertEquals(10.1f, queue.x1[0], 0f);
        Assert.assertEquals(10.2f, queue.y1[0], 0f);
        Assert.assertEquals(20.1f, queue.x1[1], 0f);
        Assert.assertEquals(20.2f, queue.y1[1], 0f);
    }

    @Test
    public void appliesOwningLayerParallaxToTiledAndParticleQuadsOnlyAtDisplayExtraction() {
        DynamicEntityRenderState ecsState = new DynamicEntityRenderState(2);
        TiledMapRenderState tiledState = new TiledMapRenderState(4);
        VfxRenderState vfxState = new VfxRenderState(4);
        DrawList drawList = new DrawList(8);
        FrameRenderQueue queue = new FrameRenderQueue(8);
        LayerStateSOA layers = new LayerStateSOA(3);
        layers.enabled[1] = true;
        layers.parallaxX[1] = 0.5f;
        layers.parallaxY[1] = 2f;
        layers.enabled[2] = true;
        layers.parallaxX[2] = 1f;
        layers.parallaxY[2] = 1f;
        OrthographicCamera camera = new OrthographicCamera();
        camera.position.set(20f, 10f, 0f);

        int firstLayerOneRef = tiledState.registerRef();
        int secondLayerOneRef = tiledState.registerRef();
        int layerTwoRef = tiledState.registerRef();
        writeTiled(tiledState, firstLayerOneRef, 10);
        writeTiled(tiledState, secondLayerOneRef, 20);
        writeTiled(tiledState, layerTwoRef, 30);
        tiledState.layerIndex[firstLayerOneRef] = 1;
        tiledState.layerIndex[secondLayerOneRef] = 1;
        tiledState.layerIndex[layerTwoRef] = 2;
        writeVfx(vfxState, 30);
        writeVfx(vfxState, 40);
        vfxState.layerIndex[0] = 1;
        vfxState.layerIndex[1] = 2;
        drawList.addTiledSlot(firstLayerOneRef);
        drawList.addTiledSlot(secondLayerOneRef);
        drawList.addTiledSlot(layerTwoRef);
        drawList.addVfxSlot(0);
        drawList.addVfxSlot(1);

        World world = new World(new WorldConfigurationBuilder().with(
                new RenderExtractFrameQueueSystem(
                        ecsState, tiledState, vfxState, layers, camera,
                        drawList, queue, new RenderStats(), 2, 0, 4)).build());
        world.process();

        Assert.assertEquals(20.1f, queue.x1[0], 0.0001f);
        Assert.assertEquals(0.2f, queue.y1[0], 0.0001f);
        Assert.assertEquals(30.1f, queue.x1[1], 0.0001f);
        Assert.assertEquals(10.2f, queue.y1[1], 0.0001f);
        Assert.assertEquals(30.1f, queue.x1[2], 0.0001f);
        Assert.assertEquals(30.2f, queue.y1[2], 0.0001f);
        Assert.assertEquals(40.1f, queue.x1[3], 0.0001f);
        Assert.assertEquals(20.2f, queue.y1[3], 0.0001f);
        Assert.assertEquals(40.1f, queue.x1[4], 0.0001f);
        Assert.assertEquals(40.2f, queue.y1[4], 0.0001f);
        Assert.assertEquals(FrameRenderQueue.SOURCE_TILED, queue.sourceDomain[0]);
        Assert.assertEquals(FrameRenderQueue.SOURCE_TILED, queue.sourceDomain[1]);
        Assert.assertEquals(FrameRenderQueue.SOURCE_TILED, queue.sourceDomain[2]);
        Assert.assertEquals(FrameRenderQueue.SOURCE_VFX, queue.sourceDomain[3]);
        Assert.assertEquals(FrameRenderQueue.SOURCE_VFX, queue.sourceDomain[4]);
        Assert.assertEquals(10.1f, tiledState.x1[firstLayerOneRef], 0f);
        Assert.assertEquals(20.1f, tiledState.x1[secondLayerOneRef], 0f);
        Assert.assertEquals(30.1f, tiledState.x1[layerTwoRef], 0f);
        Assert.assertEquals(30.1f, vfxState.x1[0], 0f);
        Assert.assertEquals(40.1f, vfxState.x1[1], 0f);
    }

    @Test
    public void extractsDrawListOrderAndCopiesDrawReadyFields() {
        DynamicEntityRenderState ecsState = new DynamicEntityRenderState(4);
        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        VfxRenderState vfxState = new VfxRenderState(16);
        DrawList drawList = new DrawList(16);
        FrameRenderQueue queue = new FrameRenderQueue(1);
        RenderStats stats = new RenderStats();

        int ecsSlot = ecsState.acquireSlotForEntity(120);
        writeSlot(ecsState, ecsSlot, 20, 3f, 4f);
        writeVfx(vfxState, 30);
        int tiledRef = tiledState.registerRef();
        writeTiled(tiledState, tiledRef, 10);

        drawList.addTiledSlot(tiledRef);
        drawList.addEcsSlot(ecsSlot);
        drawList.addVfxSlot(0);

        World world = new World(new WorldConfigurationBuilder()
                .with(new RenderExtractFrameQueueSystem(
                        ecsState,
                        tiledState,
                        vfxState,
                        drawList,
                        queue,
                        stats,
                        64,
                        160,
                        200
                ))
                .build());

        world.process();

        Assert.assertEquals(drawList.size, queue.size);
        assertQueueEntry(queue, 0, tiledRef, -1, 10, 0f, 0f, FrameRenderQueue.SOURCE_TILED);
        assertQueueEntry(queue, 1, ecsSlot, 120, 20, 3f, 4f, FrameRenderQueue.SOURCE_ECS);
        assertQueueEntry(queue, 2, 0, -1, 30, 0f, 0f, FrameRenderQueue.SOURCE_VFX);
        Assert.assertEquals(3, stats.frameQueueQuads);
        Assert.assertTrue(stats.frameQueuePeakCapacity >= 3);
        Assert.assertTrue(stats.frameQueueGrowthCount > 0);

        vfxState.textureHandle[0] = 999;
        vfxState.x1[0] = 999f;
        Assert.assertEquals(31, queue.textureHandle[2]);
        Assert.assertEquals(30.1f, queue.x1[2], 0f);

        ecsState.textureHandle[ecsSlot] = 999;
        ecsState.x1[ecsSlot] = 999f;
        Assert.assertEquals(11, queue.textureHandle[0]);
        Assert.assertEquals(10.1f, queue.x1[0], 0f);
        Assert.assertEquals(21, queue.textureHandle[1]);
        Assert.assertEquals(23.1f, queue.x1[1], 0f);
    }

    private static void writeSlot(DynamicEntityRenderState state,
                                  int slot,
                                  int base,
                                  float offsetX,
                                  float offsetY) {
        state.textureHandle[slot] = base + 1;
        state.shader[slot] = base + 2;
        state.blend[slot] = base + 3;
        state.layerIndex[slot] = base + 4;
        state.paramsId[slot] = base + 5;
        state.customParamsId[slot] = base + 6;
        state.sortKey[slot] = base + 7L;
        state.x1[slot] = base + 0.1f;
        state.y1[slot] = base + 0.2f;
        state.x2[slot] = base + 0.3f;
        state.y2[slot] = base + 0.4f;
        state.x3[slot] = base + 0.5f;
        state.y3[slot] = base + 0.6f;
        state.x4[slot] = base + 0.7f;
        state.y4[slot] = base + 0.8f;
        state.offsetX[slot] = offsetX;
        state.offsetY[slot] = offsetY;
        state.u1[slot] = base + 0.9f;
        state.v1[slot] = base + 1.1f;
        state.u2[slot] = base + 1.2f;
        state.v2[slot] = base + 1.3f;
        state.colorPacked[slot] = base + 1.4f;
        state.repeatFlags[slot] = RenderRepeatFlags.REPEAT_X;
    }

    private static void writeVfx(VfxRenderState state, int base) {
        state.addParticleQuad(
                base + 1,
                base + 2,
                base + 3,
                base + 4,
                base + 6,
                base + 5,
                base + 6,
                base + 7L,
                base + 0.1f,
                base + 0.2f,
                base + 0.3f,
                base + 0.4f,
                base + 0.5f,
                base + 0.6f,
                base + 0.7f,
                base + 0.8f,
                base + 0.9f,
                base + 1.1f,
                base + 1.2f,
                base + 1.3f,
                base + 1.4f,
                RenderRepeatFlags.REPEAT_X,
                99
        );
    }

    private static void writeTiled(TiledMapRenderState state, int ref, int base) {
        state.setRenderDataForRef(
                ref,
                base + 1,
                base + 2,
                base + 3,
                base + 4,
                base + 5,
                base + 6,
                base + 7L,
                base + 0.1f,
                base + 0.2f,
                base + 0.3f,
                base + 0.4f,
                base + 0.5f,
                base + 0.6f,
                base + 0.7f,
                base + 0.8f,
                base + 0.9f,
                base + 1.1f,
                base + 1.2f,
                base + 1.3f,
                base + 1.4f,
                1f,
                RenderRepeatFlags.REPEAT_X
        );
    }

    private static void assertQueueEntry(FrameRenderQueue queue,
                                         int index,
                                         int sourceSlot,
                                         int sourceEntity,
                                         int base,
                                         float offsetX,
                                         float offsetY,
                                         byte sourceDomain) {
        Assert.assertEquals(base + 1, queue.textureHandle[index]);
        Assert.assertEquals(base + 2, queue.shader[index]);
        Assert.assertEquals(base + 3, queue.blend[index]);
        Assert.assertEquals(base + 4, queue.layerIndex[index]);
        Assert.assertEquals(base + 5, queue.paramsId[index]);
        Assert.assertEquals(base + 6, queue.customParamsId[index]);
        Assert.assertEquals(base + 7L, queue.sortKey[index]);
        Assert.assertEquals(base + 0.1f + offsetX, queue.x1[index], 0f);
        Assert.assertEquals(base + 0.2f + offsetY, queue.y1[index], 0f);
        Assert.assertEquals(base + 0.3f + offsetX, queue.x2[index], 0f);
        Assert.assertEquals(base + 0.4f + offsetY, queue.y2[index], 0f);
        Assert.assertEquals(base + 0.5f + offsetX, queue.x3[index], 0f);
        Assert.assertEquals(base + 0.6f + offsetY, queue.y3[index], 0f);
        Assert.assertEquals(base + 0.7f + offsetX, queue.x4[index], 0f);
        Assert.assertEquals(base + 0.8f + offsetY, queue.y4[index], 0f);
        Assert.assertEquals(base + 0.9f, queue.u1[index], 0f);
        Assert.assertEquals(base + 1.1f, queue.v1[index], 0f);
        Assert.assertEquals(base + 1.2f, queue.u2[index], 0f);
        Assert.assertEquals(base + 1.3f, queue.v2[index], 0f);
        Assert.assertEquals(base + 1.4f, queue.colorPacked[index], 0f);
        Assert.assertEquals(RenderRepeatFlags.REPEAT_X, queue.repeatFlags[index]);
        Assert.assertEquals(sourceDomain, queue.sourceDomain[index]);
        Assert.assertEquals(sourceSlot, queue.sourceSlot[index]);
        Assert.assertEquals(sourceEntity, queue.sourceEntity[index]);
    }
}
