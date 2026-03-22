package games.pixscape.runtime.render;

import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;

/**
 * Render context passed to extensions and FX passes.
 * Yor can enrich it progressively.
 */
public final class RenderContext {

    public final RenderStateSOA renderState;
    public final LayerStateSOA  layerState;
    public final DrawList       drawList;

    public final MetricsBatch   batch; // or interface plus abstraite
    public final GLCaps         glCaps;

    public RenderContext(RenderStateSOA renderState,
                         LayerStateSOA layerState,
                         DrawList drawList,
                         MetricsBatch batch,
                         GLCaps glCaps) {
        this.renderState = renderState;
        this.layerState  = layerState;
        this.drawList    = drawList;
        this.batch       = batch;
        this.glCaps      = glCaps;
    }

    // Helpers to add (bindFbo, bindShader, drawFullscreenQuad, etc.)
}
