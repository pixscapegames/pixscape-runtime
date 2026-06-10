package games.pixscape.runtime.profiling;

public final class SystemProfilers {
    public static final SystemProfiler DISABLED = new SystemProfiler() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void beginFrame() {
        }

        @Override
        public long begin(int phaseId) {
            return 0L;
        }

        @Override
        public void end(int phaseId, long startNs) {
        }
    };

    private SystemProfilers() {
    }

    public static SystemProfiler orDisabled(SystemProfiler profiler) {
        return profiler != null ? profiler : DISABLED;
    }
}
