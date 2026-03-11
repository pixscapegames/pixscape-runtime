package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.graphics.Color;


public final class TintComponent extends PooledComponent {

    /**
     * Couleur stockée au format LibGDX rgba8888 :
     *
     *   Color.rgba8888(r, g, b, a)
     *   ou Color.rgba8888(Color)
     *
     * Par défaut : blanc opaque.
     */
    public int rgba = Color.rgba8888(Color.WHITE);

    @Override
    protected void reset() {
        rgba = Color.rgba8888(Color.WHITE);
    }

    /** Retourne la couleur brute au format rgba8888. */
    public int getRgba() {
        return rgba;
    }

    /** Remplit un Color fourni avec la valeur courante. */
    public Color toColor(Color out) {
        Color.rgba8888ToColor(out, rgba);
        return out;
    }

    /** Retourne un nouveau Color (à éviter dans la hot path si possible). */
    public Color toColor() {
        Color c = new Color();
        Color.rgba8888ToColor(c, rgba);
        return c;
    }
}
