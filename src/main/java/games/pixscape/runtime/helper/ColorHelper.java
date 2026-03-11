package games.pixscape.runtime.helper;

import com.badlogic.gdx.graphics.Color;

/** Color helpers for {@code 0..1 <-> int}, aligned with LibGDX {@code rgba8888} conventions. */
public final class ColorHelper {
    private ColorHelper() {}

    /**
     * Packs into LibGDX {@code rgba8888} format:
     *
     * {@code (r << 24) | (g << 16) | (b << 8) | a}
     */
    public static int packRGBA8888(float r, float g, float b, float a) {
        int R = (int)(r * 255.0f) & 0xFF;
        int G = (int)(g * 255.0f) & 0xFF;
        int B = (int)(b * 255.0f) & 0xFF;
        int A = (int)(a * 255.0f) & 0xFF;
        return (R << 24) | (G << 16) | (B << 8) | A;
    }

    /** Packs from a {@link Color} (same as {@code Color.rgba8888(color)}). */
    public static int packRGBA8888(Color c) {
        return Color.rgba8888(c);
    }

    /**
     * Unpacks an {@code rgba8888} int into {@code [r,g,b,a]} in the {@code 0..1} range.
     *
     * {@code rgbaOut4} must be an array with size {@code >= 4}.
     */
    public static void unpackRGBA8888(int rgba, float[] rgbaOut4) {
        rgbaOut4[0] = ((rgba >>> 24) & 0xFF) / 255f; // r
        rgbaOut4[1] = ((rgba >>> 16) & 0xFF) / 255f; // g
        rgbaOut4[2] = ((rgba >>>  8) & 0xFF) / 255f; // b
        rgbaOut4[3] = ( rgba         & 0xFF) / 255f; // a
    }

    /** Unpacks into a reusable {@link Color}. */
    public static void unpackRGBA8888(int rgba, Color out) {
        Color.rgba8888ToColor(out, rgba);
    }
}
