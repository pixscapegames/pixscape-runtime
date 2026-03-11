package games.pixscape.runtime.tiled;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntArray;

public final class TileChunk {
    public enum DirtyState {
        CLEAN,
        PARTIAL,
        FULL
    }
    public DirtyState dirtyState = DirtyState.FULL; // initial build
    public IntArray dirtyLocalIndices = new IntArray(false, 8);
    public int chunkX;
    public int chunkY;

    public int chunkWidth;
    public int chunkHeight;

    public int[] assetIds;

    public int soaStartIndex;
    public int soaCount;

    public boolean contentDirty = true;
    public boolean collisionDirty = true;
    public boolean visibleLastFrame = false;

    public Rectangle bounds;

    public TileChunk() {
    }

    public TileChunk(
            int chunkX,
            int chunkY,
            int chunkWidth,
            int chunkHeight,
            int worldTileX,
            int worldTileY,
            float originX,
            float originY,
            int tileWidth,
            int tileHeight,
            int soaStartIndex) {

        this.chunkX = chunkX;
        this.chunkY = chunkY;

        this.chunkWidth = chunkWidth;
        this.chunkHeight = chunkHeight;

        this.assetIds = new int[chunkWidth * chunkHeight];

        this.soaStartIndex = soaStartIndex;
        this.soaCount = chunkWidth * chunkHeight;

        float worldX = originX + worldTileX * tileWidth;
        float worldY = originY + worldTileY * tileHeight;
        float worldW = chunkWidth  * tileWidth;
        float worldH = chunkHeight * tileHeight;

        this.bounds = new Rectangle(worldX, worldY, worldW, worldH);
    }

    public int get(int localX, int localY) {
        return assetIds[localY * chunkWidth + localX];
    }

    public void set(int localX, int localY, int assetId) {
        if (localX < 0 || localY < 0 ||
                localX >= chunkWidth || localY >= chunkHeight)
            return;

        int index = localY * chunkWidth + localX;

        if (assetIds[index] == assetId)
            return;

        assetIds[index] = assetId;
        contentDirty = true;

        if (dirtyState != DirtyState.FULL) {
            dirtyState = DirtyState.PARTIAL;
            dirtyLocalIndices.add(index);
        }

        collisionDirty = true;
    }

    public int slotFor(int localX, int localY) {
        return soaStartIndex + (localY * chunkWidth + localX);
    }
}
