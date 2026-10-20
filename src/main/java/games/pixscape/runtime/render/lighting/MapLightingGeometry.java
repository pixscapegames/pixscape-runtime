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
    private final Array<LightingSurface> opaqueCasters = new Array<LightingSurface>();
    private final LongMap<Array<SpatialBlockData>> memberships = new LongMap<Array<SpatialBlockData>>();
    private static final class Receiver { float[] quad; byte flags; int assetId; LightingSurface surface; TileAlphaCompiler.Cell alpha; boolean active=true,animationCertified,animationDeferred; IntMap<Receiver> frames; }
    private final LongMap<Receiver> receivers = new LongMap<Receiver>();
    private int revision = Integer.MIN_VALUE;
    private TiledMapLayerData previous;
    private SpatialBlocksComponent previousBlocks;
    private TiledProjection projection;
    private float ox, oy, altitude;
    private int tw, th;
    private final IntMap<LocalSurfaceDescription> descriptions=new IntMap<LocalSurfaceDescription>();
    private Array<TileSurfaceDescription> authoredDescriptions;
    private TileShadowMode mode=TileShadowMode.V2;
    private TileAlphaContactIndex.MapContacts contacts;
    public void contacts(TileAlphaContactIndex.MapContacts values){if(contacts!=values){contacts=values;invalidate();}}
    private boolean alphaDirty;
    private boolean animatedReceivers;
    public boolean hasAnimatedReceivers(){return animatedReceivers;}
    /** Complete positive/negative animation certification is owned by the same cell cache. */
    public boolean animationPrepared(int gx,int gy,int assetId,byte flags){
        Receiver r=receivers.get(key(gx,gy));return r!=null&&r.assetId==assetId&&r.flags==flags&&r.animationCertified;
    }
    /** Cold enumeration before READY. Variable placement keeps V2, with every frame mesh prepared once.
     * Stable placement shares one alpha carrier, independently of the source texture page/frame.
     */
    public void prepareAnimation(TiledMapLayerData map,int gx,int gy,int assetId,byte flags,int[] frameAssets,float[][] quads){
        if(frameAssets==null||quads==null||frameAssets.length==0||frameAssets.length!=quads.length)throw new IllegalArgumentException("Animation receiver frames are incomplete");
        boolean stable=true;
        for(int i=0;i<quads.length;i++){
            if(quads[i]==null||quads[i].length!=8||frameAssets[i]<=0)throw new IllegalArgumentException("Animation receiver frame needs an image quad and source asset");
            for(int k=0;k<8;k++)if(quads[0][k]!=quads[i][k])stable=false;
        }
        long cell=key(gx,gy);animatedReceivers=true;
        Receiver previous=receivers.get(cell);if(previous!=null&&previous.animationDeferred)receivers.remove(cell);
        if(stable){receiverUnprepared(map,gx,gy,assetId,flags,quads[0],false);Receiver r=receivers.get(cell);r.assetId=assetId;r.animationCertified=true;return;}
        IntMap<Receiver> frames=new IntMap<Receiver>();Receiver first=null;int fallback=0;
        for(int i=0;i<quads.length;i++){
            Receiver cached=frames.get(frameAssets[i]);
            if(cached!=null){for(int k=0;k<8;k++)if(cached.quad[k]!=quads[i][k])throw new IllegalArgumentException("Repeated animation source has conflicting image placement");continue;}
            receivers.remove(cell);receiverUnprepared(map,gx,gy,assetId,flags,quads[i],true);cached=receivers.get(cell);
            frames.put(frameAssets[i],cached);fallback+=cached.surface.vertices.length/24;if(first==null)first=cached;
        }
        first.frames=frames;first.animationCertified=true;
        first.alpha=new TileAlphaCompiler.Cell(gx,gy,first.quad,memberships.get(cell),first.surface,false,
                "Animation placement changes between frames; prepared V2 receivers retain opaque casters",0,0,fallback);
        receivers.put(cell,first);alphaDirty=true;
    }
    /** Authoring may temporarily have unpacked frames. Keep V2 and a negative certification until
     * material publication invalidates the map; never retry the complete frame enumeration each frame. */
    public void deferAnimation(TiledMapLayerData map,int gx,int gy,int assetId,int frameAssetId,byte flags,float[] quad){
        long cell=key(gx,gy);receivers.remove(cell);receiverUnprepared(map,gx,gy,assetId,flags,quad,true);
        Receiver r=receivers.get(cell);r.animationCertified=r.animationDeferred=true;r.frames=new IntMap<Receiver>();r.frames.put(frameAssetId,r);
        r.alpha=TileAlphaCompiler.refused(gx,gy,r.quad,memberships.get(cell),r.surface,"Animation material is incomplete; V2 until material publication");animatedReceivers=true;
    }
    private final Coverage coverage=new Coverage();
    /** Cold diagnostic publication; counts triangles/patches, not source pixels or alpha contours. */
    public static final class Coverage {
        public int tiles,horizontal,vertical,fallback,authored,replacedPatches;
        public final com.badlogic.gdx.utils.ObjectIntMap<String> reasons=new com.badlogic.gdx.utils.ObjectIntMap<String>();
        private void clear(){tiles=horizontal=vertical=fallback=authored=replacedPatches=0;reasons.clear();}
    }
    public Coverage coverage(){return coverage;}
    public TileShadowMode mode(){return mode;}
    public void mode(TileShadowMode value){
        if(value==null)throw new IllegalArgumentException("Tile shadow mode is required");
        if(mode!=value){mode=value;invalidate();}
    }
    public void forget(int gx,int gy){if(receivers.remove(key(gx,gy))!=null)alphaDirty=true;}
    /** Missing material suspends admission, retaining prepared geometry for a later rebind. */
    public void suspend(int gx,int gy){Receiver r=receivers.get(key(gx,gy));if(r!=null&&r.active){r.active=false;alphaDirty=true;}}
    private void activate(Receiver r){if(!r.active){r.active=true;alphaDirty=true;}}
    /** Called once after the cold chunk mutation/preparation boundary; never from drawing or camera culling. */
    public boolean finishAlphaSheets(TiledMapLayerData map){
        if(!alphaDirty)return false;
        alphaDirty=false;coverage.clear();casters.clear();
        IntMap<Array<TileAlphaCompiler.Cell>> masks=new IntMap<Array<TileAlphaCompiler.Cell>>();
        for(LongMap.Entry<Receiver> entry:receivers){
            Receiver r=entry.value;coverage.tiles++;TileAlphaCompiler.Cell c=r.alpha;
            if(!r.active){coverage.fallback+=r.surface.vertices.length/24;coverage.reasons.getAndIncrement("Image material unavailable; opaque portions retained",0,1);continue;}
            if(c==null){if(descriptions.containsKey(r.assetId))coverage.authored++;continue;}
            coverage.horizontal+=c.horizontal;coverage.vertical+=c.vertical;coverage.fallback+=c.fallback;
            if(c.reason!=null)coverage.reasons.getAndIncrement(c.reason,0,1);
            if(c.surface.alphaSheet==null||c.owners==null)continue;
            for(int i=0;i<c.owners.size;i++){
                SpatialBlockData b=c.owners.get(i);if(!b.shadowCaster&&!b.lightOccluder)continue;
                if(Math.min(c.gx+1,b.x+b.width)<=Math.max(c.gx,b.x)||Math.min(c.gy+1,b.y+b.depth)<=Math.max(c.gy,b.y))continue;
                Array<TileAlphaCompiler.Cell> list=masks.get(b.id);
                if(list==null){list=new Array<TileAlphaCompiler.Cell>();masks.put(b.id,list);}list.add(c);
            }
        }
        for(int i=0;i<opaqueCasters.size;i++){
            LightingSurface face=opaqueCasters.get(i);Array<TileAlphaCompiler.Cell> cells=masks.get(face.sourceBlockId);
            if(cells==null)casters.add(face);else coverage.replacedPatches+=TileAlphaCompiler.replace(map,face,cells,casters);
        }
        return true;
    }
    public void invalidate(){revision=Integer.MIN_VALUE;}
    public void descriptions(Array<TileSurfaceDescription> values){
        if(authoredDescriptions!=values){authoredDescriptions=values;invalidate();}
    }

    /** Constant-time source stamp check; camera state is intentionally absent. */
    public boolean needsPreparation(TiledMapLayerData map,SpatialBlocksComponent blocks){
        return previous!=map||previousBlocks!=blocks||projection!=map.projection||revision!=(blocks==null?0:blocks.revision)
                ||ox!=map.originX||oy!=map.originY||altitude!=map.defaultTileAltitude||tw!=map.tileWidth||th!=map.tileHeight;
    }
    public boolean ensure(TiledMapLayerData map, SpatialBlocksComponent blocks) {
        int r = blocks == null ? 0 : blocks.revision;
        if (!needsPreparation(map,blocks)) return false;
        if (blocks != null && blocks.hasBlocks() && map.projection != TiledProjection.ISO)
            throw new IllegalArgumentException("Lighting volume reconstruction requires an ISO map");
        memberships.clear(); casters.clear(); opaqueCasters.clear(); receivers.clear();alphaDirty=true;animatedReceivers=false;
        descriptions.clear();
        if(authoredDescriptions!=null)for(int i=0;i<authoredDescriptions.size;i++){
            TileSurfaceDescription d=authoredDescriptions.get(i);
            if(d.assetId<=0 || d.geometry==null || descriptions.containsKey(d.assetId))
                throw new IllegalArgumentException("Tile surface descriptions require unique positive asset IDs");
            d.geometry.validate();descriptions.put(d.assetId,d.geometry);
        }
        Array<SpatialBlockData> casterBlocks=new Array<SpatialBlockData>();
        com.badlogic.gdx.utils.IntSet blockIds=new com.badlogic.gdx.utils.IntSet();
        if (blocks != null) for (int i = 0; i < blocks.blocks.size; i++) {
            SpatialBlockData b = blocks.blocks.get(i);
            if(!blockIds.add(b.id))throw new IllegalArgumentException("Lighting volumes need unique block IDs within a map");
            for (int j = 0; j < b.linkedTileRefs.size; j++) {
                SpatialBlockData.LinkedTileRef ref = b.linkedTileRefs.get(j);
                long key = key(ref.gx, ref.gy);
                Array<SpatialBlockData> owners = memberships.get(key);
                if (owners == null) { owners = new Array<SpatialBlockData>(); memberships.put(key, owners); }
                if (!owners.contains(b, true)) owners.add(b);
            }
            if (b.shadowCaster || b.lightOccluder) casterBlocks.add(b);
        }
        // Coplanar overlapping triangles can tie in the depth attachment while their
        // radial float outputs differ by an ULP. Keep submission deterministic too,
        // without changing any caster plane, vertex, flag or within-volume face order.
        casterBlocks.sort(new java.util.Comparator<SpatialBlockData>(){
            public int compare(SpatialBlockData a,SpatialBlockData b){
                int c=Float.compare(a.x,b.x);if(c!=0)return c;c=Float.compare(a.y,b.y);if(c!=0)return c;
                c=Float.compare(a.altitude,b.altitude);if(c!=0)return c;c=Float.compare(a.width,b.width);if(c!=0)return c;
                c=Float.compare(a.depth,b.depth);return c!=0?c:Float.compare(a.height,b.height);
            }
        });
        for(int i=0;i<casterBlocks.size;i++){
                Array<float[]> faces = faces(map, casterBlocks.get(i), false);
                for (int j = 0; j < faces.size; j++) {
                    LightingSurface s = new LightingSurface(triangulate(faces.get(j), null));
                    s.shadowCaster = true; s.receiveLight = false; s.sourceBlockId=casterBlocks.get(i).id; casters.add(s);
                }
        }
        opaqueCasters.addAll(casters);
        previous = map; previousBlocks = blocks; projection = map.projection; revision = r; ox = map.originX; oy = map.originY;
        altitude = map.defaultTileAltitude; tw = map.tileWidth; th = map.tileHeight;
        return true;
    }

    /** Builds a disjoint UV partition: alpha is applied exactly once at each image pixel. */
    public LightingSurface receiver(TiledMapLayerData map, int gx, int gy,
                                     int assetId, byte flags, float[] quad) {
        return receiver(map,gx,gy,assetId,assetId,flags,quad);
    }
    public LightingSurface receiver(TiledMapLayerData map,int gx,int gy,int assetId,int frameAssetId,byte flags,float[] quad){
        Receiver cached=receivers.get(key(gx,gy));
        if(cached!=null&&cached.frames!=null&&cached.assetId==assetId&&cached.flags==flags){
            Receiver frame=cached.frames.get(frameAssetId);
            if(frame==null){
                if(!cached.animationDeferred)throw new IllegalStateException("Animated tile left its prepared receiver frame set");
                long cell=key(gx,gy);receivers.remove(cell);receiverUnprepared(map,gx,gy,assetId,flags,quad,true);
                frame=receivers.get(cell);cached.frames.put(frameAssetId,frame);receivers.put(cell,cached);alphaDirty=true;
            }
            activate(cached);
            return frame.surface;
        }
        return receiverUnprepared(map,gx,gy,assetId,flags,quad,false);
    }
    private LightingSurface receiverUnprepared(TiledMapLayerData map,int gx,int gy,int assetId,byte flags,float[] quad,boolean retainV2){
        long cell = key(gx, gy);
        Receiver cached = receivers.get(cell);
        // A texture-only animation frame cannot invalidate an automatic physical partition.
        boolean same = cached != null && cached.frames==null && cached.flags == flags && (cached.assetId==assetId
                || !descriptions.containsKey(cached.assetId)&&!descriptions.containsKey(assetId));
        if (same) for (int i=0;i<8;i++) if(cached.quad[i]!=quad[i]) {same=false;break;}
        if (same) {activate(cached);return cached.surface;}
        LocalSurfaceDescription description=descriptions.get(assetId);
        if(description!=null){
            if(flags!=0)throw new IllegalArgumentException("Local volume description needs explicit transformed variant for tile flags");
            float[] origin=new float[3];LightingCoordinates.mapPoint(map,gx,gy,map.defaultTileAltitude,origin,0);
            for(int i=0;i<description.xyz.length/3;i++){
                float u=description.uv[i*2],v=description.uv[i*2+1];
                float sx=quad[0]+u*(quad[6]-quad[0])+v*(quad[2]-quad[0]);
                float sy=quad[1]+u*(quad[7]-quad[1])+v*(quad[3]-quad[1]);
                float x=origin[0]+description.xyz[i*3],y=origin[1]+description.xyz[i*3+1],z=origin[2]+description.xyz[i*3+2];
                if(Math.abs(LightingCoordinates.screenX(x,y)-sx)>.02f || Math.abs(LightingCoordinates.screenY(x,y,z)-sy)>.02f)
                    throw new IllegalArgumentException("Local tile XYZ/UV registration does not match its native image");
            }
            LightingSurface surface=LocalSurfaceCompiler.compile(description,origin[0],origin[1],origin[2]);
            cached=new Receiver();cached.quad=LocalSurfaceDescription.copy(quad);cached.flags=flags;cached.assetId=assetId;cached.surface=surface;receivers.put(cell,cached);alphaDirty=true;
            return surface;
        }
        Array<SpatialBlockData> owners = memberships.get(key(gx, gy));
        if (owners != null && flags != 0 && mode==TileShadowMode.V2&&!retainV2)
            throw new IllegalArgumentException("Automatic volume receiver does not support transformed tiles; provide local geometry");
        Array<float[]> polygons = new Array<float[]>();
        Array<ReceiverFace> candidates = owners != null && owners.size > 1 ? new Array<ReceiverFace>() : null;
        if (owners == null) {
            float[] p = new float[32];
            float[] u = {0,0,1,1}, v = {0,1,1,0};
            for (int i = 0; i < 4; i++) vertex(p, i * 8,
                    LightingCoordinates.worldX(quad[i * 2], quad[i * 2 + 1], map.defaultTileAltitude),
                    LightingCoordinates.worldY(quad[i * 2], quad[i * 2 + 1], map.defaultTileAltitude), map.defaultTileAltitude,
                    u[i], v[i], 0,0,1);
            polygons.add(p);
        } else for (int i = 0; i < owners.size; i++) {
            SpatialBlockData b = owners.get(i);
            Array<float[]> fs = faces(map, b, true);
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
                    if(candidates!=null)candidates.add(new ReceiverFace(p,quad));
                    else {
                        Array<float[]> next = new Array<float[]>();
                        for (int k = 0; k < polygons.size; k++) subtract(polygons.get(k), p, next);
                        next.add(p); polygons = next;
                    }
                }
            }
        }
        if(candidates!=null)polygons=visiblePartition(candidates);
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
        surface.receiverVertices=completeReceiverVertices(data,quad,map.defaultTileAltitude);
        cached = new Receiver(); cached.quad = LocalSurfaceDescription.copy(quad); cached.flags = flags; cached.assetId=assetId; cached.surface = surface;
        if(mode!=TileShadowMode.V2&&!retainV2){
            String rejection=null;
            if(mode!=TileShadowMode.V3_PLANE&&contacts!=null&&owners!=null)for(int i=0;i<owners.size;i++){
                String reason=contacts.rejection(owners.get(i).id,gx,gy);if(reason!=null){rejection=reason;break;}
            }
            cached.alpha=rejection==null?TileAlphaCompiler.prepare(map,gx,gy,cached.quad,owners,surface,mode,contacts)
                    :TileAlphaCompiler.refused(gx,gy,cached.quad,owners,surface,rejection);
            cached.surface=cached.alpha.surface;
        }
        receivers.put(cell, cached);alphaDirty=true;
        return cached.surface;
    }

    private static long key(int x, int y) { return ((long)x << 32) ^ (y & 0xffffffffL); }
    /** Cold tile-local partition. Larger Z is nearer along the display ray (-1,-1,+1).
     * Depth on a projected plane is affine in UV, so a crossing requires a line cut,
     * not a global face sort. Casters and authored memberships remain unchanged. */
    private static Array<float[]> visiblePartition(Array<ReceiverFace> faces){
        // Canonical geometry order also fixes triangulation and coverage extrapolation ties.
        faces.sort();
        Array<float[]> result=new Array<float[]>();
        for(int i=0;i<faces.size;i++){
            ReceiverFace face=faces.get(i);
            if(i>0&&face.compareTo(faces.get(i-1))==0)continue;
            Array<float[]> parts=new Array<float[]>();parts.add(face.polygon);
            for(int j=0;j<faces.size&&parts.size>0;j++){
                if(i==j)continue;
                ReceiverFace other=faces.get(j);
                if(face.maxU<=other.minU||other.maxU<=face.minU||face.maxV<=other.minV||other.maxV<=face.minV)continue;
                float[] front=other.inFrontOf(face,j<i);
                if(front==null)continue;
                Array<float[]> next=new Array<float[]>();
                for(int k=0;k<parts.size;k++)subtract(parts.get(k),front,next);
                parts=next;
            }
            result.addAll(parts);
        }
        return result;
    }
    private static final class ReceiverFace implements Comparable<ReceiverFace>{
        final float[] polygon;
        final double u,v,c;
        float minU=Float.POSITIVE_INFINITY,minV=Float.POSITIVE_INFINITY;
        float maxU=Float.NEGATIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        ReceiverFace(float[] p,float[] quad){
            polygon=p;
            double nx=p[5],ny=p[6],nz=p[7],den=nz-nx-ny;
            // Solve N.P=constant using X=sy-z+sx/2, Y=sy-z-sx/2.
            // Unlike a fit through clipped vertices, this keeps coplanar ties exact.
            double sx=(nx-ny)*.5,sy=nx+ny;
            u=(-sx*(quad[6]-quad[0])-sy*(quad[7]-quad[1]))/den;
            v=(-sx*(quad[2]-quad[0])-sy*(quad[3]-quad[1]))/den;
            c=(nx*p[0]+ny*p[1]+nz*p[2]-sx*quad[0]-sy*quad[1])/den;
            for(int k=0;k<p.length;k+=8){minU=Math.min(minU,p[k+3]);maxU=Math.max(maxU,p[k+3]);minV=Math.min(minV,p[k+4]);maxV=Math.max(maxV,p[k+4]);}
        }
        public int compareTo(ReceiverFace b){
            int d=Double.compare(u,b.u);if(d!=0)return d;
            d=Double.compare(v,b.v);if(d!=0)return d;
            d=Double.compare(c,b.c);if(d!=0)return d;
            // Equal depth planes: normals, then physical vertices; never IDs or insertion order.
            for(int k=5;k<8;k++){d=Float.compare(polygon[k],b.polygon[k]);if(d!=0)return d;}
            for(int k=0;k<Math.min(polygon.length,b.polygon.length);k++){d=Float.compare(polygon[k],b.polygon[k]);if(d!=0)return d;}
            return Integer.compare(polygon.length,b.polygon.length);
        }
        float[] inFrontOf(ReceiverFace b,boolean winsTie){
            double a=u-b.u,d=v-b.v,e=c-b.c;
            if(a==0&&d==0&&e==0)return winsTie?polygon:null;
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int k=0;k<polygon.length;k+=8){double z=a*polygon[k+3]+d*polygon[k+4]+e;min=Math.min(min,z);max=Math.max(max,z);}
            if(max<=0)return null;
            if(min>=0)return polygon;
            // Preserve XYZ, normals and native UV together at the exact depth crossing.
            int n=polygon.length/8,at=0;float[] temp=new float[(n+2)*8];
            for(int i=0;i<n;i++){
                int j=(i+n-1)%n;
                double z0=a*polygon[j*8+3]+d*polygon[j*8+4]+e,z1=a*polygon[i*8+3]+d*polygon[i*8+4]+e;
                boolean in0=z0>=0,in1=z1>=0;
                if(in0!=in1){double t=z0/(z0-z1);for(int k=0;k<8;k++)temp[at++]=(float)(polygon[j*8+k]+t*((double)polygon[i*8+k]-polygon[j*8+k]));}
                if(in1){System.arraycopy(polygon,i*8,temp,at,8);at+=8;}
            }
            float[] out=new float[at];System.arraycopy(temp,0,out,0,at);return out;
        }
    }
    /** Cold coverage-only approximation. Extrapolates the closest existing UV facet into each
     * previously undescribed triangle. It never changes a valid vertex or any caster geometry.
     * An image extending beyond its authored volume does not determine a unique physical depth;
     * the batch marks these extensions separately in the existing coverage diagnostic. */
    static float[] completeReceiverVertices(float[] source,float[] quad,float altitude){
        boolean missing=false;
        for(int i=0;i<source.length;i+=24)if(missingNormal(source,i)){missing=true;break;}
        if(!missing)return null;
        float[] result=LocalSurfaceDescription.copy(source);
        for(int i=0;i<source.length;i+=24){
            if(!missingNormal(source,i))continue;
            float u=(source[i+3]+source[i+11]+source[i+19])/3,v=(source[i+4]+source[i+12]+source[i+20])/3;
            int nearest=-1;float distance=Float.POSITIVE_INFINITY;
            for(int j=0;j<source.length;j+=24){
                if(missingNormal(source,j))continue;
                float ax=source[j+11]-source[j+3],ay=source[j+12]-source[j+4],bx=source[j+19]-source[j+3],by=source[j+20]-source[j+4];
                float determinant=ax*by-ay*bx;
                if(Math.abs(determinant)<.00000001f)continue;
                float px=u-source[j+3],py=v-source[j+4],a=(px*by-py*bx)/determinant,b=(ax*py-ay*px)/determinant;
                float d=0;
                if(a<0||b<0||a+b>1){
                    d=Math.min(edgeDistance(u,v,source,j,j+8),Math.min(edgeDistance(u,v,source,j+8,j+16),edgeDistance(u,v,source,j+16,j)));
                }
                if(d<distance){distance=d;nearest=j;}
            }
            for(int k=i;k<i+24;k+=8){
                if(nearest<0){
                    if(quad==null)throw new IllegalArgumentException("Surface coverage has no valid geometric facet");
                    float sx=quad[0]+source[k+3]*(quad[6]-quad[0])+source[k+4]*(quad[2]-quad[0]);
                    float sy=quad[1]+source[k+3]*(quad[7]-quad[1])+source[k+4]*(quad[3]-quad[1]);
                    result[k]=LightingCoordinates.worldX(sx,sy,altitude);result[k+1]=LightingCoordinates.worldY(sx,sy,altitude);result[k+2]=altitude;
                    result[k+5]=result[k+6]=0;result[k+7]=1;
                }else{
                    int j=nearest;float ax=source[j+11]-source[j+3],ay=source[j+12]-source[j+4],bx=source[j+19]-source[j+3],by=source[j+20]-source[j+4];
                    float determinant=ax*by-ay*bx,px=source[k+3]-source[j+3],py=source[k+4]-source[j+4];
                    float a=(px*by-py*bx)/determinant,b=(ax*py-ay*px)/determinant;
                    for(int c=0;c<3;c++){result[k+c]=source[j+c]+a*(source[j+8+c]-source[j+c])+b*(source[j+16+c]-source[j+c]);result[k+5+c]=source[j+5+c];}
                }
            }
        }
        return result;
    }
    private static boolean missingNormal(float[] vertices,int offset){return vertices[offset+5]==0&&vertices[offset+6]==0&&vertices[offset+7]==0;}
    private static float edgeDistance(float u,float v,float[] p,int a,int b){
        float dx=p[b+3]-p[a+3],dy=p[b+4]-p[a+4],length=dx*dx+dy*dy;
        float t=length<.00000001f?0:Math.max(0,Math.min(1,((u-p[a+3])*dx+(v-p[a+4])*dy)/length));
        float x=u-p[a+3]-t*dx,y=v-p[a+4]-t*dy;return x*x+y*y;
    }
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
    private static Array<float[]> faces(TiledMapLayerData m, SpatialBlockData b, boolean visible) {
        float x = b.x, y = b.y, a = x+b.width, c = y+b.depth, z = b.altitude, t = z+b.height;
        float[][] cells = {{x,y,z,a,y,z,a,y,t,x,y,t},{x,c,z,x,y,z,x,y,t,x,c,t},
                {x,y,t,a,y,t,a,c,t,x,c,t},{a,c,z,x,c,z,x,c,t,a,c,t},{a,y,z,a,c,z,a,c,t,a,y,t},
                {x,c,z,a,c,z,a,y,z,x,y,z}};
        Array<float[]> out = new Array<float[]>();
        for (int i = 0; i < (visible ? 3 : 6); i++) {
            float[] q = new float[32], point = new float[3];
            for (int n = 0; n < 4; n++) {
                LightingCoordinates.mapPoint(m,cells[i][n*3],cells[i][n*3+1],cells[i][n*3+2],point,0);
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
    static void imageUV(float[] q,float x,float y,float[] out,int at) {
        float ax=q[6]-q[0],ay=q[7]-q[1],bx=q[2]-q[0],by=q[3]-q[1];
        float d=ax*by-ay*bx;
        if(Math.abs(d)<.000001f) throw new IllegalArgumentException("Degenerate receiver image quad");
        x-=q[0];y-=q[1];out[at]=(x*by-y*bx)/d;out[at+1]=(ax*y-ay*x)/d;
    }
    private static void vertex(float[] p,int o,float x,float y,float z,float u,float v,float nx,float ny,float nz){
        p[o]=x;p[o+1]=y;p[o+2]=z;p[o+3]=u;p[o+4]=v;p[o+5]=nx;p[o+6]=ny;p[o+7]=nz;
    }
    static float[] triangulate(float[] p,float[] unused) {
        int n=p.length/8;float[] out=new float[Math.max(0,n-2)*24];int at=0;
        for(int i=1;i<n-1;i++)for(int k=0;k<3;k++){int v=k==0?0:k==1?i:i+1;System.arraycopy(p,v*8,out,at,8);at+=8;}
        return out;
    }
    static float[] clip(float[] p,int axis,float edge,boolean greater){
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
    static void subtract(float[] subject,float[] mask,Array<float[]> out){
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
