package games.pixscape.runtime.api;

/**
 * Spawns particle emitters from resources prepared by scene Runtime Availability.
 * Gameplay calls never initiate particle file or atlas preparation.
 */
public interface ParticlesAPI {
    /** @throws IllegalStateException if the effect was not prepared before READY */
    ParticleRef spawn(String effectPathOrName, float x, float y);

    /** @throws IllegalStateException if the effect was not prepared before READY */
    ParticleRef oneshot(String effectPathOrName, float x, float y);
}
