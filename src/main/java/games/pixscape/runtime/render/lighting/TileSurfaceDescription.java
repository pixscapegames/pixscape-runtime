package games.pixscape.runtime.render.lighting;

/** Map-local shared asset description; XYZ origin is the map cell's projected spatial anchor. */
public final class TileSurfaceDescription {
    public int assetId;
    public LocalSurfaceDescription geometry;
}
