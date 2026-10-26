package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockLinks;
import games.pixscape.runtime.spatial.SpatialWallGeometry;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

import java.util.HashMap;

/** Cold shadow-policy graph; keys are unchanged geometric support identities. */
public final class WallShadowGroups {
    private final HashMap<Integer,Integer> parents=new HashMap<>();
    private static final class Wall {
        int owner,id; SpatialBlockData block; TiledMapLayerData map;
        float x0,x1,y0,y1;
    }
    private final Array<Wall> walls=new Array<>();
    public void add(int owner,TiledMapLayerData map,SpatialBlocksComponent blocks,ShadowExclusionIdentities ids){
        if(blocks==null||map.projection!=TiledProjection.ISO||map.tileWidth!=2*map.tileHeight)return;
        for(SpatialBlockData b:blocks.blocks){
            if(!b.actorOccluder||b.structureId<=0||b.id<=0||!b.hasLinkedTileRefs())continue;
            Wall w=new Wall();w.owner=owner;w.id=ids.idFor(owner,b.id);w.block=b;w.map=map;
            float[] p=new float[3];LightingCoordinates.mapPoint(map,b.x,b.y,b.altitude,p,0);w.x0=p[0];w.y0=p[1];
            LightingCoordinates.mapPoint(map,b.x+b.width,b.y+b.depth,b.altitude,p,0);w.x1=p[0];w.y1=p[1];walls.add(w);
        }
    }
    public void connectWalls(){
        for(int i=0;i<walls.size;i++)for(int j=0;j<i;j++){
            Wall a=walls.get(i),b=walls.get(j);boolean joined;
            if(a.owner==b.owner){
                joined=a.block.structureId==b.block.structureId
                    &&near(a.block.altitude,b.block.altitude)&&near(a.block.height,b.block.height)
                    &&sameStrip(a,b)
                    &&(SpatialBlockLinks.contactFace(a.block,b.block)>0||SpatialBlockLinks.overlap(a.block,b.block));
            }else{
                // Same physical strip, touching storeys and positive longitudinal span.
                // General overlap, a shared owner or structureId alone never establishes this edge.
                joined=a.map.tileWidth==b.map.tileWidth&&a.map.tileHeight==b.map.tileHeight
                    &&(near(a.block.altitude+a.block.height,b.block.altitude)||near(b.block.altitude+b.block.height,a.block.altitude))
                    &&sameStrip(a,b)
                    &&(a.block.width>a.block.depth?Math.min(a.x1,b.x1)>Math.max(a.x0,b.x0)
                        :Math.min(a.y1,b.y1)>Math.max(a.y0,b.y0));
            }
            if(joined)connect(a.id,b.id);
        }
    }
    /** A construction connection is not enough: perpendicular returns must cast onto facades. */
    private static boolean sameStrip(Wall a,Wall b){
        return (a.block.width>a.block.depth&&b.block.width>b.block.depth&&near(a.y0,b.y0)&&near(a.y1,b.y1))
            ||(a.block.depth>a.block.width&&b.block.depth>b.block.width&&near(a.x0,b.x0)&&near(a.x1,b.x1));
    }
    private static boolean near(float a,float b){return Math.abs(a-b)<=SpatialWallGeometry.GEOMETRY_EPSILON;}
    public void apply(LightingSurface s){
        s.shadowGroupId=group(s.selfShadowExclusionId);
        if(s.receiverExclusionIds!=null){s.receiverShadowGroups=new int[s.receiverExclusionIds.length];
            for(int i=0;i<s.receiverExclusionIds.length;i++)s.receiverShadowGroups[i]=group(s.receiverExclusionIds[i]);}
    }

    public int group(int identity){
        if(identity<=0)return 0;
        int parent=parents.getOrDefault(identity,identity);
        if(parent!=identity){parent=group(parent);parents.put(identity,parent);}
        return parent;
    }

    /** Called only for a verified construction connection, never for generic AABB overlap. */
    public void connect(int first,int second){
        if(first<=0||second<=0)throw new IllegalArgumentException("Invalid shadow group endpoint");
        int a=group(first),b=group(second);
        if(a!=b)parents.put(Math.max(a,b),Math.min(a,b));
    }
}
