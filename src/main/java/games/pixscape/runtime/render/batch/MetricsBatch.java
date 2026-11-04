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

    default void draw(int textureHandle,
              float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
              float u, float v, float u2, float v2,
              float blV, float tlV, float trV, float brV,
              RenderStats stats) {
        if (blV != v2 || tlV != v || trV != v || brV != v2)
            throw new UnsupportedOperationException("This batch does not support independent vertex UVs");
        draw(textureHandle, x1, y1, x2, y2, x3, y3, x4, y4, u, v, u2, v2, stats);
    }

    void flush(RenderStats stats);

    void end(RenderStats stats);

    /** Discards pending geometry without drawing it and invalidates submission state. */
    default void abort() {
        throw new UnsupportedOperationException("This batch cannot abort a failed submission");
    }

    /** Cleanup must not replace the original submission error. */
    default void abort(Throwable failure) {
        try { abort(); }
        catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
    }

    void close();

    default void setTextureArrayBundle(AtlasRuntimeService.TextureArrayBundle bundle) {
        // no-op by default
    }
}
