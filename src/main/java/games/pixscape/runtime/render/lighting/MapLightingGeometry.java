package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

/** Cold map preparation. All linked memberships are indexed; no owner chosen per tile. */
public final class MapLightingGeometry {
    public final Array<LightingSurface> casters = new Array<LightingSurface>();
    private LongMap<Array<SpatialBlockData>> memberships = new LongMap<Array<SpatialBlockData>>();
    private static final class Receiver { float[] quad; byte flags; int assetId; LightingSurface surface; }
    private LongMap<Receiver> receivers = new LongMap<Receiver>();
    private LongMap<Array<Receiver>> receiverVariants = new LongMap<Array<Receiver>>();
    private int revision = Integer.MIN_VALUE;
    private TiledMapLayerData previous;
    private SpatialBlocksComponent previousBlocks;
    private TiledProjection projection;
    private float ox, oy, altitude;
    private int tw, th;
    private IntMap<LocalSurfaceDescription> descriptions=new IntMap<LocalSurfaceDescription>();
    private Array<TileSurfaceDescription> authoredDescriptions;
    private RoundedMapSupport rounded=new RoundedMapSupport();
    private int resourceRevision;
    private String resourceTag;
    private boolean hasRounded;
    private int contentRevision;
    private int publicationRevision;
    private Preview activePreview;
    public int publicationRevision(){return publicationRevision;}
    public void registerShadowGroups(int owner,ShadowExclusionIdentities ids,WallShadowGroups groups){
        groups.add(owner,previous,previousBlocks,ids);
        // Curved occurrences keep their own identity; they must not bridge two facade groups.
    }
    public int roundedProfileCompilationCount(){return rounded.profileCompilations;}
    public int roundedGeometryCompilationCount(){return rounded.geometryCompilations;}
    public RoundedWallSupport roundedSupport(int gx,int gy){return rounded.support(gx,gy);}
    /** Cold gesture capture. The caller must discard it when map/resources/authored revisions change. */
    public Preview beginRoundedPreview(int gx,int gy) {
        if(activePreview!=null)activePreview.restore();
        Receiver receiver=receivers.get(key(gx,gy));RoundedWallSupport support=rounded.support(gx,gy);
        if(receiver==null||support==null)throw new IllegalArgumentException("Rounded receiver is not prepared");
        activePreview=new Preview(receiver,support);return activePreview;
    }
    /** O(1) transient swap; existing opaque wall cuts and all other occurrences remain untouched. */
    public final class Preview {
        private final Receiver receiver;private final RoundedWallSupport initial;
        private Preview(Receiver receiver,RoundedWallSupport initial){this.receiver=receiver;this.initial=initial;}
        public boolean isCurrent(){return activePreview==this;}
        public void show(RoundedWallSupport support){
            if(!isCurrent())throw new IllegalStateException("Rounded preview context is stale");
            for(LightingSurface surface:new LightingSurface[]{support.receiver,support.texturedCaster,support.bottomCaster}){
                surface.roundedIdentity=initial.receiver.roundedIdentity;
                surface.roundedCellKey=initial.receiver.roundedCellKey;
                surface.selfShadowExclusionId=initial.receiver.selfShadowExclusionId;
                surface.shadowGroupId=initial.receiver.shadowGroupId;
            }
            receiver.surface=support.receiver;
        }
        public LightingSurface restore(){if(isCurrent()){show(initial);activePreview=null;}return initial.receiver;}
    }
    public void invalidate(){revision=Integer.MIN_VALUE;}
    public void descriptions(Array<TileSurfaceDescription> values){
        if(authoredDescriptions!=values){authoredDescriptions=values;invalidate();}
    }

    /** Resolves the actual native image at the resource publication boundary, never a guessed quad. */
    public interface NativeQuads {
        boolean resolve(TiledMapLayerData map, games.pixscape.runtime.tiled.TileChunk chunk,
                        int cell, float[] quad, boolean required);
        default int frameCount(int assetId) { return 0; }
        default boolean resolveFrame(TiledMapLayerData map, games.pixscape.runtime.tiled.TileChunk chunk,
                                     int cell, int frame, float[] quad) {
            return resolve(map,chunk,cell,quad,true);
        }
    }

