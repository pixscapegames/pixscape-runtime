package games.pixscape.runtime.api;

/**
 * One spawned Game Object instance in the current Runtime World.
 *
 * <p>The instance is anchored to its real root entity. It becomes stale when that
 * root is removed or the World is replaced. {@link #despawn()} removes the complete
 * surviving hierarchy and dependent Physics joints; repeated calls on a stale
 * instance have no effect.</p>
 */
public interface GameObjectInstance {

    /** Returns the real Game Object root captured when this instance was spawned. */
    EntityRef root();

    /** Returns whether the captured root entity is still current in its Runtime World. */
    boolean exists();

    /** Schedules safe removal of this instance's surviving hierarchy and dependent joints. */
    void despawn();
}
