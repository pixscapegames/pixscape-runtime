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
                                       float altitude, float[] corners, float[] out, int offset) {
        rectangle.corners(LightingCoordinates.worldX(footX,footY,altitude),
                LightingCoordinates.worldY(footX,footY,altitude),altitude,corners);
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<12;i+=3) {
            float x=LightingCoordinates.screenX(corners[i],corners[i+1]);
            float y=LightingCoordinates.screenY(corners[i],corners[i+1],corners[i+2]);
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        out[offset]=minX;out[offset+1]=minY;out[offset+2]=maxX;out[offset+3]=maxY;
    }
}

