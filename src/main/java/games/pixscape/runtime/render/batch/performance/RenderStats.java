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
    public int framebufferBinds;
    public int framebufferSwitchs;
    public int blendModeSwitches;
    public int blendSwitches;

    // Organisation
    public int batchesOpaque;
    public int batchesAlpha;
    public int buildDrawListScannedEcsSlots;
    public int buildDrawListScannedTiledSlots;

    public void reset() {
        extractedQuads = culledQuads = occludedQuads = drawnQuads = 0;
        drawCalls = flushes = flushStateChanges = flushCapacity = flushEnd = shaderSwitches = shaderBinds = textureBinds = 0;
        framebufferBinds = blendModeSwitches = framebufferSwitchs = blendSwitches = 0;
        batchesOpaque = batchesAlpha = 0;
        buildDrawListScannedEcsSlots = 0;
        buildDrawListScannedTiledSlots = 0;

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
        framebufferBinds += other.framebufferBinds;
        framebufferSwitchs += other.framebufferSwitchs;
        blendModeSwitches += other.blendModeSwitches;
        blendSwitches += other.blendSwitches;
        batchesOpaque += other.batchesOpaque;
        batchesAlpha += other.batchesAlpha;
        flushStateChanges += other.flushStateChanges;
        flushCapacity += other.flushCapacity;
        flushEnd += other.flushEnd;
        buildDrawListScannedEcsSlots += other.buildDrawListScannedEcsSlots;
        buildDrawListScannedTiledSlots += other.buildDrawListScannedTiledSlots;
    }
}
