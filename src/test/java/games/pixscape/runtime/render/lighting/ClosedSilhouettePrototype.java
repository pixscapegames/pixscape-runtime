package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.FloatArray;
import games.pixscape.runtime.spatial.ActorVolumeData;

/** Test-only closure of the binary texel silhouette intersected with an oriented box.
 * Keep the existing alpha-cut box faces as caps; these opaque contour faces close the
 * external AND internal boundaries. No filling or convex hull. Prepared per resource/pose.
 */
final class ClosedSilhouettePrototype {
    final float[] sides;
    final int segments;
    final long preparationNs;
    ClosedSilhouettePrototype(Pixmap image,float[] quad,ActorVolumeData volume,float x,float y,float z){
        long start=System.nanoTime();int w=image.getWidth(),h=image.getHeight(),count=0;
        boolean[] solid=new boolean[w*h];
        for(int py=0;py<h;py++)for(int px=0;px<w;px++)solid[py*w+px]=(image.getPixel(px,py)&255)>=128;
        FloatArray out=new FloatArray();
        // Merge adjacent collinear boundary edges. Every hole boundary is retained.
        for(int py=0;py<=h;py++){
            int begin=-1;
            for(int px=0;px<=w;px++){
                boolean edge=px<w&&((py>0&&solid[(py-1)*w+px])!=(py<h&&solid[py*w+px]));
                if(edge&&begin<0)begin=px;
                if(!edge&&begin>=0){face(out,quad,volume,x,y,z,(float)begin/w,1f-(float)py/h,(float)px/w,1f-(float)py/h);count++;begin=-1;}
            }
        }
        for(int px=0;px<=w;px++){
            int begin=-1;
            for(int py=0;py<=h;py++){
                boolean edge=py<h&&((px>0&&solid[py*w+px-1])!=(px<w&&solid[py*w+px]));
                if(edge&&begin<0)begin=py;
                if(!edge&&begin>=0){face(out,quad,volume,x,y,z,(float)px/w,1f-(float)begin/h,(float)px/w,1f-(float)py/h);count++;begin=-1;}
            }
        }
        sides=out.toArray();segments=count;preparationNs=System.nanoTime()-start;
    }
    private static void face(FloatArray out,float[] q,ActorVolumeData v,float x,float y,float z,float u0,float v0,float u1,float v1){
        float[] p=new float[32];float[] us={u0,u1,u1,u0},vs={v0,v1,v1,v0};
        for(int i=0;i<4;i++){
            float sx=q[0]+us[i]*(q[6]-q[0])+vs[i]*(q[2]-q[0]);
            float sy=q[1]+us[i]*(q[7]-q[1])+vs[i]*(q[3]-q[1]);
            float height=z+v.offsetZ+(i>=2?v.height:0);int o=i*8;
            p[o]=LightingCoordinates.worldX(sx,sy,height);p[o+1]=LightingCoordinates.worldY(sx,sy,height);p[o+2]=height;
            p[o+3]=p[o+4]=.5f;p[o+7]=1;
        }
        double angle=Math.toRadians(v.orientation);float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
        float cx=x+v.offsetX,cy=y+v.offsetY;
        p=cut(p,c,s,v.width*.5f+c*cx+s*cy);p=cut(p,-c,-s,v.width*.5f-c*cx-s*cy);
        p=cut(p,-s,c,v.depth*.5f-s*cx+c*cy);p=cut(p,s,-c,v.depth*.5f+s*cx-c*cy);
        for(int i=1;i<p.length/8-1;i++){out.addAll(p,0,8);out.addAll(p,i*8,8);out.addAll(p,(i+1)*8,8);}
    }
    private static float[] cut(float[] p,float ax,float ay,float limit){
        FloatArray out=new FloatArray();int count=p.length/8;
        for(int i=0;i<count;i++){
            int a=((i+count-1)%count)*8,b=i*8;
            float da=limit-ax*p[a]-ay*p[a+1],db=limit-ax*p[b]-ay*p[b+1];
            boolean ia=da>=0,ib=db>=0;
            if(ia!=ib){float t=da/(da-db);for(int k=0;k<8;k++)out.add(p[a+k]+t*(p[b+k]-p[a+k]));}
            if(ib)out.addAll(p,b,8);
        }return out.toArray();
    }
}
