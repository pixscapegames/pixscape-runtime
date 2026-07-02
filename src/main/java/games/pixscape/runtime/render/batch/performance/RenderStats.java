package games.pixscape.runtime.render.batch.performance;

public final class RenderStats {
    // Geometry
    public int extractedQuads;
    public int culledQuads;
    public int occludedQuads;
    public int drawnQuads;

    // GPU / state
    public int drawCalls;
    public int flushes;
    public int flushStateChanges;  // shader/blend
    public int flushCapacity;      // buffer plein
    public int flushEnd;
    public int shaderSwitches;
    public int shaderBinds;
    public int textureBinds;
    public int textureArrayBindSkips;
    public int projectionUploads;
    public int submittedQuads;
    public int flushedQuads;
    public int flushedVertices;
    public int framebufferBinds;
    public int framebufferSwitches;
    public int blendModeSwitches;
    public int blendSwitches;
    public long regionResolveCacheHits;
    public long regionResolveCacheMisses;

    // Organisation
    public int batchesOpaque;
    public int batchesAlpha;
    public int buildDrawListScannedEcsSlots;
    public int ecsActiveRenderSlots;
    public int ecsRenderCapacity;
    public int ecsEntityMappingCapacity;
    public int buildDrawListScannedTiledSlots;
    public int frameQueueQuads;
    public int frameQueuePeakCapacity;
    public int frameQueueGrowthCount;
    public int vfxActiveParticles;
    public int vfxPeakCapacity;
    public int vfxGrowthCount;

    public void reset() {
        extractedQuads = culledQuads = occludedQuads = drawnQuads = 0;
        drawCalls = flushes = flushStateChanges = flushCapacity = flushEnd = shaderSwitches = shaderBinds = textureBinds = 0;
        textureArrayBindSkips = projectionUploads = submittedQuads = flushedQuads = flushedVertices = 0;
        framebufferBinds = blendModeSwitches = framebufferSwitches = blendSwitches = 0;
        regionResolveCacheHits = regionResolveCacheMisses = 0L;
        batchesOpaque = batchesAlpha = 0;
        buildDrawListScannedEcsSlots = 0;
        ecsActiveRenderSlots = 0;
        ecsRenderCapacity = 0;
        ecsEntityMappingCapacity = 0;
        buildDrawListScannedTiledSlots = 0;
        frameQueueQuads = 0;
        frameQueuePeakCapacity = 0;
        frameQueueGrowthCount = 0;
        vfxActiveParticles = 0;
        vfxPeakCapacity = 0;
        vfxGrowthCount = 0;

    }

    public void add(RenderStats other) {
        extractedQuads += other.extractedQuads;
        culledQuads += other.culledQuads;
        occludedQuads += other.occludedQuads;
        drawnQuads += other.drawnQuads;
        drawCalls += other.drawCalls;
        flushes += other.flushes;
        shaderSwitches += other.shaderSwitches;
        shaderBinds += other.shaderBinds;
        textureBinds += other.textureBinds;
        textureArrayBindSkips += other.textureArrayBindSkips;
        projectionUploads += other.projectionUploads;
        submittedQuads += other.submittedQuads;
        flushedQuads += other.flushedQuads;
        flushedVertices += other.flushedVertices;
        framebufferBinds += other.framebufferBinds;
        framebufferSwitches += other.framebufferSwitches;
        blendModeSwitches += other.blendModeSwitches;
        blendSwitches += other.blendSwitches;
        regionResolveCacheHits += other.regionResolveCacheHits;
        regionResolveCacheMisses += other.regionResolveCacheMisses;
        batchesOpaque += other.batchesOpaque;
        batchesAlpha += other.batchesAlpha;
        flushStateChanges += other.flushStateChanges;
        flushCapacity += other.flushCapacity;
        flushEnd += other.flushEnd;
        buildDrawListScannedEcsSlots += other.buildDrawListScannedEcsSlots;
        ecsActiveRenderSlots += other.ecsActiveRenderSlots;
        ecsRenderCapacity = Math.max(ecsRenderCapacity, other.ecsRenderCapacity);
        ecsEntityMappingCapacity = Math.max(ecsEntityMappingCapacity, other.ecsEntityMappingCapacity);
        buildDrawListScannedTiledSlots += other.buildDrawListScannedTiledSlots;
        frameQueueQuads += other.frameQueueQuads;
        frameQueuePeakCapacity = Math.max(frameQueuePeakCapacity, other.frameQueuePeakCapacity);
        frameQueueGrowthCount += other.frameQueueGrowthCount;
        vfxActiveParticles += other.vfxActiveParticles;
        vfxPeakCapacity = Math.max(vfxPeakCapacity, other.vfxPeakCapacity);
        vfxGrowthCount += other.vfxGrowthCount;
    }
}
