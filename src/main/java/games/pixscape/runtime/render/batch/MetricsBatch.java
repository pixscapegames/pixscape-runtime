package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ShaderFloatParam;
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

    default void setParameterLayout(ShaderParameterLayout layout, RenderStats stats) {
        if (layout != null && layout.size() != 0) {
            throw new UnsupportedOperationException("This batch cannot render per-entity shader parameters");
        }
    }

    default void setEntityParameters(Array<ShaderFloatParam> parameters, RenderStats stats) {
        if (parameters != null && parameters.size != 0) {
            throw new UnsupportedOperationException("This batch cannot render per-entity shader parameters");
        }
    }

    void setBlendMode(boolean enabled, int sfactor, int dfactor, RenderStats stats);

    default void setBlendMode(boolean enabled, int src, int dst, int srcAlpha, int dstAlpha, RenderStats stats) {
        if (src != srcAlpha || dst != dstAlpha) {
            throw new UnsupportedOperationException("World light composition requires separate alpha blend factors");
        }
        setBlendMode(enabled, src, dst, stats);
    }


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
