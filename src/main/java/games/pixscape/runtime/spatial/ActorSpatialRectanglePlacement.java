package games.pixscape.runtime.spatial;

import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.render.lighting.LightingCoordinates;

/** Shared Studio/Runtime placement at the already projected physical footprint centre. */
public final class ActorSpatialRectanglePlacement {
    private ActorSpatialRectanglePlacement() {}
    public static void anchor(float x,float y,float rotation,float footX,float footY,float altitude,float[] out) {
        float c=MathUtils.cos(rotation),s=MathUtils.sin(rotation);
        x+=c*footX-s*footY;y+=s*footX+c*footY;
        out[0]=LightingCoordinates.worldX(x,y,altitude);
        out[1]=LightingCoordinates.worldY(x,y,altitude);out[2]=altitude;
    }
    /** Axis-aligned screen envelope; no texture/frame bounds or display offset are inferred. */
    public static void influenceBounds(ActorSpatialRectangleData rectangle, float footX, float footY,
                                       float[] out, int offset) {
        out[offset]=footX+rectangle.x;out[offset+1]=footY+rectangle.y;
        out[offset+2]=out[offset]+rectangle.width;out[offset+3]=out[offset+1]+rectangle.height;
    }
}

