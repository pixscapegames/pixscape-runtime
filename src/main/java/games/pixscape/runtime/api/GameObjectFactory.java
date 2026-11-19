package games.pixscape.runtime.api;

/** Scene-owned immutable definition access; this factory is not an instance pool or reset policy. */
public interface GameObjectFactory {
    /** Uses scene-layer index 0 with the same destination, native-readiness and cleanup checks as GameObjectsAPI.spawn. */
    default GameObjectInstance spawn(float x, float y) {
        return spawn(x, y, 0);
    }

    /** Same destination/admission contract as {@link GameObjectsAPI#spawn(String, float, float, int)}. */
    GameObjectInstance spawn(float x, float y, int layerIndex);

    /** Authored counts for reservation, not a membership list for existing instances. */
    int nodeCount();

    int jointCount();
}
