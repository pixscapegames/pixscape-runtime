package games.pixscape.runtime.tiled.profile;

import games.pixscape.runtime.tiled.TiledProjection;

public final class RuntimeTilesetProfile {
    public int tilesetId;
    public String logicalPath;
    public int tileWidth;
    public int tileHeight;
    public int referenceCellWidth;
    public int referenceCellHeight;
    public TiledProjection projection = TiledProjection.ORTHO;
    public RuntimeTilesetAnchor anchor = RuntimeTilesetAnchor.TOP_CENTER;
    public int offsetX;
    public int offsetY;
    public RuntimeTilesetRenderSize renderSize = RuntimeTilesetRenderSize.NATIVE;
    public int[] tileAssetIds = new int[0];
}
