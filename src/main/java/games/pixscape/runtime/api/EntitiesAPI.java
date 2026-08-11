package games.pixscape.runtime.api;

/**
 * High-level entity identity access.
 *
 * <p>Use {@code stableId} as the preferred persistent/public identity.
 * {@code entityId} is runtime/ECS-oriented and may be short-lived or recycled.</p>
 */
public interface EntitiesAPI {
    /**
     * Returns a tolerant reference bound to the current incarnation of the provided
     * runtime {@code entityId}. An absent or later-stale reference is inert.
     */
    EntityRef ofEntityId(int entityId);

    /**
     * Returns a tolerant reference bound to the entity currently resolved from a
     * persistent/public {@code stableId}. It does not follow later reuse or replacement.
     */
    EntityRef ofStableId(int stableId);

    /**
     * Same as {@link #ofEntityId(int)} but throws when the entity does not exist at
     * acquisition time. The returned reference can later become stale and inert.
     */
    EntityRef requireEntityId(int entityId);

    /**
     * Same as {@link #ofStableId(int)} but throws when not found at acquisition time.
     * The returned reference can later become stale and inert.
     */
    EntityRef requireStableId(int stableId);

    /**
     * Strictly resolves the entity currently indexed by {@code tag}.
     * The returned reference can later become stale and inert.
     */
    EntityRef requireTag(String tag);

    /**
     * Strictly resolves the entity currently indexed by {@code name}.
     * The returned reference can later become stale and inert.
     */
    EntityRef requireName(String name);

    /**
     * Resolves runtime {@code entityId} from {@code stableId}, or {@code -1} when missing.
     */
    int entityIdOf(int stableId);

    int findEntityId(int stableId, int defaultValue);

    /**
     * Resolves persistent/public {@code stableId} from runtime {@code entityId}.
     */
    int stableIdOf(int entityId);

    /**
     * Ensures and returns a stableId for the runtime {@code entityId}.
     */
    int ensureStableId(int entityId);

    boolean existsEntityId(int entityId);

    boolean existsStableId(int stableId);

    /**
     * Schedules entity destruction through ECS deletion.
     *
     * <p>Effects become fully visible after the next normal world processing step
     * (for example the next engine {@code render()} call that processes the world).</p>
     */
    void destroy(EntityRef ref);

    /**
     * Same semantics as {@link #destroy(EntityRef)} for a runtime entityId.
     */
    void destroyEntityId(int entityId);

    /**
     * Same semantics as {@link #destroy(EntityRef)} for a stableId lookup.
     */
    void destroyStableId(int stableId);
}
