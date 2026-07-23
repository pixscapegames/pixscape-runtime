package games.pixscape.runtime.prefab;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsBodyCompiler;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;
import games.pixscape.runtime.physics.PhysicsShapeIdState;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RuntimePrefabFragmentSpawner {

    private final IdentityRegistry identityRegistry;
    private final PhysicsShapeIdAllocator physicsShapeIdAllocator;
    private final PhysicsBodyCompiler physicsBodyCompiler = new PhysicsBodyCompiler();

    public RuntimePrefabFragmentSpawner(
            IdentityRegistry identityRegistry, PhysicsShapeIdState physicsShapeIdState) {
        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null");
        }
        this.identityRegistry = identityRegistry;
        this.physicsShapeIdAllocator = new PhysicsShapeIdAllocator(physicsShapeIdState);
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
        ComponentMapper<PhysicsShapesComponent> mShapes =
                world.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<PhysicsCompiledFixturesComponent> mCompiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class);

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

            PhysicsShapesComponent shapes = mShapes.getSafe(eid, null);
            if (shapes != null && shapes.shapes != null) {
                for (int shapeIndex = 0; shapeIndex < shapes.shapes.size; shapeIndex++) {
                    PhysicsShapeData shape = shapes.shapes.get(shapeIndex);
                    if (shape == null) {
                        rollbackCreated(world, created);
                        throw new IllegalArgumentException(
                                "Prefab contains a null physics shape for entity " + eid + ".");
                    }
                    shape.physicsShapeId =
                            physicsShapeIdAllocator.allocateNewPhysicsShapeId();
                }
                try {
                    physicsBodyCompiler.compile(shapes);
                } catch (RuntimeException ex) {
                    rollbackCreated(world, created);
                    throw ex;
                }
            }
            if (mCompiled.has(eid)) {
                mCompiled.remove(eid);
            }
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

    private static void rollbackCreated(World world, IntBag created) {
        for (int i = created.size() - 1; i >= 0; i--) {
            world.delete(created.get(i));
        }
    }
}
