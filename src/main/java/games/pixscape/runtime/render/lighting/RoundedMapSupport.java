package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;

/** Map-owned cold adaptation of explicit asset profiles. Never resolves by image content or asset name. */
final class RoundedMapSupport {
    private static final class Profile {
        final RoundedWallDescription description;
        final RoundedWallSupport.Profile tangents;
        Profile(RoundedWallDescription d){description=d.copy();tangents=new RoundedWallSupport.Profile(d);}
    }
    private static final class Placement {
        Profile profile;int xWall,yWall;float cx,cy,radius,thickness,base,height;
        float[] quad;RoundedWallSupport support;
        boolean same(Placement p){return p!=null&&profile==p.profile&&xWall==p.xWall&&yWall==p.yWall
            &&cx==p.cx&&cy==p.cy&&radius==p.radius&&thickness==p.thickness&&base==p.base&&height==p.height;}
    }
    private IntMap<Profile> profiles=new IntMap<Profile>();
    private LongMap<Placement> placements=new LongMap<Placement>();
    private IntMap<Placement> cuts=new IntMap<Placement>();
    int profileCompilations,geometryCompilations;

    RoundedMapSupport() {}
    void connectShadowGroups(int owner,ShadowExclusionIdentities ids,WallShadowGroups groups){
        for(LongMap.Entry<Placement> e:placements){
            int corner=ids.idForCorner(owner,e.key);
            groups.connect(corner,ids.idFor(owner,e.value.xWall));
            groups.connect(corner,ids.idFor(owner,e.value.yWall));
        }
    }
    /** The preparation below replaces indexes and placements; shared compiled supports are immutable. */
    RoundedMapSupport(RoundedMapSupport source){
        profiles=source.profiles;placements=source.placements;
        profileCompilations=source.profileCompilations;geometryCompilations=source.geometryCompilations;
    }

    /** Cold O(descriptions + authored cells + linked memberships), with O(1) lookups afterwards.
     * Candidate indexes publish together. Unchanged occurrences retain their world-space support. */
    void prepare(TiledMapLayerData map,LongMap<Array<SpatialBlockData>> memberships,Array<TileSurfaceDescription> descriptions){
        IntMap<Profile> nextProfiles=new IntMap<Profile>();int builtProfiles=0;
        if(descriptions!=null)for(int i=0;i<descriptions.size;i++){
            TileSurfaceDescription d=descriptions.get(i);if(d.rounded==null)continue;
            Profile p=profiles.get(d.assetId);
            if(p==null||!p.description.sameAs(d.rounded)){p=new Profile(d.rounded);builtProfiles++;}
            nextProfiles.put(d.assetId,p);
        }
        LongMap<Placement> next=new LongMap<Placement>();IntMap<Placement> nextCuts=new IntMap<Placement>();
        if(nextProfiles.size>0){
            if(map.projection!=TiledProjection.ISO||map.tileWidth!=map.tileHeight*2)
                throw new IllegalArgumentException("Rounded walls require an untransformed 2:1 ISO map");
            for(TileChunk chunk:map.getChunks())for(int i=0;i<chunk.cellCount();i++){
                Profile p=nextProfiles.get(chunk.assetIds[i]);if(p==null)continue;
                int gx=chunk.chunkX*map.chunkSize+i%chunk.chunkWidth,gy=chunk.chunkY*map.chunkSize+i/chunk.chunkWidth;
                long key=key(gx,gy);
                if(chunk.transformFlags[i]!=0)throw invalid(gx,gy,"transformed tile requires its own supported profile");
                Placement placed=resolve(map,gx,gy,p,memberships.get(key));
                Placement old=placements.get(key);if(placed.same(old)){placed.support=old.support;placed.quad=old.quad;}
                if(nextCuts.containsKey(placed.xWall)||nextCuts.containsKey(placed.yWall))
                    throw invalid(gx,gy,"multiple rounded replacements on one wall are not supported");
                next.put(key,placed);nextCuts.put(placed.xWall,placed);nextCuts.put(placed.yWall,placed);
            }
        }
        profiles=nextProfiles;placements=next;cuts=nextCuts;profileCompilations+=builtProfiles;
    }
    private static Placement resolve(TiledMapLayerData map,int gx,int gy,Profile profile,Array<SpatialBlockData> owners){
        RoundedWallPlacement resolved=new RoundedWallPlacement(map,gx,gy,profile.description,owners);
        Placement p=new Placement();p.profile=profile;p.xWall=resolved.xWall;p.yWall=resolved.yWall;
        p.cx=resolved.cx;p.cy=resolved.cy;p.radius=resolved.radius;p.thickness=resolved.thickness;p.base=resolved.base;p.height=resolved.height;
        return p;
    }
    RoundedWallSupport support(int gx,int gy){Placement p=placements.get(key(gx,gy));return p==null?null:p.support;}
    LightingSurface receiver(int gx,int gy,float[] quad,Array<LightingSurface> casters){
        Placement p=placements.get(key(gx,gy));if(p==null)return null;
        boolean same=p.quad!=null;for(int i=0;same&&i<8;i++)same=p.quad[i]==quad[i];
        if(!same){
            RoundedWallSupport support;
            try {
                support=new RoundedWallSupport(p.cx,p.cy,p.radius,p.thickness,p.base,p.height,p.profile.tangents,quad);
            } catch(IllegalArgumentException invalid) {
                throw new LightingAdmissionException("Rounded tile ("+gx+","+gy+"): "+invalid.getMessage(),invalid);
            }
            p.quad=LocalSurfaceDescription.copy(quad);p.support=support;geometryCompilations++;
            for(LightingSurface surface:new LightingSurface[]{support.receiver,support.texturedCaster,support.bottomCaster}){
                surface.roundedIdentity=true;surface.roundedCellKey=key(gx,gy);
            }
        }
        return p.support.receiver;
    }
    float[] trim(int blockId,float[] vertices){
        Placement p=cuts.get(blockId);if(p==null)return vertices;
        int axis=blockId==p.xWall?0:1;float edge=axis==0?p.cx:p.cy;FloatArray out=new FloatArray();
        // Preserve the validated fixture's triangle order and interpolation exactly.
        for(int o=0;o<vertices.length;o+=24){float[] polygon=new float[32];int n=0;
            for(int i=0;i<3;i++){int a=o+(i+2)%3*8,b=o+i*8;float da=vertices[a+axis]-edge,db=vertices[b+axis]-edge;boolean ia=da>=0,ib=db>=0;
                if(ia!=ib){float t=da/(da-db);for(int k=0;k<8;k++)polygon[n++]=vertices[a+k]+t*(vertices[b+k]-vertices[a+k]);}
                if(ib){System.arraycopy(vertices,b,polygon,n,8);n+=8;}}
            for(int i=1;i<n/8-1;i++){out.addAll(polygon,0,8);out.addAll(polygon,i*8,8);out.addAll(polygon,(i+1)*8,8);}
        }return out.toArray();
    }
    private static IllegalArgumentException invalid(int gx,int gy,String reason){return new IllegalArgumentException("Rounded tile ("+gx+","+gy+"): "+reason);}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
}
