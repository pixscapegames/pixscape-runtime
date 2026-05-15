package games.pixscape.runtime.render;

public final class SortKey64 {
    private SortKey64() {
    }

    // Bits
    public static final int PASS_BITS = 2;   // 0..3
    public static final int SHADER_BITS = 7;   // 0..127
    public static final int BLEND_BITS = 4;   // 0..15
    public static final int LAYER_BITS = 9;   // 0..511

    public static final int TEX_BITS = 12;  // 0..4095
    public static final int Z_BITS = 16;  // biased => -32768..+32767
    public static final int TIE_BITS = 14;  // 0..16383

    public static final int MAX_TEXTURE_HANDLE = (1 << TEX_BITS) - 1;
    public static final int MAX_TIE = (1 << TIE_BITS) - 1;

    static {
        int sum = PASS_BITS + SHADER_BITS + BLEND_BITS + TEX_BITS + LAYER_BITS + Z_BITS + TIE_BITS;
        if (sum != 64) {
            throw new AssertionError("SortKey64 layout must sum to 64 bits, got " + sum);
        }
    }

    // Masks
    private static final long PASS_MASK = (1L << PASS_BITS) - 1L;
    private static final long SHADER_MASK = (1L << SHADER_BITS) - 1L;
    private static final long BLEND_MASK = (1L << BLEND_BITS) - 1L;
    private static final long TEX_MASK = (1L << TEX_BITS) - 1L;

    private static final long LAYER_MASK = (1L << LAYER_BITS) - 1L;
    private static final long Z_MASK = (1L << Z_BITS) - 1L;
    private static final long TIE_MASK = (1L << TIE_BITS) - 1L;

    private static final long Z_BIAS = 1L << (Z_BITS - 1);

    // ORDERED: [layer|z|pass|tie|shader|blend|tex]
    private static final int ORD_TEX_SHIFT = 0;
    private static final int ORD_BLEND_SHIFT = ORD_TEX_SHIFT + TEX_BITS;
    private static final int ORD_SHADER_SHIFT = ORD_BLEND_SHIFT + BLEND_BITS;
    private static final int ORD_TIE_SHIFT = ORD_SHADER_SHIFT + SHADER_BITS;
    private static final int ORD_PASS_SHIFT = ORD_TIE_SHIFT + TIE_BITS;
    private static final int ORD_Z_SHIFT = ORD_PASS_SHIFT + PASS_BITS;
    private static final int ORD_LAYER_SHIFT = ORD_Z_SHIFT + Z_BITS;

    // MATERIAL_FIRST: [layer|z|pass|shader|blend|tex|tie]
    private static final int MAT_TIE_SHIFT = 0;
    private static final int MAT_TEX_SHIFT = MAT_TIE_SHIFT + TIE_BITS;
    private static final int MAT_BLEND_SHIFT = MAT_TEX_SHIFT + TEX_BITS;
    private static final int MAT_SHADER_SHIFT = MAT_BLEND_SHIFT + BLEND_BITS;
    private static final int MAT_PASS_SHIFT = MAT_SHADER_SHIFT + SHADER_BITS;
    private static final int MAT_Z_SHIFT = MAT_PASS_SHIFT + PASS_BITS;
    private static final int MAT_LAYER_SHIFT = MAT_Z_SHIFT + Z_BITS;

    public static long packOrdered(int passId, int shaderIdx, int blendModeId, int textureHandle,
                                   int layer, int z, int tie) {
        long zb = ((long) z + Z_BIAS) & Z_MASK;
        return (((long) layer & LAYER_MASK) << ORD_LAYER_SHIFT) |
                (zb << ORD_Z_SHIFT) |
                (((long) passId & PASS_MASK) << ORD_PASS_SHIFT) |
                (((long) tie & TIE_MASK) << ORD_TIE_SHIFT) |
                (((long) shaderIdx & SHADER_MASK) << ORD_SHADER_SHIFT) |
                (((long) blendModeId & BLEND_MASK) << ORD_BLEND_SHIFT) |
                (((long) textureHandle & TEX_MASK) << ORD_TEX_SHIFT);
    }

    public static long packMaterialFirst(int passId, int shaderIdx, int blendModeId, int textureHandle,
                                         int layer, int z, int tie) {
        long zb = ((long) z + Z_BIAS) & Z_MASK;
        return (((long) layer & LAYER_MASK) << MAT_LAYER_SHIFT) |
                (zb << MAT_Z_SHIFT) |
                (((long) passId & PASS_MASK) << MAT_PASS_SHIFT) |
                (((long) shaderIdx & SHADER_MASK) << MAT_SHADER_SHIFT) |
                (((long) blendModeId & BLEND_MASK) << MAT_BLEND_SHIFT) |
                (((long) textureHandle & TEX_MASK) << MAT_TEX_SHIFT) |
                (((long) tie & TIE_MASK) << MAT_TIE_SHIFT);
    }

    public static long packForBlend(int shaderIdx, int blendModeId, int textureHandle,
                                    int layer, int z, int runtimeOrder) {
        int tie = runtimeOrder & (int) TIE_MASK;

        BlendMode mode = BlendMode.fromId(blendModeId);
        int passId = (mode != null ? mode.passId() : BlendMode.PASS_ORDERED);

        if (passId == BlendMode.PASS_COMMUTATIVE || passId == BlendMode.PASS_OPAQUE) {
            return packMaterialFirst(passId, shaderIdx, blendModeId, textureHandle, layer, z, tie);
        }
        return packOrdered(passId, shaderIdx, blendModeId, textureHandle, layer, z, tie);
    }

    // Unpack debug
    public static int unpackLayerOrdered(long k) {
        return (int) ((k >>> ORD_LAYER_SHIFT) & LAYER_MASK);
    }

    public static int unpackZOrdered(long k) {
        long zb = (k >>> ORD_Z_SHIFT) & Z_MASK;
        return (int) (zb - Z_BIAS);
    }

    public static int unpackPassOrdered(long k) {
        return (int) ((k >>> ORD_PASS_SHIFT) & PASS_MASK);
    }

    public static int unpackTieOrdered(long k) {
        return (int) ((k >>> ORD_TIE_SHIFT) & TIE_MASK);
    }

    // MaterialId = [shader|blend|tex] (int)
    public static int packMaterialId(int shaderIdx, int blendModeId, int textureHandle) {
        return ((shaderIdx & (int) SHADER_MASK) << (BLEND_BITS + TEX_BITS))
                | ((blendModeId & (int) BLEND_MASK) << TEX_BITS)
                | (textureHandle & (int) TEX_MASK);
    }

    public static int unpackMaterialShaderIdx(int materialId) {
        return (materialId >>> (BLEND_BITS + TEX_BITS)) & (int) SHADER_MASK;
    }

    public static int unpackMaterialBlendModeId(int materialId) {
        return (materialId >>> TEX_BITS) & (int) BLEND_MASK;
    }

    public static int unpackMaterialTextureHandle(int materialId) {
        return materialId & (int) TEX_MASK;
    }
}