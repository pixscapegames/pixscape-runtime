package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Read-only integration with the existing POC copy; never opens a GL context or rewrites a scene. */
public class SilhouetteDemoPreparationTest {
    @Test public void prepareActualDemoAssociationsInBothOrders() throws Exception {
        File root=new File("../shadow-volume-silhouette-demo/studio");
        Assume.assumeTrue("Existing local demo only",new File(root,"scenes/scene1.json").isFile());
        Json json=new Json();json.setIgnoreUnknownFields(true);
        JsonValue scene=new JsonReader().parse(new FileHandle(new File(root,"scenes/scene1.json")));
        IntMap<JsonValue> assets=new IntMap<JsonValue>();
        for(JsonValue asset:new JsonReader().parse(new FileHandle(new File(root,"assets.json"))).get("assets"))assets.put(asset.getInt("id"),asset);
        int total=0,mismatches=0,rounded=0,uncovered=0,uncast=0;
        IntSet illustrated=new IntSet();
        for(JsonValue entity:scene.get("entities")){
            JsonValue components=entity.get("components"),tiles=components.get("TiledLayerComponent"),spatial=components.get("SpatialBlocksComponent");
            if(tiles==null||spatial==null||spatial.get("blocks").size==0)continue;
            TiledMapLayerData map=new TiledMapLayerData(tiles.getInt("mapWidthCells"),tiles.getInt("mapHeightCells"),tiles.getInt("tileWidth"),tiles.getInt("tileHeight"),tiles.getInt("chunkSize"),TiledProjection.ISO);
            map.originX=tiles.getFloat("originX",0);map.originY=tiles.getFloat("originY",0);map.defaultTileAltitude=tiles.getFloat("defaultTileAltitude",0);
            int n=tiles.get("tileAssetIds").getInt("size");
            int[] xs=tiles.get("tileXs").get("items").asIntArray(),ys=tiles.get("tileYs").get("items").asIntArray(),ids=tiles.get("tileAssetIds").get("items").asIntArray();
            for(int i=0;i<n;i++)map.setTile(xs[i],ys[i],ids[i]);
            SpatialBlocksComponent blocks=new SpatialBlocksComponent();
            for(JsonValue value:spatial.get("blocks")){
                SpatialBlockData block=json.readValue(SpatialBlockData.class,value);
                blocks.blocks.add(block);total++;
                for(SpatialBlockData.LinkedTileRef ref:block.linkedTileRefs)if(map.getTile(ref.gx,ref.gy)!=ref.tileAssetId)mismatches++;
            }
            Array<TileSurfaceDescription> descriptions=new Array<TileSurfaceDescription>();
            if(tiles.has("lightingDescriptions"))for(JsonValue d:tiles.get("lightingDescriptions"))descriptions.add(json.readValue(TileSurfaceDescription.class,d));
            final IntMap<float[]> dimensions=new IntMap<float[]>();
            final IntMap<RuntimeTilesetProfile> profiles=new IntMap<RuntimeTilesetProfile>();
            for(int id:ids){if(id<=0||profiles.containsKey(id))continue;JsonValue asset=assets.get(id),parent=assets.get(asset.getInt("tilesetId"));
                BufferedImage image=ImageIO.read(new File(root,asset.getString("sourceRelPath")));
                dimensions.put(id,new float[]{image.getWidth(),image.getHeight()});
                RuntimeTilesetProfile p=new RuntimeTilesetProfile();p.tileWidth=parent.getInt("tileWidth");p.tileHeight=parent.getInt("tileHeight");
                p.referenceCellWidth=parent.getInt("referenceCellWidth");p.referenceCellHeight=parent.getInt("referenceCellHeight");
                p.projection=TiledProjection.ISO;p.anchor=RuntimeTilesetAnchor.valueOf(parent.getString("anchor").toUpperCase(java.util.Locale.ROOT).replace('-','_'));
                p.offsetX=parent.getInt("offsetX",0);p.offsetY=parent.getInt("offsetY",0);profiles.put(id,p);
            }
            MapLightingGeometry.NativeQuads quads=new MapLightingGeometry.NativeQuads(){
                public boolean resolve(TiledMapLayerData m,TileChunk c,int cell,float[] quad,boolean required){
                    int id=c.assetIds[cell];float[] size=dimensions.get(id);
                    TileQuadTransforms.buildSpriteQuad(m,c.chunkX*m.chunkSize+cell%c.chunkWidth,c.chunkY*m.chunkSize+cell/c.chunkWidth,
                            (int)size[0],(int)size[1],profiles.get(id),c.transformFlags[cell],quad);return true;
                }
            };
            MapLightingGeometry geometry=new MapLightingGeometry();geometry.descriptions(descriptions);
            Assert.assertTrue(geometry.ensure(map,blocks,quads,"read-only-demo",1));
            LongMap<Boolean> seen=new LongMap<Boolean>();
            for(SpatialBlockData block:blocks.blocks)for(SpatialBlockData.LinkedTileRef ref:block.linkedTileRefs){
                long key=((long)ref.gx<<32)^(ref.gy&0xffffffffL);if(seen.containsKey(key))continue;seen.put(key,true);
                int id=map.getTile(ref.gx,ref.gy);JsonValue asset=assets.get(id);BufferedImage image=ImageIO.read(new File(root,asset.getString("sourceRelPath")));
                float[] q=new float[8];TileQuadTransforms.buildSpriteQuad(map,ref.gx,ref.gy,image.getWidth(),image.getHeight(),profiles.get(id),(byte)0,q);
                LightingSurface s=geometry.receiver(map,ref.gx,ref.gy,id,(byte)0,q);int missing=0,missingCaster=0,firstX=-1,firstY=-1;
                BufferedImage diagnostic=new BufferedImage(image.getWidth()*2,image.getHeight()+24,BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D graphics=diagnostic.createGraphics();graphics.setColor(java.awt.Color.GRAY);graphics.fillRect(0,0,diagnostic.getWidth(),diagnostic.getHeight());
                graphics.drawImage(image,0,24,null);graphics.drawImage(image,image.getWidth(),24,null);graphics.setColor(java.awt.Color.WHITE);
                graphics.drawString("Cell "+ref.gx+","+ref.gy+" asset "+id+" | red = no physical receiver",4,16);graphics.dispose();
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                    if((image.getRGB(x,y)>>>24)<128)continue;
                    float u=(x+.5f)/image.getWidth(),v=1-(y+.5f)/image.getHeight();
                    if(!covered(s.vertices,u,v)){missing++;diagnostic.setRGB(x+image.getWidth(),y+24,0xff2020);if(firstX<0){firstX=x;firstY=y;}}
                    boolean casterCoverage=false;
                    if(s.casterContributions!=null){for(LightingSurface c:s.casterContributions)if(covered(c.vertices,u,v)){casterCoverage=true;break;}}
                    else if(s.casterSurface!=null)casterCoverage=covered(s.casterSurface.vertices,u,v);
                    if(!casterCoverage)missingCaster++;
                }
                if(missing>0)System.out.println("OPAQUE_WITHOUT_PHYSICAL_RECEIVER cell="+ref.gx+","+ref.gy+" asset="+id+" pixels="+missing+" casterMissing="+missingCaster+" firstPixel="+firstX+","+firstY);
                if((id==1885||id==1978||id==2027||id==2026)&&illustrated.add(id)){
                    File output=new File("build/silhouette-validation/coverage-"+id+".png");output.getParentFile().mkdirs();ImageIO.write(diagnostic,"png",output);
                }
                uncovered+=missing;uncast+=missingCaster;
            }
            blocks.blocks.reverse();blocks.revision++;
            Assert.assertTrue(geometry.ensure(map,blocks,quads,"read-only-demo",1));
            for(int i=0;i<n;i++)if(geometry.roundedSupport(xs[i],ys[i])!=null)rounded++;
            for(int i=0;i<100;i++)Assert.assertFalse(geometry.ensure(map,blocks,quads,"read-only-demo",1));
        }
        Assert.assertEquals(28,total);Assert.assertEquals(22,mismatches);Assert.assertEquals(2,rounded);
        System.out.println("DEMO_PREPARED blocks="+total+" staleAssetSnapshots="+mismatches+" corners="+rounded);
        System.out.println("DEMO_OPAQUE_WITHOUT_PHYSICAL_RECEIVER pixels="+uncovered);
        System.out.println("DEMO_OPAQUE_WITHOUT_CASTER_PROJECTION pixels="+uncast);
    }
    private static boolean covered(float[] p,float u,float v){
        for(int i=0;i<p.length;i+=24){
            if(p[i+5]==0&&p[i+6]==0&&p[i+7]==0)continue;
            float ax=p[i+11]-p[i+3],ay=p[i+12]-p[i+4],bx=p[i+19]-p[i+3],by=p[i+20]-p[i+4];
            float det=ax*by-ay*bx;if(Math.abs(det)<1e-10f)continue;
            float x=u-p[i+3],y=v-p[i+4],a=(x*by-y*bx)/det,b=(ax*y-ay*x)/det;
            if(a>=-1e-6f&&b>=-1e-6f&&a+b<=1+1e-6f)return true;
        }return false;
    }
}
