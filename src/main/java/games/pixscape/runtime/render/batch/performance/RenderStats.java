package games.pixscape.runtime.render.batch.performance;

public final class RenderStats {
    public static final int ECS_SKIP_NONE = 0;
    public static final int ECS_SKIP_DISABLED = 1;
    public static final int ECS_SKIP_NOT_VISIBLE = 2;
    public static final int ECS_SKIP_INVALID_LAYER = 3;
    public static final int ECS_SKIP_DISABLED_LAYER = 4;
    public static final int ECS_SKIP_NOT_SPRITE = 5;

    public static final int ECS_COMPONENT_ACTIVE = 1;
    public static final int ECS_COMPONENT_TRANSFORM = 1 << 1;
    public static final int ECS_COMPONENT_BOUNDS = 1 << 2;
    public static final int ECS_COMPONENT_MATERIAL = 1 << 3;
    public static final int ECS_COMPONENT_TEXTURE_REGION = 1 << 4;
    public static final int ECS_COMPONENT_VISIBILITY = 1 << 5;
    public static final int ECS_COMPONENT_BODY = 1 << 6;
    public static final int ECS_COMPONENT_FIXTURES = 1 << 7;
    public static final int ECS_COMPONENT_SPATIAL_HEIGHT = 1 << 8;

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
    public int ecsEmittedRenderSlots;
    public int ecsSkippedDisabledSlots;
    public int ecsSkippedNotVisibleSlots;
    public int ecsSkippedInvalidLayerSlots;
    public int ecsSkippedDisabledLayerSlots;
    public int ecsSkippedNonSpriteSlots;
    public int ecsFirstSkippedReason;
    public int ecsFirstSkippedEntityId;
    public int ecsFirstSkippedRenderSlot;
    public int ecsFirstSkippedMappedSlot;
    public int ecsFirstSkippedKind;
    public int ecsFirstSkippedLayer;
    public int ecsFirstSkippedComponentFlags;
    public int buildDrawListScannedTiledSlots;
    public int tiledChunksTested;
    public int tiledChunksOutside;
    public int tiledChunksFullyInside;
    public int tiledChunksPartial;
    public int tiledRenderableRefsConsidered;
    public int tiledRenderableRefsVisible;
    public int tiledRenderableRefsCulled;
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
        ecsEmittedRenderSlots = 0;
        ecsSkippedDisabledSlots = 0;
        ecsSkippedNotVisibleSlots = 0;
        ecsSkippedInvalidLayerSlots = 0;
        ecsSkippedDisabledLayerSlots = 0;
        ecsSkippedNonSpriteSlots = 0;
        ecsFirstSkippedReason = ECS_SKIP_NONE;
        ecsFirstSkippedEntityId = -1;
        ecsFirstSkippedRenderSlot = -1;
        ecsFirstSkippedMappedSlot = -1;
        ecsFirstSkippedKind = 0;
        ecsFirstSkippedLayer = -1;
        ecsFirstSkippedComponentFlags = 0;
        buildDrawListScannedTiledSlots = 0;
        tiledChunksTested = 0;
        tiledChunksOutside = 0;
        tiledChunksFullyInside = 0;
        tiledChunksPartial = 0;
        tiledRenderableRefsConsidered = 0;
        tiledRenderableRefsVisible = 0;
        tiledRenderableRefsCulled = 0;
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
        ecsEmittedRenderSlots += other.ecsEmittedRenderSlots;
        ecsSkippedDisabledSlots += other.ecsSkippedDisabledSlots;
        ecsSkippedNotVisibleSlots += other.ecsSkippedNotVisibleSlots;
        ecsSkippedInvalidLayerSlots += other.ecsSkippedInvalidLayerSlots;
        ecsSkippedDisabledLayerSlots += other.ecsSkippedDisabledLayerSlots;
        ecsSkippedNonSpriteSlots += other.ecsSkippedNonSpriteSlots;
        if (ecsFirstSkippedReason == ECS_SKIP_NONE && other.ecsFirstSkippedReason != ECS_SKIP_NONE) {
            ecsFirstSkippedReason = other.ecsFirstSkippedReason;
            ecsFirstSkippedEntityId = other.ecsFirstSkippedEntityId;
            ecsFirstSkippedRenderSlot = other.ecsFirstSkippedRenderSlot;
            ecsFirstSkippedMappedSlot = other.ecsFirstSkippedMappedSlot;
            ecsFirstSkippedKind = other.ecsFirstSkippedKind;
            ecsFirstSkippedLayer = other.ecsFirstSkippedLayer;
            ecsFirstSkippedComponentFlags = other.ecsFirstSkippedComponentFlags;
        }
        buildDrawListScannedTiledSlots += other.buildDrawListScannedTiledSlots;
        tiledChunksTested += other.tiledChunksTested;
        tiledChunksOutside += other.tiledChunksOutside;
        tiledChunksFullyInside += other.tiledChunksFullyInside;
        tiledChunksPartial += other.tiledChunksPartial;
        tiledRenderableRefsConsidered += other.tiledRenderableRefsConsidered;
        tiledRenderableRefsVisible += other.tiledRenderableRefsVisible;
        tiledRenderableRefsCulled += other.tiledRenderableRefsCulled;
        frameQueueQuads += other.frameQueueQuads;
        frameQueuePeakCapacity = Math.max(frameQueuePeakCapacity, other.frameQueuePeakCapacity);
        frameQueueGrowthCount += other.frameQueueGrowthCount;
        vfxActiveParticles += other.vfxActiveParticles;
        vfxPeakCapacity = Math.max(vfxPeakCapacity, other.vfxPeakCapacity);
        vfxGrowthCount += other.vfxGrowthCount;
    }
}
