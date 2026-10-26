package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.*;

/** Geometry-only rollback witness. Retains the previous wall ownership, not a claim of physical correctness. */
public class RoundedJunctionDemoTest {
    @Test public void auditOpaqueReceiversAtBothAuthoredJoins() throws Exception {
        File root = new File("../shadow-volume-silhouette-demo/studio");
        org.junit.Assume.assumeTrue(new File(root,"scenes/scene1.json").isFile());
        Json json=new Json(); json.setIgnoreUnknownFields(true);
        JsonValue scene=new JsonReader().parse(new FileHandle(new File(root,"scenes/scene1.json")));
        IntMap<JsonValue> assets=new IntMap<>();
        for(JsonValue a:new JsonReader().parse(new FileHandle(new File(root,"assets.json"))).get("assets")) assets.put(a.getInt("id"),a);
        int occurrences=0;
        for(JsonValue entity:scene.get("entities")) {
            JsonValue components=entity.get("components"), t=components.get("TiledLayerComponent");
            if(t==null || !t.has("lightingDescriptions")) continue;
            TiledMapLayerData map=new TiledMapLayerData(t.getInt("mapWidthCells"),t.getInt("mapHeightCells"),t.getInt("tileWidth"),t.getInt("tileHeight"),t.getInt("chunkSize"),TiledProjection.ISO);
            map.originX=t.getFloat("originX",0);map.originY=t.getFloat("originY",0);map.defaultTileAltitude=t.getFloat("defaultTileAltitude",0);
            int[] xs=t.get("tileXs").get("items").asIntArray(),ys=t.get("tileYs").get("items").asIntArray(),ids=t.get("tileAssetIds").get("items").asIntArray();
            for(int i=0;i<t.get("tileAssetIds").getInt("size");i++) map.setTile(xs[i],ys[i],ids[i]);
            SpatialBlocksComponent blocks=new SpatialBlocksComponent();
            for(JsonValue b:components.get("SpatialBlocksComponent").get("blocks")) blocks.blocks.add(json.readValue(SpatialBlockData.class,b));
            Array<TileSurfaceDescription> descriptions=new Array<>();
            for(JsonValue d:t.get("lightingDescriptions")) descriptions.add(json.readValue(TileSurfaceDescription.class,d));
            MapLightingGeometry geometry=new MapLightingGeometry();geometry.descriptions(descriptions);geometry.ensure(map,blocks);
            for(int index=0;index<t.get("tileAssetIds").getInt("size");index++) {
                TileSurfaceDescription description=null;
                for(TileSurfaceDescription d:descriptions) if(d.assetId==ids[index]&&d.rounded!=null) description=d;
                if(description==null) continue;
                int gx=xs[index],gy=ys[index]; Array<SpatialBlockData> owners=new Array<>();
                for(SpatialBlockData b:blocks.blocks) for(SpatialBlockData.LinkedTileRef r:b.linkedTileRefs)
                    if(r.gx==gx&&r.gy==gy) {owners.add(b);break;}
                RoundedWallPlacement placement=new RoundedWallPlacement(map,gx,gy,description.rounded,owners);
                float[] cornerQuad=quad(root,assets,map,gx,gy);
                LightingSurface corner=geometry.receiver(map,gx,gy,ids[index],(byte)0,cornerQuad);
                BufferedImage cornerImage=ImageIO.read(new File(root,assets.get(ids[index]).getString("sourceRelPath")));
                occurrences++;
                System.out.println("CORNER cell="+gx+","+gy+" center="+placement.cx+","+placement.cy+" base="+placement.base+" height="+placement.height+" xWall="+placement.xWall+" yWall="+placement.yWall);
                for(SpatialBlockData block:owners) for(SpatialBlockData.LinkedTileRef r:block.linkedTileRefs) {
                    if(r.gx==gx&&r.gy==gy) continue;
                    int asset=map.getTile(r.gx,r.gy);float[] q=quad(root,assets,map,r.gx,r.gy);
                    LightingSurface s=geometry.receiver(map,r.gx,r.gy,asset,(byte)0,q);
                    BufferedImage image=ImageIO.read(new File(root,assets.get(asset).getString("sourceRelPath")));
                    int axis=block.id==placement.xWall?0:1;float edge=axis==0?placement.cx:placement.cy;
                    int wrong=0;String first="";boolean witnessed=false;
                    for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                        if((image.getRGB(x,y)>>>24)<128)continue;
                        float u=(x+.5f)/image.getWidth(),v=1-(y+.5f)/image.getHeight();
                        for(int o=0;o<s.vertices.length;o+=24) {
                            if(s.receiverBlockIds[o/24]!=block.id)continue;
                            float[] point=at(s.vertices,o,u,v);if(point==null)continue;
                            if(point[axis]<edge-.001f) {
                                wrong++;if(first.isEmpty())first=" pixel="+x+","+y+" receiverTriangle="+(o/24)+" world="+java.util.Arrays.toString(point);
                                if(!witnessed && x%4==0 && y%4==0) {
                                    for(int angle=0;angle<360&&!witnessed;angle+=15) for(float height:new float[]{40,140,240}) {
                                        float[] light={placement.cx+240*(float)Math.cos(Math.toRadians(angle)),placement.cy+240*(float)Math.sin(Math.toRadians(angle)),height};
                                        float dot=0;for(int k=0;k<3;k++)dot+=(light[k]-point[k])*s.vertices[o+5+k];
                                        if(dot<=0)continue;
                                        for(int c=0;c<corner.casterContributions.size&&!witnessed;c++) {
                                            float[] data=corner.casterContributions.get(c).vertices;
                                            for(int offset=0;offset<data.length&&!witnessed;offset+=24) {
                                                float[] hit=hit(data,offset,point,light);if(hit==null)continue;
                                                int px=Math.min(cornerImage.getWidth()-1,Math.max(0,(int)(hit[3]*cornerImage.getWidth())));
                                                int py=Math.min(cornerImage.getHeight()-1,Math.max(0,(int)((1-hit[4])*cornerImage.getHeight())));
                                                if((cornerImage.getRGB(px,py)>>>24)<128)continue;
                                                witnessed=true;
                                                System.out.println("RAY_WITNESS receiverBlock="+block.id+" receiverCell="+r.gx+","+r.gy+" pixel="+x+","+y+" triangle="+o/24+" world="+java.util.Arrays.toString(point)+" light="+java.util.Arrays.toString(light)+" casterCorner="+gx+","+gy+" contribution="+c+" triangle="+offset/24+" hitXYZUV="+java.util.Arrays.toString(hit));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    System.out.println("WALL_JOIN cell="+r.gx+","+r.gy+" block="+block.id+" opaquePixelsInRemovedPortion="+wrong+first);
                    // Diagnostic count, not a validity criterion: the rejected reassignment made it zero.
                    if((r.gx==gx+1&&r.gy==gy)||(r.gx==gx&&r.gy==gy+1)){
                        int px=axis==0?99:156;boolean matched=false;
                        for(int o=0;o<s.vertices.length;o+=24){
                            float[] point=at(s.vertices,o,(px+.5f)/image.getWidth(),1-339.5f/image.getHeight());
                            if(point==null)continue;
                            assertEquals("Rollback retains this tile's wall ownership",block.id,s.receiverBlockIds[o/24]);
                            assertEquals(placement.base+placement.height,point[2],.001f);matched=true;
                        }
                        assertTrue("The replacement must retain the opaque witness",matched);
                        if(axis==0){
                            // Frozen Preview witness (915,590), translated to each occurrence.
                            float sx=placement.cx-placement.cy+127.075f;
                            float sy=(placement.cx+placement.cy)*.5f-11.325f;
                            float u=(sx-q[0])/(q[6]-q[0]),v=(sy-q[1])/(q[3]-q[1]);
                            boolean found=false;
                            for(int o=0;o<s.vertices.length;o+=24){
                                float[] point=at(s.vertices,o,u,v);if(point==null)continue;
                                assertEquals(block.id,s.receiverBlockIds[o/24]);
                                assertEquals("Previous frozen witness triangle",1,o/24);
                                assertEquals(placement.cx-.925f,point[0],.001f);
                                assertEquals(placement.cy-128,point[1],.001f);
                                assertEquals(53.13755f,point[2],.001f);found=true;
                                float[] light={placement.cx-320,placement.cy,40};
                                float[] crossing=hit(corner.casterContributions.first().vertices,4*24,point,light);
                                assertNotNull("Previously foreign curve facade intersects the witness ray",crossing);
                                int ix=(int)(crossing[3]*cornerImage.getWidth()),iy=(int)((1-crossing[4])*cornerImage.getHeight());
                                assertEquals("The responsible caster texel is opaque",255,cornerImage.getRGB(ix,iy)>>>24);
                                System.out.println("FROZEN_WITNESS corner="+gx+","+gy+" receiverTriangle="+o/24+" XYZ="+java.util.Arrays.toString(point)+" wall="+s.receiverBlockIds[o/24]+" casterTriangle=4 hitXYZUV="+java.util.Arrays.toString(crossing));
                            }
                            assertTrue(found);
                        }
                    }
                }
                assertTrue(corner.roundedIdentity);
            }
        }
        assertEquals(2,occurrences);
    }
    static float[] hit(float[] p,int o,float[] origin,float[] light) {
        double[] a=new double[3],b=new double[3],d=new double[3],s=new double[3];
        for(int k=0;k<3;k++){a[k]=p[o+8+k]-p[o+k];b[k]=p[o+16+k]-p[o+k];d[k]=light[k]-origin[k];s[k]=origin[k]-p[o+k];}
        double[] h=cross(d,b),q=cross(s,a);double det=dot(a,h);if(Math.abs(det)<1e-9)return null;
        double u=dot(s,h)/det,v=dot(d,q)/det,t=dot(b,q)/det;
        if(u<0||v<0||u+v>1||t<.00001||t>=1)return null;
        float[] result=new float[5];for(int k=0;k<3;k++)result[k]=(float)(origin[k]+t*d[k]);
        for(int k=3;k<5;k++)result[k]=(float)(p[o+k]+u*(p[o+8+k]-p[o+k])+v*(p[o+16+k]-p[o+k]));return result;
    }
    static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static float[] at(float[] p,int o,float u,float v) {
        float ax=p[o+11]-p[o+3],ay=p[o+12]-p[o+4],bx=p[o+19]-p[o+3],by=p[o+20]-p[o+4],det=ax*by-ay*bx;
        if(Math.abs(det)<1e-10f)return null;
        float dx=u-p[o+3],dy=v-p[o+4],a=(dx*by-dy*bx)/det,b=(ax*dy-ay*dx)/det;
        if(a<0||b<0||a+b>1)return null;
        return new float[]{p[o]+a*(p[o+8]-p[o])+b*(p[o+16]-p[o]),p[o+1]+a*(p[o+9]-p[o+1])+b*(p[o+17]-p[o+1]),p[o+2]+a*(p[o+10]-p[o+2])+b*(p[o+18]-p[o+2])};
    }
    private static float[] quad(File root,IntMap<JsonValue> assets,TiledMapLayerData map,int gx,int gy)throws Exception {
        JsonValue asset=assets.get(map.getTile(gx,gy)),parent=assets.get(asset.getInt("tilesetId"));
        BufferedImage image=ImageIO.read(new File(root,asset.getString("sourceRelPath")));
        RuntimeTilesetProfile p=new RuntimeTilesetProfile();p.tileWidth=parent.getInt("tileWidth");p.tileHeight=parent.getInt("tileHeight");
        p.referenceCellWidth=parent.getInt("referenceCellWidth");p.referenceCellHeight=parent.getInt("referenceCellHeight");p.projection=TiledProjection.ISO;
        p.anchor=RuntimeTilesetAnchor.valueOf(parent.getString("anchor").toUpperCase(java.util.Locale.ROOT).replace('-','_'));
        p.offsetX=parent.getInt("offsetX",0);p.offsetY=parent.getInt("offsetY",0);
        float[] q=new float[8];TileQuadTransforms.buildSpriteQuad(map,gx,gy,image.getWidth(),image.getHeight(),p,(byte)0,q);return q;
    }
}
