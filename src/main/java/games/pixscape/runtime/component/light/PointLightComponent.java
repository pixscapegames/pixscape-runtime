package games.pixscape.runtime.component.light;

import com.artemis.PooledComponent;

public class PointLightComponent extends PooledComponent {

    // Couleur linéaire
    public float r = 1f;
    public float g = 1f;
    public float b = 1f;

    // Intensité globale
    public float intensity = 1f;

    // Rayon en world units
    public float radius = 200f;

    // Exposant d’atténuation (1 = linéaire, 2 = plus dur)
    public float falloff = 1.5f;

    // Enabled flag (important pour l’éditeur)
    public boolean enabled = true;

    @Override
    protected void reset() {
        r = 1f;
        g = 1f;
        b = 1f;
        intensity = 1f;
        radius = 200f;
        falloff = 1.5f;
        enabled = true;
    }
}
