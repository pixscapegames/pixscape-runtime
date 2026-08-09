package games.pixscape.runtime.api;

/**
 * Particle handle bound to one entity incarnation and Runtime World.
 * It becomes inert if that entity is removed or the World is replaced.
 */
public interface ParticleRef {
    int entityId();

    EntityRef entity();

    TransformFacade transform();

    ParticleFacade particles();

    ParticleRef play();

    ParticleRef pause();

    ParticleRef stop();

    /**
     * Controls continuous emission without removing the particle entity; a
     * persistent particle can later be restarted.
     */
    ParticleRef loop(boolean loop);

    ParticleRef scale(float scale);

    void remove();
}
