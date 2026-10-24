package games.pixscape.runtime.render.lighting;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.MultiTextureMeshBatch;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.GLCaps;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;
import java.nio.FloatBuffer;

/**
 * Controlled GL witness for the two-depth POC.  It has two opaque silhouette
 * receivers (block 101 and 102), two overlapping faces for each self caster,
 * a common exterior caster (identity 3), and a farther backstop (identity 4).
 *
 * The first shadow depth must be the self identity.  The second depth must
 * pass both faces of that identity and stop at the exterior caster.  Each
 * receiver is then compared with the old one-receiver R oracle in a separate
 * render.  This is intentionally independent of the Studio demo assets.
 */
public final class TwoDepthShadowFixtureGlCheck {
    private static final String B="pixscape.poc.twoDepthSelfShadowExclusion";
    private static final String R="pixscape.poc.excludeTiledSelfShadowBlockId";
    private static final int RESOLUTION=256;
    private static boolean forward;
    private static float orbitY,orbitZ=100;
    private static int quality=1;
    public static void main(String[] args){System.setProperty("pixscape.test.splitShadowGroups","true");verify();}

    public static void verify(){
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.setWindowedMode(64,32);
        cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){@Override public void create(){
            try { for(quality=1;quality<=2;quality++){
                forward=false;run();System.out.println("MULTI_ID_ORACLE_PASS renderer=historical quality="+quality+" resolution=256 orbit=12x3");
                forward=true;run();System.out.println("MULTI_ID_ORACLE_PASS renderer=forward quality="+quality+" resolution=256 orbit=12x3");}
            } catch(Throwable error){ failure[0]=error; }
            finally { ShaderRegistry.disposeAll();InternalTextures.dispose();TextureRegistry.clear();Gdx.app.exit(); }
        }},cfg);
        if(failure[0]!=null)throw new AssertionError("Two-depth controlled fixture",failure[0]);
    }

    private static void run() throws Exception {
        String oldB=System.getProperty(B),oldR=System.getProperty(R);
        try {
            ShaderRegistry.initDefaults();
            Pixmap pixmap=new Pixmap(1,1,Pixmap.Format.RGBA8888);pixmap.setColor(Color.WHITE);pixmap.fill();
            // Forward's cold atlas builder copies TextureData's original Pixmap for
            // every oracle. Keep that source alive until the final array is built.
            Texture texture=new Texture(pixmap);
            try {
                // Both receivers together: exactly one d1/id1 and one d2 replay per light.
                Result both=render(texture,true,true,true,0f);
                Result onlyA=render(texture,true,false,true,0f);
                Result onlyB=render(texture,false,true,true,0f);
                Assert.assertEquals("B shadow pass count is independent of receiver count",both.shadowDraws,onlyA.shadowDraws);
                Assert.assertEquals("B d2 replay is independent of receiver count",both.secondDraws,onlyA.secondDraws);
                Assert.assertEquals("B replay bytes are independent of receiver count",both.secondBytes,onlyB.secondBytes);

                // d1 contains the respective self face, while d2 is farther: it crossed two
                // faces of that identity and reached the exterior identity 3 before id 4.
                Assert.assertEquals("receiver A d1 identity",1f,both.firstA,.001f);
                Assert.assertEquals("receiver B d1 identity",2f,both.firstB,.001f);
                Assert.assertTrue("A d2 must lie behind both self faces",both.secondA>both.firstDepthA+.05f);
                Assert.assertTrue("B d2 must lie behind both self faces",both.secondB>both.firstDepthB+.05f);
                Assert.assertEquals("A d2 stops at exterior identity 3",exteriorDepth(-20,0f),both.secondA,.025f);
                Assert.assertEquals("B d2 stops at exterior identity 3",exteriorDepth(20,0f),both.secondB,.025f);

                // R is deliberately run once for each receiver; its alternate atlas omits only
                // that receiver's self caster.  It remains the oracle for B at the same pose.
                Result oracleA=render(texture,true,false,false,0f);
                Result oracleB=render(texture,false,true,false,0f);
                assertPixel("receiver A, B == R",both.pixelA,oracleA.pixelA);
                assertPixel("receiver B, B == R",both.pixelB,oracleB.pixelB);

                // A small mobile-light displacement retains the same first identity and the
                // B/R agreement.  This covers the transition without rebuilding per receiver.
                Result moved=render(texture,true,true,true,4f);
                Result movedA=render(texture,true,false,false,4f);
                Result movedB=render(texture,false,true,false,4f);
                Assert.assertEquals("moving A d1 identity",1f,moved.firstA,.001f);
                Assert.assertEquals("moving B d1 identity",2f,moved.firstB,.001f);
                Assert.assertTrue("moving A d2 remains behind its self faces",moved.secondA>moved.firstDepthA+.05f);
                Assert.assertTrue("moving B d2 remains behind its self faces",moved.secondB>moved.firstDepthB+.05f);
                assertPixel("moving receiver A, B == R",moved.pixelA,movedA.pixelA);
                assertPixel("moving receiver B, B == R",moved.pixelB,movedB.pixelB);
                Assert.assertEquals("moving light keeps global B pass count",both.shadowDraws,moved.shadowDraws);
                for(float height:new float[]{60,100,150})for(int angle=0;angle<360;angle+=30){
                    float x=30*(float)Math.cos(Math.toRadians(angle));orbitY=30*(float)Math.sin(Math.toRadians(angle));orbitZ=height;
                    Result orbit=render(texture,true,true,true,x);
                    Result a=render(texture,true,false,false,x),b=render(texture,false,true,false,x);
                    if(Math.abs(orbit.pixelA-a.pixelA)>2||Math.abs(orbit.pixelB-b.pixelB)>2)
                        System.out.println("ORACLE_DIFFERENCE forward="+forward+" quality="+quality+" height="+height+" angle="+angle
                                +" A="+orbit.pixelA+"/"+a.pixelA+" B="+orbit.pixelB+"/"+b.pixelB
                                +" id1="+orbit.firstA+" d1="+orbit.firstDepthA+" d2="+orbit.secondA+" oracleD="+a.firstDepthA);
                    assertPixel("orbit A height="+height+" angle="+angle,orbit.pixelA,a.pixelA);
                    assertPixel("orbit B height="+height+" angle="+angle,orbit.pixelB,b.pixelB);
                    Assert.assertEquals("Orbit does not add per-receiver passes",both.shadowDraws,orbit.shadowDraws);
                }
            } finally { texture.dispose();pixmap.dispose(); }
        } finally { orbitY=0;orbitZ=100;restore(B,oldB);restore(R,oldR); }
    }

    private static Result render(Texture texture,boolean receiverA,boolean receiverB,boolean twoDepth,float lightX)throws Exception {
        System.setProperty(B,Boolean.toString(twoDepth));
        if(twoDepth)System.clearProperty(R);else System.setProperty(R,receiverA?"101":"102");
        FrameRenderQueue q=new FrameRenderQueue(4);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
        OrthographicCamera camera=new OrthographicCamera(64,32);camera.position.set(32,16,0);camera.update();
        AtlasRuntimeService.TextureArrayBundle bundle=forward?AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(texture),1,1,GLCaps.detect()):null;
        MetricsBatch batch=forward?new TextureArrayMeshBatch(64):new MultiTextureMeshBatch(64);
        if(forward)((TextureArrayMeshBatch)batch).setTextureArrayBundle(bundle);
        RenderStats stats=new RenderStats();
        RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,camera,0,0,0,batch,stats,new RenderStatsSink(100),forward);
        World world=new World(new WorldConfigurationBuilder().with(submit).build());
        try {
            submit.setEnabled(false);PointLightComponent component=world.getMapper(PointLightComponent.class).create(world.create());
            component.shadowQuality=1;component.shadowResolution=RESOLUTION;component.radius=250;world.process();
            int handle=TextureRegistry.handleOf(texture),shader=ShaderRegistry.indexOf((forward?ShaderMode.TEXTURE_ARRAY:ShaderMode.MULTI_TEXTURE).defaultShaderName());
            LightingSurface selfA=caster(-6,0,70,-8,0,60,1,101);
            LightingSurface selfB=caster(6,0,70,8,0,60,2,102);
            LightingSurface exterior=caster(-14,0,30,14,0,30,3,0);
            LightingSurface backstop=caster(-18,0,10,18,0,10,4,0);
            LightingSurface a=receiver(-20,1,101,selfA),b=receiver(20,2,102,selfB);
            if(receiverA){addReceiver(q,handle,shader,0,0,32,a);}
            if(receiverB){addReceiver(q,handle,shader,1,32,64,b);}
            // Independent oracle: remove only the requested caster from this dedicated
            // fixture's atlas, identically for both renderers. No product per-receiver atlas.
            if(twoDepth||!receiverA)addCaster(q,selfA,handle);
            if(twoDepth||!receiverB)addCaster(q,selfB,handle);
            addCaster(q,exterior,handle);addCaster(q,backstop,handle);
            q.lighting.staticCasterCount=q.lighting.casters.size;q.lighting.staticCasterRevision=1;q.lighting.casterIndex.rebuild(q.lighting,q.lighting.staticCasterCount);
            LightingFrame.Light light=new LightingFrame.Light();light.x=lightX;light.y=orbitY;light.z=orbitZ;light.radius=250;light.falloff=1;light.intensity=1;light.r=light.g=light.b=1;light.quality=quality;light.resolution=RESOLUTION;
            q.lighting.lights.add(light);submit.prepareComposition();submit.lightingRenderer().prepareFrame(q.lighting);submit.setEnabled(true);clear();world.process();
            Result result=new Result();result.pixelA=receiverA?red(16,16):0;result.pixelB=receiverB?red(48,16):0;
            result.shadowDraws=submit.lightingRenderer().shadowDraws;result.secondDraws=submit.lightingRenderer().twoDepthShadowDraws;result.secondBytes=submit.lightingRenderer().twoDepthShadowUploadedBytes;
            result.firstDepthA=readAtlas(submit.lightingRenderer(),"atlas",0,-20-lightX,-orbitY,-orbitZ);
            result.firstDepthB=readAtlas(submit.lightingRenderer(),"atlas",0,20-lightX,-orbitY,-orbitZ);
            if(twoDepth){
                result.firstA=readAtlas(submit.lightingRenderer(),"atlas",1,-20-lightX,-orbitY,-orbitZ);
                result.firstB=readAtlas(submit.lightingRenderer(),"atlas",1,20-lightX,-orbitY,-orbitZ);
                result.secondA=readAtlas(submit.lightingRenderer(),"secondAtlas",0,-20-lightX,-orbitY,-orbitZ);
                result.secondB=readAtlas(submit.lightingRenderer(),"secondAtlas",0,20-lightX,-orbitY,-orbitZ);
            }
            Assert.assertEquals("fixture GL state",GL20.GL_NO_ERROR,Gdx.gl.glGetError());return result;
        } finally {world.dispose();batch.close();if(bundle!=null)bundle.textureArray.dispose();}
    }

    private static LightingSurface receiver(float x,int identity,int block,LightingSurface self){
        float[] v=new float[48];for(int i=0;i<6;i++){int o=i*8;v[o]=x;v[o+2]=0;v[o+3]=(i==2||i==4||i==5)?1:0;v[o+4]=(i==1||i==2||i==4)?1:0;v[o+7]=1;}
        LightingSurface result=new LightingSurface(v);result.imageQuad=true;result.forwardTopology=ForwardTopology.NATIVE;result.twoSided=true;result.pocSourceBlockId=block;result.casterSurface=self;result.selfShadowExclusionId=identity;
        result.receiverExclusionIds=new int[]{identity,identity};return result;
    }
    private static LightingSurface caster(float x1,float y1,float z1,float x2,float y2,float z2,int identity,int block){
        float[] v=new float[48];triangle(v,0,x1,y1,z1);triangle(v,24,x2,y2,z2);
        LightingSurface result=new LightingSurface(v);result.shadowCaster=true;result.selfShadowExclusionId=identity;result.pocSourceBlockId=block;return result;
    }
    private static void triangle(float[] v,int start,float x,float y,float z){
        float[][] p={{x-12,y-12,z},{x+12,y-12,z},{x,y+12,z}};
        for(int i=0;i<3;i++){int o=start+i*8;v[o]=p[i][0];v[o+1]=p[i][1];v[o+2]=p[i][2];v[o+3]=i==1?1:.5f;v[o+4]=i==2?1:0;v[o+7]=1;}
    }
    private static void addReceiver(FrameRenderQueue q,int texture,int shader,int slot,float x0,float x1,LightingSurface surface){
        q.addQuad(texture,shader,0,0,0,0,0,x0,0,x0,32,x1,32,x1,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_TILED,slot,slot);
        q.lighting.tiles.put(slot,surface);
    }
    private static void addCaster(FrameRenderQueue q,LightingSurface surface,int texture){
        if(Boolean.getBoolean("pixscape.test.splitShadowGroups")&&(surface.pocSourceBlockId==101||surface.pocSourceBlockId==102)){
            for(int i=0;i<2;i++){
                LightingSurface part=new LightingSurface(java.util.Arrays.copyOfRange(surface.vertices,i*24,(i+1)*24));
                part.shadowCaster=true;part.pocSourceBlockId=surface.pocSourceBlockId;
                part.selfShadowExclusionId=100+surface.selfShadowExclusionId*2+i;
                part.shadowGroupId=surface.selfShadowExclusionId;
                addCasterPart(q,part,texture);
            }return;
        }
        addCasterPart(q,surface,texture);
    }
    private static void addCasterPart(FrameRenderQueue q,LightingSurface surface,int texture){
        LightingFrame.Caster caster=new LightingFrame.Caster();caster.surface=surface;caster.texture=texture;caster.u2=caster.v2=1;caster.bounds();q.lighting.casters.add(caster);
    }
    /** The exterior triangles sit at z=30 on the two witness rays (x = receiverX*.7). */
    private static float exteriorDepth(float receiverX,float lightX){return (float)Math.sqrt(receiverX*.7f*receiverX*.7f+70f*70f)/250f;}
    private static void clear(){Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,0);Gdx.gl.glViewport(0,0,64,32);Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);}
    private static int red(int x,int y){Pixmap pixel=Pixmap.createFromFrameBuffer(x,y,1,1);try{return pixel.getPixel(0,0)>>>24;}finally{pixel.dispose();}}
    private static float readAtlas(LightingRenderer renderer,String field,int attachment,float dx,float dy,float dz)throws Exception {
        java.lang.reflect.Field member=LightingRenderer.class.getDeclaredField(field);member.setAccessible(true);FrameBuffer atlas=(FrameBuffer)member.get(renderer);
        float ax=Math.abs(dx),ay=Math.abs(dy),az=Math.abs(dz);int face;float qx,qy;
        if(ax>=ay&&ax>=az){if(dx>0){face=0;qx=-dz/ax;qy=-dy/ax;}else{face=1;qx=dz/ax;qy=-dy/ax;}}
        else if(ay>=az){if(dy>0){face=2;qx=dx/ay;qy=dz/ay;}else{face=3;qx=dx/ay;qy=-dz/ay;}}
        else if(dz>0){face=4;qx=dx/az;qy=-dy/az;}else{face=5;qx=-dx/az;qy=-dy/az;}
        int x=(int)((face%3)*RESOLUTION+Math.max(.5f/RESOLUTION,Math.min(1-.5f/RESOLUTION,qx*.5f+.5f))*RESOLUTION);
        int y=(int)((face/3)*RESOLUTION+Math.max(.5f/RESOLUTION,Math.min(1-.5f/RESOLUTION,qy*.5f+.5f))*RESOLUTION);
        FloatBuffer pixel=BufferUtils.newFloatBuffer(1);Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,atlas.getFramebufferHandle());Gdx.gl30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+attachment);Gdx.gl.glReadPixels(x,y,1,1,GL30.GL_RED,GL20.GL_FLOAT,pixel);Gdx.gl30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,0);return pixel.get(0);
    }
    private static void assertPixel(String label,int actual,int expected){Assert.assertEquals(label,expected,actual,2);}
    private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
    private static final class Result {int pixelA,pixelB,shadowDraws,secondDraws;long secondBytes;float firstA,firstB,firstDepthA,firstDepthB,secondA,secondB;}
}
