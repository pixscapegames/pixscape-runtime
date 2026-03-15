package games.pixscape.runtime.render;

import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;

/**
 * Contexte de rendu passé aux extensions et aux passes FX.
 * Tu pourras l’enrichir progressivement.
 */
public final class RenderContext {

    public final RenderStateSOA renderState;
    public final LayerStateSOA  layerState;
    public final DrawList       drawList;

    public final MetricsBatch   batch; // ou interface plus abstraite
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

    // Helpers à rajouter (bindFbo, bindShader, drawFullscreenQuad, etc.)
}
