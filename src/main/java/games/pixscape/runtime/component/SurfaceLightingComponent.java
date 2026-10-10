package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import games.pixscape.runtime.render.lighting.LocalSurfaceDescription;

/** Explicit sprite sheet placement; independent of Spatial actor sorting height. */
public final class SurfaceLightingComponent extends PooledComponent {
    public boolean receiveLight = true;
    public boolean shadowCaster = false;
    public float altitude = 0;
    /** Stable foot in the complete, untrimmed image, bottom-left UV convention. */
    public float anchorU = .5f, anchorV = 0;
    /** Horizontal sheet direction in common XYZ, never turned towards a light. */
    public float directionX = 1, directionY = -1;
    public float alphaThreshold = .5f;
    public LocalSurfaceDescription description;
    public SurfaceLightingComponent copy() {
        SurfaceLightingComponent c=new SurfaceLightingComponent();c.receiveLight=receiveLight;c.shadowCaster=shadowCaster;
        c.altitude=altitude;c.anchorU=anchorU;c.anchorV=anchorV;c.directionX=directionX;c.directionY=directionY;
        c.alphaThreshold=alphaThreshold;c.description=description==null?null:description.copy();return c;
    }
    @Override protected void reset() {
        receiveLight = true; shadowCaster = false; altitude = 0;
        anchorU = .5f; anchorV = 0; directionX = 1; directionY = -1;
        alphaThreshold = .5f; description = null;
    }
}
