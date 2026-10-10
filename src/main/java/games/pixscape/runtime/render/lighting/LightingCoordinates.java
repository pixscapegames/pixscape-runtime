package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Common metric: native world pixels, before camera and display parallax. */
public final class LightingCoordinates {
    private LightingCoordinates() {}
    public static float screenX(float x, float y) { return x - y; }
    public static float screenY(float x, float y, float z) { return (x + y) * .5f + z; }
    public static float worldX(float sx, float sy, float z) { return sy - z + sx * .5f; }
    public static float worldY(float sx, float sy, float z) { return sy - z - sx * .5f; }
    /** Registration is explicitly independent of the map's sorting altitude. */
    public static float mapZ(TiledMapLayerData map, float planeZ, float altitude) {
        return planeZ + altitude - map.defaultTileAltitude;
    }
    public static void mapPoint(TiledMapLayerData map, float planeZ, float gx, float gy,
                                float altitude, float[] out, int offset) {
        map.projectSpatialPoint(gx, gy, altitude, out, offset);
        float z = mapZ(map, planeZ, altitude);
        float sx = out[offset], sy = out[offset + 1];
        out[offset] = worldX(sx, sy, z);
        out[offset + 1] = worldY(sx, sy, z);
        out[offset + 2] = z;
    }
}
