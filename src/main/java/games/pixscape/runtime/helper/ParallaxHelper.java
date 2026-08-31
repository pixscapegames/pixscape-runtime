package games.pixscape.runtime.helper;

import com.badlogic.gdx.math.Vector2;

public final class ParallaxHelper {

    private ParallaxHelper() {
    }

    /**
     * Convention:
     * - {@code factor = 1} means no parallax ({@code offset 0,0})
     * - {@code factor < 1} means a "far" layer (moves slower than the camera)
     * - {@code factor > 1} means a "near" layer (moves faster than the camera)
     */
    public static void computeParallaxOffset(
            float cameraX,
            float cameraY,
            float factorX,
            float factorY,
            Vector2 out
    ) {
        float offsetX = (1f - factorX) * cameraX;
        float offsetY = (1f - factorY) * cameraY;
        out.set(offsetX, offsetY);
    }

}
