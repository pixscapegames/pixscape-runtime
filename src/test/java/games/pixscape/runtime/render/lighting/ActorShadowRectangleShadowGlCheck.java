package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.MultiTextureMeshBatch;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.system.LightingGeometrySystem;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;

/** Opt-in bounded GL comparison. Procedural articulated alpha fixture, never user scene data.
 * Timings exclude screenshot readback, presentation/VSync and cold resource preparation.
 */
public final class ActorShadowRectangleShadowGlCheck extends ApplicationAdapter {
    private static final int FRAMES=48;
    private static final float[][] PLANES={{-150,-120,0,-150,120,0,150,120,0,150,-120,0},{-150,90,0,-150,90,135,150,90,135,150,90,0}};
    private final LightingSurface[] planeSurfaces=new LightingSurface[2];
    private final float[][] planeQuads=new float[2][];
    private FileHandle output;
    private Texture[] textures=new Texture[2];private Pixmap[] pixels=new Pixmap[2];
    private World world;private RenderSubmitSystem submit;private LightingGeometrySystem geometry;
    private MetricsBatch batch;private AtlasRuntimeService.TextureArrayBundle bundle;
    private FrameRenderQueue queue;private DynamicEntityRenderState dynamic;private RenderStats stats;
    private int condition,frame,count;private boolean forward;
    private int[] entities=new int[8];
    private final LightingFrame.Light light=new LightingFrame.Light();
    private final float[] quad=new float[8];private final long[] times=new long[FRAMES];
    private long drawSum,uploadSum,prepSum;private long deadline;private Throwable failure;
    public static void main(String[] args){
        ActorShadowRectangleShadowGlCheck app=new ActorShadowRectangleShadowGlCheck();app.output=new FileHandle(args[0]);app.output.mkdirs();
        Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setInitialVisible(false);c.disableAudio(true);
        c.setWindowedMode(640,480);c.useVsync(true);c.setForegroundFPS(30);
        c.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(app,c);if(app.failure!=null)throw new AssertionError(app.failure);
    }
    @Override public void create(){try{
        deadline=System.nanoTime()+55_000_000_000L;ShaderRegistry.initDefaults();InternalTextures.initIfNeeded();
        for(int i=0;i<2;i++){pixels[i]=person(i);textures[i]=new Texture(pixels[i]);}
        output.child("measurements.csv").writeString("renderer,geometry,actors,samples,renderCpuMedianMs,renderCpuP95Ms,publicationCpuMeanMs,drawsMean,uploadBytesMean\n",false);
        System.out.println("READY pid="+ProcessHandle.current().pid()+" runtime="+ActorSpatialRectangleData.class.getProtectionDomain().getCodeSource().getLocation());
        setup();
    }catch(Throwable t){fail(t);}}
    private void setup(){
        forward=condition>=2;count=condition%2==0?1:2;frame=0;drawSum=uploadSum=prepSum=0;
        dynamic=new DynamicEntityRenderState(16);queue=new FrameRenderQueue(32);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
        OrthographicCamera camera=new OrthographicCamera(320,240);camera.position.set(0,55,0);camera.update();
        bundle=forward?AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(textures),64,128,GLCaps.detect()):null;
        batch=forward?new TextureArrayMeshBatch(512):new MultiTextureMeshBatch(512);
        if(forward)((TextureArrayMeshBatch)batch).setTextureArrayBundle(bundle);
        stats=new RenderStats();geometry=new LightingGeometrySystem(dynamic,new TiledMapRenderState(4),queue,layers);
        submit=new RenderSubmitSystem(layers,queue,camera,.22f,.22f,.22f,batch,stats,new RenderStatsSink(100),forward);
        world=new World(new WorldConfigurationBuilder().with(geometry,submit).build());geometry.setEnabled(false);submit.setEnabled(false);
        PointLightComponent source=world.getMapper(PointLightComponent.class).create(world.create());source.shadowQuality=1;source.shadowResolution=256;source.radius=500;
        for(int i=0;i<count;i++){
            int e=entities[i]=world.create(),slot=dynamic.acquireSlotForEntity(e);dynamic.enabled[slot]=true;dynamic.visible[slot]=true;
            dynamic.blend[slot]=BlendMode.ALPHA.id;dynamic.u2[slot]=dynamic.v2[slot]=1;
            TransformComponent t=world.getMapper(TransformComponent.class).create(e);t.x=(i%4-1.5f)*(count==1?0:55);t.y=i/4*-35;
            SpatialPhysicsFootprintComponent foot=world.getMapper(SpatialPhysicsFootprintComponent.class).create(e);foot.valid=true;
            SpatialHeightComponent h=world.getMapper(SpatialHeightComponent.class).create(e);h.rectangles=new ActorSpatialRectangles();
            // Fixed local rectangle; the light changes only its shadow orientation.
            ActorSpatialRectangleData v=new ActorSpatialRectangleData();v.x=-32;v.width=64;v.height=128;
            if(i%2==0){world.getMapper(AssetRefComponent.class).create(e).assetId=7;
                AnimationComponent a=world.getMapper(AnimationComponent.class).create(e);a.currentClip="walk";
                h.rectangles.put(7,"walk",v);v=v.copy();v.width=72;h.rectangles.put(7,"idle",v);
            }else h.rectangles.staticRectangle=v;
            world.getMapper(SurfaceLightingComponent.class).create(e).shadowCaster=true;
            setQuad(i,0);
        }
        world.process();geometry.prepareRuntimeAvailability();submit.prepareComposition();submit.setEnabled(true);
        light.radius=500;light.intensity=1;light.falloff=1;light.r=light.g=light.b=1;light.resolution=256;light.quality=1;
    }
    private void setQuad(int index,float altitude){
        int e=entities[index],s=dynamic.entityIdToRenderSlot[e];TransformComponent t=world.getMapper(TransformComponent.class).get(e);
        float x=t.x,y=t.y+altitude;
        dynamic.x1[s]=dynamic.x2[s]=x-32;dynamic.x3[s]=dynamic.x4[s]=x+32;
        dynamic.y1[s]=dynamic.y4[s]=y;dynamic.y2[s]=dynamic.y3[s]=y+128;
        dynamic.u1[s]=frame%12<6?0:1;dynamic.u2[s]=frame%12<6?1:0;
        dynamic.textureHandle[s]=TextureRegistry.handleOf(textures[index%2==0?(frame/6)%2:0]);
    }
    @Override public void render(){if(failure!=null)return;try{
        if(System.nanoTime()>deadline)throw new AssertionError("Internal deadline exceeded");
        float angle=(frame%12)*30;light.x=130*(float)Math.cos(Math.toRadians(angle));light.y=130*(float)Math.sin(Math.toRadians(angle));light.z=frame%24<12?100:190;
        // Sequential close, on-plane, vertical and below-plane sources. No projector fallback.
        if(frame>=36){float[] y={-20,-2,-.01f,0,.01f,2,20,0,0,0,0,0};
            float[] z={64,64,64,64,64,64,64,160,128,1,0,-20};
            light.x=0;light.y=y[frame-36];light.z=z[frame-36];}
        for(int i=0;i<count;i++){
            int e=entities[i];SpatialHeightComponent h=world.getMapper(SpatialHeightComponent.class).get(e);
            // The projected actor origin rises together with physical altitude.
            float altitude=frame>=24?30:0;h.altitude=altitude;
            TransformComponent t=world.getMapper(TransformComponent.class).get(e);t.y=i/4*-35+altitude;
            AnimationComponent a=world.getMapper(AnimationComponent.class).getSafe(e,null);
            if(a!=null){a.currentClip=frame%24<12?"walk":"idle";a.frame=(frame/6)%2;}
            ActorSpatialRectangleData v=h.rectangles.get(a==null?0:7,a==null?null:a.currentClip);
            v.pivotX=frame/12*2;setQuad(i,0);
        }
        geometry.setEnabled(true);geometry.process();geometry.setEnabled(false);
        queue.lighting.lights.add(light);
        queue.size=0;queue.lighting.tiles.clear();
        int shader=ShaderRegistry.indexOf((forward?ShaderMode.TEXTURE_ARRAY:ShaderMode.MULTI_TEXTURE).defaultShaderName());
        addPlane(shader,0,PLANES[0],0,0,1);
        addPlane(shader,1,PLANES[1],0,-1,0);
        for(int i=0;i<count;i++){int e=entities[i],s=dynamic.entityIdToRenderSlot[e];copyQuad(s,quad);
            queue.addQuad(dynamic.textureHandle[s],shader,BlendMode.ALPHA.id,0,0,0,0,quad[0],quad[1],quad[2],quad[3],quad[4],quad[5],quad[6],quad[7],0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_ECS,s,e);
        }
        for(int i=0;i<count;i++){
            LightingSurface surface=queue.lighting.entities.get(entities[i]);
            Assert.assertEquals(6,surface.shadowVertexCount);
            for(float v:surface.shadowVertices)Assert.assertTrue(Float.isFinite(v));
        }
        submit.lightingRenderer().prepareFrame(queue.lighting);stats.reset();
        Gdx.gl.glClearColor(.08f,.08f,.1f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        long start=System.nanoTime();world.process();times[frame]=System.nanoTime()-start;
        if(frame>=24){drawSum+=stats.drawCalls;uploadSum+=stats.bufferUploadBytes;prepSum+=queue.lighting.preparationCpuNs;}
        Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        if(count==1&&frame%3==0||count==8&&frame==9){Pixmap p=Pixmap.createFromFrameBuffer(0,0,640,480);try{PixmapIO.writePNG(output.child(label()+(count==1?"":"-actors-8")+"-frame-"+frame+".png"),p,-1,true);}finally{p.dispose();}}
        if(count==1&&frame==3)verifyReceiverOracle();
        if(count==1&&frame==3)verifyPeerCaster();
        frame++;if(frame==FRAMES){
            long[] measured=java.util.Arrays.copyOfRange(times,24,48);java.util.Arrays.sort(measured);
            String row=(forward?"forward":"legacy")+","+"rectangle-alpha"+","+count+",24,"+measured[12]/1e6+","+measured[22]/1e6+","+Double.toString(prepSum/24e6)+","+drawSum/24.0+","+uploadSum/24.0+"\n";
            output.child("measurements.csv").writeString(row,true);System.out.print(row);close();condition++;
            if(condition==4){output.child("DONE").writeString("PASS",false);Gdx.app.exit();}else setup();
        }
    }catch(Throwable t){fail(t);}}
    private String label(){return (forward?"forward":"legacy")+"-"+"rectangle";}
    private static int pixelDifference(Pixmap a,Pixmap b){
        int count=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getPixel(x,y)!=b.getPixel(x,y))count++;return count;
    }
    /** Same image and volume as the first instance, translated between it and the light. */
    private void verifyPeerCaster(){
        LightingFrame.Caster self=queue.lighting.casters.first(),peer=new LightingFrame.Caster();
        float[] vertices=java.util.Arrays.copyOf(self.surface.shadowVertices,self.surface.shadowVertexCount*8);
        for(int i=0;i<vertices.length;i+=8){vertices[i]+=light.x*.5f;vertices[i+1]+=light.y*.5f;vertices[i+2]+=(light.z-64)*.5f;}
        peer.surface=new LightingSurface(vertices);peer.surface.actor=true;peer.surface.shadowCaster=true;peer.surface.alphaThreshold=self.surface.alphaThreshold;
        peer.texture=self.texture;peer.u1=self.u1;peer.v1=self.v1;peer.u2=self.u2;peer.v2=self.v2;peer.bounds();
        float floor=queue.colorPacked[0],wall=queue.colorPacked[1];queue.colorPacked[0]=queue.colorPacked[1]=Color.BLACK.toFloatBits();
        Pixmap absent=null,asActor=null,asDecor=null;
        try{
            absent=readOracle();queue.lighting.casters.add(peer);asActor=readOracle();
            peer.surface.actor=false;asDecor=readOracle();
            System.out.println("PEER_CASTER renderer="+(forward?"forward":"legacy")+" actorCategoryChanged="+pixelDifference(absent,asActor)+" decorCategoryChanged="+pixelDifference(absent,asDecor));
            Assert.assertTrue("Distinct actor casts on actor",pixelDifference(absent,asActor)>0);
            Assert.assertEquals("Category must not change exclusion policy",0,pixelDifference(asActor,asDecor));
            PixmapIO.writePNG(output.child(label()+"-peer-as-actor.png"),asActor,-1,true);
            PixmapIO.writePNG(output.child(label()+"-peer-as-decor.png"),asDecor,-1,true);
        }finally{
            queue.lighting.casters.removeValue(peer,true);queue.colorPacked[0]=floor;queue.colorPacked[1]=wall;
            if(absent!=null)absent.dispose();if(asActor!=null)asActor.dispose();if(asDecor!=null)asDecor.dispose();
        }
    }
    /** Test-only external opaque screen between light and actor. It is not part of the timed scene. */
    private void verifyReceiverOracle(){
        LightingSurface actor=queue.lighting.entities.get(entities[0]);
        float[] vertices=actor.vertices.clone();
        for(int o=0;o<vertices.length;o+=8){vertices[o]=(vertices[o]+light.x)*.5f;vertices[o+1]=(vertices[o+1]+light.y)*.5f;vertices[o+2]=(vertices[o+2]+light.z)*.5f;}
        LightingFrame.Caster exterior=new LightingFrame.Caster();exterior.surface=new LightingSurface(vertices);
        exterior.surface.shadowCaster=true;exterior.texture=TextureRegistry.WHITE_HANDLE;exterior.u2=exterior.v2=1;exterior.bounds();
        LightingFrame.Caster self=queue.lighting.casters.first();
        // ALPHA (including packed 254/255 alpha) blends the floor behind the sprite.
        // Freeze that background, otherwise removing self legitimately changes its ground shadow.
        float floorColor=queue.colorPacked[0],wallColor=queue.colorPacked[1];
        queue.colorPacked[0]=queue.colorPacked[1]=Color.BLACK.toFloatBits();
        Pixmap unblocked=readOracle(),withExterior=null,withoutSelf=null;
        try {
            queue.lighting.casters.add(exterior);withExterior=readOracle();
            queue.lighting.casters.removeValue(self,true);withoutSelf=readOracle();
            int changed=0,checked=0,different=0,maxDelta=0;
            PixmapIO.writePNG(output.child(label()+"-exterior-oracle.png"),withExterior,-1,true);
            PixmapIO.writePNG(output.child(label()+"-without-self-oracle.png"),withoutSelf,-1,true);
            Pixmap alpha=pixels[(frame/6)%2];copyQuad(dynamic.entityIdToRenderSlot[entities[0]],quad);
            for(int y=2;y<126;y+=3)for(int x=2;x<62;x+=3){
                if((alpha.getPixel(x,y)&255)<250)continue;
                float u=(x+.5f)/64,v=1-(y+.5f)/128;
                int px=(int)((quad[0]+u*(quad[6]-quad[0])+160)*2);
                int py=(int)((quad[1]+v*(quad[3]-quad[1])+65)*2);
                int actual=withExterior.getPixel(px,py),expected=withoutSelf.getPixel(px,py);
                if(actual!=expected)different++;
                for(int shift=8;shift<=24;shift+=8)maxDelta=Math.max(maxDelta,Math.abs(((actual>>>shift)&255)-((expected>>>shift)&255)));
                if(unblocked.getPixel(px,py)!=withExterior.getPixel(px,py))changed++;checked++;
            }
            System.out.println("ACTOR_RECEIVER_ORACLE renderer="+(forward?"forward":"legacy")+" checked="+checked+" exteriorChanged="+changed+" different="+different+" maxDelta="+maxDelta);
            Assert.assertTrue("Exterior shadow must still reach actor",changed>10);
            Assert.assertEquals("Actor self exclusion",0,different);
        }finally{
            queue.colorPacked[0]=floorColor;queue.colorPacked[1]=wallColor;
            queue.lighting.casters.removeValue(exterior,true);if(!queue.lighting.casters.contains(self,true))queue.lighting.casters.add(self);
            unblocked.dispose();if(withExterior!=null)withExterior.dispose();if(withoutSelf!=null)withoutSelf.dispose();
        }
    }
    private Pixmap readOracle(){
        submit.lightingRenderer().prepareFrame(queue.lighting);
        Gdx.gl.glClearColor(.08f,.08f,.1f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
        return Pixmap.createFromFrameBuffer(0,0,640,480);
    }
    private void copyQuad(int s,float[] q){q[0]=dynamic.x1[s];q[1]=dynamic.y1[s];q[2]=dynamic.x2[s];q[3]=dynamic.y2[s];q[4]=dynamic.x3[s];q[5]=dynamic.y3[s];q[6]=dynamic.x4[s];q[7]=dynamic.y4[s];}
    private void addPlane(int shader,int slot,float[] xyz,float nx,float ny,float nz){
        if(planeSurfaces[slot]==null){
        float[] vertices=new float[48];float[] screen=new float[8];
        for(int j=0;j<4;j++){screen[j*2]=LightingCoordinates.screenX(xyz[j*3],xyz[j*3+1]);screen[j*2+1]=LightingCoordinates.screenY(xyz[j*3],xyz[j*3+1],xyz[j*3+2]);}
        for(int j=0;j<6;j++){int c=SpriteSurfaceSupport.corner(j),o=j*8;System.arraycopy(xyz,c*3,vertices,o,3);vertices[o+3]=c>=2?1:0;vertices[o+4]=c==1||c==2?1:0;vertices[o+5]=nx;vertices[o+6]=ny;vertices[o+7]=nz;}
        LightingSurface surface=new LightingSurface(vertices);surface.forwardTopology=ForwardTopology.NATIVE;surface.twoSided=true;
        planeSurfaces[slot]=surface;planeQuads[slot]=screen;
        }
        queue.lighting.tiles.put(slot,planeSurfaces[slot]);float[] screen=planeQuads[slot];
        queue.addQuad(TextureRegistry.WHITE_HANDLE,shader,0,0,0,0,0,screen[0],screen[1],screen[2],screen[3],screen[4],screen[5],screen[6],screen[7],0,0,1,1,Color.toFloatBits(.65f,.65f,.65f,1),(byte)0,FrameRenderQueue.SOURCE_TILED,slot,slot);
    }
    private static Pixmap person(int frame){
        Pixmap p=new Pixmap(64,128,Pixmap.Format.RGBA8888);p.setColor(0,0,0,0);p.fill();p.setColor(.85f,.55f,.25f,1);
        p.fillCircle(32,14,10);p.fillRectangle(23,26,18,52);
        p.fillRectangle(frame==0?5:8,32,10,45);p.fillRectangle(frame==0?49:46,32,10,45);
        p.fillRectangle(14,28,36,10);p.fillRectangle(20,74,10,54);p.fillRectangle(35+frame*7,74,10,54);
        return p;
    }
    private void fail(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();}
    private void close(){if(world!=null){world.dispose();world=null;}if(batch!=null){batch.close();batch=null;}if(bundle!=null){bundle.textureArray.dispose();bundle=null;}}
    @Override public void dispose(){close();for(int i=0;i<2;i++){if(textures[i]!=null)textures[i].dispose();if(pixels[i]!=null)pixels[i].dispose();}ShaderRegistry.disposeAll();InternalTextures.dispose();TextureRegistry.clear();}
}
