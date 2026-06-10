package games.pixscape.runtime.profiling;

import org.junit.Assert;
import org.junit.Test;

public class FrameSystemProfilerTest {
    @Test
    public void disabledProfilerRecordsNothing() {
        ManualClock clock = new ManualClock();
        FrameSystemProfiler profiler = new FrameSystemProfiler(clock);

        long start = profiler.begin(SystemProfilePhases.RENDER_SORT);
        clock.advance(1_000_000L);
        profiler.end(SystemProfilePhases.RENDER_SORT, start);

        Assert.assertEquals(0L, profiler.totalNs());
        Assert.assertEquals(0L, profiler.durationNs(SystemProfilePhases.RENDER_SORT));
        Assert.assertEquals(-1, profiler.worstPhaseId());
    }

    @Test
    public void enabledProfilerRecordsOnePhase() {
        ManualClock clock = new ManualClock();
        FrameSystemProfiler profiler = new FrameSystemProfiler(clock);
        profiler.setEnabled(true);

        long start = profiler.begin(SystemProfilePhases.RENDER_TILED_SYNC);
        clock.advance(2_500_000L);
        profiler.end(SystemProfilePhases.RENDER_TILED_SYNC, start);

        Assert.assertEquals(2_500_000L, profiler.totalNs());
        Assert.assertEquals(2_500_000L, profiler.durationNs(SystemProfilePhases.RENDER_TILED_SYNC));
        Assert.assertEquals(SystemProfilePhases.RENDER_TILED_SYNC, profiler.worstPhaseId());
        Assert.assertEquals(2_500_000L, profiler.worstNs());
    }

    @Test
    public void beginFrameResetsCurrentFrame() {
        ManualClock clock = new ManualClock();
        FrameSystemProfiler profiler = new FrameSystemProfiler(clock);
        profiler.setEnabled(true);

        long start = profiler.begin(SystemProfilePhases.CULLING);
        clock.advance(900_000L);
        profiler.end(SystemProfilePhases.CULLING, start);
        profiler.beginFrame();

        Assert.assertEquals(0L, profiler.totalNs());
        Assert.assertEquals(0L, profiler.durationNs(SystemProfilePhases.CULLING));
        Assert.assertEquals(-1, profiler.worstPhaseId());
    }

    @Test
    public void reportIncludesWorstPhaseAndUnprofiledRemainder() {
        ManualClock clock = new ManualClock();
        FrameSystemProfiler profiler = new FrameSystemProfiler(clock);
        profiler.setEnabled(true);

        long renderStart = profiler.begin(SystemProfilePhases.RENDER_SUBMIT);
        clock.advance(3_000_000L);
        profiler.end(SystemProfilePhases.RENDER_SUBMIT, renderStart);

        long sortStart = profiler.begin(SystemProfilePhases.RENDER_SORT);
        clock.advance(1_000_000L);
        profiler.end(SystemProfilePhases.RENDER_SORT, sortStart);

        StringBuilder report = new StringBuilder();
        profiler.appendReport(report, 10_000_000L);
        String text = report.toString();

        Assert.assertTrue(text.contains("profiledSystemsTotal: 4.0ms"));
        Assert.assertTrue(text.contains("unprofiledRemainder: 6.0ms"));
        Assert.assertTrue(text.contains("worst: RenderSubmitSystem 3.0ms"));
        Assert.assertTrue(text.contains("RenderSortSystem: 1.0ms"));
    }

    @Test
    public void phaseNamesAreUnique() {
        for (int i = 0; i < SystemProfilePhases.PHASE_COUNT; i++) {
            String name = SystemProfilePhases.name(i);
            Assert.assertNotNull(name);
            Assert.assertFalse(name.isEmpty());
            for (int j = i + 1; j < SystemProfilePhases.PHASE_COUNT; j++) {
                Assert.assertNotEquals(name, SystemProfilePhases.name(j));
            }
        }
    }

    private static final class ManualClock implements NanoClock {
        private long nowNs;

        @Override
        public long nanoTime() {
            return nowNs;
        }

        void advance(long ns) {
            nowNs += ns;
        }
    }
}
