package games.pixscape.runtime.render;

import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;

/**
 * Render context passed to extensions and FX passes.
 * Yor can enrich it progressively.
 */
public final class RenderContext {

    public final DynamicEntityRenderState dynamicEntityState;
    public final LayerStateSOA layerState;
    public final DrawList drawList;
    public final FrameRenderQueue frameQueue;
    public final VfxRenderState vfxState;
    public final TiledMapRenderState tiledState;

    public final MetricsBatch batch; // or interface plus abstraite
    public final GLCaps glCaps;

    public RenderContext(DynamicEntityRenderState dynamicEntityState,
                         LayerStateSOA layerState,
                         DrawList drawList,
                         FrameRenderQueue frameQueue,
                         VfxRenderState vfxState,
                         TiledMapRenderState tiledState,
                         MetricsBatch batch,
                         GLCaps glCaps) {
        this.dynamicEntityState = dynamicEntityState;
        this.layerState = layerState;
        this.drawList = drawList;
        this.frameQueue = frameQueue;
        this.vfxState = vfxState;
        this.tiledState = tiledState;
        this.batch = batch;
        this.glCaps = glCaps;
    }

    // Helpers to add (bindFbo, bindShader, drawFullscreenQuad, etc.)
}
