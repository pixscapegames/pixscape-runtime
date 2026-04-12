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

    BlendMode(int id, boolean blending, int srcFactor, int dstFactor) {
        this.id = id;
        this.blending = blending;
        this.srcFactor = srcFactor;
        this.dstFactor = dstFactor;
    }

    /**
     * Returns the render pass family used for sorting and batching.
     */
    public int passId() {
        return switch (this) {
            case OPAQUE, CUTOUT -> PASS_OPAQUE;
            case ADDITIVE, ADDITIVE_ALPHA, MULTIPLY -> PASS_COMMUTATIVE;
            default -> PASS_ORDERED;
        };
    }

    /**
     * Resolves a blend mode from serialized numeric ids.
     */
    public static BlendMode fromId(int id) {
        return switch (id) {
            case 0 -> OPAQUE;
            case 1 -> ALPHA;
            case 2 -> PREMULT_ALPHA;
            case 3 -> ADDITIVE;
            case 4 -> ADDITIVE_ALPHA;
            case 5 -> MULTIPLY;
            case 6 -> MULTIPLY_ALPHA;
            case 7 -> CUTOUT;
            default -> OPAQUE;
        };
    }

    /**
     * Returns all blend mode enum names in declaration order.
     */
    public static Array<String> blendNames() {
        BlendMode[] modes = values();
        var names = new Array<String>(modes.length);
        for (int i = 0, n = modes.length; i < n; i++) names.add(modes[i].name());
        return names;
    }
}
