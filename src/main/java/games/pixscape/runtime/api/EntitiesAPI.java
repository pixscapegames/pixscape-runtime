package games.pixscape.runtime.api;

/**
 * High-level entity identity access.
 *
 * <p>Use {@code stableId} as the preferred persistent/public identity.
 * {@code entityId} is runtime/ECS-oriented and may be short-lived or recycled.</p>
 */
public interface EntitiesAPI {
    /**
     * Returns a reference bound to the provided runtime {@code entityId}.
     */
    EntityRef ofEntityId(int entityId);

    /**
     * Returns a reference resolved from a persistent/public {@code stableId}.
     */
    EntityRef ofStableId(int stableId);

    /**
     * Same as {@link #ofEntityId(int)} but throws when the entity does not exist.
     */
    EntityRef requireEntityId(int entityId);

    /**
     * Same as {@link #ofStableId(int)} but throws when not found.
     */
    EntityRef requireStableId(int stableId);

    EntityRef requireTag(String tag);

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
