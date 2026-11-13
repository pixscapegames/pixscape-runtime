package games.pixscape.runtime.engine;

import games.pixscape.runtime.api.GameObjectFactory;
import games.pixscape.runtime.system.CompositionCostsBenchmark;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Desktop diagnostic only. Run with -XX:-UseTLAB to record actual outside-TLAB allocations. */
public final class GameObjectAllocationProfile {
    public static void main(String[] args) throws Exception {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        Path output = Path.of(System.getProperty("profile.output"));
        Files.createDirectories(output);
        for (int nodes : new int[]{8, 64}) {
            Path recording = output.resolve("factory-" + nodes + ".jfr");
            try (var h = new PixscapeEngineGameObjectSpawnTest.Harness(CompositionCostsBenchmark.asset(nodes))) {
                h.ready();
                GameObjectFactory factory = h.engine.api().gameObjects().prepare("declared");
                for (int i = 0; i < 2000; i++) {
                    factory.spawn(100, 200).despawn();
                    h.frame(0);
                }
                try (Recording r = new Recording()) {
                    r.enable("jdk.ObjectAllocationOutsideTLAB").withStackTrace().withThreshold(Duration.ZERO);
                    r.setDestination(recording);
                    r.start();
                    var allocation = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
                    long thread = Thread.currentThread().threadId(), total = 0;
                    for (int i = 0; i < 100; i++) {
                        long before = allocation.getThreadAllocatedBytes(thread);
                        var instance = factory.spawn(100, 200);
                        total += allocation.getThreadAllocatedBytes(thread) - before;
                        instance.despawn();
                        h.frame(0);
                    }
                    r.stop();
                    System.out.println("ThreadMXBean " + nodes + ": " + total / 100.0 + " B/spawn");
                }
            }
            Map<String, long[]> totals = new LinkedHashMap<>();
            long missingStack = 0;
            try (RecordingFile recordingFile = new RecordingFile(recording)) {
                while (recordingFile.hasMoreEvents()) {
                    RecordedEvent event = recordingFile.readEvent();
                    if (!event.getEventType().getName().equals("jdk.ObjectAllocationOutsideTLAB")) {
                        continue;
                    }
                    if (event.getStackTrace() == null) {
                        missingStack++;
                        continue;
                    }
                    boolean spawn = false;
                    StringBuilder stack = new StringBuilder();
                    for (RecordedFrame frame : event.getStackTrace().getFrames()) {
                        String owner = frame.getMethod().getType().getName();
                        String method = frame.getMethod().getName();
                        stack.append(owner).append('.').append(method).append(':').append(frame.getLineNumber()).append(" > ");
                        if (owner.startsWith("games.pixscape.runtime.engine.PixscapeEngine$") && method.equals("spawn")) {
                            spawn = true;
                            break;
                        }
                    }
                    if (spawn) {
                        String key = event.getClass("objectClass").getName() + ",\"" + stack + "\"";
                        long[] value = totals.computeIfAbsent(key, ignored -> new long[2]);
                        value[0]++;
                        value[1] += event.getLong("allocationSize");
                    }
                }
            }
            StringBuilder csv = new StringBuilder("class,stack,count,bytes,operations\n");
            totals.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                    .forEach(e -> csv.append(e.getKey()).append(',').append(e.getValue()[0]).append(',')
                            .append(e.getValue()[1]).append(",100\n"));
            Files.writeString(output.resolve("factory-" + nodes + ".csv"), csv);
            System.out.println("Profile " + nodes + ": " + totals.size() + " allocation stacks; missing stacks=" + missingStack);
        }
    }
}
