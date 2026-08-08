package games.pixscape.runtime.loading;

/** User-facing phases of a complete progressive scene load. */
public enum SceneLoadPhase {
    FILES,
    SCENE,
    RUNTIME,
    /** The requested scene is active and all known heavyweight preparation is complete. */
    READY
}
