package games.pixscape.runtime.engine;

import games.pixscape.runtime.api.GameObjectFactory;
import games.pixscape.runtime.api.GameObjectInstance;
import games.pixscape.runtime.api.GameObjectsAPI;
import games.pixscape.runtime.system.CompositionCostsBenchmark;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Includes public instance handles/name lookup, unlike the lower-level split-phase benchmark. */
public final class GameObjectApiCostsBenchmark {
    public static void main(String[] args) throws Exception {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        System.out.println("API,nodes,round,path,median_ns,p95_ns,java_B_per_op,first_ns,first_java_B");
        boolean supportsFactory;
        try {
            GameObjectsAPI.class.getMethod("prepare", String.class);
            supportsFactory = true;
        } catch (NoSuchMethodException beforeOptimization) {
            supportsFactory = false;
        }
        for (int nodes : new int[]{8, 64, 4096}) {
            for (int round = 0; round < 3; round++) {
                measure(nodes, round, false);
                if (supportsFactory) {
                    measure(nodes, round, true);
                }
            }
        }
    }

    private static void measure(int nodes, int round, boolean prepared) throws Exception {
        try (var h = new PixscapeEngineGameObjectSpawnTest.Harness(CompositionCostsBenchmark.asset(nodes))) {
            h.ready();
            GameObjectsAPI api = h.engine.api().gameObjects();
            GameObjectFactory factory = prepared ? api.prepare("declared") : null;
            int count = nodes == 4096 ? 40 : 160;
            long[] ns = new long[count];
            long[] bytes = new long[count];
            var allocations = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long thread = Thread.currentThread().threadId();
            for (int i = 0; i < count; i++) {
                long allocation = allocations.getThreadAllocatedBytes(thread), start = System.nanoTime();
                GameObjectInstance instance = prepared ? factory.spawn(100, 200) : api.spawn("declared", 100, 200);
                ns[i] = System.nanoTime() - start;
                bytes[i] = allocations.getThreadAllocatedBytes(thread) - allocation;
                instance.despawn();
                h.barrier();
            }
            long[] sorted = Arrays.copyOfRange(ns, count / 2, count);
            Arrays.sort(sorted);
            long allocation = 0;
            for (int i = count / 2; i < count; i++) {
                allocation += bytes[i];
            }
            System.out.println("API," + nodes + "," + round + "," + (prepared ? "factory" : "name")
                    + "," + sorted[sorted.length / 2] + "," + sorted[(int) Math.ceil(sorted.length * .95) - 1]
                    + "," + allocation / sorted.length + "," + ns[0] + "," + bytes[0]);
        }
    }
}
