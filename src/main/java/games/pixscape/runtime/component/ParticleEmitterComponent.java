package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Describes a particle emitter based on a LibGDX .p file.
 * This component is serializable (only simple types).
 * Runtime is responsible for creating actual ParticleEffect objects from this.
 */
public final class ParticleEmitterComponent extends PooledComponent {

    /**
     * Relative path to the .p file (e.g.: "particles/fire.p").
     */
    public String effectPath;

    /**
     * Atlas tag to use for particle sprites (e.g.: "MainScene" or "Particles").
     */
    public String atlasTag;

    /**
     * Whether the emitter follows the entity (Transform).
     */
    public boolean localSpace = true;

    /**
     * Automatically start when the entity appears.
     */
    public boolean autoStart = true;

    /**
     * Loop indefinitely. If false: plays once then remains complete.
     */
    public boolean looping = true;

    /**
     * Runtime one-shot lifecycle helper. Scene-authored emitters keep this false.
     */
    public boolean autoRemoveWhenComplete = false;

    public boolean paused = false;
    public boolean playRequested = false;
    public boolean restartRequested = false;

    public ParticleEmitterComponent() {
    }

    @Override
    protected void reset() {
        effectPath = "";
        atlasTag = "";
        localSpace = true;
        looping = true;
        autoRemoveWhenComplete = false;
        autoStart = true;
        paused = false;
        playRequested = false;
        restartRequested = false;
    }

    public ParticleEmitterComponent(String effectPath, String atlasTag) {
        this.effectPath = effectPath;
        this.atlasTag = atlasTag;
    }
}
