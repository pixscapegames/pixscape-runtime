package games.pixscape.runtime.api;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;

/** One-shot internal closure builder for safe entity and hierarchy removal. */
final class HierarchyRemoval {
    private HierarchyRemoval() {
    }

    static void schedule(World world, int entityId) {
        if (world == null || !world.getEntityManager().isActive(entityId)) return;

        IntSet removed = new IntSet();
        IntArray queue = new IntArray(false, 8);
        add(entityId, removed, queue);

        ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<GameObjectMemberComponent> members = world.getMapper(GameObjectMemberComponent.class);
        IntBag memberEntities = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectMemberComponent.class)).getEntities();
        int[] memberData = memberEntities.getData();

        for (int cursor = 0; cursor < queue.size; cursor++) {
            PixscapeIdentityComponent parentIdentity = identities.getSafe(queue.get(cursor), null);
            if (parentIdentity == null || parentIdentity.stableId <= 0) continue;
            int parentStableId = parentIdentity.stableId;
            for (int i = 0, n = memberEntities.size(); i < n; i++) {
                int candidate = memberData[i];
                GameObjectMemberComponent member = members.get(candidate);
                if (member.parentStableId == parentStableId) add(candidate, removed, queue);
            }
        }

        ComponentMapper<PhysicsBodyComponent> bodies = world.getMapper(PhysicsBodyComponent.class);
        ComponentMapper<PhysicsJointComponent> joints = world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> gears = world.getMapper(PhysicsGearJointComponent.class);
        IntBag jointEntities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class)).getEntities();
        int[] jointData = jointEntities.getData();

        boolean changed;
        do {
            changed = false;
            for (int i = 0, n = jointEntities.size(); i < n; i++) {
                int jointEntityId = jointData[i];
                if (removed.contains(jointEntityId)) continue;
                PhysicsJointComponent joint = joints.get(jointEntityId);
                if ((bodies.has(joint.aEid) && removed.contains(joint.aEid))
                        || (bodies.has(joint.bEid) && removed.contains(joint.bEid))) {
                    add(jointEntityId, removed, queue);
                    changed = true;
                }
            }
            for (int i = 0, n = jointEntities.size(); i < n; i++) {
                int jointEntityId = jointData[i];
                if (removed.contains(jointEntityId)) continue;
                PhysicsGearJointComponent gear = gears.getSafe(jointEntityId, null);
                if (gear != null && (removed.contains(gear.joint1Eid)
                        || removed.contains(gear.joint2Eid))) {
                    add(jointEntityId, removed, queue);
                    changed = true;
                }
            }
        } while (changed);

        for (int i = 0; i < queue.size; i++) world.delete(queue.get(i));
    }

    private static void add(int entityId, IntSet removed, IntArray queue) {
        if (entityId >= 0 && removed.add(entityId)) queue.add(entityId);
    }
}
