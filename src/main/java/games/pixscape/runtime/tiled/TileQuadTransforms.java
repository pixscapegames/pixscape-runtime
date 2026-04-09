package games.pixscape.runtime.tiled;

public final class TileQuadTransforms {
    private TileQuadTransforms() {}

    /**
     * Construit le quad monde d'un tile sprite transformé.
     *
     * out8 order:
     * 0,1 = BL
     * 2,3 = TL
     * 4,5 = TR
     * 6,7 = BR
     */
    public static void buildSpriteQuad(TiledMapLayerData map,
                                       int gx,
                                       int gy,
                                       int spriteW,
                                       int spriteH,
                                       byte flags,
                                       float[] out8) {

        map.tileToSpriteQuad(gx, gy, spriteW, spriteH, out8);

        flags = TileTransformFlags.sanitize(flags);
        if (flags == TileTransformFlags.NONE) return;

        float cx = (out8[0] + out8[4]) * 0.5f;
        float cy = (out8[1] + out8[5]) * 0.5f;

        float halfW = spriteW * 0.5f;
        float halfH = spriteH * 0.5f;

        writeCorner(cx, cy, -halfW, -halfH, flags, out8, 0); // BL
        writeCorner(cx, cy, -halfW,  halfH, flags, out8, 2); // TL
        writeCorner(cx, cy,  halfW,  halfH, flags, out8, 4); // TR
        writeCorner(cx, cy,  halfW, -halfH, flags, out8, 6); // BR
    }

    private static void writeCorner(float cx,
                                    float cy,
                                    float lx,
                                    float ly,
                                    byte flags,
                                    float[] out8,
                                    int outIndex) {

        float tx;
        float ty;

        switch (flags & 0x7) {
            case TileTransformFlags.NONE -> {
                tx = lx;
                ty = ly;
            }
            case TileTransformFlags.FLIP_H -> {
                tx = -lx;
                ty = ly;
            }
            case TileTransformFlags.FLIP_V -> {
                tx = lx;
                ty = -ly;
            }
            case TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V -> {
                tx = -lx;
                ty = -ly;
            }
            case TileTransformFlags.FLIP_D -> {
                tx = ly;
                ty = lx;
            }
            case TileTransformFlags.FLIP_D | TileTransformFlags.FLIP_H -> {
                tx = -ly;
                ty = lx;
            }
            case TileTransformFlags.FLIP_D | TileTransformFlags.FLIP_V -> {
                tx = ly;
                ty = -lx;
            }
            case TileTransformFlags.FLIP_D | TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V -> {
                tx = -ly;
                ty = -lx;
            }
            default -> {
                tx = lx;
                ty = ly;
            }
        }

        out8[outIndex] = cx + tx;
        out8[outIndex + 1] = cy + ty;
    }
}