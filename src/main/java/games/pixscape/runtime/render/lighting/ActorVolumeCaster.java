package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.spatial.ActorVolumeData;

/** Alpha-cut box boundary. No light-dependent deformation and no opaque box underneath.
 * This is an approximate shell, not a reconstruction of hidden anatomy or alpha-contour walls.
 * Fixed scratch/storage: six quads clipped against four image borders, at most eight corners each.
 */
public final class ActorVolumeCaster {
    public static final int MAX_VERTICES=108;
    private static final int[] FACES={0,3,2,1,4,5,6,7,0,1,5,4,1,2,6,5,2,3,7,6,3,0,4,7};
    public final float[] vertices=new float[MAX_VERTICES*8];
    private final float[] box=new float[24],a=new float[32],last=new float[18];
    private final SilhouetteFaceCompiler compiler=new SilhouetteFaceCompiler();
    private boolean initialized;
    public int vertexCount;
    public int updates;
    public void update(ActorVolumeData volume,float x,float y,float z,float[] quad) {
        boolean same=initialized;
        for(int i=0;i<8;i++)if(last[i]!=quad[i])same=false;
        if(last[8]!=x||last[9]!=y||last[10]!=z||last[11]!=volume.width||last[12]!=volume.depth
                ||last[13]!=volume.height||last[14]!=volume.offsetX||last[15]!=volume.offsetY
                ||last[16]!=volume.offsetZ||last[17]!=volume.orientation)same=false;
        if(same)return;
        volume.corners(x,y,z,box);vertexCount=0;
        for(int face=0;face<6;face++){
            int p=FACES[face*4]*3,q=FACES[face*4+1]*3,r=FACES[face*4+2]*3;
            float ux=box[q]-box[p],uy=box[q+1]-box[p+1],uz=box[q+2]-box[p+2];
            float vx=box[r]-box[p],vy=box[r+1]-box[p+1],vz=box[r+2]-box[p+2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=length;ny/=length;nz/=length;
            for(int j=0;j<4;j++){
                int k=FACES[face*4+j]*3,o=j*8;
                a[o]=box[k];a[o+1]=box[k+1];a[o+2]=box[k+2];
                a[o+5]=nx;a[o+6]=ny;a[o+7]=nz;
            }
            vertexCount+=compiler.compile(a,quad,vertices,vertexCount*8);
        }
        System.arraycopy(quad,0,last,0,8);last[8]=x;last[9]=y;last[10]=z;
        last[11]=volume.width;last[12]=volume.depth;last[13]=volume.height;
        last[14]=volume.offsetX;last[15]=volume.offsetY;last[16]=volume.offsetZ;last[17]=volume.orientation;
        initialized=true;updates++;
    }
}
