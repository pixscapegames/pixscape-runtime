package games.pixscape.runtime.api;

/** Scene-owned immutable definition access; this factory is not an instance pool or reset policy. */
public interface GameObjectFactory {
    /** Same complete native-readiness and failure-cleanup contract as GameObjectsAPI.spawn. */
    GameObjectInstance spawn(float x, float y);

    /** Authored counts for reservation, not a membership list for existing instances. */
    int nodeCount();

    int jointCount();
}