    /** Complete detached preparation. No live caster or receiver changes on admission failure. */
    public boolean ensure(TiledMapLayerData map, SpatialBlocksComponent blocks, NativeQuads quads,
                          String tag, int resources) {
        if (!needsUpdate(map,blocks) && resourceRevision==resources
                && (resourceTag==null?tag==null:resourceTag.equals(tag))) return false;
        if(activePreview!=null)activePreview.restore();
        MapLightingGeometry next=new MapLightingGeometry();
        next.authoredDescriptions=authoredDescriptions;
        next.rounded=new RoundedMapSupport(rounded);
        try {
            next.ensure(map,blocks);
            // A missing linked cell is not visited by the chunk loop below. Reject it
            // before a detached candidate can replace a live opaque caster.
            for(LongMap.Entry<Array<SpatialBlockData>> entry:next.memberships){
                if(!requiresSilhouetteImage(entry.value))continue;
                int gx=(int)(entry.key>>32),gy=(int)entry.key;
                if(map.getTile(gx,gy)<=0)
                    throw new LightingAdmissionException("Silhouette caster linked cell is empty at ("+gx+","+gy+")");
            }
            float[] quad=new float[8];
            com.badlogic.gdx.utils.IntSet explicit=new com.badlogic.gdx.utils.IntSet();
            if(authoredDescriptions!=null)for(TileSurfaceDescription d:authoredDescriptions)explicit.add(d.assetId);
            for(games.pixscape.runtime.tiled.TileChunk chunk:map.getChunks())for(int i=0;i<chunk.cellCount();i++){
                int asset=chunk.assetIds[i];if(asset<=0)continue;
                int gx=chunk.chunkX*map.chunkSize+i%chunk.chunkWidth;
                int gy=chunk.chunkY*map.chunkSize+i/chunk.chunkWidth;
                boolean required=explicit.contains(asset)||requiresSilhouetteImage(next.memberships.get(key(gx,gy)));
                if(required)for(int frame=0,count=quads.frameCount(asset);frame<count;frame++){
                    try {
                        if(!quads.resolveFrame(map,chunk,i,frame,quad))
                            throw new LightingAdmissionException("Native image unavailable");
                        next.receiver(map,gx,gy,asset,chunk.transformFlags[i],quad);
                    } catch(IllegalArgumentException invalid) {
                        throw new LightingAdmissionException("Tile asset "+asset+" at ("+gx+","+gy+") frame "+frame+": "+invalid.getMessage(),invalid);
                    }
                }
                // Restore the displayed frame after examining variants, including its caster.
                boolean available=quads.resolve(map,chunk,i,quad,required);
                if(required&&!available)throw new IllegalArgumentException("Native image unavailable for tile asset "+asset);
                if(available)
                    next.receiver(map,chunk.chunkX*map.chunkSize+i%chunk.chunkWidth,
                            chunk.chunkY*map.chunkSize+i/chunk.chunkWidth,asset,chunk.transformFlags[i],quad);
            }
        } catch(IllegalArgumentException invalid) {
            throw new LightingAdmissionException("Tile lighting candidate rejected: "+invalid.getMessage(),invalid);
        }
        memberships=next.memberships;descriptions=next.descriptions;receivers=next.receivers;receiverVariants=next.receiverVariants;rounded=next.rounded;
        casters.clear();casters.addAll(next.casters);hasRounded=next.hasRounded;contentRevision=next.contentRevision;
        previous=next.previous;previousBlocks=next.previousBlocks;projection=next.projection;revision=next.revision;
        ox=next.ox;oy=next.oy;altitude=next.altitude;tw=next.tw;th=next.th;
        resourceRevision=resources;resourceTag=tag;
        publicationRevision++;
        return true;
    }

