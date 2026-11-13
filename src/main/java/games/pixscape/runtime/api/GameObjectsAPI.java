package games.pixscape.runtime.api;

import java.util.function.Consumer;
import com.badlogic.gdx.files.FileHandle;

/**
 * Runtime API for synchronously spawning exported Game Object assets into the active scene.
 *
 * <p>Runtime Availability makes an asset definition available but does not place an instance.
 * Call only after project loading and scene {@code READY}. Accepted names are
 * {@code enemy}, {@code enemy.gameobject}, and the canonical recommended form
 * {@code gameobjects/enemy.gameobject}. The scene owns a validated, prepared definition until
 * scene/project replacement or disposal. Spawning never rereads that definition; each instance
 * gets independent mutable state and fresh identities. Unprepared types fail immediately;
 * explicitly available on-demand files can be registered with {@link #prepare(String, FileHandle)}.
 * Missing or invalid declared definitions and unavailable visual bindings fail during scene preparation.</p>
 */
public interface GameObjectsAPI {

    /**
     * Acquires a reusable factory for a definition prepared automatically before scene READY.
     * Normalizes the name once. The factory is invalid after scene retirement or replacement
     * of this asset's prepared definition. Each spawn still creates independent mutable state.
     */
    GameObjectFactory prepare(String name);

    /**
     * Explicit cold preparation/reload of an already downloaded/readable exported definition.
     * Its visual dependencies must already exist in the current scene atlas. Does no downloading
     * or atlas publication. Failure preserves the previous definition; success invalidates old
     * factories for this name, without changing existing instances. Owns a detached parsed model,
     * not the supplied file. Available on the normal engine thread after scene READY.
     */
    GameObjectFactory prepare(String name, FileHandle readyFile);

    /**
     * Reserves World-owned temporary placement storage, including ancestors in nodeCapacity.
     * Does not create entities, preallocate native physics, or reset pooled instances.
     * Capacity grows if exceeded. Reuse the caller's roots array to avoid its own allocations.
     * Pulley replacement and exception diagnostics may still allocate. Not thread-safe/reentrant.
     */
    void reservePlacement(int nodeCapacity, int jointCapacity);

    /**
     * Rigidly places the current subtrees rooted at the supplied live references. Overlapping roots
     * are counted once. Positions/pivot/translation use world pixels and rotation uses radians.
     * The operation rotates around the pivot, then translates. Linear velocities rotate with it;
     * angular velocities survive, unless resetVelocities explicitly clears both.
     *
     * <p>Every configured Body and incident joint must be realized, and every joint dependency must
     * remain inside the explicitly selected perimeter. Rejection precedes mutation. Call outside
     * Box2D callbacks/stepping, after structural publication; no World process or step is performed.</p>
     */
    void place(EntityRef[] roots, float pivotX, float pivotY, float dx, float dy,
               float rotationRad, boolean resetVelocities);

    /**
     * Spawns an independent instance with its real root at the given world position.
     * Internal transforms remain local to the hierarchy; the asset definition is not mutated.
     * Composed transforms, materializable bodies and all authored joints are ready on return.
     * This does not advance simulation or process the World. Members without materializable
     * physics still have no native body. Failure rolls back only this attempt.
     *
     * @param name Game Object name or canonical logical asset ID
     * @param x    world-space X coordinate of the new root
     * @param y    world-space Y coordinate of the new root
     * @return an incarnation-safe handle for the newly spawned instance
     * @throws IllegalArgumentException if either position coordinate is not finite
     * @throws IllegalStateException    if physical creation is required while Box2D is locked;
     *                                  this is checked before allocating entities or identities. Use {@link #spawnDeferred}
     *                                  from contact callbacks instead. Assets without physics remain allowed.
     */
    GameObjectInstance spawn(String name, float x, float y);

    /**
     * Requests a complete spawn in the current READY scene. Safe to enqueue from contact callbacks.
     * Requests run in FIFO order at the start of the next normal engine render, before World
     * systems, on that render's calling thread and outside the Box2D lock. Requests queued by
     * callbacks during delivery wait for another render. No background work or forced step occurs.
     *
     * <p>Exactly one callback is invoked: success only after the complete instance is ready,
     * or failure after rollback. Undeclared names and preparation errors reach failure.
     * Scene retirement, World replacement and disposal cancel outstanding requests through
     * failure on the lifecycle caller's thread; requests never migrate to another scene.
     * A scene load that fails before retiring the active scene does not cancel its requests.
     * Without further renders or retirement, accepted requests remain queued.</p>
     *
     * <p>Both callbacks are required. Callback exceptions are logged, do not cause a second
     * notification, and do not roll back a successfully delivered instance. Callbacks may
     * enqueue further requests; this API, like the engine, is not thread-safe.</p>
     *
     * @throws IllegalArgumentException if either callback is null
     * @throws IllegalStateException    if no scene is READY when the request is submitted
     */
    void spawnDeferred(String name, float x, float y,
                       Consumer<GameObjectInstance> success, Consumer<Throwable> failure);

}
