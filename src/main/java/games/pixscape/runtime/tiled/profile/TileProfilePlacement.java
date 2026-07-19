package games.pixscape.runtime.tiled.profile;

public final class TileProfilePlacement {
    private TileProfilePlacement() {
    }

    /**
     * Builds an untransformed sprite quad in BL, TL, TR, BR order.
     *
     * <p>Offsets use y-up world coordinates: +X moves right, +Y moves upward.</p>
     */
    public static void buildSpriteQuad(float cellX,
                                       float cellY,
                                       int mapCellWidth,
                                       int mapCellHeight,
                                       int spriteWidth,
                                       int spriteHeight,
                                       RuntimeTilesetProfile profile,
                                       float[] out8) {
        if (profile == null) {
            throw new IllegalArgumentException("tileset profile is required for tiled sprite placement");
        }

        int referenceCellWidth = profile.referenceCellWidth > 0
                ? profile.referenceCellWidth
                : mapCellWidth;
        int referenceCellHeight = profile.referenceCellHeight > 0
                ? profile.referenceCellHeight
                : mapCellHeight;

        RuntimeTilesetAnchor anchor = profile.anchor != null
                ? profile.anchor
                : RuntimeTilesetAnchor.TOP_CENTER;

        // V1 profiles are native-size only: spriteWidth/spriteHeight are atlas region dimensions.
        // Runtime cellX/cellY is the cell bounding-rect origin for both ortho and iso placement.
        writeAnchoredQuad(
                cellX,
                cellY,
                referenceCellWidth,
                referenceCellHeight,
                spriteWidth,
                spriteHeight,
                anchor,
                profile.offsetX,
                profile.offsetY,
                out8
        );
    }

    public static void buildTopCenterDefaultSpriteQuad(float cellX,
                                                       float cellY,
                                                       int mapCellWidth,
                                                       int mapCellHeight,
                                                       int spriteWidth,
                                                       int spriteHeight,
                                                       float[] out8) {
        float x = cellX + (mapCellWidth - spriteWidth) * 0.5f;
        float y = cellY + mapCellHeight - spriteHeight;
        writeQuad(x, y, spriteWidth, spriteHeight, out8);
    }

    public static void computeSpriteBounds(float[] quad8, float[] out4) {
        requireQuad(quad8);
        requireBounds(out4);

        float minX = Math.min(Math.min(quad8[0], quad8[2]), Math.min(quad8[4], quad8[6]));
        float minY = Math.min(Math.min(quad8[1], quad8[3]), Math.min(quad8[5], quad8[7]));
        float maxX = Math.max(Math.max(quad8[0], quad8[2]), Math.max(quad8[4], quad8[6]));
        float maxY = Math.max(Math.max(quad8[1], quad8[3]), Math.max(quad8[5], quad8[7]));

        out4[0] = minX;
        out4[1] = minY;
        out4[2] = maxX;
        out4[3] = maxY;
    }

    private static void writeAnchoredQuad(float cellX,
                                          float cellY,
                                          int referenceCellWidth,
                                          int referenceCellHeight,
                                          int spriteWidth,
                                          int spriteHeight,
                                          RuntimeTilesetAnchor anchor,
                                          int offsetX,
                                          int offsetY,
                                          float[] out8) {
        float cellAnchorX;
        float cellAnchorY;
        float spriteAnchorX;
        float spriteAnchorY;

        switch (anchor) {
            case BOTTOM_CENTER:
                cellAnchorX = cellX + referenceCellWidth * 0.5f;
                cellAnchorY = cellY;
                spriteAnchorX = spriteWidth * 0.5f;
                spriteAnchorY = 0f;
                break;
            case BOTTOM_LEFT:
                cellAnchorX = cellX;
                cellAnchorY = cellY;
                spriteAnchorX = 0f;
                spriteAnchorY = 0f;
                break;
            case CENTER:
                cellAnchorX = cellX + referenceCellWidth * 0.5f;
                cellAnchorY = cellY + referenceCellHeight * 0.5f;
                spriteAnchorX = spriteWidth * 0.5f;
                spriteAnchorY = spriteHeight * 0.5f;
                break;
            case TOP_LEFT:
                cellAnchorX = cellX;
                cellAnchorY = cellY + referenceCellHeight;
                spriteAnchorX = 0f;
                spriteAnchorY = spriteHeight;
                break;
            case TOP_CENTER:
            default:
                cellAnchorX = cellX + referenceCellWidth * 0.5f;
                cellAnchorY = cellY + referenceCellHeight;
                spriteAnchorX = spriteWidth * 0.5f;
                spriteAnchorY = spriteHeight;
                break;
        }

        float x = cellAnchorX + offsetX - spriteAnchorX;
        float y = cellAnchorY + offsetY - spriteAnchorY;
        writeQuad(x, y, spriteWidth, spriteHeight, out8);
    }

    private static void writeQuad(float x,
                                  float y,
                                  int spriteWidth,
                                  int spriteHeight,
                                  float[] out8) {
        requireQuad(out8);

        float x2 = x + spriteWidth;
        float y2 = y + spriteHeight;

        out8[0] = x;
        out8[1] = y;
        out8[2] = x;
        out8[3] = y2;
        out8[4] = x2;
        out8[5] = y2;
        out8[6] = x2;
        out8[7] = y;
    }

    private static void requireQuad(float[] out8) {
        if (out8 == null || out8.length < 8) {
            throw new IllegalArgumentException("out8 must contain at least 8 floats");
        }
    }

    private static void requireBounds(float[] out4) {
        if (out4 == null || out4.length < 4) {
            throw new IllegalArgumentException("out4 must contain at least 4 floats");
        }
    }
}
