package games.pixscape.runtime.api;

/**
 * High-level particle emitter controls for one entity.
 */
public interface ParticleFacade {
    boolean exists();

    ParticleFacade setEffect(String effectPath, String atlasTag);

    ParticleFacade setLocalSpace(boolean localSpace);

    ParticleFacade setLooping(boolean looping);

    ParticleFacade setAutoStart(boolean autoStart);

    ParticleFacade play();

    ParticleFacade pause();

    ParticleFacade resume();

    ParticleFacade restart();

    ParticleFacade stop();

    boolean isPaused();

    boolean isLooping();
}
