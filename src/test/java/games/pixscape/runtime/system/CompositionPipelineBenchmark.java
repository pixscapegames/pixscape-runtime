package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.gameobject.*;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.*;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Arrays;

/** Opt-in comparative desktop benchmark; no timing assertions in the normal test suite. */
public final class CompositionPipelineBenchmark {
    private static final com.sun.management.ThreadMXBean ALLOC =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final long THREAD = Thread.currentThread().threadId();

    public static void main(String[] args) throws Exception {
        GdxNativesLoader.load();
        System.out.println("nodes,body_stride,depth,bodies,joints,round,phase,ns_per_op,heap_bytes_per_op,median_ns,p95_ns");
        // Same class and inputs run in separate JVMs against either the preserved baseline jar or current classes.
        for (int[] scenario : new int[][]{{128, 4, 2}, {1024, 4, 8}, {1024, 2, 32}, {4096, 4, 8}}) {
            for (int round = 0; round < 4; round++) {
                try (Fixture f = new Fixture(scenario[0], scenario[1], scenario[2])) {
                    long start = System.nanoTime(), allocation = bytes();
                    f.sync.process();
                    f.report(round, "native_creation", System.nanoTime() - start, bytes() - allocation, 1);
                    f.world.process(); // Publishes native-body subscriptions before the timed stable pipeline.
                    for (int i = 0; i < 500; i++) {
                        f.frame(null, null);
                    }
                    long[] ns = new long[4], heap = new long[4];
                    long[][] samples = new long[4][1200];
                    for (int i = 0; i < 1200; i++) {
                        for (int phase = 0; phase < 4; phase++) {
                            ns[phase] = 0;
                        }
                        f.frame(ns, heap);
                        for (int phase = 0; phase < 4; phase++) {
                            samples[phase][i] = ns[phase];
                        }
                    }
                    String[] phases = {"hierarchy_pre", "native_sync", "box2d_step", "writeback_post"};
                    for (int i = 0; i < phases.length; i++) {
                        f.reportSamples(round, phases[i], samples[i], heap[i]);
                    }
                    f.measurePlacement(round);
                    f.measureSpawn(round);
                }
            }
        }
    }

    private static long bytes() {
        return ALLOC.getThreadAllocatedBytes(THREAD);
    }

    static final class Fixture implements AutoCloseable {
        final int nodes, stride, depth;
        final SceneMetaRuntime meta = new SceneMetaRuntime();
        final IdentityRegistry ids = new IdentityRegistry();
        final Box2dWorldService box = new Box2dWorldService(100f, new Vector2(), false);
        final GameObjectHierarchySystem hierarchy = new GameObjectHierarchySystem();
        final PhysicsPoseAuthority authority = new PhysicsPoseAuthority();
        final Box2dSyncSystem sync = new Box2dSyncSystem(box);
        final GameObjectPhysicsWritebackSystem post = new GameObjectPhysicsWritebackSystem();
        final World world;
        final IntArray roots = new IntArray();
        int bodyCount, jointCount;

