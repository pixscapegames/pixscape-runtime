package games.pixscape.runtime.render;

public final class DirtyBits {

    private DirtyBits() {}

    public static final int NONE       = 0;

    public static final int GEOMETRY   = 1 << 0;
    public static final int MATERIAL   = 1 << 1;
    public static final int COLOR      = 1 << 2;
    public static final int ORDER      = 1 << 3;
    public static final int LAYER      = 1 << 4;
    public static final int CAMERA     = 1 << 5;

    // geometry submask packing
    public static final int GEOM_SUB_BITS  = 5;
    public static final int GEOM_SUB_SHIFT = 7; // bits 7..11

    private static final int GEOM_SUB_MASK = ((1 << GEOM_SUB_BITS) - 1) << GEOM_SUB_SHIFT;

    // coarse bits AFTER geom-sub area
    public static final int PHYSICS = 1 << (GEOM_SUB_SHIFT + GEOM_SUB_BITS);       // 1<<12
    public static final int JOINTS  = 1 << (GEOM_SUB_SHIFT + GEOM_SUB_BITS + 1);   // 1<<13 ✅

    public static final int EVERYTHING =
            GEOMETRY | MATERIAL | COLOR | ORDER | LAYER | CAMERA | PHYSICS | JOINTS;

    public static final int COARSE_MASK = EVERYTHING;

    public static int geomSubFromPacked(int packed) {
        return (packed & GEOM_SUB_MASK) >>> GEOM_SUB_SHIFT;
    }

    public static int geomSubPackInto(int packed, int geomSub) {
        int sub = (geomSub & ((1 << GEOM_SUB_BITS) - 1)) << GEOM_SUB_SHIFT;
        return (packed & ~GEOM_SUB_MASK) | sub;
    }
}
