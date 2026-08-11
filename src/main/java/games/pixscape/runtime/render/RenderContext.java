package games.pixscape.runtime.render;

import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;

/**
 * Runtime implementation detail. Public Java visibility does not make this type part of the
 * supported compatibility API.
 *
 * <p>Internal aggregate of borrowed render-pipeline state. Supported custom submission should
 * consume {@link FrameRenderQueue} and the documented engine getters instead.</p>
 */
public final class RenderContext {

    public final DynamicEntityRenderState dynamicEntityState;
    public final LayerStateSOA layerState;
    public final DrawList drawList;
    public final FrameRenderQueue frameQueue;
    public final VfxRenderState vfxState;
    public final TiledMapRenderState tiledState;

    public final MetricsBatch batch;
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
}
