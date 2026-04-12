package games.pixscape.runtime.api;

/**
 * Read-only light component presence checks for one entity.
 */
public interface LightFacade {
    boolean hasPoint();
    boolean hasCone();
}
