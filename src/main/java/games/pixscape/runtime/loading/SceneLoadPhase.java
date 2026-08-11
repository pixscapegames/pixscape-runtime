package games.pixscape.runtime.loading;

/** {@code HIGH_LEVEL} phases reported by a progressive scene load. */
public enum SceneLoadPhase {
    /** Project and scene files plus their declared resources are being read. */
    FILES,
    /** The detached scene candidate is being deserialized and prepared. */
    SCENE,
    /** Runtime services and the candidate World are being prepared and published. */
    RUNTIME,
    /** The requested scene is active and all known heavyweight preparation is complete. */
    READY
}
