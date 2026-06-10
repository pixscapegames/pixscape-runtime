package games.pixscape.runtime.profiling;

import com.badlogic.gdx.utils.TimeUtils;

public final class FrameSystemProfiler implements SystemProfiler {
    private static final NanoClock TIME_UTILS_CLOCK = new NanoClock() {
        @Override
        public long nanoTime() {
            return TimeUtils.nanoTime();
        }
    };

    private final NanoClock clock;
    private final long[] durationsNs = new long[SystemProfilePhases.PHASE_COUNT];
    private boolean enabled;
    private long totalNs;
    private int worstPhaseId = -1;
    private long worstNs;

    public FrameSystemProfiler() {
        this(TIME_UTILS_CLOCK);
    }

    public FrameSystemProfiler(NanoClock clock) {
        this.clock = clock != null ? clock : TIME_UTILS_CLOCK;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            beginFrame();
        }
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void beginFrame() {
        for (int i = 0; i < durationsNs.length; i++) {
            durationsNs[i] = 0L;
        }
        totalNs = 0L;
        worstPhaseId = -1;
        worstNs = 0L;
    }

    @Override
    public long begin(int phaseId) {
        if (!enabled) {
            return 0L;
        }
        return clock.nanoTime();
    }

    @Override
    public void end(int phaseId, long startNs) {
        if (!enabled) {
            return;
        }
        if (phaseId < 0 || phaseId >= durationsNs.length) {
            return;
        }

        long elapsed = clock.nanoTime() - startNs;
        if (elapsed < 0L) {
            elapsed = 0L;
        }

        durationsNs[phaseId] += elapsed;
        totalNs += elapsed;
        if (durationsNs[phaseId] > worstNs) {
            worstNs = durationsNs[phaseId];
            worstPhaseId = phaseId;
        }
    }

    public long durationNs(int phaseId) {
        return phaseId >= 0 && phaseId < durationsNs.length ? durationsNs[phaseId] : 0L;
    }

    public long totalNs() {
        return totalNs;
    }

    public int worstPhaseId() {
        return worstPhaseId;
    }

    public long worstNs() {
        return worstNs;
    }

    public long unprofiledRemainderNs(long worldProcessNs) {
        long remainder = worldProcessNs - totalNs;
        return remainder > 0L ? remainder : 0L;
    }

    public void appendReport(StringBuilder out, long worldProcessNs) {
        if (out == null) return;

        out.append("  profiledSystemsTotal: ")
                .append(formatMs(totalNs))
                .append("ms\n");
        out.append("  unprofiledRemainder: ")
                .append(formatMs(unprofiledRemainderNs(worldProcessNs)))
                .append("ms\n");
        if (worstPhaseId >= 0) {
            out.append("  worst: ")
                    .append(SystemProfilePhases.name(worstPhaseId))
                    .append(' ')
                    .append(formatMs(worstNs))
                    .append("ms\n");
        }

        for (int i = 0; i < durationsNs.length; i++) {
            long duration = durationsNs[i];
            if (duration <= 0L) continue;
            out.append("    ")
                    .append(SystemProfilePhases.name(i))
                    .append(": ")
                    .append(formatMs(duration))
                    .append("ms\n");
        }
    }

    public static String formatMs(long ns) {
        long tenths = (ns + 50_000L) / 100_000L;
        return Long.toString(tenths / 10L) + "." + Long.toString(tenths % 10L);
    }
}
