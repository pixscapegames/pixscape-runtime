package games.pixscape.runtime.render.lighting;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.spatial.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.*;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.system.*;
import org.junit.Assert;

/** Test-only comparison of complete box shell versus either reference-view skin.
 * Uses real hero frames; authored diagnostic envelope, not an archived user-scene pose.
 * Only actor zero is filtered; the second instance and identity policy are unchanged.
 */
public final class SingleSkinGlCheck extends ApplicationAdapter {
    private static final int FRAMES=48;
    private static final float[] YAWS={0,45,90,37.25f};
    private static final float[][] PLANES={{-1000,-1000,0,-1000,1000,0,1000,1000,0,1000,-1000,0},{-300,180,0,-300,180,260,300,180,260,300,180,0}};
    private final LightingSurface[] planeSurfaces=new LightingSurface[2];
    private final float[][] planeQuads=new float[2][];
    private FileHandle output;
    private static final boolean REAL=Boolean.getBoolean("pixscape.test.realActorSkin");
    private static final boolean EXACT45=Boolean.getBoolean("pixscape.test.exactFrame45");
    private static final boolean ROUNDED=Boolean.getBoolean("pixscape.test.roundedActorSheet");
    private final RoundedActorSheetPrototype[] rounded={new RoundedActorSheetPrototype(),new RoundedActorSheetPrototype()};
    private Texture[] textures=new Texture[REAL?64:2];private Pixmap[] pixels=new Pixmap[REAL?64:2];
    private World world;private RenderSubmitSystem submit;private LightingGeometrySystem geometry;
    private MetricsBatch batch;private AtlasRuntimeService.TextureArrayBundle bundle;
    private FrameRenderQueue queue;private DynamicEntityRenderState dynamic;private RenderStats stats;
    private int condition,frame,count;private boolean forward;private int skin;
    private int[] entities=new int[8];
    private final LightingFrame.Light light=new LightingFrame.Light();
    private final float[] quad=new float[8],selected=new float[ActorVolumeCaster.MAX_VERTICES*8];private final long[] times=new long[FRAMES];
    private long drawSum,uploadSum,prepSum;private long deadline;private Throwable failure;
    public static void main(String[] args){
        SingleSkinGlCheck app=new SingleSkinGlCheck();app.output=new FileHandle(args[0]);app.output.mkdirs();
        Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setInitialVisible(false);c.disableAudio(true);
        c.setWindowedMode(1280,960);c.useVsync(true);c.setForegroundFPS(30);
        c.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(app,c);if(app.failure!=null)throw new AssertionError(app.failure);
    }
    @Override public void create(){try{
        deadline=System.nanoTime()+55_000_000_000L;ShaderRegistry.initDefaults();InternalTextures.initIfNeeded();
        for(int i=0;i<textures.length;i++){
            pixels[i]=new Pixmap(new FileHandle(REAL?"../tiled-iso-demo/studio/orig/animations/hero_idle__a1303/hero_idle__a1303_"+String.format("%04d",i)+".png":"../shadow-volume-silhouette-demo/studio/orig/animations/hero_run__a1302/hero_run__a1302_"+(i==0?"0000":"0004")+".png"));
            textures[i]=new Texture(pixels[i]);
        }
        output.child("measurements.csv").writeString("renderer,geometry,actors,samples,renderCpuMedianMs,renderCpuP95Ms,publicationCpuMeanMs,drawsMean,uploadBytesMean\n",false);
        System.out.println("READY pid="+ProcessHandle.current().pid()+" runtime="+ActorVolumeData.class.getProtectionDomain().getCodeSource().getLocation());
        setup();
    }catch(Throwable t){fail(t);}}
    private void setup(){
        int variants=ROUNDED?2:EXACT45?5:3;
        forward=condition>=variants;skin=condition%variants;count=2;frame=EXACT45?45:0;drawSum=uploadSum=prepSum=0;
        if(ROUNDED&&Boolean.getBoolean("pixscape.test.roundedSheetActorExchange"))frame=42;
        dynamic=new DynamicEntityRenderState(16);queue=new FrameRenderQueue(32);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
        OrthographicCamera camera=new OrthographicCamera(960,720);camera.position.set(0,90,0);camera.update();
        bundle=forward?AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(textures),180,180,GLCaps.detect()):null;
        batch=forward?new TextureArrayMeshBatch(512):new MultiTextureMeshBatch(512);
        if(forward)((TextureArrayMeshBatch)batch).setTextureArrayBundle(bundle);
        stats=new RenderStats();geometry=new LightingGeometrySystem(dynamic,new TiledMapRenderState(4),queue,layers);
        submit=new RenderSubmitSystem(layers,queue,camera,.22f,.22f,.22f,batch,stats,new RenderStatsSink(100),forward);
        world=new World(new WorldConfigurationBuilder().with(geometry,submit).build());geometry.setEnabled(false);submit.setEnabled(false);
        PointLightComponent source=world.getMapper(PointLightComponent.class).create(world.create());source.shadowQuality=1;source.shadowResolution=256;source.radius=500;
        for(int i=0;i<count;i++){
            int e=entities[i]=world.create(),slot=dynamic.acquireSlotForEntity(e);dynamic.enabled[slot]=true;dynamic.visible[slot]=true;
            dynamic.blend[slot]=BlendMode.ALPHA.id;dynamic.u2[slot]=dynamic.v2[slot]=1;
            TransformComponent t=world.getMapper(TransformComponent.class).create(e);t.x=i==0?-65:80;t.y=0;
            SpatialPhysicsFootprintComponent foot=world.getMapper(SpatialPhysicsFootprintComponent.class).create(e);foot.valid=true;
            SpatialHeightComponent h=world.getMapper(SpatialHeightComponent.class).create(e);h.volumes=new ActorVolumes();
            // Keep the complete 64px silhouette inside the envelope, including yaw 45/90.
            ActorVolumeData v=new ActorVolumeData();v.width=128;v.depth=128;v.height=180;
            if(REAL){v.width=86.40844f;v.depth=32;v.height=82.98047f;v.offsetX=22.82808f;v.offsetY=10.003464f;}
            if(i%2==0){world.getMapper(AssetRefComponent.class).create(e).assetId=7;
                AnimationComponent a=world.getMapper(AnimationComponent.class).create(e);a.currentClip="walk";
                h.volumes.put(7,"walk",v);v=v.copy();if(!REAL)v.depth=140;h.volumes.put(7,"idle",v);
            }else h.volumes.staticVolume=v;
            world.getMapper(SurfaceLightingComponent.class).create(e).shadowCaster=true;
            setQuad(i,0);
        }
        world.process();geometry.prepareRuntimeAvailability();submit.prepareComposition();submit.setEnabled(true);
        light.radius=500;light.intensity=1;light.falloff=1;light.r=light.g=light.b=1;light.resolution=256;light.quality=1;
    }
    private void setQuad(int index,float altitude){
        int e=entities[index],s=dynamic.entityIdToRenderSlot[e];TransformComponent t=world.getMapper(TransformComponent.class).get(e);
        float x=t.x,y=t.y+altitude;
        dynamic.x1[s]=dynamic.x2[s]=x-90;dynamic.x3[s]=dynamic.x4[s]=x+90;
        dynamic.y1[s]=dynamic.y4[s]=y;dynamic.y2[s]=dynamic.y3[s]=y+180;
        if(REAL){
            dynamic.x1[s]=dynamic.x2[s]=x-129.71706f;dynamic.x3[s]=dynamic.x4[s]=x+129.71706f;
            dynamic.y1[s]=dynamic.y4[s]=y-64.858f;dynamic.y2[s]=dynamic.y3[s]=y+194.576f;
            dynamic.u1[s]=(frame/8)%2;dynamic.u2[s]=1-dynamic.u1[s];
        }
        dynamic.textureHandle[s]=TextureRegistry.handleOf(textures[index%2==0?(REAL?(frame*4/3)%64:(frame/6)%2):0]);
    }
    @Override public void render(){if(failure!=null)return;try{
        if(System.nanoTime()>deadline)throw new AssertionError("Internal deadline exceeded");
        float angle=frame*7.5f;light.x=220*(float)Math.cos(Math.toRadians(angle));light.y=220*(float)Math.sin(Math.toRadians(angle));light.z=frame<24?80:250;
        if(ROUNDED&&Boolean.getBoolean("pixscape.test.roundedSheetWall")){light.x=0;light.y=-220;light.z=120;}
        for(int i=0;i<count;i++){
            int e=entities[i];SpatialHeightComponent h=world.getMapper(SpatialHeightComponent.class).get(e);
            // The projected actor origin rises together with physical altitude.
            float altitude=0;h.altitude=altitude;
            TransformComponent t=world.getMapper(TransformComponent.class).get(e);t.y=i/4*-35+altitude;
            AnimationComponent a=world.getMapper(AnimationComponent.class).getSafe(e,null);
            if(a!=null){a.currentClip=frame%24<12?"walk":"idle";a.frame=(frame/6)%2;}
            ActorVolumeData v=h.volumes.get(a==null?0:7,a==null?null:a.currentClip);
            v.orientation=REAL?343.33826f+frame*7.5f:frame*2f;setQuad(i,0);
        }
        geometry.setEnabled(true);geometry.process();geometry.setEnabled(false);
        queue.lighting.lights.add(light);
        LightingSurface tested=queue.lighting.entities.get(entities[0]);
        if(ROUNDED){LightingSurface peer=queue.lighting.entities.get(entities[1]);
            Assert.assertEquals(tested.shadowGroupId,tested.selfShadowExclusionId);
            Assert.assertEquals(peer.shadowGroupId,peer.selfShadowExclusionId);
            Assert.assertNotEquals(tested.shadowGroupId,peer.shadowGroupId);}
        tested.shadowVertices=tested.actorVolume.vertices;tested.shadowVertexCount=tested.actorVolume.vertexCount;
        // The archived frame-45 run had the temporary rear policy on the peer.
        if(EXACT45){LightingSurface peer=queue.lighting.entities.get(entities[1]);
            float[] rear=new float[peer.shadowVertexCount*8];int n=0;
            for(int o=0;o<peer.shadowVertexCount*8;o+=24)if(peer.shadowVertices[o+7]-peer.shadowVertices[o+5]-peer.shadowVertices[o+6]<0){System.arraycopy(peer.shadowVertices,o,rear,n*8,24);n+=3;}
            peer.shadowVertices=rear;peer.shadowVertexCount=n;
        }
        if(ROUNDED && skin!=0){
            for(int i=0;i<count;i++){
                int e=entities[i],slot=dynamic.entityIdToRenderSlot[e];copyQuad(slot,quad);
                TransformComponent t=world.getMapper(TransformComponent.class).get(e);
                SpatialHeightComponent h=world.getMapper(SpatialHeightComponent.class).get(e);
                AnimationComponent a=world.getMapper(AnimationComponent.class).getSafe(e,null);
                ActorVolumeData v=h.volumes.get(a==null?0:7,a==null?null:a.currentClip);
                rounded[i].update(v,t.y+t.x*.5f,t.y-t.x*.5f,h.altitude,quad);
                LightingSurface s=queue.lighting.entities.get(e);s.shadowVertices=rounded[i].vertices;s.shadowVertexCount=rounded[i].vertexCount;
            }
            for(LightingFrame.Caster c:queue.lighting.casters)c.bounds();
        }
        if(skin!=0 && !ROUNDED){
            LightingSurface own=queue.lighting.entities.get(entities[0]);int n=0;
            for(int o=0;o<own.shadowVertexCount*8;o+=24){
                float side=-own.shadowVertices[o+5]-own.shadowVertices[o+6]+own.shadowVertices[o+7];
                float facing=own.shadowVertices[o+5]*(light.x-own.shadowVertices[o])+own.shadowVertices[o+6]*(light.y-own.shadowVertices[o+1])+own.shadowVertices[o+7]*(light.z-own.shadowVertices[o+2]);
                if(skin==1?side>0:skin==2?side<0:skin==3?facing>0:facing<0){System.arraycopy(own.shadowVertices,o,selected,n*8,24);n+=3;}
            }
            if(frame%6==0){
                Pixmap alpha=pixels[REAL?(frame*4/3)%64:(frame/6)%2];
                System.out.println("SKIN_COVERAGE skin="+skin+" frame="+frame+" full="+coverage(alpha,own.shadowVertices,own.shadowVertexCount)+" selected="+coverage(alpha,selected,n));
                Assert.assertEquals("Skin must retain the supported alpha coverage",coverage(alpha,own.shadowVertices,own.shadowVertexCount),coverage(alpha,selected,n));
            }
            own.shadowVertices=selected;own.shadowVertexCount=n;
            queue.lighting.casters.first().bounds();
        }
        queue.size=0;queue.lighting.tiles.clear();
        int shader=ShaderRegistry.indexOf((forward?ShaderMode.TEXTURE_ARRAY:ShaderMode.MULTI_TEXTURE).defaultShaderName());
        addPlane(shader,0,PLANES[0],0,0,1);
        addPlane(shader,1,PLANES[1],0,-1,0);
        for(int i=0;i<count;i++){int e=entities[i],s=dynamic.entityIdToRenderSlot[e];copyQuad(s,quad);
            queue.addQuad(dynamic.textureHandle[s],shader,BlendMode.ALPHA.id,0,0,0,0,quad[0],quad[1],quad[2],quad[3],quad[4],quad[5],quad[6],quad[7],dynamic.u1[s],0,dynamic.u2[s],1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_ECS,s,e);
        }
        submit.lightingRenderer().prepareFrame(queue.lighting);stats.reset();
        Gdx.gl.glClearColor(.08f,.08f,.1f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        long start=System.nanoTime();world.process();times[frame]=System.nanoTime()-start;
        if(frame>=24){drawSum+=stats.drawCalls;uploadSum+=stats.bufferUploadBytes;prepSum+=queue.lighting.preparationCpuNs;}
        Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        if(frame%3==0){Pixmap p=Pixmap.createFromFrameBuffer(0,0,1280,960);try{PixmapIO.writePNG(output.child(label()+"-frame-"+frame+".png"),p,-1,true);}finally{p.dispose();}}
        if(frame%12==0 || ROUNDED&&frame==42){
            LightingFrame.Caster peer=queue.lighting.casters.get(1);queue.lighting.casters.removeIndex(1);
            try{
                submit.lightingRenderer().prepareFrame(queue.lighting);
                Gdx.gl.glClearColor(.08f,.08f,.1f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                Pixmap isolated=Pixmap.createFromFrameBuffer(0,0,1280,960);
                try{PixmapIO.writePNG(output.child(label()+"-isolated-frame-"+frame+".png"),isolated,-1,true);}finally{isolated.dispose();}
            }finally{queue.lighting.casters.add(peer);}
        }
        if(EXACT45){close();condition++;if(condition==(ROUNDED?4:10)){output.child("DONE").writeString("PASS",false);Gdx.app.exit();}else setup();return;}
        frame++;if(frame==FRAMES){
            long[] measured=java.util.Arrays.copyOfRange(times,24,48);java.util.Arrays.sort(measured);
            String row=(forward?"forward":"legacy")+","+("skin-"+skin)+","+count+",24,"+measured[12]/1e6+","+measured[22]/1e6+","+Double.toString(prepSum/24e6)+","+drawSum/24.0+","+uploadSum/24.0+"\n";
            output.child("measurements.csv").writeString(row,true);System.out.print(row);close();condition++;
            if(condition==(ROUNDED?4:6)){output.child("DONE").writeString("PASS",false);Gdx.app.exit();}else setup();
        }
    }catch(Throwable t){fail(t);}}
    private String label(){return (forward?"forward":"legacy")+"-"+(ROUNDED&&skin!=0?"rounded-sheet":"skin-"+skin);}
    private static int coverage(Pixmap alpha,float[] mesh,int count){
        int hits=0;
        for(int y=0;y<alpha.getHeight();y++)for(int x=0;x<alpha.getWidth();x++){
            if((alpha.getPixel(x,y)&255)<128)continue;
            float u=(x+.5f)/alpha.getWidth(),v=1-(y+.5f)/alpha.getHeight();
            for(int o=0;o<count*8;o+=24){
                float ax=mesh[o+11]-mesh[o+3],ay=mesh[o+12]-mesh[o+4],bx=mesh[o+19]-mesh[o+3],by=mesh[o+20]-mesh[o+4];
                float determinant=ax*by-ay*bx;if(Math.abs(determinant)<1e-9)continue;
                float px=u-mesh[o+3],py=v-mesh[o+4],a=(px*by-py*bx)/determinant,b=(ax*py-ay*px)/determinant;
                if(a>=-.00001f&&b>=-.00001f&&a+b<=1.00001f){hits++;break;}
            }
        }return hits;
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
    private void fail(Throwable t){failure=t;t.printStackTrace();Gdx.app.exit();}
    private void close(){if(world!=null){world.dispose();world=null;}if(batch!=null){batch.close();batch=null;}if(bundle!=null){bundle.textureArray.dispose();bundle=null;}}
    @Override public void dispose(){close();for(int i=0;i<textures.length;i++){if(textures[i]!=null)textures[i].dispose();if(pixels[i]!=null)pixels[i].dispose();}ShaderRegistry.disposeAll();InternalTextures.dispose();TextureRegistry.clear();}
}
