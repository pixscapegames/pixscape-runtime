package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

/** Cold map preparation. All linked memberships are indexed; no owner chosen per tile. */
public final class MapLightingGeometry {
    public final Array<LightingSurface> casters = new Array<LightingSurface>();
    private final LongMap<Array<SpatialBlockData>> memberships = new LongMap<Array<SpatialBlockData>>();
    private static final class Receiver { float[] quad; byte flags; int assetId; LightingSurface surface; }
    private final LongMap<Receiver> receivers = new LongMap<Receiver>();
    private int revision = Integer.MIN_VALUE;
    private TiledMapLayerData previous;
    private float plane, ox, oy, altitude;
    private int tw, th;
    private final IntMap<LocalSurfaceDescription> descriptions=new IntMap<LocalSurfaceDescription>();
    private Array<TileSurfaceDescription> authoredDescriptions;
    public void invalidate(){revision=Integer.MIN_VALUE;}
    public void descriptions(Array<TileSurfaceDescription> values){
        if(authoredDescriptions!=values){authoredDescriptions=values;invalidate();}
    }

    public boolean ensure(TiledMapLayerData map, SpatialBlocksComponent blocks, float planeZ) {
        int r = blocks == null ? 0 : blocks.revision;
        if (previous == map && revision == r && plane == planeZ && ox == map.originX
                && oy == map.originY && altitude == map.defaultTileAltitude
                && tw == map.tileWidth && th == map.tileHeight) return false;
        if (blocks != null && blocks.hasBlocks() && map.projection != TiledProjection.ISO)
            throw new IllegalArgumentException("Lighting volume reconstruction requires an ISO map");
        memberships.clear(); casters.clear(); receivers.clear();
        descriptions.clear();
        if(authoredDescriptions!=null)for(int i=0;i<authoredDescriptions.size;i++){
            TileSurfaceDescription d=authoredDescriptions.get(i);
            if(d.assetId<=0 || d.geometry==null || descriptions.containsKey(d.assetId))
                throw new IllegalArgumentException("Tile surface descriptions require unique positive asset IDs");
            d.geometry.validate();descriptions.put(d.assetId,d.geometry);
        }
        if (blocks != null) for (int i = 0; i < blocks.blocks.size; i++) {
            SpatialBlockData b = blocks.blocks.get(i);
            for (int j = 0; j < b.linkedTileRefs.size; j++) {
                SpatialBlockData.LinkedTileRef ref = b.linkedTileRefs.get(j);
                long key = key(ref.gx, ref.gy);
                Array<SpatialBlockData> owners = memberships.get(key);
                if (owners == null) { owners = new Array<SpatialBlockData>(); memberships.put(key, owners); }
                if (!owners.contains(b, true)) owners.add(b);
            }
            if (b.shadowCaster || b.lightOccluder) {
                Array<float[]> faces = faces(map, planeZ, b, false);
                for (int j = 0; j < faces.size; j++) {
                    LightingSurface s = new LightingSurface(triangulate(faces.get(j), null));
                    s.shadowCaster = true; s.receiveLight = false; casters.add(s);
                }
            }
        }
        previous = map; revision = r; plane = planeZ; ox = map.originX; oy = map.originY;
        altitude = map.defaultTileAltitude; tw = map.tileWidth; th = map.tileHeight;
        return true;
    }

