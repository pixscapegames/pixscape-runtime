package games.pixscape.runtime.component.light;

import com.artemis.PooledComponent;

public class PointLightComponent extends PooledComponent {

    // Linear color
    public float r = 1f;
    public float g = 1f;
    public float b = 1f;

    // Global intensity
    public float intensity = 1f;

    // Rayon en world units
    public float radius = 200f;

    // Attenuation exponent (1 = linear, 2 = harder)
    public float falloff = 1.5f;

    // Enabled flag (important for the editor)
    public boolean enabled = true;
    /** Common XYZ pixel units; independent of sprite dimensions and Spatial ordering. */
    public float height = 128f;
    /** 0: unshadowed, 1: hard, 2: 3x3 PCF. */
    public int shadowQuality = 1;
    public int shadowResolution = 512;

    @Override
    protected void reset() {
        r = 1f;
        g = 1f;
        b = 1f;
        intensity = 1f;
        radius = 200f;
        falloff = 1.5f;
        enabled = true;
        height = 128f; shadowQuality = 1; shadowResolution = 512;
    }
}
