package games.pixscape.runtime.api;

public interface TileEditFacade {
    int get(int x, int y);
    byte getFlags(int x, int y);

    TileEditFacade set(int x, int y, int assetId);
    TileEditFacade set(int x, int y, int assetId, byte flags);
    TileEditFacade clear(int x, int y);

    TileEditFacade fillRect(int x, int y, int width, int height, int assetId);
    TileEditFacade fillRect(int x, int y, int width, int height, int assetId, byte flags);
    TileEditFacade clearRect(int x, int y, int width, int height);

    TileEditFacade hLine(int x, int y, int length, int assetId);
    TileEditFacade hLine(int x, int y, int length, int assetId, byte flags);
    TileEditFacade vLine(int x, int y, int length, int assetId);
    TileEditFacade vLine(int x, int y, int length, int assetId, byte flags);

    TileEditFacade markAllDirty();
}
