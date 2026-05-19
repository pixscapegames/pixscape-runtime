package games.pixscape.runtime.api;

public interface ParticlesAPI {
    ParticleRef spawn(String effectPathOrName, float x, float y);

    ParticleRef oneshot(String effectPathOrName, float x, float y);
}