    /** Builds a disjoint UV partition: alpha is applied exactly once at each image pixel. */
    public LightingSurface receiver(TiledMapLayerData map, float planeZ, int gx, int gy,
                                     int assetId, byte flags, float[] quad) {
        long cell = key(gx, gy);
        Receiver cached = receivers.get(cell);
        boolean same = cached != null && cached.flags == flags && cached.assetId==assetId;
        if (same) for (int i=0;i<8;i++) if(cached.quad[i]!=quad[i]) {same=false;break;}
        if (same) return cached.surface;
        LocalSurfaceDescription description=descriptions.get(assetId);
        if(description!=null){
            if(flags!=0)throw new IllegalArgumentException("Local volume description needs explicit transformed variant for tile flags");
            float[] origin=new float[3];LightingCoordinates.mapPoint(map,planeZ,gx,gy,map.defaultTileAltitude,origin,0);
            for(int i=0;i<description.xyz.length/3;i++){
                float u=description.uv[i*2],v=description.uv[i*2+1];
                float sx=quad[0]+u*(quad[6]-quad[0])+v*(quad[2]-quad[0]);
                float sy=quad[1]+u*(quad[7]-quad[1])+v*(quad[3]-quad[1]);
                float x=origin[0]+description.xyz[i*3],y=origin[1]+description.xyz[i*3+1],z=origin[2]+description.xyz[i*3+2];
                if(Math.abs(LightingCoordinates.screenX(x,y)-sx)>.02f || Math.abs(LightingCoordinates.screenY(x,y,z)-sy)>.02f)
                    throw new IllegalArgumentException("Local tile XYZ/UV registration does not match its native image");
            }
            LightingSurface surface=LocalSurfaceCompiler.compile(description,origin[0],origin[1],origin[2]);
            cached=new Receiver();cached.quad=LocalSurfaceDescription.copy(quad);cached.flags=flags;cached.assetId=assetId;cached.surface=surface;receivers.put(cell,cached);
            return surface;
        }
        Array<SpatialBlockData> owners = memberships.get(key(gx, gy));
        if (owners != null && flags != 0)
            throw new IllegalArgumentException("Automatic volume receiver does not support transformed tiles; provide local geometry");
        Array<float[]> polygons = new Array<float[]>();
        if (owners == null) {
            float[] p = new float[32];
            float[] u = {0,0,1,1}, v = {0,1,1,0};
            for (int i = 0; i < 4; i++) vertex(p, i * 8,
                    LightingCoordinates.worldX(quad[i * 2], quad[i * 2 + 1], planeZ),
                    LightingCoordinates.worldY(quad[i * 2], quad[i * 2 + 1], planeZ), planeZ,
                    u[i], v[i], 0,0,1);
            polygons.add(p);
        } else for (int i = 0; i < owners.size; i++) {
            SpatialBlockData b = owners.get(i);
            Array<float[]> fs = faces(map, planeZ, b, true);
            for (int f = 0; f < fs.size; f++) {
                float[] p = fs.get(f);
                for (int n = 0; n < p.length; n += 8) {
                    float sx = LightingCoordinates.screenX(p[n], p[n+1]);
                    float sy = LightingCoordinates.screenY(p[n], p[n+1], p[n+2]);
                    imageUV(quad, sx, sy, p, n+3);
                }
                p = clip(p, 3, 0, true); p = clip(p, 3, 1, false);
                p = clip(p, 4, 0, true); p = clip(p, 4, 1, false);
                if (p.length >= 24 && uvArea(p)>.000001f) {
                    // Later local faces own overlaps; native image order is never changed.
                    Array<float[]> next = new Array<float[]>();
                    for (int k = 0; k < polygons.size; k++) subtract(polygons.get(k), p, next);
                    next.add(p); polygons = next;
                }
            }
        }
        if (owners != null) {
            float[] full = new float[32];
            vertex(full,0,0,0,0,0,0,0,0,0);vertex(full,8,0,0,0,0,1,0,0,0);
            vertex(full,16,0,0,0,1,1,0,0,0);vertex(full,24,0,0,0,1,0,0,0,0);
            Array<float[]> missing = new Array<float[]>(); missing.add(full);
            for(int i=0;i<polygons.size;i++) {
                Array<float[]> next=new Array<float[]>();
                for(int j=0;j<missing.size;j++)subtract(missing.get(j),polygons.get(i),next);
                missing=next;
            }
            polygons.addAll(missing); // Zero normal marks an explicitly undescribed image region.
        }
        int size = 0;
        for (int i = 0; i < polygons.size; i++) size += (polygons.get(i).length / 8 - 2) * 24;
        float[] data = new float[size]; int offset = 0;
        for (int i = 0; i < polygons.size; i++) {
            float[] t = triangulate(polygons.get(i), null);
            System.arraycopy(t, 0, data, offset, t.length); offset += t.length;
        }
        LightingSurface surface = new LightingSurface(data); surface.approximate = true;
        cached = new Receiver(); cached.quad = LocalSurfaceDescription.copy(quad); cached.flags = flags; cached.assetId=assetId; cached.surface = surface;
        receivers.put(cell, cached);
        return surface;
    }

