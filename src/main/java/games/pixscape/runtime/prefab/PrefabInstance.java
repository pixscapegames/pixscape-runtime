package games.pixscape.runtime.prefab;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;

public final class PrefabInstance {
    private final IntArray entityIds = new IntArray();
    private final IntIntMap localToEntity = new IntIntMap();

    public void addMapping(int localId, int entityId) {
        localToEntity.put(localId, entityId);
        entityIds.add(entityId);
    }

    public int getEntityForLocalId(int localId) {
        return localToEntity.get(localId, -1);
    }

    public IntArray getEntityIds() {
        return entityIds;
    }
}
