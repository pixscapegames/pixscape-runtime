package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.spatial.ActorVolumeData;

/** Test-only single bowed sheet. No alpha scan, light input, extra atlas, or skin duplication. */
public final class RoundedActorSheetPrototype {
    public static final int SEGMENTS=4, MAX_VERTICES=SEGMENTS*SEGMENTS*18;
    public final float[] vertices=new float[MAX_VERTICES*8];
    private final double[][] triangle=new double[3][3],polygon=new double[5][3];
    private final double[] last=new double[18];
    private double cx,cy,cz,dx,dy,width,depth,height;
    private float[] quad;
    private boolean initialized;
    public int vertexCount,updates;

    public void update(ActorVolumeData v,float ax,float ay,float az,float[] q){
        boolean same=initialized;
        for(int i=0;i<8;i++)if(last[i]!=q[i])same=false;
        if(last[8]!=ax||last[9]!=ay||last[10]!=az||last[11]!=v.width||last[12]!=v.depth||last[13]!=v.height
                ||last[14]!=v.offsetX||last[15]!=v.offsetY||last[16]!=v.offsetZ||last[17]!=v.orientation)same=false;
        if(same)return;
        cx=ax+v.offsetX;cy=ay+v.offsetY;cz=az+v.offsetZ;quad=q;
        dx=Math.cos(Math.toRadians(v.orientation));dy=Math.sin(Math.toRadians(v.orientation));
        width=v.width;depth=v.depth;height=v.height;vertexCount=0;
        for(int y=0;y<SEGMENTS;y++)for(int x=0;x<SEGMENTS;x++){
            double u=x/(double)SEGMENTS,vv=y/(double)SEGMENTS,e=1.0/SEGMENTS;
            split(u,vv,u,vv+e,u+e,vv+e);split(u,vv,u+e,vv+e,u+e,vv);
        }
        for(int i=0;i<8;i++)last[i]=q[i];last[8]=ax;last[9]=ay;last[10]=az;
        last[11]=v.width;last[12]=v.depth;last[13]=v.height;last[14]=v.offsetX;last[15]=v.offsetY;last[16]=v.offsetZ;last[17]=v.orientation;
        updates++;initialized=true;
    }
    /** Returns the unbowed altitude; X,Y will subsequently be recovered from the same projection. */
    private double baseZ(double u,double v){
        double sx=BaselineActorShadowImageMapping.x(quad,(float)u,(float)v),sy=BaselineActorShadowImageMapping.y(quad,(float)u,(float)v);
        double a=dx-dy,b=-dy-dx,s=sx-(cx-cy),longitudinal=a*a*width*width,transverse=b*b*depth*depth,t,r;
        if(longitudinal>=transverse){t=s/a;r=0;} // Here a cannot vanish for a valid positive box.
        else{
            double ratio=longitudinal/transverse,centralWeight=ratio*(2-ratio);
            // centralWeight*s/a evaluated without division by a; continuous at a=0.
            t=s*a*width*width/transverse*(2-ratio)
                    +(1-centralWeight)*s*a*width*width/(longitudinal+transverse);
            r=(s-a*t)/b; // Here b cannot vanish.
        }
        return sy-(cx+cy+(dx+dy)*t+(dx-dy)*r)*.5;
    }
    private void split(double u0,double v0,double u1,double v1,double u2,double v2){
        triangle[0][0]=u0;triangle[0][1]=v0;triangle[1][0]=u1;triangle[1][1]=v1;triangle[2][0]=u2;triangle[2][1]=v2;
        for(int i=0;i<3;i++)triangle[i][2]=baseZ(triangle[i][0],triangle[i][1]);
        // Split the ground line exactly before clamping; do not lift feet by interpolating a clamped coarse grid.
        for(int side=-1;side<=1;side+=2){int count=0;
            for(int i=0;i<3;i++){
                double[] p=triangle[i],q=triangle[(i+1)%3];double d=side*(p[2]-cz),e=side*(q[2]-cz);
                if(d>=0){System.arraycopy(p,0,polygon[count++],0,3);}
                if((d<0)!=(e<0)){double t=d/(d-e);double[] a=polygon[count++];a[0]=p[0]+t*(q[0]-p[0]);a[1]=p[1]+t*(q[1]-p[1]);a[2]=cz;}
            }
            for(int i=1;i<count-1;i++){
                double area=(polygon[i][0]-polygon[0][0])*(polygon[i+1][1]-polygon[0][1])-(polygon[i][1]-polygon[0][1])*(polygon[i+1][0]-polygon[0][0]);
                if(Math.abs(area)<1e-14)continue;
                int start=vertexCount*8;append(polygon[0]);append(polygon[i]);append(polygon[i+1]);
                double ax=vertices[start+8]-vertices[start],ay=vertices[start+9]-vertices[start+1],az=vertices[start+10]-vertices[start+2];
                double bx=vertices[start+16]-vertices[start],by=vertices[start+17]-vertices[start+1],bz=vertices[start+18]-vertices[start+2];
                double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,len=Math.sqrt(nx*nx+ny*ny+nz*nz);
                for(int k=0;k<3;k++){int o=start+k*8;vertices[o+5]=(float)(nx/len);vertices[o+6]=(float)(ny/len);vertices[o+7]=(float)(nz/len);}
            }
        }
    }
    private void append(double[] p){
        double u=p[0],v=p[1],z=Math.max(cz,p[2]);
        double h=Math.min(1,(z-cz)/height),profile=4*u*(1-u);
        // Historical single-sheet bow, now dimensioned and oriented by the authored box.
        // The displayed sprite is unchanged; this transverse caster bow intentionally changes its projection.
        double bow=depth*.5*profile*h;
        double sx=BaselineActorShadowImageMapping.x(quad,(float)u,(float)v),sy=BaselineActorShadowImageMapping.y(quad,(float)u,(float)v);
        int o=vertexCount++*8;vertices[o]=(float)(sy-z+sx*.5-dy*bow);vertices[o+1]=(float)(sy-z-sx*.5+dx*bow);vertices[o+2]=(float)z;
        vertices[o+3]=(float)u;vertices[o+4]=(float)v;
    }
}
