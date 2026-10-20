package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

/** Cold, conservative support choice from current cell memberships; never examines alpha or asset identity.
 * Each associated volume keeps its own cell-local replacement box. Incompatible contacts retain opaque casters.
 */
public final class TileShadowSupport {
    public static final int BILLBOARD=0,X_NORMAL=1,Y_NORMAL=2,HORIZONTAL=3;
    public final Array<float[]> portions=new Array<float[]>();
    public TileShadowGeometry geometry;
    public String rejection;
    public int orientation;
    public float width,thickness,height;
    public float contactX,contactY,contactZ,normalX,normalY,normalZ;
    public TileShadowSupport(TiledMapLayerData map,int gx,int gy,float[] quad,Array<SpatialBlockData> owners,LightingSurface receiver){
        this(map,gx,gy,quad,owners,receiver,true);
    }
    /** Product alpha compilation needs support parameters, not the legacy tent carrier's buffers. */
    TileShadowSupport(TiledMapLayerData map,int gx,int gy,float[] quad,Array<SpatialBlockData> owners,LightingSurface receiver,boolean legacyCarrier){
        if(map.projection!=TiledProjection.ISO||map.tileWidth!=2*map.tileHeight){rejection="Support needs the calibrated 2:1 ISO metric";return;}
        if(owners==null||owners.size==0){rejection="No associated spatial volume";return;}
        float[] selected=null;int selectedKind=-1;float best=-1;
        boolean horizontal=horizontal(receiver);float horizontalZ=horizontal?receiver.vertices[2]:0;
        float[] point=new float[3];
        for(int i=0;i<owners.size;i++){
            SpatialBlockData b=owners.get(i);if(!b.shadowCaster&&!b.lightOccluder)continue;
            float x=Math.max(gx,b.x),y=Math.max(gy,b.y),xx=Math.min(gx+1,b.x+b.width),yy=Math.min(gy+1,b.y+b.depth);
            if(xx<=x||yy<=y){rejection="Linked volume has no cell-local footprint";return;}
            LightingCoordinates.mapPoint(map,x,y,b.altitude,point,0);float px=point[0],py=point[1];
            LightingCoordinates.mapPoint(map,xx,yy,b.altitude+b.height,point,0);
            float[] box={px,py,b.altitude,point[0],point[1],point[2]};portions.add(box);
            if(!legacyCarrier){
                // Cell clipping owns replacement coverage, never the calibration reference.
                LightingCoordinates.mapPoint(map,b.x,b.y,b.altitude,point,0);px=point[0];py=point[1];
                LightingCoordinates.mapPoint(map,b.x+b.width,b.y+b.depth,b.altitude+b.height,point,0);
                box=new float[]{px,py,b.altitude,point[0],point[1],point[2]};
            }
            int kind=horizontal?HORIZONTAL:legacyCarrier
                    ?b.width>1&&b.width>b.depth?Y_NORMAL:b.depth>1&&b.depth>b.width?X_NORMAL:BILLBOARD
                    :b.width>=2*b.depth?Y_NORMAL:b.depth>=2*b.width?X_NORMAL:BILLBOARD;
            if(selected!=null){
                if(kind!=selectedKind||Math.abs(box[2]-selected[2])>.01f||Math.abs(box[5]-selected[5])>.01f){rejection="Associated volumes define incompatible orientations or altitudes";return;}
                float plane=kind==X_NORMAL?(box[0]+box[3])*.5f:kind==Y_NORMAL?(box[1]+box[4])*.5f:(box[0]+box[1]+box[3]+box[4])*.25f;
                float other=kind==X_NORMAL?(selected[0]+selected[3])*.5f:kind==Y_NORMAL?(selected[1]+selected[4])*.5f:(selected[0]+selected[1]+selected[3]+selected[4])*.25f;
                if(!horizontal&&Math.abs(plane-other)>.01f){rejection="Associated volumes do not share a contact plane";return;}
            }
            float area=(box[3]-box[0])*(box[4]-box[1]);
            if(selected==null||area>best||area==best&&less(box,selected)){selected=box;selectedKind=kind;best=area;}
        }
        if(selected==null){rejection="No enabled caster volume";return;}
        orientation=selectedKind;float wx=selected[3]-selected[0],wy=selected[4]-selected[1];height=selected[5]-selected[2];
        float nx=orientation==X_NORMAL?1:orientation==Y_NORMAL||horizontal?0:(float)Math.sqrt(.5),ny=orientation==Y_NORMAL?1:orientation==X_NORMAL||horizontal?0:(float)Math.sqrt(.5),nz=horizontal?1:0;
        thickness=horizontal?height:Math.abs(nx)*wx+Math.abs(ny)*wy;
        width=horizontal?(float)Math.hypot(wx,wy):Math.abs(ny)*wx+Math.abs(nx)*wy;
        contactX=(selected[0]+selected[3])*.5f;contactY=(selected[1]+selected[4])*.5f;contactZ=horizontal?horizontalZ:selected[2];
        normalX=nx;normalY=ny;normalZ=nz;
        try{if(legacyCarrier)geometry=new TileShadowGeometry(quad,contactX,contactY,contactZ,nx,ny,nz,thickness*.5f,horizontal?0:height);}
        catch(IllegalArgumentException e){rejection=e.getMessage();}
    }
    private static boolean less(float[] a,float[] b){for(int i=0;i<6;i++)if(a[i]!=b[i])return a[i]<b[i];return false;}
    private static boolean horizontal(LightingSurface receiver){
        if(receiver==null||receiver.vertices.length==0)return false;float[] v=receiver.vertices;float z=v[2],area=0;
        for(int i=0;i<v.length;i+=24){
            for(int j=i;j<i+24;j+=8)if(v[j+7]<.999f||Math.abs(v[j+2]-z)>.01f)return false;
            area+=Math.abs((v[i+11]-v[i+3])*(v[i+20]-v[i+4])-(v[i+19]-v[i+3])*(v[i+12]-v[i+4]))*.5f;
        }return Math.abs(area-1)<.0001f;
    }
}
