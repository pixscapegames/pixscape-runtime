package games.pixscape.runtime.render.lighting;

/** Existing anchored sprite-sheet geometry, independent of normal-map resources. */
public final class SpriteSurfaceSupport {
    private SpriteSurfaceSupport(){}
    public static void sheet(float[] vertices,float[] q,float u,float v,float altitude,float dx,float dy){
        float sx=q[0]+u*(q[6]-q[0])+v*(q[2]-q[0]),sy=q[1]+u*(q[7]-q[1])+v*(q[3]-q[1]);
        float x=LightingCoordinates.worldX(sx,sy,altitude),y=LightingCoordinates.worldY(sx,sy,altitude),den=dx-dy;
        if(Math.abs(den)<.000001f)throw new IllegalArgumentException("Sheet direction projects to zero width");
        float nx=-dy,ny=dx,len=(float)Math.sqrt(nx*nx+ny*ny);nx/=len;ny/=len;
        for(int i=0;i<6;i++){
            int c=corner(i),o=i*8;float t=(q[c*2]-sx)/den,px=x+dx*t,py=y+dy*t;
            write(vertices,o,px,py,q[c*2+1]-(px+py)*.5f,c,nx,ny,0);
        }
    }
    private static int corner(int i){return i==0||i==3?0:i==1?1:i==2||i==4?2:3;}
    private static void write(float[] a,int o,float x,float y,float z,int c,float nx,float ny,float nz){
        a[o]=x;a[o+1]=y;a[o+2]=z;a[o+3]=c>=2?1:0;a[o+4]=c==1||c==2?1:0;
        a[o+5]=nx;a[o+6]=ny;a[o+7]=nz;
    }
}
