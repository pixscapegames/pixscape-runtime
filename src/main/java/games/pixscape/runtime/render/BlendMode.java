package games.pixscape.runtime.render;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Array;

/**
 * Supported blend modes for sprite and tile rendering passes.
 */
public enum BlendMode {
    OPAQUE(0, false, 0, 0),
    CUTOUT(7, false, 0, 0),
    ALPHA(1, true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
    PREMULT_ALPHA(2, true, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA),
    ADDITIVE(3, true, GL20.GL_ONE, GL20.GL_ONE),
    ADDITIVE_ALPHA(4, true, GL20.GL_SRC_ALPHA, GL20.GL_ONE),
    MULTIPLY(5, true, GL20.GL_DST_COLOR, GL20.GL_ZERO),
    MULTIPLY_ALPHA(6, true, GL20.GL_DST_COLOR, GL20.GL_ONE_MINUS_SRC_ALPHA);

    public static final int PASS_OPAQUE = 0;
    public static final int PASS_ORDERED = 1;
    public static final int PASS_COMMUTATIVE = 2;

    public final int id;
    public final boolean blending;
    public final int srcFactor, dstFactor;

    private static final Array<String> BLEND_NAMES = buildBlendNames();

    BlendMode(int id, boolean blending, int srcFactor, int dstFactor) {
        this.id = id;
        this.blending = blending;
        this.srcFactor = srcFactor;
        this.dstFactor = dstFactor;
    }

    private static Array<String> buildBlendNames() {
        Array<String> names = new Array<>(values().length);
        for (BlendMode mode : values()) {
            names.add(mode.name());
        }
        return names;
    }

    /**
     * Returns the render pass family used for sorting and batching.
     */
    public int passId() {
        switch (this) {
            case OPAQUE:
            case CUTOUT:
                return PASS_OPAQUE;
            case ADDITIVE:
            case ADDITIVE_ALPHA:
            case MULTIPLY:
                return PASS_COMMUTATIVE;
            default:
                return PASS_ORDERED;
        }
    }

    /**
     * Resolves a blend mode from serialized numeric ids.
     */
    public static BlendMode fromId(int id) {
        switch (id) {
            case 0:
                return OPAQUE;
            case 1:
                return ALPHA;
            case 2:
                return PREMULT_ALPHA;
            case 3:
                return ADDITIVE;
            case 4:
                return ADDITIVE_ALPHA;
            case 5:
                return MULTIPLY;
            case 6:
                return MULTIPLY_ALPHA;
            case 7:
                return CUTOUT;
            default:
                return OPAQUE;
        }
    }

    /**
     * Returns the shared list of blend mode names.
     * Callers must treat the returned array as read-only.
     */
    public static Array<String> blendNames() {
        return BLEND_NAMES;
    }
}
