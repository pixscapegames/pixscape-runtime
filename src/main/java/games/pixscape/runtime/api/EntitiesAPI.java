package games.pixscape.runtime.api;

public interface EntitiesAPI {
    EntityRef ofEntityId(int entityId);
    EntityRef ofStableId(long stableId);

    EntityRef requireEntityId(int entityId);
    EntityRef requireStableId(long stableId);
    EntityRef requireTag(String tag);
    EntityRef requireName(String name);

    int entityIdOf(long stableId);
    int findEntityId(long stableId, int defaultValue);

    long stableIdOf(int entityId);
    long ensureStableId(int entityId);

    boolean existsEntityId(int entityId);
    boolean existsStableId(long stableId);

    void destroy(EntityRef ref);
    void destroyEntityId(int entityId);
    void destroyStableId(long stableId);
}
