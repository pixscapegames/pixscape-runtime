package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Optional overrides applied to the emitter at render time.
 * No dirty flag: particle rendering is recomputed each frame.
 */
public final class ParticleOverridesComponent extends PooledComponent {

    /**
     * Enables/disables applying overrides.
     */
    public boolean enabled = true;

    /**
     * Multiplies particle size (>=0).
     */
    public float sizeMul = 1f;

    /**
     * Multiplie l'alpha (>=0).
     */
    public float alphaMul = 1f;

    /**
     * -1 = none, sinon RGBA packed int.
     */
    public int tintRgba = -1;

    @Override
    protected void reset() {
        enabled = true;
        sizeMul = 1f;
        alphaMul = 1f;
        tintRgba = -1;
    }
}
