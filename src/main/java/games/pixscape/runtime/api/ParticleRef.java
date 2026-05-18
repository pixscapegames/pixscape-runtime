package games.pixscape.runtime.api;

public interface ParticleRef {
    int entityId();

    EntityRef entity();

    TransformFacade transform();

    ParticleFacade particles();

    ParticleRef play();

    ParticleRef pause();

    ParticleRef stop();

    ParticleRef loop(boolean loop);

    ParticleRef scale(float scale);

    void remove();
}
