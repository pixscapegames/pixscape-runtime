package games.pixscape.runtime.gameobject;

import com.artemis.utils.IntBag;

public class SpawnResult {

    private final IntBag createdEntityIds;
    private final int rootEntityId;

    public SpawnResult(IntBag createdEntityIds) {
        this(createdEntityIds, createdEntityIds != null && createdEntityIds.size() > 0
                ? createdEntityIds.get(0) : -1);
    }

    public SpawnResult(IntBag createdEntityIds, int rootEntityId) {
        this.createdEntityIds = createdEntityIds;
        this.rootEntityId = rootEntityId;
    }

    public IntBag createdEntityIds() {
        return createdEntityIds;
    }

    public int rootEntityId() {
        return rootEntityId;
    }
}
