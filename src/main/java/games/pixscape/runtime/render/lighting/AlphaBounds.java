package games.pixscape.runtime.render.lighting;

/** Derived pixel-cell bounds in the original, untrimmed image: U right, V up.
 * Packing coordinates never enter this data. A threshold mismatch requires the full-image fallback. */
public final class AlphaBounds {
    public float minU, minV, maxU=1, maxV=1, threshold=.5f;
    public boolean empty;
    public int sourceWidth, sourceHeight;

    public boolean usable(float threshold) {
        return Float.floatToIntBits(this.threshold)==Float.floatToIntBits(threshold)
                && sourceWidth>0 && sourceHeight>0 && minU>=0 && minV>=0
                && maxU<=1 && maxV<=1 && minU<=maxU && minV<=maxV;
    }
    public AlphaBounds copy() {
        AlphaBounds b=new AlphaBounds();b.minU=minU;b.minV=minV;b.maxU=maxU;b.maxV=maxV;
        b.threshold=threshold;b.empty=empty;b.sourceWidth=sourceWidth;b.sourceHeight=sourceHeight;return b;
    }
}
