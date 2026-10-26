package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.spatial.ActorSpatialRectangleData;

/** Two alpha-bearing triangles. Geometry and UVs are independent of the light.
 * Rebuilt only when authored placement or the displayed image quad changes.
 */
public final class ActorShadowRectangleCaster {
    public static final int MAX_VERTICES=6;
    public final float[] vertices=new float[MAX_VERTICES*8];
    private final float[] corners=new float[12],last=new float[17];
    private boolean initialized;
    public int vertexCount;
    public int updates;
    public void update(ActorSpatialRectangleData rectangle,float x,float y,float z,float[] quad) {
        boolean same=initialized;
        for(int i=0;i<8;i++)if(last[i]!=quad[i])same=false;
        if(last[8]!=x||last[9]!=y||last[10]!=z||last[11]!=rectangle.width
                ||last[12]!=rectangle.height||last[13]!=rectangle.offsetX||last[14]!=rectangle.offsetY
                ||last[15]!=rectangle.offsetZ||last[16]!=rectangle.orientation)same=false;
        if(same)return;
        rectangle.corners(x,y,z,corners);
        float nx=(corners[10]-corners[1])/rectangle.width;
        float ny=-(corners[9]-corners[0])/rectangle.width;
        for(int i=0;i<MAX_VERTICES;i++) {
            int c=SpriteSurfaceSupport.corner(i)*3,o=i*8;
            vertices[o]=corners[c];vertices[o+1]=corners[c+1];vertices[o+2]=corners[c+2];
            SilhouetteImageMapping.imageUV(quad,LightingCoordinates.screenX(corners[c],corners[c+1]),
                    LightingCoordinates.screenY(corners[c],corners[c+1],corners[c+2]),vertices,o+3);
            vertices[o+5]=nx;vertices[o+6]=ny;vertices[o+7]=0;
        }
        vertexCount=MAX_VERTICES;
        System.arraycopy(quad,0,last,0,8);last[8]=x;last[9]=y;last[10]=z;
        last[11]=rectangle.width;last[12]=rectangle.height;
        last[13]=rectangle.offsetX;last[14]=rectangle.offsetY;last[15]=rectangle.offsetZ;last[16]=rectangle.orientation;
        initialized=true;updates++;
    }
}
