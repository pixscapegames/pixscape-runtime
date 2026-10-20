package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.LongMap;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Cold scene-wide band preparation. Exact coplanar boundary contacts share one affine reference.
 * Nearby, misaligned and orthogonal storeys retain V2; no authored gap is closed. Spatial buckets
 * bound discovery and map/block indexes resolve both bands and refusals in O(1) after publication.
 */
public final class TileAlphaContactIndex {
    private static final float BUCKET=256,GUARD_DISTANCE=2;
    /** Derived coefficients only: these extents never create a replacement volume or caster. */
    public static final class Band {
        public final float x,y,z,nx,ny,height,halfThickness;
        private Band(Box b){
            x=(b.x+b.xx)*.5f;y=(b.y+b.yy)*.5f;z=b.z;height=b.top-b.z;
            nx=b.axis==1?1:0;ny=b.axis==2?1:0;
            halfThickness=(b.axis==1?b.xx-b.x:b.yy-b.y)*.5f;
        }
    }
    public static final class MapContacts {
        private final IntMap<Band> bands=new IntMap<Band>();
        private final IntMap<String> rejections=new IntMap<String>();
        private final IntMap<LongMap<String>> localRejections=new IntMap<LongMap<String>>();
        public Band band(int block){return bands.get(block);}
        /** Diagnostic summary only; admission uses the indexed cell decision below. */
        public String rejection(int block){return rejections.get(block);}
        /** Prepared positive and negative cell decisions; orthogonal contacts do not poison a straight band. */
        public String rejection(int block,int gx,int gy){
            LongMap<String> cells=localRejections.get(block);return cells==null?null:cells.get(((long)gx<<32)^(gy&0xffffffffL));
        }
    }
    private static final class Stamp {
        TiledMapLayerData map;SpatialBlocksComponent blocks;games.pixscape.runtime.tiled.TiledProjection projection;int revision,tw,th;float x,y,z;
        boolean matches(TiledMapLayerData m,SpatialBlocksComponent b){return map==m&&blocks==b&&projection==m.projection&&revision==(b==null?0:b.revision)&&tw==m.tileWidth&&th==m.tileHeight&&x==m.originX&&y==m.originY&&z==m.defaultTileAltitude;}
    }
    private static final class Box {
        int mapEntity,block,index,axis,parent,size=1;float x,y,xx,yy,z,top;
        TiledMapLayerData map;SpatialBlockData source;
    }
    private final IntMap<Stamp> stamps=new IntMap<Stamp>();
    private final IntMap<MapContacts> contacts=new IntMap<MapContacts>();
    private final Array<Box> boxes=new Array<Box>();
    private int checked;
    private boolean changed;
    public void begin(){checked=0;changed=false;}
    public void check(int entity,TiledMapLayerData map,SpatialBlocksComponent blocks){checked++;Stamp s=stamps.get(entity);if(s==null||!s.matches(map,blocks))changed=true;}
    public boolean needsRebuild(){return changed||checked!=stamps.size;}
    public void clear(){stamps.clear();boxes.clear();contacts.clear();}
    public void add(int entity,TiledMapLayerData map,SpatialBlocksComponent blocks){
        Stamp s=new Stamp();s.map=map;s.blocks=blocks;s.projection=map.projection;s.revision=blocks==null?0:blocks.revision;s.tw=map.tileWidth;s.th=map.tileHeight;s.x=map.originX;s.y=map.originY;s.z=map.defaultTileAltitude;stamps.put(entity,s);
        contacts.put(entity,new MapContacts());
        if(blocks==null)return;float[] p=new float[3];IntSet ids=new IntSet();
        for(int i=0;i<blocks.blocks.size;i++){
            SpatialBlockData b=blocks.blocks.get(i);
            if(!ids.add(b.id))throw new IllegalArgumentException("Duplicate block identity in alpha contact preparation: "+entity+":"+b.id);
            if(!b.shadowCaster&&!b.lightOccluder||b.height<=0||b.width<=0||b.depth<=0)continue;
            Box box=new Box();box.index=box.parent=boxes.size;box.mapEntity=entity;box.block=b.id;box.z=b.altitude;box.top=b.altitude+b.height;box.map=map;box.source=b;
            box.axis=map.projection!=games.pixscape.runtime.tiled.TiledProjection.ISO||map.tileWidth!=2*map.tileHeight?0:b.depth>=2*b.width?1:b.width>=2*b.depth?2:0;
            LightingCoordinates.mapPoint(map,b.x,b.y,0,p,0);box.x=p[0];box.y=p[1];
            LightingCoordinates.mapPoint(map,b.x+b.width,b.y+b.depth,0,p,0);box.xx=p[0];box.yy=p[1];boxes.add(box);
        }
    }
    public void compile(){
        LongMap<Array<Box>> buckets=new LongMap<Array<Box>>();IntSet seen=new IntSet();
        for(int i=0;i<boxes.size;i++){
            Box b=boxes.get(i);seen.clear();
            int x0=(int)Math.floor(b.x/BUCKET),x1=(int)Math.floor(b.xx/BUCKET),y0=(int)Math.floor(b.y/BUCKET),y1=(int)Math.floor(b.yy/BUCKET);
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                long key=((long)x<<32)^(y&0xffffffffL);Array<Box> list=buckets.get(key);
                if(list==null){list=new Array<Box>();buckets.put(key,list);}
                for(int n=0;n<list.size;n++){
                    Box a=list.get(n);if(!seen.add(a.index))continue;connect(a,b);
                }
                list.add(b);
            }
        }
        // Aggregate after connectivity is final; insertion order cannot change the common coefficients.
        Box[] sums=new Box[boxes.size];Band[] bands=new Band[boxes.size];
        for(int i=0;i<boxes.size;i++){
            Box b=boxes.get(i);int root=root(b.index);Box s=sums[root];
            if(s==null){s=new Box();s.axis=b.axis;s.x=b.x;s.y=b.y;s.xx=b.xx;s.yy=b.yy;s.z=b.z;s.top=b.top;sums[root]=s;}
            else{s.x=Math.min(s.x,b.x);s.y=Math.min(s.y,b.y);s.xx=Math.max(s.xx,b.xx);s.yy=Math.max(s.yy,b.yy);s.z=Math.min(s.z,b.z);s.top=Math.max(s.top,b.top);}
        }
        for(int i=0;i<boxes.size;i++){
            Box b=boxes.get(i);int root=root(i);Box s=sums[root];MapContacts map=contacts.get(b.mapEntity);
            if(boxes.get(root).size>1){if(bands[root]==null)bands[root]=new Band(s);map.bands.put(b.block,bands[root]);}
        }
    }
    private void connect(Box a,Box b){
        float dx=Math.min(a.xx,b.xx)-Math.max(a.x,b.x),dy=Math.min(a.yy,b.yy)-Math.max(a.y,b.y);
        float dz=Math.min(a.top,b.top)-Math.max(a.z,b.z);
        boolean stacked=a.top==b.z||b.top==a.z;
        boolean section=a.axis!=0&&a.axis==b.axis&&(a.axis==1?a.x==b.x&&a.xx==b.xx:a.y==b.y&&a.yy==b.yy);
        boolean side=section&&dz>0&&(a.axis==1?a.yy==b.y||b.yy==a.y:a.xx==b.x||b.xx==a.x);
        if(section&&(stacked&&dx>0&&dy>0||side)){union(a.index,b.index);return;}
        if(dx>0&&dy>0&&(Math.abs(a.top-b.z)<=GUARD_DISTANCE||Math.abs(b.top-a.z)<=GUARD_DISTANCE)){
            String reason=!stacked?"Authored storey gap or overlap intersects this cell; V2 retained locally"
                    :"Incompatible storey sections intersect this cell; V2 retained locally";
            guard(a,b,reason);guard(b,a,reason);
        }
    }
    private void guard(Box a,Box b,String reason){
        float x=Math.max(a.x,b.x),y=Math.max(a.y,b.y),xx=Math.min(a.xx,b.xx),yy=Math.min(a.yy,b.yy);
        MapContacts target=contacts.get(a.mapEntity);LongMap<String> cells=target.localRejections.get(a.block);
        float[] p=new float[6];
        for(int i=0;i<a.source.linkedTileRefs.size;i++){
            SpatialBlockData.LinkedTileRef ref=a.source.linkedTileRefs.get(i);
            LightingCoordinates.mapPoint(a.map,ref.gx,ref.gy,0,p,0);LightingCoordinates.mapPoint(a.map,ref.gx+1,ref.gy+1,0,p,3);
            if(Math.min(xx,p[3])<=Math.max(x,p[0])||Math.min(yy,p[4])<=Math.max(y,p[1]))continue;
            if(cells==null){cells=new LongMap<String>();target.localRejections.put(a.block,cells);}
            long key=((long)ref.gx<<32)^(ref.gy&0xffffffffL);String old=cells.get(key);
            if(old==null||reason.compareTo(old)<0)cells.put(key,reason);
            old=target.rejections.get(a.block);if(old==null||reason.compareTo(old)<0)target.rejections.put(a.block,reason);
        }
    }
    private int root(int i){int r=i;while(boxes.get(r).parent!=r)r=boxes.get(r).parent;while(i!=r){int next=boxes.get(i).parent;boxes.get(i).parent=r;i=next;}return r;}
    private void union(int a,int b){a=root(a);b=root(b);if(a==b)return;if(boxes.get(a).size<boxes.get(b).size){int t=a;a=b;b=t;}boxes.get(b).parent=a;boxes.get(a).size+=boxes.get(b).size;}
    public MapContacts contacts(int mapEntity){return contacts.get(mapEntity);}
}