    private static long key(int x, int y) { return ((long)x << 32) ^ (y & 0xffffffffL); }
    /** Completes a local image with unlit coverage; overlapping UV triangles are ambiguous. */
    static float[] completeLocal(float[] vertices) {
        Array<float[]> polygons=new Array<float[]>();float area=0;
        for(int i=0;i<vertices.length;i+=24){
            float[] triangle=new float[24];System.arraycopy(vertices,i,triangle,0,24);
            float a=uvArea(triangle);if(a<.000001f)throw new IllegalArgumentException("Local surface has a degenerate UV triangle");
            area+=a;polygons.add(triangle);
        }
        float[] full=new float[32];full[12]=1;full[19]=full[20]=1;full[27]=1;
        Array<float[]> missing=new Array<float[]>();missing.add(full);
        for(int i=0;i<polygons.size;i++){
            Array<float[]> next=new Array<float[]>();
            for(int j=0;j<missing.size;j++)subtract(missing.get(j),polygons.get(i),next);
            missing=next;
        }
        int length=vertices.length;
        for(int i=0;i<missing.size;i++){area+=uvArea(missing.get(i));length+=(missing.get(i).length/8-2)*24;}
        if(Math.abs(area-1)> .0001f)throw new IllegalArgumentException("Local surface UV triangles overlap");
        float[] complete=new float[length];System.arraycopy(vertices,0,complete,0,vertices.length);int offset=vertices.length;
        for(int i=0;i<missing.size;i++){float[] part=triangulate(missing.get(i),null);System.arraycopy(part,0,complete,offset,part.length);offset+=part.length;}
        return complete;
    }
    private static float uvArea(float[] polygon){
        float area=0;int n=polygon.length/8;
        for(int i=0;i<n;i++){int j=(i+1)%n;area+=polygon[i*8+3]*polygon[j*8+4]-polygon[j*8+3]*polygon[i*8+4];}
        return Math.abs(area)*.5f;
    }
    private static Array<float[]> faces(TiledMapLayerData m, float p, SpatialBlockData b, boolean visible) {
        float x = b.x, y = b.y, a = x+b.width, c = y+b.depth, z = b.altitude, t = z+b.height;
        float[][] cells = {{x,y,z,a,y,z,a,y,t,x,y,t},{x,c,z,x,y,z,x,y,t,x,c,t},
                {x,y,t,a,y,t,a,c,t,x,c,t},{a,c,z,x,c,z,x,c,t,a,c,t},{a,y,z,a,c,z,a,c,t,a,y,t},
                {x,c,z,a,c,z,a,y,z,x,y,z}};
        Array<float[]> out = new Array<float[]>();
        for (int i = 0; i < (visible ? 3 : 6); i++) {
            float[] q = new float[32], point = new float[3];
            for (int n = 0; n < 4; n++) {
                LightingCoordinates.mapPoint(m,p,cells[i][n*3],cells[i][n*3+1],cells[i][n*3+2],point,0);
                vertex(q,n*8,point[0],point[1],point[2],0,0,0,0,0);
            }
            float ax=q[8]-q[0], ay=q[9]-q[1], az=q[10]-q[2];
            float bx=q[16]-q[0], by=q[17]-q[1], bz=q[18]-q[2];
            float nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
            float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if (length == 0) continue;
            for(int n=0;n<4;n++){q[n*8+5]=nx/length;q[n*8+6]=ny/length;q[n*8+7]=nz/length;}
            out.add(q);
        }
        return out;
    }
    private static void imageUV(float[] q,float x,float y,float[] out,int at) {
        float ax=q[6]-q[0],ay=q[7]-q[1],bx=q[2]-q[0],by=q[3]-q[1];
        float d=ax*by-ay*bx;
        if(Math.abs(d)<.000001f) throw new IllegalArgumentException("Degenerate receiver image quad");
        x-=q[0];y-=q[1];out[at]=(x*by-y*bx)/d;out[at+1]=(ax*y-ay*x)/d;
    }
    private static void vertex(float[] p,int o,float x,float y,float z,float u,float v,float nx,float ny,float nz){
        p[o]=x;p[o+1]=y;p[o+2]=z;p[o+3]=u;p[o+4]=v;p[o+5]=nx;p[o+6]=ny;p[o+7]=nz;
    }
    private static float[] triangulate(float[] p,float[] unused) {
        int n=p.length/8;float[] out=new float[Math.max(0,n-2)*24];int at=0;
        for(int i=1;i<n-1;i++)for(int k=0;k<3;k++){int v=k==0?0:k==1?i:i+1;System.arraycopy(p,v*8,out,at,8);at+=8;}
        return out;
    }
    private static float[] clip(float[] p,int axis,float edge,boolean greater){
        return cut(p,axis==3?1:0,axis==4?1:0,-edge,greater);
    }
    private static float[] cut(float[] p,float a,float b,float c,boolean positive){
        int n=p.length/8;if(n<3)return new float[0];float[] temp=new float[(n+2)*8];int at=0;
        for(int i=0;i<n;i++){
            int j=(i+n-1)%n;float d0=a*p[j*8+3]+b*p[j*8+4]+c,d1=a*p[i*8+3]+b*p[i*8+4]+c;
            if(!positive){d0=-d0;d1=-d1;}boolean in0=d0>=-.000001f,in1=d1>=-.000001f;
            if(in0!=in1){float t=d0/(d0-d1);for(int k=0;k<8;k++)temp[at++]=p[j*8+k]+t*(p[i*8+k]-p[j*8+k]);}
            if(in1){System.arraycopy(p,i*8,temp,at,8);at+=8;}
        }
        float[] out=new float[at];System.arraycopy(temp,0,out,0,at);return out;
    }
    private static void subtract(float[] subject,float[] mask,Array<float[]> out){
        if(subject.length<24 || uvArea(subject)<.000001f)return;
        if(mask.length<24 || uvArea(mask)<.000001f){out.add(subject);return;}
        float area=0;int n=mask.length/8;
        for(int i=0;i<n;i++){int j=(i+1)%n;area+=mask[i*8+3]*mask[j*8+4]-mask[j*8+3]*mask[i*8+4];}
        float sign=area>=0?1:-1;float[] remaining=subject;
        for(int i=0;i<n && remaining.length>=24 && uvArea(remaining)>.000001f;i++){
            int j=(i+1)%n;float dx=mask[j*8+3]-mask[i*8+3],dy=mask[j*8+4]-mask[i*8+4];
            float a=-dy*sign,b=dx*sign,c=(dy*mask[i*8+3]-dx*mask[i*8+4])*sign;
            if(Math.abs(a)+Math.abs(b)<.000001f)continue;
            float[] outside=cut(remaining,a,b,c,false);if(outside.length>=24 && uvArea(outside)>.000001f)out.add(outside);
            remaining=cut(remaining,a,b,c,true);
        }
    }
}
