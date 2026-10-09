package games.pixscape.runtime.render;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;

/** Blend policy for replaying the final queue without changing its order or materials. */
public final class LightCompositionPass {
    public static final int ORIGINAL = 0;
    public static final int FIELD = 1;

    private LightCompositionPass() {}

    public static void apply(int pass, boolean light, int blendId, MetricsBatch batch,
                             ShaderProgram shader, RenderStats stats) {
        validate(light, blendId);
        BlendMode blend = BlendMode.fromId(blendId);
        int src = source(pass, light, blend), dst = destination(pass, light, blend);
        batch.setBlendMode(true, src, dst,
                pass == ORIGINAL ? coverageSource(blend) : src,
                pass == ORIGINAL ? coverageDestination(blend) : dst, stats);
        if (shader != null) {
            boolean opaque = pass == ORIGINAL && !blend.blending;
            if (opaque && !shader.hasUniform("u_worldCoverage")) {
                throw new IllegalArgumentException("Opaque/cutout material must support u_worldCoverage for light composition");
            }
            shader.bind();
            if (shader.hasUniform("u_worldCoverage")) shader.setUniformf("u_worldCoverage", opaque ? 1f : 0f);
            if (shader.hasUniform("u_cutoutThreshold")) shader.setUniformf("u_cutoutThreshold", blend == BlendMode.CUTOUT ? .5f : -1f);
        }
    }

    public static boolean skip(int pass, boolean light, int blend) {
        if (pass == ORIGINAL) return light;
        return !light && (blend == BlendMode.ADDITIVE.id || blend == BlendMode.ADDITIVE_ALPHA.id);
    }

    public static void validate(boolean light, int blend) {
        if (blend < 0 || blend > 7) throw new IllegalArgumentException("Unknown composition blend: " + blend);
        if (light) {
            if (blend != BlendMode.ADDITIVE.id && blend != BlendMode.ADDITIVE_ALPHA.id)
                throw new IllegalArgumentException("Light composition requires ADDITIVE or ADDITIVE_ALPHA lights; blend=" + blend);
        } else if (blend == BlendMode.MULTIPLY.id || blend == BlendMode.MULTIPLY_ALPHA.id) {
            throw new IllegalArgumentException("World light composition does not support multiplicative decor blends; blend=" + blend);
        }
    }

    public static int source(int pass, boolean light, BlendMode blend) {
        return pass == FIELD && !light ? GL20.GL_ZERO : (blend.blending ? blend.srcFactor : GL20.GL_ONE);
    }

    public static int destination(int pass, boolean light, BlendMode blend) {
        if (pass != FIELD || light) return blend.blending ? blend.dstFactor : GL20.GL_ZERO;
        return blend == BlendMode.OPAQUE || blend == BlendMode.CUTOUT ? GL20.GL_ZERO : GL20.GL_ONE_MINUS_SRC_ALPHA;
    }

    /** Coverage in the original buffer, independent of emitted additive RGB. */
    public static int coverageSource(BlendMode blend) {
        return blend == BlendMode.ADDITIVE || blend == BlendMode.ADDITIVE_ALPHA ? GL20.GL_ZERO : GL20.GL_ONE;
    }

    public static int coverageDestination(BlendMode blend) {
        return blend == BlendMode.ADDITIVE || blend == BlendMode.ADDITIVE_ALPHA ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA;
    }
}
