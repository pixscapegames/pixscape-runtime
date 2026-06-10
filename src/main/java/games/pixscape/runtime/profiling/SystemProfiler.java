package games.pixscape.runtime.profiling;

public interface SystemProfiler {
    boolean enabled();

    void beginFrame();

    long begin(int phaseId);

    void end(int phaseId, long startNs);
}
