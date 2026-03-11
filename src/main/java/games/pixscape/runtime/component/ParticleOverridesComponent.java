package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Overrides optionnels appliqués à l'émetteur au moment du rendu.
 * Pas de dirty: le rendu des particules est recalculé chaque frame.
 */
public final class ParticleOverridesComponent extends PooledComponent {

    /** Active/désactive l'application des overrides. */
    public boolean enabled = true;

    /** Multiplie la taille des particules (>=0). */
    public float sizeMul = 1f;

    /** Multiplie l'alpha (>=0). */
    public float alphaMul = 1f;

    /** -1 = none, sinon RGBA packed int. */
    public int tintRgba = -1;

    @Override
    protected void reset() {
        enabled = true;
        sizeMul = 1f;
        alphaMul = 1f;
        tintRgba = -1;
    }
}
