package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.math.Affine2;

final class HudBatchVertexWriter {
    static final int LIBGDX_FLOATS_PER_VERTEX = 5;
    static final int HUD_FLOATS_PER_VERTEX = 6;
    static final int LIBGDX_FLOATS_PER_QUAD = 20;
    static final int HUD_FLOATS_PER_QUAD = 24;

    private HudBatchVertexWriter() {
    }

    static int copyWithLayer(float[] source, int sourceOffset, int sourceCount,
                             float[] target, int targetOffset, float layer,
                             Affine2 adjustment) {
        int sourceEnd = sourceOffset + sourceCount;
        int targetIndex = targetOffset;
        for (int sourceIndex = sourceOffset; sourceIndex < sourceEnd;
             sourceIndex += LIBGDX_FLOATS_PER_VERTEX) {
            float x = source[sourceIndex];
            float y = source[sourceIndex + 1];
            if (adjustment == null) {
                target[targetIndex++] = x;
                target[targetIndex++] = y;
            } else {
                target[targetIndex++] = adjustment.m00 * x + adjustment.m01 * y + adjustment.m02;
                target[targetIndex++] = adjustment.m10 * x + adjustment.m11 * y + adjustment.m12;
            }
            target[targetIndex++] = source[sourceIndex + 2];
            target[targetIndex++] = source[sourceIndex + 3];
            target[targetIndex++] = source[sourceIndex + 4];
            target[targetIndex++] = layer;
        }
        return targetIndex - targetOffset;
    }
}

