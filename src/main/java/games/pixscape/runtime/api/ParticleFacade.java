package games.pixscape.runtime.api;

/**
 * High-level particle emitter controls for one entity.
 * Setters may establish the minimum authored transform and emitter capability.
 */
public interface ParticleFacade {
    boolean exists();

    /**
     * Changes the authored particle resource request.
     *
     * <p>Runtime realizes the request during synchronization, so it may not become live
     * immediately. If preparation fails, the previous pooled effect remains active and Runtime
     * retries while the authored and live identities differ. Successful replacement is atomic;
     * the caller never owns or disposes the pooled effect.</p>
     *
     * @throws IllegalArgumentException when the effect path or atlas tag is blank
     */
    ParticleFacade setEffect(String effectPath, String atlasTag);

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
