package games.pixscape.runtime.render.batch.performance;

import org.junit.Assert;
import org.junit.Test;

public class RenderStatsTest {

    @Test
    public void resetClearsTextureArrayInstrumentationFields() {
        RenderStats stats = new RenderStats();
        stats.textureArrayBindSkips = 1;
        stats.projectionUploads = 2;
        stats.submittedQuads = 3;
        stats.flushedQuads = 4;
        stats.flushedVertices = 5;
        stats.regionResolveCacheHits = 6L;
        stats.regionResolveCacheMisses = 7L;
        stats.frameQueueQuads = 8;
        stats.frameQueuePeakCapacity = 9;
        stats.frameQueueGrowthCount = 10;
        stats.vfxActiveParticles = 11;
        stats.vfxPeakCapacity = 12;
        stats.vfxGrowthCount = 13;
        stats.tiledChunksTested = 14;
        stats.tiledChunksOutside = 15;
        stats.tiledChunksFullyInside = 16;
        stats.tiledChunksPartial = 17;
        stats.tiledRenderableRefsConsidered = 18;
        stats.tiledRenderableRefsVisible = 19;
        stats.tiledRenderableRefsCulled = 20;

        stats.reset();

        Assert.assertEquals(0, stats.textureArrayBindSkips);
        Assert.assertEquals(0, stats.projectionUploads);
        Assert.assertEquals(0, stats.submittedQuads);
        Assert.assertEquals(0, stats.flushedQuads);
        Assert.assertEquals(0, stats.flushedVertices);
        Assert.assertEquals(0L, stats.regionResolveCacheHits);
        Assert.assertEquals(0L, stats.regionResolveCacheMisses);
        Assert.assertEquals(0, stats.frameQueueQuads);
        Assert.assertEquals(0, stats.frameQueuePeakCapacity);
        Assert.assertEquals(0, stats.frameQueueGrowthCount);
        Assert.assertEquals(0, stats.vfxActiveParticles);
        Assert.assertEquals(0, stats.vfxPeakCapacity);
        Assert.assertEquals(0, stats.vfxGrowthCount);
        Assert.assertEquals(0, stats.tiledChunksTested);
        Assert.assertEquals(0, stats.tiledChunksOutside);
        Assert.assertEquals(0, stats.tiledChunksFullyInside);
        Assert.assertEquals(0, stats.tiledChunksPartial);
        Assert.assertEquals(0, stats.tiledRenderableRefsConsidered);
        Assert.assertEquals(0, stats.tiledRenderableRefsVisible);
        Assert.assertEquals(0, stats.tiledRenderableRefsCulled);
    }

    @Test
    public void addAccumulatesTextureArrayInstrumentationFields() {
        RenderStats total = new RenderStats();
        total.textureArrayBindSkips = 1;
        total.projectionUploads = 2;
        total.submittedQuads = 3;
        total.flushedQuads = 4;
        total.flushedVertices = 5;
        total.regionResolveCacheHits = 6L;
        total.regionResolveCacheMisses = 7L;
        total.frameQueueQuads = 8;
        total.frameQueuePeakCapacity = 9;
        total.frameQueueGrowthCount = 10;
        total.vfxActiveParticles = 11;
        total.vfxPeakCapacity = 12;
        total.vfxGrowthCount = 13;
        total.tiledChunksTested = 14;
        total.tiledChunksOutside = 15;
        total.tiledChunksFullyInside = 16;
        total.tiledChunksPartial = 17;
        total.tiledRenderableRefsConsidered = 18;
        total.tiledRenderableRefsVisible = 19;
        total.tiledRenderableRefsCulled = 20;

        RenderStats frame = new RenderStats();
        frame.textureArrayBindSkips = 10;
        frame.projectionUploads = 20;
        frame.submittedQuads = 30;
        frame.flushedQuads = 40;
        frame.flushedVertices = 50;
        frame.regionResolveCacheHits = 60L;
        frame.regionResolveCacheMisses = 70L;
        frame.frameQueueQuads = 80;
        frame.frameQueuePeakCapacity = 90;
        frame.frameQueueGrowthCount = 100;
        frame.vfxActiveParticles = 110;
        frame.vfxPeakCapacity = 120;
        frame.vfxGrowthCount = 130;
        frame.tiledChunksTested = 140;
        frame.tiledChunksOutside = 150;
        frame.tiledChunksFullyInside = 160;
        frame.tiledChunksPartial = 170;
        frame.tiledRenderableRefsConsidered = 180;
        frame.tiledRenderableRefsVisible = 190;
        frame.tiledRenderableRefsCulled = 200;

        total.add(frame);

        Assert.assertEquals(11, total.textureArrayBindSkips);
        Assert.assertEquals(22, total.projectionUploads);
        Assert.assertEquals(33, total.submittedQuads);
        Assert.assertEquals(44, total.flushedQuads);
        Assert.assertEquals(55, total.flushedVertices);
        Assert.assertEquals(66L, total.regionResolveCacheHits);
        Assert.assertEquals(77L, total.regionResolveCacheMisses);
        Assert.assertEquals(88, total.frameQueueQuads);
        Assert.assertEquals(90, total.frameQueuePeakCapacity);
        Assert.assertEquals(110, total.frameQueueGrowthCount);
        Assert.assertEquals(121, total.vfxActiveParticles);
        Assert.assertEquals(120, total.vfxPeakCapacity);
        Assert.assertEquals(143, total.vfxGrowthCount);
        Assert.assertEquals(154, total.tiledChunksTested);
        Assert.assertEquals(165, total.tiledChunksOutside);
        Assert.assertEquals(176, total.tiledChunksFullyInside);
        Assert.assertEquals(187, total.tiledChunksPartial);
        Assert.assertEquals(198, total.tiledRenderableRefsConsidered);
        Assert.assertEquals(209, total.tiledRenderableRefsVisible);
        Assert.assertEquals(220, total.tiledRenderableRefsCulled);
    }
}
