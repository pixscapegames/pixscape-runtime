package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.graphics.Color;


public final class TintComponent extends PooledComponent {

    /**
     * Color stored in LibGDX rgba8888 format:
     *
     *   Color.rgba8888(r, g, b, a)
     *   or Color.rgba8888(Color)
     *
     * Default: opaque white.
     */
    public int rgba = Color.rgba8888(Color.WHITE);

    @Override
    protected void reset() {
        rgba = Color.rgba8888(Color.WHITE);
    }

    /** Returns the raw color in rgba8888 format. */
    public int getRgba() {
        return rgba;
    }

    /** Fills a provided Color with the current value. */
    public Color toColor(Color out) {
        Color.rgba8888ToColor(out, rgba);
        return out;
    }

    /** Returns a new Color (avoid in the hot path when possible). */
    public Color toColor() {
        Color c = new Color();
        Color.rgba8888ToColor(c, rgba);
        return c;
    }
}
