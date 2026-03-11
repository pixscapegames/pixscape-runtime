package games.pixscape.runtime.api;

/**
 * Runtime module extension point, conceptually similar to a HyperLap2D plugin.
 * Implementations register systems, editable components, render hooks, and post-processing passes.
 */
public interface RuntimeModule {
    /**
     * Registers this module into the runtime API bootstrap.
     */
    void register(RuntimeAPI api);
}
