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
     * <p>The replacement must already be prepared by scene Runtime Availability. Successful
     * replacement remains atomic; the caller never owns or disposes the pooled effect.</p>
     *
     * @throws IllegalArgumentException when the effect path or atlas tag is blank
     * @throws IllegalStateException when the requested effect was not prepared before READY
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
