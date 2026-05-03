package games.pixscape.runtime.prefab;

import com.artemis.utils.IntBag;

public class SpawnResult {

    private final IntBag createdEntityIds;

    public SpawnResult(IntBag createdEntityIds) {
        this.createdEntityIds = createdEntityIds;
    }

    public IntBag createdEntityIds() {
        return createdEntityIds;
    }
}
