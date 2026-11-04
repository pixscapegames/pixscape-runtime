package games.pixscape.runtime.api;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.hierarchy.GameObjectTopologyState;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;

/** API-owned scratch for the indexed removal closure. No World or handle is retained here. */
final class HierarchyRemoval {
    private final IntSet removed = new IntSet();
    private final IntArray queue = new IntArray(false, 16);

    void schedule(World world, int entityId) {
        if (world == null || !world.getEntityManager().isActive(entityId)) return;
        removed.clear();
        queue.clear();
        add(entityId);
        GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        GameObjectTopologyState topology = hierarchy != null ? hierarchy.topologyForRemoval() : null;
        Box2dSyncSystem physics = world.getSystem(Box2dSyncSystem.class);
        for (int cursor = 0; cursor < queue.size; cursor++) {
            int current = queue.get(cursor);
            if (topology != null && current < topology.getEntityCapacity()) {
                for (int child = topology.firstChildEntityId[current]; child >= 0;
                     child = topology.nextSiblingEntityId[child]) add(child);
            }
            if (physics != null) {
                if (world.getMapper(PhysicsBodyComponent.class).has(current)) {
                    addAll(physics.jointDependencies().jointsForBody(current));
                }
                addAll(physics.jointDependencies().gearsForSource(current));
            }
        }
        // Collect first: no ECS mutation can change a borrowed adjacency list above.
        for (int i = queue.size - 1; i >= 0; i--) world.delete(queue.get(i));
    }

    private void addAll(IntArray ids) {
        if (ids == null) return;
        for (int i = 0; i < ids.size; i++) add(ids.get(i));
    }

    private void add(int entity) {
        if (entity >= 0 && removed.add(entity)) queue.add(entity);
    }
}
