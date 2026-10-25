package games.pixscape.runtime.spatial;

import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.render.lighting.LightingCoordinates;

/** Shared Studio/Runtime placement at the already projected physical footprint centre. */
public final class ActorVolumePlacement {
    private ActorVolumePlacement() {}
    public static void anchor(float x,float y,float rotation,float footX,float footY,float altitude,float[] out) {
        float c=MathUtils.cos(rotation),s=MathUtils.sin(rotation);
        x+=c*footX-s*footY;y+=s*footX+c*footY;
        out[0]=LightingCoordinates.worldX(x,y,altitude);
        out[1]=LightingCoordinates.worldY(x,y,altitude);out[2]=altitude;
    }
}
