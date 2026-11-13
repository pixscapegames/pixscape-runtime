package games.pixscape.runtime.engine;

import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.system.CompositionCostsBenchmark;
import games.pixscape.runtime.system.CompositionMemoryAgent;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Public factory measurements with the normal frame outside the measured operation. */
public final class GameObjectFactoryAuditBenchmark {
    private static final com.sun.management.ThreadMXBean ALLOC =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final long THREAD = Thread.currentThread().threadId();

    public static void main(String[] args) throws Exception {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        for (int nodes : new int[]{8, 64}) {
            for (int round = 0; round < 3; round++) {
                try (var h = new PixscapeEngineGameObjectSpawnTest.Harness(CompositionCostsBenchmark.asset(nodes))) {
                    h.ready();
                    var factory = h.engine.api().gameObjects().prepare("declared");
                    var planField = PixscapeEngine.class.getDeclaredField("activeSceneAvailability");
                    planField.setAccessible(true);
                    var plan = (games.pixscape.runtime.loading.SceneAvailabilityPlan) planField.get(h.engine);
                    var model = plan.requireGameObject("gameobjects/declared.gameobject");
                    System.out.println("MODEL," + nodes + "," + round + "," + CompositionMemoryAgent.ownedBytes(model));
                    for (int i = 0; i < 2000; i++) {
                        factory.spawn(100, 200).despawn();
                        h.frame(0);
                    }
                    long[] ns = new long[300];
                    long total = 0;
                    int lowId = Integer.MAX_VALUE, highId = -1;
                    for (int i = 0; i < ns.length; i++) {
                        long bytes = bytes(), start = System.nanoTime();
                        var instance = factory.spawn(100, 200);
                        ns[i] = System.nanoTime() - start;
                        total += bytes() - bytes;
                        lowId = Math.min(lowId, instance.root().entityId());
                        highId = Math.max(highId, instance.root().entityId());
                        instance.despawn();
                        h.frame(0);
                    }
                    Arrays.sort(ns);
                    System.out.println("FACTORY," + nodes + "," + round + "," + ns[150] + "," + ns[284]
                            + "," + (double) total / ns.length + "," + lowId + "," + highId);
                }
            }
        }
        // Warm placement code on a separate World, never on the measured instances below.
        try (var h = new PixscapeEngineGameObjectSpawnTest.Harness(CompositionCostsBenchmark.asset(8))) {
            h.ready();
            var api = h.engine.api().gameObjects();
            var factory = api.prepare("declared");
            var instance = factory.spawn(0, 0);
            EntityRef[] roots = {instance.root()};
            api.reservePlacement(8, 1);
            for (int i = 0; i < 5000; i++) {
                api.place(roots, 0, 0, .01f, 0, .001f, false);
            }
        }
        // Optional allocation diagnosis only; do not use recorded runs for timing comparisons.
        jdk.jfr.Recording recording = null;
        String profileOutput = System.getProperty("placement.profile");
        if (profileOutput != null) {
            recording = new jdk.jfr.Recording();
            recording.enable("jdk.ObjectAllocationOutsideTLAB").withStackTrace();
            recording.enable("jdk.ObjectAllocationInNewTLAB").withStackTrace();
            recording.enable("jdk.Deoptimization").withStackTrace();
            recording.setDestination(java.nio.file.Path.of(profileOutput));
            recording.start();
        }
        for (int nodes : new int[]{8, 64, 4096}) {
            for (int round = 0; round < 4; round++) {
                try (var h = new PixscapeEngineGameObjectSpawnTest.Harness(CompositionCostsBenchmark.asset(nodes))) {
                    h.ready();
                    var api = h.engine.api().gameObjects();
                    var factory = api.prepare("declared");
                    var instance = factory.spawn(0, 0);
                    EntityRef[] roots = {instance.root()};
                    long bytes = bytes(), start = System.nanoTime();
                    api.reservePlacement(factory.nodeCount(), factory.jointCount());
                    long reserveNs = System.nanoTime() - start, reserveBytes = bytes() - bytes;
                    long[] ns = new long[32], allocated = new long[32];
                    for (int i = 0; i < ns.length; i++) {
                        bytes = bytes();
                        start = System.nanoTime();
                        api.place(roots, 0, 0, .01f, 0, .001f, false);
                        ns[i] = System.nanoTime() - start;
                        allocated[i] = bytes() - bytes;
                    }
                    var buffer = h.hierarchy().getClass().getDeclaredField("placementBuffer");
                    buffer.setAccessible(true);
                    long retained = CompositionMemoryAgent.ownedBytes(buffer.get(h.hierarchy()));
                    for (int i = 0; i < allocated.length; i++) {
                        if (allocated[i] != 0) {
                            System.out.println("PLACEMENT_ALLOCATION," + nodes + "," + round + "," + i + "," + allocated[i]);
                        }
                    }
                    System.out.println("RESERVE," + nodes + "," + round + "," + reserveNs + "," + reserveBytes + "," + retained);
                    System.out.println("FIRST_PLACE," + nodes + "," + round + "," + ns[0] + "," + allocated[0]);
                    long[] subsequent = Arrays.copyOfRange(ns, 1, ns.length);
                    Arrays.sort(subsequent);
                    long total = 0;
                    for (int i = 1; i < allocated.length; i++) {
                        total += allocated[i];
                    }
                    System.out.println("NEXT_PLACE," + nodes + "," + round + "," + subsequent[15] + "," + subsequent[29]
                            + "," + (double) total / subsequent.length);
                }
            }
        }
        if (recording != null) {
            recording.stop();
            recording.close();
        }
    }

    private static long bytes() {
        return ALLOC.getThreadAllocatedBytes(THREAD);
    }
}
