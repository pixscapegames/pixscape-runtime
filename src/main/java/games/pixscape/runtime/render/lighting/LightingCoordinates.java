package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

/** Common metric: native world pixels, before camera and display parallax. */
public final class LightingCoordinates {
    private LightingCoordinates() {}
    public static float screenX(float x, float y) { return x - y; }
    public static float screenY(float x, float y, float z) { return (x + y) * .5f + z; }
    public static float worldX(float sx, float sy, float z) { return sy - z + sx * .5f; }
    public static float worldY(float sx, float sy, float z) { return sy - z - sx * .5f; }
    /** The drawn plane is D; physical Z is the authored absolute altitude A. */
    public static void mapPoint(TiledMapLayerData map, float gx, float gy,
                                float altitude, float[] out, int offset) {
        // Algebraic inverse of projectSpatialPoint, evaluated before float rounding.
        // A cancels from XY. A screen-space float round trip otherwise moves a
        // face by an ULP as its altitude or its longitudinal endpoint changes.
        double base=(double)map.originY-map.defaultTileAltitude;
        double halfX=map.originX*.5;
        if(map.projection==TiledProjection.ISO) {
            double quarterW=map.tileWidth*.25,halfH=map.tileHeight*.5;
            out[offset]=(float)(base+halfX+quarterW+gx*(halfH+quarterW)+gy*(halfH-quarterW));
            out[offset+1]=(float)(base-halfX-quarterW+gx*(halfH-quarterW)+gy*(halfH+quarterW));
        } else {
            double x=gx*(map.tileWidth*.5),y=gy*(double)map.tileHeight;
            out[offset]=(float)(base+halfX+x+y);
            out[offset+1]=(float)(base-halfX-x+y);
        }
        out[offset + 2] = altitude;
    }
}
