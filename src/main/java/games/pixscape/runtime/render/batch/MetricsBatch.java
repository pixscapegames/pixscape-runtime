package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;

/**
 * {@code SUPPORTED_EXPERT} data-oriented Pixscape submission batch; this is not a LibGDX immediate-mode
 * {@code Batch} API.
 *
 * <p>When obtained from {@link games.pixscape.runtime.engine.PixscapeEngine#getMetricsBatch()},
 * the object is borrowed and engine-owned. Default submission controls its per-frame
 * {@link #begin(Matrix4, RenderStats) begin}/{@link #end(RenderStats) end} lifecycle, and the
 * engine closes it during disposal. Expert custom submitters may drive that lifecycle but
 * must leave the batch ended and must not close it.</p>
 */
public interface MetricsBatch extends AutoCloseable {
    void begin(Matrix4 combined, RenderStats stats);

    void setShader(ShaderProgram shader, RenderStats stats);

    void setBlendMode(boolean enabled, int sfactor, int dfactor, RenderStats stats);

    void setColor(float r, float g, float b, float a);

    default void setPackedColor(float packed) {
        throw new UnsupportedOperationException("Packed color not supported by this batch");
    }

    void draw(int textureHandle,
              float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
              float u, float v, float u2, float v2,
              RenderStats stats);

    void flush(RenderStats stats);

    void end(RenderStats stats);

    void close();

    default void setTextureArrayBundle(AtlasRuntimeService.TextureArrayBundle bundle) {
        // no-op by default
    }
}
