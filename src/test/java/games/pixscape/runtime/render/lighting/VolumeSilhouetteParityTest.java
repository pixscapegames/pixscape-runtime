package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.tiled.*;
import games.pixscape.runtime.tiled.profile.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Same image and physical box, through the actual tile compiler and actor publication. */
public class VolumeSilhouetteParityTest {
    @Test public void auditClippingVertexSets(){
        float[] q={-32,0,-32,128,32,128,32,0};
        ActorVolumeData v=new ActorVolumeData();v.width=v.depth=64;v.height=128;
        AuditActorVolumeCaster a=new AuditActorVolumeCaster(false,false),b=new AuditActorVolumeCaster(false,true);
        a.update(v,0,0,0,q);b.update(v,0,0,0,q);
        java.util.Set<String> left=vertexSet(a),right=vertexSet(b);
        System.out.println("CLIP_VERTEX_SETS bitwiseXYZUVEqual="+left.equals(right)+" original="+left.size()+" tileClip="+right.size()+" vertices="+a.vertexCount+"/"+b.vertexCount);
        left.removeAll(right);System.out.println("ORIGINAL_ONLY_VERTICES="+left);
    }
    private static java.util.Set<String> vertexSet(AuditActorVolumeCaster mesh){
        java.util.Set<String> out=new java.util.TreeSet<String>();
        for(int o=0;o<mesh.vertexCount*8;o+=8){
            String key="";for(int k=0;k<5;k++)key+=Float.floatToIntBits(mesh.vertices[o+k])+",";
            out.add(key);
        }return out;
    }
    static LightingSurface tileCaster(float[] quad,float width,float depth,float height,float x,float y,float z){
        TiledMapLayerData map=new TiledMapLayerData(8,8,64,32,4,TiledProjection.ISO);
        map.originX=x-y-width*.5f+depth*.5f-32;
        map.originY=(x+y)*.5f-(width+depth)*.25f;
        SpatialBlockData block=new SpatialBlockData();block.id=1;block.width=width/32;block.depth=depth/32;
        block.height=height;block.altitude=z;block.shadowCaster=true;block.addLinkedTileRef(0,0,7);block.linkedTileRefsAuthored=true;
        return tileCaster(map,block,0,0,7,quad);
    }
    private static LightingSurface tileCaster(TiledMapLayerData map,SpatialBlockData block,int gx,int gy,int asset,float[] quad){
        map.setTile(gx,gy,asset);SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(block);
        MapLightingGeometry geometry=new MapLightingGeometry();
        geometry.ensure(map,blocks,(m,c,cell,out,required)->{System.arraycopy(quad,0,out,0,8);return true;},"parity",1);
        return geometry.receiver(map,gx,gy,asset,(byte)0,quad).casterSurface;
    }
    @Test public void equivalentBoxHasEquivalentRayCoverageBeforeAnyRepresentationChange(){
        float[] q={-32,0,-32,128,32,128,32,0};
        ActorVolumeData v=new ActorVolumeData();v.width=v.depth=64;v.height=128;
        ActorVolumeCaster actor=new ActorVolumeCaster();actor.update(v,0,0,0,q);
        LightingSurface tile=tileCaster(q,64,64,128,0,0,0);
        assertEquivalent(tile.vertices,actor.vertices,actor.vertexCount);
    }
    @Test public void actualScaffoldContributionHasTheSamePhysicalCoverage() {
        FileHandle root=new FileHandle("../shadow-volume-silhouette-demo/studio");
        Assume.assumeTrue(root.child("scenes/scene1.json").exists());
        Json json=new Json();json.setIgnoreUnknownFields(true);
        JsonValue entity=new JsonReader().parse(root.child("scenes/scene1.json")).get("entities").get("6").get("components");
        JsonValue data=entity.get("TiledLayerComponent");SpatialBlockData block=null;
        for(JsonValue b:entity.get("SpatialBlocksComponent").get("blocks"))if(b.getInt("id")==29)block=json.readValue(SpatialBlockData.class,b);
        assertNotNull(block);assertEquals(3,block.linkedTileRefs.size);
        TiledMapLayerData map=new TiledMapLayerData(64,64,data.getInt("tileWidth"),data.getInt("tileHeight"),8,TiledProjection.ISO);
        map.originX=data.getFloat("originX",0);map.originY=data.getFloat("originY",0);map.defaultTileAltitude=data.getFloat("defaultTileAltitude",0);
        RuntimeTilesetProfile profile=new RuntimeTilesetProfile();profile.tileWidth=256;profile.tileHeight=512;
        profile.referenceCellWidth=256;profile.referenceCellHeight=128;profile.projection=TiledProjection.ISO;profile.anchor=RuntimeTilesetAnchor.BOTTOM_CENTER;
        float[] q=new float[8];TileQuadTransforms.buildSpriteQuad(map,23,34,256,512,profile,(byte)0,q);
        // Isolate the first indexed contribution; the authored scene is never modified.
        block.linkedTileRefs.clear();block.addLinkedTileRef(23,34,2054);
        LightingSurface tile=tileCaster(map,block,23,34,2054,q);
        float[] lo=new float[3],hi=new float[3];LightingCoordinates.mapPoint(map,block.x,block.y,block.altitude,lo,0);
        LightingCoordinates.mapPoint(map,block.x+block.width,block.y+block.depth,block.altitude,hi,0);
        ActorVolumeData v=new ActorVolumeData();v.width=hi[0]-lo[0];v.depth=hi[1]-lo[1];v.height=block.height;
        ActorVolumeCaster actor=new ActorVolumeCaster();actor.update(v,(lo[0]+hi[0])*.5f,(lo[1]+hi[1])*.5f,lo[2],q);
        assertEquivalent(tile.vertices,actor.vertices,actor.vertexCount);
        System.out.println("SCAFFOLD_PARITY block=29 cell=23,34 asset=2054 width="+v.width+" depth="+v.depth+" height="+v.height);
    }
    static void assertEquivalent(float[] tile,float[] actor,int count){
        // Every published vertex must lie on the other mesh; different fan diagonals are allowed.
        checkVertices(tile,tile.length/8,actor,count);checkVertices(actor,count,tile,tile.length/8);
    }
    private static void checkVertices(float[] a,int count,float[] b,int otherCount){
        for(int o=0;o<count*8;o+=8){boolean found=false;
            for(int k=0;k<otherCount*8;k+=24){
                float ux=b[k+8]-b[k],uy=b[k+9]-b[k+1],uz=b[k+10]-b[k+2];
                float vx=b[k+16]-b[k],vy=b[k+17]-b[k+1],vz=b[k+18]-b[k+2];
                float px=a[o]-b[k],py=a[o+1]-b[k+1],pz=a[o+2]-b[k+2];
                float uu=ux*ux+uy*uy+uz*uz,vv=vx*vx+vy*vy+vz*vz,uv=ux*vx+uy*vy+uz*vz;
                float pu=px*ux+py*uy+pz*uz,pv=px*vx+py*vy+pz*vz,d=uu*vv-uv*uv;if(Math.abs(d)<1e-8)continue;
                float s=(pu*vv-pv*uv)/d,t=(pv*uu-pu*uv)/d;
                if(s<-.001||t<-.001||s+t>1.001)continue;
                if(Math.abs(px-s*ux-t*vx)+Math.abs(py-s*uy-t*vy)+Math.abs(pz-s*uz-t*vz)>.01)continue;
                assertEquals(a[o+3],b[k+3]+s*(b[k+11]-b[k+3])+t*(b[k+19]-b[k+3]),.0001);
                assertEquals(a[o+4],b[k+4]+s*(b[k+12]-b[k+4])+t*(b[k+20]-b[k+4]),.0001);found=true;break;
            }
            assertTrue("Vertex outside equivalent support: "+a[o]+","+a[o+1]+","+a[o+2],found);
        }
    }
}
