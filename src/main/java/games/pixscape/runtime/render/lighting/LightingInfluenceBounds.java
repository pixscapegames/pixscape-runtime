package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.ShaderOrigin;
import games.pixscape.runtime.service.ShaderRegistry;

/** Allocation-free conservative physical-sphere bounds, including actual receiver display offsets.
 * One linear vertex scan per frame, then constant work per light. No caster filtering is changed.
 */
public final class LightingInfluenceBounds {
    private double minX,minY,maxX,maxY;
    private double xx,xy,yx,yy,tx,ty,rx,ry;
    private boolean uncertain,empty;
    public int x,y,width,height;

    public void prepare(FrameRenderQueue q,OrthographicCamera camera) {
        float[] m=camera.combined.val;
        uncertain=m[Matrix4.M30]!=0 || m[Matrix4.M31]!=0 || m[Matrix4.M32]!=0 || m[Matrix4.M33]!=1;
        xx=m[Matrix4.M00];xy=m[Matrix4.M01];yx=m[Matrix4.M10];yy=m[Matrix4.M11];tx=m[Matrix4.M03];ty=m[Matrix4.M13];
        rx=Math.sqrt(2*xx*xx+1.5*xy*xy);ry=Math.sqrt(2*yx*yx+1.5*yy*yy);
        minX=minY=Double.POSITIVE_INFINITY;maxX=maxY=Double.NEGATIVE_INFINITY;empty=true;
        for(int i=0;i<q.size && !uncertain;i++) {
            if(q.light[i]!=0 || q.textureHandle[i]==0 || q.blend[i]==3 || q.blend[i]==4)continue;
            // Preserve reference handling of nonstandard materials and repeat coverage.
            if(q.repeatFlags[i]!=0 || ShaderRegistry.getOrigin(q.shader[i])!=ShaderOrigin.CORE){uncertain=true;break;}
            LightingSurface s=q.sourceDomain[i]==FrameRenderQueue.SOURCE_TILED?q.lighting.tiles.get(q.sourceSlot[i]):q.lighting.entities.get(q.sourceEntity[i]);
            if(s==null || !s.receiveLight)continue;
            float[] v=s.alphaSheet!=null?s.alphaSheet.calibrated:s.tileShadowReceiver?s.tileShadow.calibrated:s.receiverVertices!=null?s.receiverVertices:s.vertices;
            double sheetX=0,sheetY=0;
            if(s.alphaSheet!=null){
                sheetX=Math.abs(xx)*s.alphaSheet.maximumScreenX+Math.abs(xy)*s.alphaSheet.maximumScreenY;
                sheetY=Math.abs(yx)*s.alphaSheet.maximumScreenX+Math.abs(yy)*s.alphaSheet.maximumScreenY;
            }else if(s.tileShadowReceiver){
                TileShadowGeometry g=s.tileShadow;
                double sx=(g.normalX-g.normalY)*g.halfThickness;
                double sy=((g.normalX+g.normalY)*.5+g.normalZ)*g.halfThickness;
                sheetX=Math.abs(xx*sx+xy*sy);sheetY=Math.abs(yx*sx+yy*sy);
            }
            if(v==null || v.length%8!=0){uncertain=true;break;}
            for(int j=0;j<v.length;j+=8) {
                double sx,sy;
                if(s.imageQuad) {
                    int c=SpriteSurfaceSupport.corner(j/8);
                    sx=c==0?q.x1[i]:c==1?q.x2[i]:c==2?q.x3[i]:q.x4[i];
                    sy=c==0?q.y1[i]:c==1?q.y2[i]:c==2?q.y3[i]:q.y4[i];
                } else {
                    // Match LightingBatch's actual float interpolation, including completion vertices.
                    sx=q.x1[i]+v[j+3]*(q.x4[i]-q.x1[i])+v[j+4]*(q.x2[i]-q.x1[i]);
                    sy=q.y1[i]+v[j+3]*(q.y4[i]-q.y1[i])+v[j+4]*(q.y2[i]-q.y1[i]);
                }
                double px=(double)v[j]-v[j+1],py=((double)v[j]+v[j+1])*.5+v[j+2];
                double dx=xx*(sx-px)+xy*(sy-py),dy=yx*(sx-px)+yy*(sy-py);
                if(!finite(dx) || !finite(dy) || Math.abs(px)>1e7 || Math.abs(py)>1e7){uncertain=true;break;}
                minX=Math.min(minX,dx-sheetX);maxX=Math.max(maxX,dx+sheetX);minY=Math.min(minY,dy-sheetY);maxY=Math.max(maxY,dy+sheetY);empty=false;
            }
        }
    }
    private static boolean finite(double n){return n==n && n!=Double.POSITIVE_INFINITY && n!=Double.NEGATIVE_INFINITY;}
    public boolean compute(LightingFrame.Light l,int w,int h) {
        x=y=0;width=w;height=h;
        if(uncertain || !finite(l.x) || !finite(l.y) || !finite(l.z) || !finite(l.radius) || l.radius<=0)return true;
        if(empty){width=height=0;return false;}
        double sx=(double)l.x-l.y,sy=((double)l.x+l.y)*.5+l.z;
        double cx=xx*sx+xy*sy+tx,cy=yx*sx+yy*sy+ty;
        // Outward padding covers raster edges and float arithmetic in both vertex pipelines.
        double ex=rx*l.radius,ey=ry*l.radius;
        double padX=2+1e-5*(1+Math.abs(cx)+ex+Math.abs(minX)+Math.abs(maxX))*w;
        double padY=2+1e-5*(1+Math.abs(cy)+ey+Math.abs(minY)+Math.abs(maxY))*h;
        double left=(cx-ex+minX+1)*w*.5-padX,right=(cx+ex+maxX+1)*w*.5+padX;
        double bottom=(cy-ey+minY+1)*h*.5-padY,top=(cy+ey+maxY+1)*h*.5+padY;
        if(!finite(left)||!finite(right)||!finite(bottom)||!finite(top))return true;
        x=(int)Math.max(0,Math.min(w,Math.floor(left)));y=(int)Math.max(0,Math.min(h,Math.floor(bottom)));
        width=(int)Math.max(0,Math.min(w,Math.ceil(right)))-x;height=(int)Math.max(0,Math.min(h,Math.ceil(top)))-y;
        return width>0 && height>0;
    }
}
