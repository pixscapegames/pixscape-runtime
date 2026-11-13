package games.pixscape.runtime.system;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.gameobject.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.IdentityRegistry;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

/** Same split-phase experiment against the before jar and current implementation. */
public final class CompositionCostsBenchmark {
    private static final com.sun.management.ThreadMXBean ALLOC =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        System.out.println("nodes,round,phase,median_ns,p95_ns,java_B_per_op,retained_java_B,bodies,joints");
        for (int nodes : new int[]{8, 64, 4096}) {
            FileHandle file = new FileHandle(Files.createTempFile("composition-costs-", ".gameobject").toFile());
            try {
                new GameObjectAssetLoader().save(file, asset(nodes));
                for (int round = 0; round < 3; round++) {
                    try (var f = new CompositionPipelineBenchmark.Fixture(0, 4, 8)) {
                        var spawner = new GameObjectRuntimeFragmentSpawner(f.ids, f.meta, new AtlasRuntimeService());
                        var preparation = new Samples(10);
                        GameObjectRuntimeFragmentSpawner.PreparedGameObject prepared = null;
                        for (int i = 0; i < 10; i++) {
                            preparation.start();
                            prepared = spawner.prepare(file, "gameobjects/costs.gameobject");
                            preparation.end(i);
                            if (i == 0) {
                                preparation.reportFirst(nodes, round, "first_load_prepare", 0, 0);
                            }
                        }
                        preparation.report(nodes, round, "load_prepare", CompositionMemoryAgent.ownedBytes(prepared), 0, 0);
                        System.out.println("SPAWN_BUFFER," + nodes + "," + round + "," + CompositionMemoryAgent.ownedBytes(spawner));
                        int count = nodes == 4096 ? 40 : 120;
                        var ecs = new Samples(count);
                        var nativePhysics = new Samples(count);
                        for (int i = 0; i < count; i++) {
                            ecs.start();
                            SpawnResult instance = spawner.spawnPrepared(f.world, prepared, 0f, 0f);
                            f.hierarchy.resolvePreparedSpawn(instance.createdEntityIds());
                            ecs.end(i);
                            long componentsBefore = i == 0 ? componentBytes(f, instance) : 0;
                            nativePhysics.start();
                            f.sync.materializePreparedSpawn(instance.createdEntityIds());
                            nativePhysics.end(i);
                            if (i == 0) {
                                System.out.println("COMPONENT_BYTES," + nodes + "," + round + "," + componentsBefore
                                        + "," + componentBytes(f, instance));
                                ecs.reportFirst(nodes, round, "first_instance", 0, 0);
                                nativePhysics.reportFirst(nodes, round, "first_native", f.box.world.getBodyCount(), f.box.world.getJointCount());
                            }
                            discard(f, instance);
                        }
                        ecs.report(nodes, round, "instance_warm", 0, nodes / 4, nodes == 64 ? 15 : nodes / 8);
                        nativePhysics.report(nodes, round, "native_warm", 0, nodes / 4, nodes == 64 ? 15 : nodes / 8);
                        var burst = new Samples(40);
                        int width = nodes == 4096 ? 2 : 8;
                        SpawnResult[] instances = new SpawnResult[width];
                        for (int i = 0; i < 40; i++) {
                            burst.start();
                            for (int j = 0; j < width; j++) {
                                instances[j] = spawner.spawnPreparedRuntime(f.world, prepared, j * 10000f, 0f);
                            }
                            burst.end(i);
                            for (SpawnResult instance : instances) {
                                discard(f, instance);
                            }
                        }
                        burst.report(nodes, round, "burst_" + width, CompositionMemoryAgent.ownedBytes(prepared), nodes / 4 * width,
                                (nodes == 64 ? 15 : nodes / 8) * width);
                    }
                }
            } finally {
                file.delete();
            }
        }
    }

    private static long componentBytes(CompositionPipelineBenchmark.Fixture f, SpawnResult instance) throws Exception {
        var components = new com.artemis.utils.Bag<com.artemis.Component>();
        var owned = new java.util.ArrayList<Object>();
        for (int i = 0; i < instance.createdEntityIds().size(); i++) {
            components.clear();
            f.world.getEntity(instance.createdEntityIds().get(i)).getComponents(components);
            for (int j = 0; j < components.size(); j++) {
                owned.add(components.get(j));
            }
        }
        return CompositionMemoryAgent.ownedBytes(owned);
    }

    private static void discard(CompositionPipelineBenchmark.Fixture f, SpawnResult instance) {
        var created = instance.createdEntityIds();
        f.sync.discardPreparedSpawn(created);
        for (int i = created.size() - 1; i >= 0; i--) {
            int entity = created.get(i);
            f.hierarchy.discardPreparedSpawn(entity);
            IdentityRegistry.unindexEntityImmediately(f.world, entity);
            f.world.delete(entity);
        }
        f.world.process(); // Benchmark cleanup only, outside all measured operations.
    }

    public static GameObjectAsset asset(int nodes) {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        int previousBody = -1;
        for (int i = 1; i <= nodes; i++) {
            var entity = new GameObjectAsset.GameObjectEntityData();
            entity.sourceEntityId = i;
            entity.identity = new GameObjectAsset.IdentityData();
            entity.identity.name = "Node " + i;
            entity.transform = new GameObjectAsset.TransformData();
            entity.transform.scaleX = entity.transform.scaleY = 1f;
            entity.transform.x = i == 1 ? 0f : i * 100f;
            entity.entityIndex = new GameObjectAsset.EntityIndexData();
            if (i == 1) {
                entity.gameObject = new GameObjectAsset.GameObjectData();
            } else {
                entity.parentSourceEntityId = 1;
            }
            if (i % 4 == 2) {
                entity.physicsBody = new GameObjectAsset.PhysicsBodyData();
                entity.physicsBody.type = PhysicsBodyComponent.DYNAMIC;
                var shape = new GameObjectAsset.PhysicsShapeData();
                shape.localShapeId = 1;
                shape.geometry = new PhysicsGeometryData();
                shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
                entity.physicsShapes.add(shape);
                if (previousBody > 0) {
                    var joint = new GameObjectAsset.GameObjectJointData();
                    joint.jointLocalId = asset.joints.size + 1;
                    joint.bodyALocalEntityId = previousBody;
                    joint.bodyBLocalEntityId = i;
                    joint.distance = new GameObjectAsset.DistanceJointData();
                    joint.distance.lengthM = 4f;
                    asset.joints.add(joint);
                    previousBody = nodes == 64 ? i : -1;
                } else {
                    previousBody = i;
                }
            }
            asset.entities.add(entity);
        }
        return asset;
    }

    private static final class Samples {
        final long[] ns, bytes;
        long start, allocation;

        Samples(int count) {
            ns = new long[count];
            bytes = new long[count];
        }

        void start() {
            allocation = ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId());
            start = System.nanoTime();
        }

        void end(int i) {
            ns[i] = System.nanoTime() - start;
            bytes[i] = ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocation;
        }

        void reportFirst(int nodes, int round, String phase, int bodies, int joints) {
            print(nodes, round, phase, ns[0], ns[0], bytes[0], 0, bodies, joints);
        }

        void report(int nodes, int round, String phase, long retained, int bodies, int joints) {
            long[] sorted = Arrays.copyOfRange(ns, ns.length / 2, ns.length);
            Arrays.sort(sorted);
            long total = 0;
            for (int i = ns.length / 2; i < ns.length; i++) {
                total += bytes[i];
            }
            print(nodes, round, phase, sorted[sorted.length / 2], sorted[(int) Math.ceil(sorted.length * .95) - 1],
                    total / sorted.length, retained, bodies, joints);
        }

        void print(int nodes, int round, String phase, long median, long p95, long bytes,
                   long retained, int bodies, int joints) {
            System.out.printf(Locale.ROOT, "%d,%d,%s,%d,%d,%d,%d,%d,%d%n",
                    nodes, round, phase, median, p95, bytes, retained, bodies, joints);
        }
    }
}
