package games.pixscape.runtime.prefab;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;

public class RuntimePrefabFragmentSpawner {

    private final IdentityRegistry identityRegistry;

    public RuntimePrefabFragmentSpawner(IdentityRegistry identityRegistry) {
        if (identityRegistry == null) throw new IllegalArgumentException("identityRegistry must not be null");
        this.identityRegistry = identityRegistry;
    }

    public SpawnResult spawn(World world, SaveFileFormat fragment, float offsetX, float offsetY) {
        if (world == null) throw new IllegalArgumentException("world must not be null");
        if (fragment == null) throw new IllegalArgumentException("fragment must not be null");

        EntitySubscription subAll = world.getAspectSubscriptionManager().get(Aspect.all());
        Set<Integer> before = toSet(subAll.getEntities());

        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        if (wsm == null) throw new IllegalStateException("WorldSerializationManager is required");
        if (!(wsm.getSerializer() instanceof JsonArtemisSerializer)) {
            wsm.setSerializer(new JsonArtemisSerializer(world));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, fragment);
        wsm.load(new ByteArrayInputStream(out.toByteArray()), SaveFileFormat.class);

        // No world.process() here: loading mutates entity storage immediately, and we intentionally
        // avoid advancing gameplay systems during spawn.
        IntBag after = subAll.getEntities();
        IntBag created = new IntBag();

        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        ComponentMapper<PixscapeIdentityComponent> mIdentity = world.getMapper(PixscapeIdentityComponent.class);

        identityRegistry.bind(world);

        for (int i = 0; i < after.size(); i++) {
            int eid = after.get(i);
            if (before.contains(eid)) continue;

            created.add(eid);

            TransformComponent t = mTransform.get(eid);
            if (t != null) {
                t.x += offsetX;
                t.y += offsetY;
            }

            PixscapeIdentityComponent id = mIdentity.get(eid);
            if (id != null) {
                id.stableId = -1L;
            }

            identityRegistry.ensureStableId(eid);
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            for (int i = 0; i < created.size(); i++) {
                dirty.mark(created.get(i), DirtyBits.GEOMETRY | DirtyBits.MATERIAL | DirtyBits.COLOR | DirtyBits.ORDER | DirtyBits.LAYER | DirtyBits.PHYSICS | DirtyBits.JOINTS);
            }
        }

        return new SpawnResult(created);
    }

    private static Set<Integer> toSet(IntBag bag) {
        Set<Integer> out = new HashSet<>(Math.max(16, bag.size() * 2));
        for (int i = 0; i < bag.size(); i++) out.add(bag.get(i));
        return out;
    }
}
