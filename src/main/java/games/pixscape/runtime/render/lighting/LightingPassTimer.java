package games.pixscape.runtime.render.lighting;

/** Optional backend profiling observer. Implementations must not block, allocate or alter render state. */
public interface LightingPassTimer {
    int ORIGINAL=0,SHADOW=1,RECEIVERS=2,ACCUMULATION=3,COMPOSITION=4;
    void begin(int pass);
    void end(int pass);
}
