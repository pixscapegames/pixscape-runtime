package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Runtime only: resolved UV/sizes (not serialized).
 */
public final class TextureRegionComponent extends PooledComponent {
    public transient float u1, v1, u2, v2;  // normalized UV
    public transient int pixW, pixH;        // packed size (optional)
    public transient boolean valid;         // true if resolved

    @Override
    protected void reset() {
        u1 = v1 = u2 = v2 = 0f;
        pixW = pixH = 0;
        valid = false;
    }
}
