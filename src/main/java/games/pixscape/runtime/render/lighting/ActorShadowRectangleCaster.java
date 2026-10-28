package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.spatial.ActorSpatialRectangleData;

/** Prepared alpha mapping and a separate reusable per-light pair of triangles.
 * The reference publication and its swept bounds never depend on a light.
 */
public final class ActorShadowRectangleCaster {
    public static final int MAX_VERTICES=6;
    private static final float DIAGONAL=.70710678f;
    public final float[] vertices=new float[MAX_VERTICES*8];
    public final float[] lightVertices=new float[MAX_VERTICES*8];
    private final float[] horizontal=new float[MAX_VERTICES],last=new float[17];
    private boolean initialized;
    public int vertexCount;
    public int updates;
    private float pivotX,pivotY,radius,minZ,maxZ;
    public void update(ActorSpatialRectangleData r,float x,float y,float z,float[] quad) {
        boolean same=initialized;
        for(int i=0;i<8;i++)if(last[i]!=quad[i])same=false;
        if(last[8]!=x||last[9]!=y||last[10]!=z||last[11]!=r.x||last[12]!=r.y
                ||last[13]!=r.width||last[14]!=r.height||last[15]!=r.pivotX||last[16]!=r.pivotY)same=false;
        if(same)return;
        float sx=LightingCoordinates.screenX(x,y),sy=LightingCoordinates.screenY(x,y,z);
        pivotX=x+LightingCoordinates.worldX(r.pivotX,r.pivotY,0);
        pivotY=y+LightingCoordinates.worldY(r.pivotX,r.pivotY,0);
        radius=Math.max(Math.abs(r.x-r.pivotX),Math.abs(r.x+r.width-r.pivotX))*DIAGONAL;
        minZ=z+r.y-r.pivotY;maxZ=minZ+r.height;
        for(int i=0;i<MAX_VERTICES;i++) {
            int c=SpriteSurfaceSupport.corner(i),o=i*8;
            float px=r.x+(c>=2?r.width:0),py=r.y+(c==1||c==2?r.height:0);
            horizontal[i]=(px-r.pivotX)*DIAGONAL;
            vertices[o]=pivotX+(px-r.pivotX)*.5f;vertices[o+1]=pivotY-(px-r.pivotX)*.5f;
            vertices[o+2]=z+py-r.pivotY;
            SilhouetteImageMapping.imageUV(quad,sx+px,sy+py,vertices,o+3);
            vertices[o+5]=DIAGONAL;vertices[o+6]=DIAGONAL;vertices[o+7]=0;
        }
        vertexCount=MAX_VERTICES;
        System.arraycopy(quad,0,last,0,8);last[8]=x;last[9]=y;last[10]=z;
        last[11]=r.x;last[12]=r.y;last[13]=r.width;last[14]=r.height;last[15]=r.pivotX;last[16]=r.pivotY;
        initialized=true;updates++;
    }
    /** Once per caster/light before the first face; all depth replays reuse the same vertices. */
    public void faceLight(float lightX,float lightY) {
        float dx=lightX-pivotX,dy=lightY-pivotY,d2=dx*dx+dy*dy;
        float tx=DIAGONAL,ty=-DIAGONAL;
        if(d2>.000001f){float inverse=1f/(float)Math.sqrt(d2);tx=-dy*inverse;ty=dx*inverse;}
        System.arraycopy(vertices,0,lightVertices,0,vertexCount*8);
        for(int i=0;i<vertexCount;i++) {
            int o=i*8;lightVertices[o]=pivotX+horizontal[i]*tx;lightVertices[o+1]=pivotY+horizontal[i]*ty;
            lightVertices[o+5]=ty;lightVertices[o+6]=-tx;
        }
    }
    public void bounds(LightingFrame.Caster caster) {
        caster.minX=pivotX-radius;caster.maxX=pivotX+radius;
        caster.minY=pivotY-radius;caster.maxY=pivotY+radius;caster.minZ=minZ;caster.maxZ=maxZ;
    }
}
