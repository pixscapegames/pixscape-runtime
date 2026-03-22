package games.pixscape.runtime.component.light;

import com.artemis.PooledComponent;

public class ConeLightComponent extends PooledComponent {

    // Linear color
    public float r = 1f;
    public float g = 1f;
    public float b = 1f;

    // Global intensity
    public float intensity = 1f;

    // Rayon en world units
    public float radius = 200f;

    // Angle total (deg)
    public float coneAngleDeg = 45f;

    // Direction (deg, 0 = +X)
    public float rotationDeg = 0f;

    // Cone edge softening (0..1)
    public float softness = 0.1f;

    // Attenuation exponent (1 = linear, 2 = harder)
    public float falloff = 1.5f;

    // Enabled flag (important for the editor)
    public boolean enabled = true;

    @Override
    protected void reset() {
        r = 1f;
        g = 1f;
        b = 1f;
        intensity = 1f;
        radius = 200f;
        coneAngleDeg = 45f;
        rotationDeg = 0f;
        softness = 0.1f;
        falloff = 1.5f;
        enabled = true;
    }
}