        Fixture(int nodes, int stride, int depth) {
            this.nodes = nodes;
            this.stride = stride;
            this.depth = depth;
            meta.physicsEnabled = true;
            meta.gravityX = meta.gravityY = 0f;
            ids.bind(null, null);
            world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(nodes * 2),
                    authority, hierarchy, sync, post).build());
            ids.bind(world, meta);
            sync.setSceneMeta(meta);
            sync.setStepEnabled(false);
            sync.setEnabled(false);
            int previousBody = -1, previousStable = -1;
            for (int i = 0; i < nodes; i++) {
                int entity = world.create();
                int stable = ids.ensureStableId(entity);
                world.getMapper(EntityIndexComponent.class).create(entity);
                TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
                transform.x = i % depth == 0 ? (i / depth) * 1000f : 170f;
                transform.rotationRad = .013f;
                transform.originX = 3f;
                world.getMapper(GameObjectComponent.class).create(entity);
                if (i % depth == 0) {
                    roots.add(entity);
                } else {
                    world.getMapper(GameObjectMemberComponent.class).create(entity).parentStableId = previousStable;
                }
                previousStable = stable;
                if (i % stride == 0) {
                    bodyCount++;
                    world.getMapper(PhysicsBodyComponent.class).create(entity).type = PhysicsBodyComponent.DYNAMIC;
                    PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(entity);
                    PhysicsShapeData shape = new PhysicsShapeData();
                    shape.physicsShapeId = meta.nextPhysicsShapeId++;
                    shape.geometry = new PhysicsGeometryData();
                    shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
                    shape.maskBits = 0;
                    shapes.shapes.add(shape);
                    PhysicsService.publishPreparedCandidate(shapes,
                            world.getMapper(PhysicsCompiledFixturesComponent.class).create(entity),
                            PhysicsService.prepareBodyCandidate(shapes.shapes));
                    if (previousBody >= 0) {
                        int joint = world.create();
                        ids.ensureStableId(joint);
                        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(joint);
                        base.aEid = previousBody;
                        base.bEid = entity;
                        world.getMapper(PhysicsDistanceJointComponent.class).create(joint).lengthM = 10f;
                        jointCount++;
                        previousBody = -1;
                    } else {
                        previousBody = entity;
                    }
                }
            }
            ids.rebuild();
            world.process();
            authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
            sync.setEnabled(true);
        }

        void frame(long[] ns, long[] heap) {
            for (int phase = 0; phase < 4; phase++) {
                long allocation = heap == null ? 0 : bytes();
                long start = ns == null ? 0 : System.nanoTime();
                switch (phase) {
                    case 0 -> hierarchy.process();
                    case 1 -> sync.process();
                    case 2 -> box.step(1f / 60f);
                    case 3 -> post.process();
                    default -> throw new AssertionError();
                }
                if (ns != null) {
                    ns[phase] += System.nanoTime() - start;
                    heap[phase] += bytes() - allocation;
                }
            }
        }

        void measurePlacement(int round) throws Exception {
            Class<?> type;
            try {
                type = Class.forName("games.pixscape.runtime.hierarchy.GameObjectPlacement");
            } catch (ClassNotFoundException absentOnBaseline) {
                return;
            }
            Method prepare = type.getMethod("prepare", World.class, IntArray.class, float.class, float.class,
                    float.class, float.class, float.class, boolean.class);
            Method apply = type.getMethod("applyAfter");
            long heap = 0;
            long[] samples = new long[100];
            for (int i = 0; i < 140; i++) {
                long allocation = bytes(), start = System.nanoTime();
                Object placement = prepare.invoke(null, world, roots, 0f, 0f, .5f, .2f, .001f, false);
                apply.invoke(placement);
                long elapsed = System.nanoTime() - start, allocated = bytes() - allocation;
                if (i == 0) {
                    report(round, "placement_first", elapsed, allocated, 1);
                }
                if (i >= 40) {
                    samples[i - 40] = elapsed;
                    heap += allocated;
                }
            }
            reportSamples(round, "placement_prepare_apply", samples, heap);
            try {
                GameObjectHierarchySystem.class.getMethod("placeComposition", IntArray.class,
                        float.class, float.class, float.class, float.class, float.class, boolean.class);
            } catch (NoSuchMethodException unavailableBeforeOptimization) {
                return;
            }
            long allocation = bytes(), start = System.nanoTime();
            GameObjectHierarchySystem.class.getMethod("reservePlacement", int.class, int.class)
                    .invoke(hierarchy, nodes, jointCount);
            report(round, "placement_reserve", System.nanoTime() - start, bytes() - allocation, 1);
            heap = 0;
            for (int i = 0; i < 140; i++) {
                allocation = bytes();
                start = System.nanoTime();
                hierarchy.placeComposition(roots, 0f, 0f, .5f, .2f, .001f, false);
                long elapsed = System.nanoTime() - start, allocated = bytes() - allocation;
                if (i == 0) {
                    report(round, "placement_reused_first", elapsed, allocated, 1);
                }
                if (i >= 40) {
                    samples[i - 40] = elapsed;
                    heap += allocated;
                }
            }
            reportSamples(round, "placement_reused", samples, heap);
            var bufferField = GameObjectHierarchySystem.class.getDeclaredField("placementBuffer");
            bufferField.setAccessible(true);
            System.out.println("PLACEMENT_BUFFER," + nodes + "," + stride + "," + depth + "," + round
                    + "," + CompositionMemoryAgent.ownedBytes(bufferField.get(hierarchy)));
        }

        void measureSpawn(int round) throws Exception {
            FileHandle file = new FileHandle(Files.createTempFile("composition-bench-", ".gameobject").toFile());
            try {
                new GameObjectAssetLoader().save(file, spawnAsset());
                var spawner = new GameObjectRuntimeFragmentSpawner(ids, meta, new AtlasRuntimeService());
                var prepared = spawner.prepare(file, "gameobjects/benchmark.gameobject");
                long heap = 0;
                long[] samples = new long[50];
                for (int i = 0; i < 80; i++) {
                    long allocation = bytes(), start = System.nanoTime();
                    spawner.spawnPreparedRuntime(world, prepared, 100000f + i * 1000f, 0f);
                    if (i >= 30) {
                        samples[i - 30] = System.nanoTime() - start;
                        heap += bytes() - allocation;
                    }
                }
                reportSamples(round, "spawn_8_nodes_2_bodies_1_joint", samples, heap);
            } finally {
                file.delete();
            }
        }

        void report(int round, String phase, long ns, long heap, int count) {
            System.out.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%s,%.1f,%.1f,%.1f,%.1f%n", nodes, stride, depth,
                    bodyCount, jointCount, round, phase, (double) ns / count, (double) heap / count, (double) ns / count, (double) ns / count);
        }

        void reportSamples(int round, String phase, long[] samples, long heap) {
            long total = 0;
            for (long sample : samples) {
                total += sample;
            }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%s,%.1f,%.1f,%d,%d%n", nodes, stride, depth,
                    bodyCount, jointCount, round, phase, (double) total / samples.length, (double) heap / samples.length,
                    samples[samples.length / 2], samples[(int) Math.ceil(samples.length * .95) - 1]);
        }

        @Override
        public void close() {
            ids.bind(null, null);
            world.dispose();
            box.dispose();
        }
    }

    private static GameObjectAsset spawnAsset() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        for (int i = 1; i <= 8; i++) {
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
            if (i == 2 || i == 3) {
                entity.physicsBody = new GameObjectAsset.PhysicsBodyData();
                entity.physicsBody.type = PhysicsBodyComponent.DYNAMIC;
                var shape = new GameObjectAsset.PhysicsShapeData();
                shape.localShapeId = 1;
                shape.geometry = new PhysicsGeometryData();
                shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
                entity.physicsShapes.add(shape);
            }
            asset.entities.add(entity);
        }
        var joint = new GameObjectAsset.GameObjectJointData();
        joint.jointLocalId = 1;
        joint.bodyALocalEntityId = 2;
        joint.bodyBLocalEntityId = 3;
        joint.distance = new GameObjectAsset.DistanceJointData();
        joint.distance.lengthM = 1f;
        asset.joints.add(joint);
        return asset;
    }
}
