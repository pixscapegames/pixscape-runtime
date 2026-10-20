package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongMap;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Cold miter of an existing V2 partition. UV, triangles and undescribed provenance stay unchanged.
 * Only physically coincident partition vertices share displacements; a depth discontinuity is not a joint.
 */
final class TileAlphaJunction {
    private static int axis(float[] v,int i){return Math.abs(v[i+5])>.999f?1:Math.abs(v[i+6])>.999f?2:0;}
    static TileAlphaCompiler.Cell prepare(TiledMapLayerData map,int gx,int gy,float[] quad,Array<SpatialBlockData> owners,LightingSurface legacy,TileShadowMode mode,TileAlphaContactIndex.MapContacts contacts){
        if(owners==null||owners.size!=2||mode==TileShadowMode.V3_PROGRESSIVE)return null;
        SpatialBlockData x=null,y=null;
        for(int i=0;i<2;i++){SpatialBlockData b=owners.get(i);if(!b.shadowCaster&&!b.lightOccluder
                ||Math.min(gx+1,b.x+b.width)<=Math.max(gx,b.x)||Math.min(gy+1,b.y+b.depth)<=Math.max(gy,b.y))return null;
            if(b.depth>=2*b.width)x=b;else if(b.width>=2*b.depth)y=b;}
        if(x==null||y==null||x.altitude!=y.altitude||x.height!=y.height||x.height<=0
                ||Math.min(x.x+x.width,y.x+y.width)<=Math.max(x.x,y.x)||Math.min(x.y+x.depth,y.y+y.depth)<=Math.max(x.y,y.y))return null;
        float[] box=new float[6];LightingCoordinates.mapPoint(map,x.x,x.y,0,box,0);LightingCoordinates.mapPoint(map,x.x+x.width,x.y+x.depth,0,box,3);
        float cx=(box[0]+box[3])*.5f,ex=(box[3]-box[0])*.5f,x0=box[0],x1=box[3],yMin=box[1],yMax=box[4];
        LightingCoordinates.mapPoint(map,y.x,y.y,0,box,0);LightingCoordinates.mapPoint(map,y.x+y.width,y.y+y.depth,0,box,3);
        float cy=(box[1]+box[4])*.5f,ey=(box[4]-box[1])*.5f,y0=box[1],y1=box[4],xMin=box[0],xMax=box[3];
        float px=Float.NaN,py=Float.NaN;float[] raw=legacy.vertices;
        for(int i=0;i<raw.length;i+=24){int a=axis(raw,i);if(a==1){if(px==px&&Math.abs(px-raw[i])>.001f)return null;px=raw[i];}else if(a==2){if(py==py&&Math.abs(py-raw[i+1])>.001f)return null;py=raw[i+1];}}
        if(px!=px||py!=py||Math.min(Math.abs(px-x0),Math.abs(px-x1))>.001f||Math.min(Math.abs(py-y0),Math.abs(py-y1))>.001f
                ||px<xMin||px>xMax||py<yMin||py>yMax)return null;
        TileAlphaContactIndex.Band bx=contacts==null?null:contacts.band(x.id),by=contacts==null?null:contacts.band(y.id);
        float zx=bx==null?x.altitude:bx.z,zy=by==null?y.altitude:by.z,hx=bx==null?x.height:bx.height,hy=by==null?y.height:by.height;
        if(zx!=zy)return null;if(bx!=null){cx=bx.x;ex=bx.halfThickness;}if(by!=null){cy=by.y;ey=by.halfThickness;}
        float kx=mode==TileShadowMode.V3_FIXED?ex/hx:0,ky=mode==TileShadowMode.V3_FIXED?ey/hy:0;
        float[] original=legacy.receiverVertices==null?raw:legacy.receiverVertices,data=LocalSurfaceDescription.copy(original),visible=LocalSurfaceDescription.copy(raw);
        LongMap<IntArray> vertices=new LongMap<IntArray>();
        for(int i=0;i<raw.length;i+=8){int u=Math.round(raw[i+3]*65536),v=Math.round(raw[i+4]*65536);long key=((long)u<<32)^(v&0xffffffffL);IntArray list=vertices.get(key);if(list==null){list=new IntArray();vertices.put(key,list);}list.add(i);}
        boolean joint=false;
        for(int i=0;i<data.length;i+=8){
            int bits=axis(original,i),u=Math.round(raw[i+3]*65536),v=Math.round(raw[i+4]*65536);
            for(int dv=-1;dv<=1;dv++)for(int du=-1;du<=1;du++){
                IntArray list=vertices.get(((long)(u+du)<<32)^((v+dv)&0xffffffffL));if(list==null)continue;
                for(int n=0;n<list.size;n++){int j=list.get(n);if(Math.abs(raw[i+3]-raw[j+3])+Math.abs(raw[i+4]-raw[j+4])>.00002f
                        ||Math.abs(original[i]-original[j])+Math.abs(original[i+1]-original[j+1])+Math.abs(original[i+2]-original[j+2])>.001f)continue;
                    bits|=axis(raw,j);
                }
            }
            if(bits==3)joint=true;float z=original[i+2];
            if((bits&1)!=0)data[i]+=cx-px+kx*(z-zx);
            if((bits&2)!=0)data[i+1]+=cy-py+ky*(z-zy);
            float nx=original[i+5],ny=original[i+6],nz=original[i+7]-kx*nx-ky*ny,n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(n>0){data[i+5]=nx/n;data[i+6]=ny/n;data[i+7]=nz/n;}
            if(raw[i+5]!=0||raw[i+6]!=0||raw[i+7]!=0)System.arraycopy(data,i,visible,i,8);
        }
        if(!joint)return null;
        boolean[] admitted=new boolean[data.length/24];Array<TileAlphaSheet.Part> parts=new Array<TileAlphaSheet.Part>();int horizontal=0,vertical=0,fallback=0;
        for(int i=0;i<data.length;i+=24){
            if(raw[i+5]==0&&raw[i+6]==0&&raw[i+7]==0){fallback++;continue;}
            admitted[i/24]=true;if(Math.abs(raw[i+7])>.999f)horizontal++;else vertical++;
            parts.add(new TileAlphaSheet.Part(i,i+24,data[i],data[i+1],data[i+2],data[i+5],data[i+6],data[i+7],0,0));
        }
        TileAlphaSheet.Part[] ranges=new TileAlphaSheet.Part[parts.size];for(int i=0;i<ranges.length;i++)ranges[i]=parts.get(i);
        LightingSurface surface=new LightingSurface(visible);surface.approximate=true;surface.shadowCaster=true;
        surface.alphaSheet=new TileAlphaSheet(data,ranges,TileShadowMode.V3_PLANE,admitted,TileAlphaCompiler.receiverFaces(raw));
        return new TileAlphaCompiler.Cell(gx,gy,quad,owners,surface,false,"V2 UV partition; cold orthogonal miter; undescribed portions retain V2",horizontal,vertical,fallback,raw);
    }
}
