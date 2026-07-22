package games.pixscape.runtime.prefab;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RuntimePrefabFragmentSpawner {

    private final IdentityRegistry identityRegistry;

    public RuntimePrefabFragmentSpawner(IdentityRegistry identityRegistry) {
        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null");
        }
        this.identityRegistry = identityRegistry;
    }

    public SpawnResult spawn(World world, SaveFileFormat fragment, float offsetX, float offsetY) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (fragment == null) {
            throw new IllegalArgumentException("fragment must not be null");
        }

        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        if (wsm == null) {
            throw new IllegalStateException("WorldSerializationManager is required");
        }
        if (!(wsm.getSerializer() instanceof JsonArtemisSerializer)) {
            wsm.setSerializer(new JsonArtemisSerializer(world));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, fragment);

        SaveFileFormat loaded =
                wsm.load(new ByteArrayInputStream(out.toByteArray()), SaveFileFormat.class);

        IntBag created = new IntBag();
        for (int i = 0; i < loaded.entities.size(); i++) {
            created.add(loaded.entities.get(i));
        }

        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        ComponentMapper<PixscapeIdentityComponent> mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        remapFixtureIdentities(world, created);

        identityRegistry.bind(world);

        for (int i = 0; i < created.size(); i++) {
            int eid = created.get(i);

            TransformComponent t = mTransform.get(eid);
            if (t != null) {
                t.x += offsetX;
                t.y += offsetY;
            }

            PixscapeIdentityComponent id = mIdentity.get(eid);
            if (id != null) {
                id.stableId = IdentityRegistry.UNASSIGNED_STABLE_ID;
            }

            identityRegistry.ensureStableId(eid);
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            for (int i = 0; i < created.size(); i++) {
                dirty.mark(
                        created.get(i),
                        DirtyBits.GEOMETRY
                                | DirtyBits.MATERIAL
                                | DirtyBits.COLOR
                                | DirtyBits.ORDER
                                | DirtyBits.LAYER
                                | DirtyBits.PHYSICS
                                | DirtyBits.JOINTS
                );
            }
        }

        return new SpawnResult(created);
    }

    private static void remapFixtureIdentities(World world, IntBag created) {
        ComponentMapper<PhysicsFixturesComponent> mFixtures = world.getMapper(PhysicsFixturesComponent.class);
        ComponentMapper<SpatialBlocksComponent> mBlocks = world.getMapper(SpatialBlocksComponent.class);
        IntIntMap remap = new IntIntMap();

        for (int i = 0; i < created.size(); i++) {
            int eid = created.get(i);
            PhysicsFixturesComponent fixtures = mFixtures.getSafe(eid, null);
            if (fixtures == null || fixtures.fixtures == null) continue;
            for (int j = 0; j < fixtures.fixtures.size; j++) {
                FixtureDefData fixture = fixtures.fixtures.get(j);
                if (fixture == null) continue;
                int sourceId = fixture.fixtureId;
                if (sourceId <= 0) {
                    throw new IllegalStateException(
                            "Prefab fixture has an invalid source fixtureId: entity=" + eid + ", fixtureId=" + sourceId);
                }
                if (remap.containsKey(sourceId)) {
                    throw new IllegalStateException(
                            "Prefab contains duplicate fixtureId=" + sourceId + "; fixture identities must be scene-global.");
                }
                FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
                if (allocator == null) {
                    throw new IllegalStateException(
                            "FixtureIdAllocatorSystem is required to spawn prefab fixtures.");
                }
                int targetId = allocator.allocateNewFixtureId();
                remap.put(sourceId, targetId);
                fixture.fixtureId = targetId;
            }
        }

        for (int i = 0; i < created.size(); i++) {
            int eid = created.get(i);
            SpatialBlocksComponent blocks = mBlocks.getSafe(eid, null);
            if (blocks == null || blocks.blocks == null) continue;
            for (int j = 0; j < blocks.blocks.size; j++) {
                SpatialBlockData block = blocks.blocks.get(j);
                if (block == null || block.fixtureId == 0) continue;
                int targetId = remap.get(block.fixtureId, 0);
                if (targetId == 0) {
                    throw new IllegalStateException(
                            "Prefab spatial block references a missing fixture: entity=" + eid
                                    + ", blockId=" + block.id + ", fixtureId=" + block.fixtureId);
                }
                block.fixtureId = targetId;
            }
        }
    }
}