    private boolean needsUpdate(TiledMapLayerData map, SpatialBlocksComponent blocks){
        return previous!=map || previousBlocks!=blocks || projection!=map.projection
                || revision!=(blocks==null?0:blocks.revision) || ox!=map.originX || oy!=map.originY
                || altitude!=map.defaultTileAltitude || tw!=map.tileWidth || th!=map.tileHeight
                || ((memberships.size>0||authoredDescriptions!=null&&authoredDescriptions.size>0)
                    &&contentRevision!=map.contentRevision());
    }

    /** Structural preparation only. Production publication uses the native-quad overload above. */
    public boolean ensure(TiledMapLayerData map, SpatialBlocksComponent blocks) {
        int r = blocks == null ? 0 : blocks.revision;
        if (!needsUpdate(map,blocks)) return false;
        if (blocks != null && blocks.hasBlocks() && map.projection != TiledProjection.ISO)
            throw new IllegalArgumentException("Lighting volume reconstruction requires an ISO map");
        LongMap<Array<SpatialBlockData>> nextMemberships=new LongMap<Array<SpatialBlockData>>();
        IntMap<LocalSurfaceDescription> nextDescriptions=new IntMap<LocalSurfaceDescription>();
        com.badlogic.gdx.utils.IntSet assetIds=new com.badlogic.gdx.utils.IntSet();boolean nextHasRounded=false;
        if(authoredDescriptions!=null)for(int i=0;i<authoredDescriptions.size;i++){
            TileSurfaceDescription d=authoredDescriptions.get(i);
            if(d==null)throw new IllegalArgumentException("Null tile surface description");
            d.validate();
            if(!assetIds.add(d.assetId))
                throw new IllegalArgumentException("Tile surface descriptions require unique positive asset IDs");
            if(d.geometry!=null)nextDescriptions.put(d.assetId,d.geometry);else nextHasRounded=true;
        }
        Array<SpatialBlockData> casterBlocks=new Array<SpatialBlockData>();
        com.badlogic.gdx.utils.IntSet blockIds=new com.badlogic.gdx.utils.IntSet();
        if (blocks != null) for (int i = 0; i < blocks.blocks.size; i++) {
            SpatialBlockData b = blocks.blocks.get(i);
            if(b==null||b.linkedTileRefs==null)throw new LightingAdmissionException("Null block or association list");
            if(b.id>0&&!blockIds.add(b.id))throw new LightingAdmissionException("Duplicate block identity "+b.id);
            for (int j = 0; j < b.linkedTileRefs.size; j++) {
                SpatialBlockData.LinkedTileRef ref = b.linkedTileRefs.get(j);
                if(ref==null)throw new LightingAdmissionException("Null tile association in block "+b.id);
                // Studio permits replacing an occupied cell without relinking its wall.
                // tileAssetId records the authoring snapshot, not a second texture source.
                long key = key(ref.gx, ref.gy);
                Array<SpatialBlockData> owners = nextMemberships.get(key);
                if (owners == null) { owners = new Array<SpatialBlockData>(); nextMemberships.put(key, owners); }
                if (!owners.contains(b, true)) owners.add(b);
            }
        }
        for(Array<SpatialBlockData> owners:nextMemberships.values())owners.sort(new java.util.Comparator<SpatialBlockData>(){
            public int compare(SpatialBlockData a,SpatialBlockData b){return Integer.compare(a.id,b.id);}
        });
        // Admission must see the complete bipartite graph, not an insertion prefix.
        if (blocks != null) for (int i = 0; i < blocks.blocks.size; i++) {
            SpatialBlockData b=blocks.blocks.get(i);
            if (usesSilhouette(b)) validateSilhouetteCaster(b,nextMemberships);
            if ((b.shadowCaster || b.lightOccluder) && !usesSilhouette(b)) casterBlocks.add(b);
        }
        rounded.prepare(map,nextMemberships,authoredDescriptions);
        memberships=nextMemberships;descriptions.clear();descriptions.putAll(nextDescriptions);
        casters.clear();receivers.clear();receiverVariants.clear();hasRounded=nextHasRounded;contentRevision=map.contentRevision();
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
                    float[] triangles=rounded.trim(casterBlocks.get(i).id,triangulate(faces.get(j), null));
                    if(triangles.length==0)continue;
                    LightingSurface s = new LightingSurface(triangles);
                    s.shadowCaster = true; s.receiveLight = false; casters.add(s);
                }
        }
        previous = map; previousBlocks = blocks; projection = map.projection; revision = r; ox = map.originX; oy = map.originY;
        altitude = map.defaultTileAltitude; tw = map.tileWidth; th = map.tileHeight;
        return true;
    }

    /** Admission uses the complete cell graph; a tile may contribute to distinct volumes. */
    private static void validateSilhouetteCaster(SpatialBlockData block,
                                                  LongMap<Array<SpatialBlockData>> memberships) {
        if(block.id<=0)throw new LightingAdmissionException("Silhouette caster requires a positive authored block identity");
        if (!block.shadowCaster && !block.lightOccluder)
            throw new LightingAdmissionException("Silhouette caster needs Shadow caster or Light occluder enabled");
        if (!block.linkedTileRefsAuthored || block.linkedTileRefs == null || block.linkedTileRefs.size == 0)
            throw new LightingAdmissionException("Silhouette caster requires authored linked tiles");
        for(SpatialBlockData.LinkedTileRef ref:block.linkedTileRefs){
            if(ref==null||ref.tileAssetId<=0)
                throw new LightingAdmissionException("Silhouette caster requires a linked tile asset");
            Array<SpatialBlockData> owners=memberships.get(key(ref.gx,ref.gy));
            if(owners==null||!owners.contains(block,true))
                throw new LightingAdmissionException("Silhouette caster missing from association graph");
        }
    }

    /** Builds a disjoint UV partition: alpha is applied exactly once at each image pixel. */
    public LightingSurface receiver(TiledMapLayerData map, int gx, int gy,
                                     int assetId, byte flags, float[] quad) {
        long cell = key(gx, gy);
        Receiver cached = receivers.get(cell);
        // A texture-only animation frame cannot invalidate an automatic physical partition.
        boolean same=matches(cached,assetId,flags,quad);
        if (same) return cached.surface;
        Array<Receiver> variants=receiverVariants.get(cell);
        if(variants!=null)for(Receiver variant:variants)if(matches(variant,assetId,flags,quad)){
            receivers.put(cell,variant);return variant.surface;
        }
        if(activePreview!=null&&activePreview.receiver==cached)activePreview.restore();
        LightingSurface curved=rounded.receiver(gx,gy,quad,casters);
        if(curved!=null){
            cached=new Receiver();cached.quad=LocalSurfaceDescription.copy(quad);cached.flags=flags;cached.assetId=assetId;cached.surface=curved;remember(cell,cached);
            return curved;
        }
        LocalSurfaceDescription description=descriptions.get(assetId);
        if(description!=null){
            if(requiresSilhouetteImage(memberships.get(cell)))
                throw new LightingAdmissionException("Local authored support and linked volume contributions need an explicit ownership contract at ("+gx+","+gy+")");
            if(flags!=0)throw new LightingAdmissionException("Local volume description needs explicit transformed variant for tile flags");
            float[] origin=new float[3];LightingCoordinates.mapPoint(map,gx,gy,map.defaultTileAltitude,origin,0);
            for(int i=0;i<description.xyz.length/3;i++){
                float u=description.uv[i*2],v=description.uv[i*2+1];
                float sx=quad[0]+u*(quad[6]-quad[0])+v*(quad[2]-quad[0]);
                float sy=quad[1]+u*(quad[7]-quad[1])+v*(quad[3]-quad[1]);
                float x=origin[0]+description.xyz[i*3],y=origin[1]+description.xyz[i*3+1],z=origin[2]+description.xyz[i*3+2];
                if(Math.abs(LightingCoordinates.screenX(x,y)-sx)>.02f || Math.abs(LightingCoordinates.screenY(x,y,z)-sy)>.02f)
                    throw new LightingAdmissionException("Local tile XYZ/UV registration does not match its native image");
            }
            LightingSurface surface=LocalSurfaceCompiler.compile(description,origin[0],origin[1],origin[2]);
            surface.forwardTopology=ForwardTopology.compile(surface,quad);
            cached=new Receiver();cached.quad=LocalSurfaceDescription.copy(quad);cached.flags=flags;cached.assetId=assetId;cached.surface=surface;remember(cell,cached);
            return surface;
        }
        Array<SpatialBlockData> owners = memberships.get(key(gx, gy));
        if (owners != null && flags != 0)
            throw new LightingAdmissionException("Automatic volume receiver does not support transformed tiles; provide local geometry");
        Array<float[]> polygons = new Array<float[]>();
        com.badlogic.gdx.utils.IntArray polygonOwners=new com.badlogic.gdx.utils.IntArray();
        com.badlogic.gdx.utils.BooleanArray polygonSpriteShading = new com.badlogic.gdx.utils.BooleanArray();
        Array<ReceiverFace> candidates = owners != null ? new Array<ReceiverFace>() : null;
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
                    if(candidates!=null)candidates.add(new ReceiverFace(p,quad,usesSilhouette(b)?b.id:0,!b.shadeUnlitFaces));
                    else {
                        Array<float[]> next = new Array<float[]>();
                        for (int k = 0; k < polygons.size; k++) subtract(polygons.get(k), p, next);
                        next.add(p); polygons = next;
                    }
                }
            }
        }
        if(candidates!=null)polygons=visiblePartition(candidates,polygonOwners,polygonSpriteShading);
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
        int[] blockIds=new int[size/24];
        boolean[] spriteShading=new boolean[size/24];
        for (int i = 0; i < polygons.size; i++) {
            float[] t = triangulate(polygons.get(i), null);
            int owner=i<polygonOwners.size?polygonOwners.get(i):0;
            java.util.Arrays.fill(blockIds,offset/24,(offset+t.length)/24,owner);
            if(i<polygonSpriteShading.size && polygonSpriteShading.get(i))
                java.util.Arrays.fill(spriteShading,offset/24,(offset+t.length)/24,true);
            System.arraycopy(t, 0, data, offset, t.length); offset += t.length;
        }
        LightingSurface surface = new LightingSurface(data); surface.approximate = true;
        surface.receiverBlockIds=blockIds;
        surface.receiverSpriteShading=spriteShading;
        surface.receiverVertices=completeReceiverVertices(data,quad,map.defaultTileAltitude,spriteShading);
        surface.forwardTopology=ForwardTopology.compile(surface,quad);
        if(owners!=null)for(SpatialBlockData owner:owners){
            if(!usesSilhouette(owner))continue;
            LightingSurface contribution=silhouetteCaster(map,owner,quad);
            if(surface.casterContributions==null)surface.casterContributions=new Array<LightingSurface>();
            surface.casterContributions.add(contribution);surface.shadowCaster=true;
        }
        if(surface.casterContributions!=null&&surface.casterContributions.size==1){
            surface.casterSurface=surface.casterContributions.first();
            surface.pocSourceBlockId=surface.casterSurface.pocSourceBlockId;
        }
        cached = new Receiver(); cached.quad = LocalSurfaceDescription.copy(quad); cached.flags = flags; cached.assetId=assetId; cached.surface = surface;
        remember(cell,cached);
        return surface;
    }

    private boolean matches(Receiver cached,int assetId,byte flags,float[] quad){
        if(cached==null||cached.flags!=flags||cached.assetId!=assetId
                &&(descriptions.containsKey(cached.assetId)||descriptions.containsKey(assetId)))return false;
        for(int i=0;i<8;i++)if(cached.quad[i]!=quad[i])return false;
        return true;
    }
    private void remember(long cell,Receiver receiver){
        receivers.put(cell,receiver);
        Array<Receiver> variants=receiverVariants.get(cell);
        if(variants==null){variants=new Array<Receiver>();receiverVariants.put(cell,variants);}
        variants.add(receiver);
    }

    private static boolean requiresSilhouetteImage(Array<SpatialBlockData> owners){
        if(owners!=null)for(SpatialBlockData owner:owners)if(usesSilhouette(owner))return true;
        return false;
    }
    public static boolean hasSilhouetteCasters(SpatialBlocksComponent blocks){
        return blocks!=null&&requiresSilhouetteImage(blocks.blocks);
    }
    /** One product path. Unauthored/unlinked volumes keep their explicit opaque role. */
    public static boolean usesSilhouette(SpatialBlockData block){
        return block!=null&&(block.shadowCaster||block.lightOccluder)
                &&block.linkedTileRefsAuthored&&block.hasLinkedTileRefs();
    }

    /**
     * Cold projection from existing volume positions to its linked native image. Every point
     * uses its reference screen projection; clipped UVs remove the volume outside that image.
     * Alpha is then sampled once by the existing shadow shader, which retains physical XYZ.
     */
    private LightingSurface silhouetteCaster(TiledMapLayerData map,SpatialBlockData block,float[] quad){
        Array<float[]> projected=new Array<float[]>();int length=0;
        SilhouetteFaceCompiler compiler=new SilhouetteFaceCompiler();float[] compiled=new float[144];
        Array<float[]> volumeFaces=faces(map,block,false);
        for(int i=0;i<volumeFaces.size;i++){
            float[] face=volumeFaces.get(i);
            int count=compiler.compile(face,quad,compiled,0);if(count==0)continue;
            float[] triangles=new float[count*8];System.arraycopy(compiled,0,triangles,0,triangles.length);
            triangles=rounded.trim(block.id,triangles);projected.add(triangles);length+=triangles.length;
        }
        if(length==0)throw new LightingAdmissionException("Silhouette caster volume projects outside its linked tile image");
        float[] vertices=new float[length];int at=0;
        for(int i=0;i<projected.size;i++){float[] triangles=projected.get(i);System.arraycopy(triangles,0,vertices,at,triangles.length);at+=triangles.length;}
        LightingSurface result=new LightingSurface(vertices);result.shadowCaster=true;result.receiveLight=false;
        result.pocSourceBlockId=block.id;
        return result;
    }

    private static long key(int x, int y) { return ((long)x << 32) ^ (y & 0xffffffffL); }
    /** Cold tile-local partition. Larger Z is nearer along the display ray (-1,-1,+1).
     * Depth on a projected plane is affine in UV, so a crossing requires a line cut,
     * not a global face sort. Casters and authored memberships remain unchanged. */
    private static Array<float[]> visiblePartition(Array<ReceiverFace> faces,com.badlogic.gdx.utils.IntArray owners,com.badlogic.gdx.utils.BooleanArray spriteShading){
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
            for(int k=0;k<parts.size;k++){owners.add(face.owner);spriteShading.add(face.spriteShading);}
        }
        return result;
    }
    private static final class ReceiverFace implements Comparable<ReceiverFace>{
        final float[] polygon;
        final int owner;
        final boolean spriteShading;
        final double u,v,c;
        float minU=Float.POSITIVE_INFINITY,minV=Float.POSITIVE_INFINITY;
        float maxU=Float.NEGATIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        ReceiverFace(float[] p,float[] quad,int owner,boolean spriteShading){
            this.spriteShading=spriteShading;
            polygon=p;this.owner=owner;
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
            d=Integer.compare(polygon.length,b.polygon.length);
            return d!=0?d:Integer.compare(owner,b.owner);
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
        return completeReceiverVertices(source,quad,altitude,null);
    }
    private static float[] completeReceiverVertices(float[] source,float[] quad,float altitude,boolean[] spriteShading){
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
            if(spriteShading!=null && nearest>=0) spriteShading[i/24]=spriteShading[nearest/24];
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
    private static void imageUV(float[] q,float x,float y,float[] out,int at) {
        SilhouetteImageMapping.imageUV(q,x,y,out,at);
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
