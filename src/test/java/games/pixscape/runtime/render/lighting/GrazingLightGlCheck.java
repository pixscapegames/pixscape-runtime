package games.pixscape.runtime.render.lighting;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.MultiTextureMeshBatch;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;
import java.nio.FloatBuffer;

/** Exercises the production batch and receiver programs with a constant, known world-space witness. */
public final class GrazingLightGlCheck {
    public static void verify() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);cfg.setWindowedMode(32,32);cfg.disableAudio(true);
        cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){
            public void create(){
                World world=null;MultiTextureMeshBatch batch=null;Texture color=null;NormalMapAtlas normals=null;
                try {
                    ShaderRegistry.initDefaults();
                    Pixmap cp=new Pixmap(1,1,Pixmap.Format.RGBA8888);cp.setColor(Color.WHITE);cp.fill();color=new Texture(cp);cp.dispose();
                    Pixmap np=new Pixmap(1,1,Pixmap.Format.RGBA8888);np.drawPixel(0,0,0x8080ffff);Texture nt=new Texture(np);np.dispose();
                    TextureAtlas ca=new TextureAtlas(),na=new TextureAtlas();
                    ca.addRegion("color__a1",color,0,0,1,1);na.addRegion("normal__a1",nt,0,0,1,1);
                    normals=new NormalMapAtlas("grazing",na,ca);
                    FrameRenderQueue q=new FrameRenderQueue(4);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
                    OrthographicCamera camera=new OrthographicCamera(32,32);camera.position.set(16,16,0);camera.update();
                    batch=new MultiTextureMeshBatch(32);RenderStats stats=new RenderStats();
                    RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,camera,.2f,.2f,.2f,batch,stats,new RenderStatsSink(100));
                    world=new World(new WorldConfigurationBuilder().with(submit).build());submit.setEnabled(false);
                    int receiver=world.create();PointLightComponent component=world.getMapper(PointLightComponent.class).create(world.create());component.shadowQuality=0;world.process();
                    q.addQuad(TextureRegistry.handleOf(color),ShaderRegistry.indexOf(ShaderMode.MULTI_TEXTURE.defaultShaderName()),1,0,0,0,0,
                            0,0,0,32,32,32,32,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_ECS,0,receiver);
                    float[] vertices=new float[48];int[] corner={0,1,2,0,2,3};
                    for(int i=0;i<6;i++){int c=corner[i],o=i*8;vertices[o+3]=c>=2?1:0;vertices[o+4]=c==1||c==2?1:0;}
                    LightingSurface surface=new LightingSurface(vertices);surface.imageQuad=true;q.lighting.entities.put(receiver,surface);
                    LightingFrame.Light light=new LightingFrame.Light();light.radius=512;light.falloff=1.5f;light.intensity=.4f;light.r=light.g=light.b=1;light.resolution=256;
                    q.lighting.lights.add(light);submit.prepareComposition();compileEs300OnDesktop(submit);submit.setEnabled(true);
                    // Distinct orientations, deliberately nonunit normals: normalization is part of the contract.
                    for(float[] n:new float[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{.6f,.8f,0}})for(boolean mapped:new boolean[]{false,true}){
                        for(int o=0;o<48;o+=8){vertices[o+5]=7*n[0];vertices[o+6]=7*n[1];vertices[o+7]=0;}
                        surface.normalMap=mapped?normals.resolve(1):null;
                        for(float angle:new float[]{-6,0,2,2.9f,3.1f,4,4.9f,5.1f,6,20}){
                            double a=Math.toRadians(angle);light.x=100*n[0]*(float)Math.sin(a);light.y=100*n[1]*(float)Math.sin(a);light.z=100*(float)Math.cos(a);
                            surface.actor=true;surface.twoSided=false;draw(world);float actor=field(submit);
                            surface.actor=false;draw(world);float decor=field(submit);
                            Assert.assertTrue("Actor keeps direct minimum",actor>0);
                            if(angle<=3)Assert.assertEquals("Entire decor direct contribution vanishes, including mapped normal/minimum",0,decor,0);
                            else if(angle>=5)Assert.assertEquals("Reference is exact above five degrees",actor,decor,0);
                            else {
                                double t=(Math.sin(a)-.052335956)/(.087155743-.052335956);double gate=t*t*(3-2*t);
                                // RGBA16F output quantization only; zero and reference endpoints above use exact equality.
                                Assert.assertEquals("Geometric normal controls the ramp",actor*gate,decor,.0005);
                            }
                            surface.twoSided=true;draw(world);float twoSidedDecor=field(submit);
                            surface.actor=true;draw(world);Assert.assertEquals("Two-sided decor/actor reception remains independent",twoSidedDecor,field(submit),0);
                            surface.actor=false;surface.twoSided=false;
                        }
                    }
                    verifyOrientedWall(world,submit,q,surface,vertices,normals,light);
                    surface.alphaSheet=null;surface.imageQuad=true;
                    for(int o=0;o<48;o+=8){vertices[o]=vertices[o+1]=vertices[o+2]=0;}
                    surface.normalMap=null;for(int o=0;o<48;o+=8){vertices[o+5]=1;vertices[o+6]=vertices[o+7]=0;}
                    light.x=1;light.y=0;light.z=100;draw(world);float[] ambient=pixel();
                    light.intensity=0;draw(world);Assert.assertArrayEquals("Ambient remains intact when the direct ramp is zero",ambient,pixel(),0);
                    light.intensity=.4f;light.x=40;light.z=100;draw(world);float one=field(submit);
                    LightingFrame.Light other=new LightingFrame.Light();other.x=40;other.z=100;other.radius=512;other.falloff=1.5f;other.intensity=.4f;other.r=other.g=other.b=1;other.resolution=256;
                    q.lighting.lights.add(other);draw(world);Assert.assertTrue("Second contribution is accumulated independently",field(submit)>one*1.99f);
                    Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                }catch(Throwable t){failure[0]=t;}
                finally{if(world!=null)world.dispose();if(batch!=null)batch.close();if(normals!=null)normals.dispose();if(color!=null)color.dispose();ShaderRegistry.disposeAll();Gdx.app.exit();}
            }
        },cfg);
        if(failure[0]!=null)throw new AssertionError("Grazing decor reception failed",failure[0]);
    }
    private static void verifyOrientedWall(World world,RenderSubmitSystem submit,FrameRenderQueue q,LightingSurface s,float[] vertices,NormalMapAtlas normals,LightingFrame.Light light)throws Exception {
        s.actor=false;s.twoSided=true;
        for(int side=1;side<=4;side++)for(boolean mapped:new boolean[]{false,true}){
            boolean axisX=side<=2;float sign=side==1||side==3?-1:1;
            for(int at=0;at<vertices.length;at+=8){vertices[at]=axisX?70:0;vertices[at+1]=axisX?0:70;vertices[at+2]=0;vertices[at+5]=axisX?.9950372f:0;vertices[at+6]=axisX?0:.9950372f;vertices[at+7]=-.09950372f;}
            s.alphaSheet=new TileAlphaSheet(vertices,new TileAlphaSheet.Part[]{new TileAlphaSheet.Part(0,48,0,0,0,1,0,0,0,0)},TileShadowMode.V3_PLANE,null,new float[]{side,50,side,50});
            s.normalMap=mapped?normals.resolve(1):null;light.x=light.y=0;light.z=100;light.quality=0;
            if(axisX)light.x=50+sign*120;else light.y=50+sign*120;draw(world);Assert.assertTrue("An admitted physical face receives direct light",field(submit)>0);
            float previous=0;
            for(int step=0;step<=100;step++){float distance=step*.1f;if(axisX)light.x=50+sign*distance;else light.y=50+sign*distance;draw(world);float value=field(submit);Assert.assertTrue("The physical-side transition is continuous",Math.abs(value-previous)<.03f);previous=value;}
            for(float behind:new float[]{0,.01f,1,10,30,60,120,250}){
                if(axisX)light.x=50-sign*behind;else light.y=50-sign*behind;draw(world);
                Assert.assertEquals("No relighting after crossing the physical plane, side="+side+", mapped="+mapped,0,field(submit),0);
            }
            q.lighting.lights.clear();draw(world);float[] ambient=pixel();q.lighting.lights.add(light);draw(world);Assert.assertArrayEquals("Forbidden direct light does not change ambient",ambient,pixel(),0);
            LightingFrame.Light other=new LightingFrame.Light();other.x=axisX?50+sign*120:0;other.y=axisX?0:50+sign*120;other.z=100;other.radius=512;other.falloff=1.5f;other.intensity=.4f;other.r=other.g=other.b=1;other.resolution=256;
            q.lighting.lights.add(other);draw(world);float combined=field(submit);q.lighting.lights.removeIndex(0);draw(world);Assert.assertEquals("A forbidden light cannot suppress another light",field(submit),combined,0);Assert.assertTrue(combined>0);
            q.lighting.lights.clear();q.lighting.lights.add(light);
        }
        s.alphaSheet=null;s.twoSided=false;
    }
    private static void draw(World world){Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,0);Gdx.gl.glViewport(0,0,32,32);Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();}
    /** ES syntax check on the accessible desktop compiler; not an execution of WebGL or an Android GLES backend. */
    private static void compileEs300OnDesktop(RenderSubmitSystem submit)throws Exception {
        String prefix="#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n";
        for(String name:new String[]{"receiver","mappedReceiver","shadow","accumulate"}){
            java.lang.reflect.Field f=LightingRenderer.class.getDeclaredField(name);f.setAccessible(true);
            com.badlogic.gdx.graphics.glutils.ShaderProgram source=(com.badlogic.gdx.graphics.glutils.ShaderProgram)f.get(submit.lightingRenderer());
            com.badlogic.gdx.graphics.glutils.ShaderProgram es=new com.badlogic.gdx.graphics.glutils.ShaderProgram(
                    source.getVertexShaderSource().replace("#version 330 core\n",prefix),source.getFragmentShaderSource().replace("#version 330 core\n",prefix));
            try{Assert.assertTrue("ES300 syntax on desktop driver, "+name+": "+es.getLog(),es.isCompiled());}finally{es.dispose();}
        }
        System.out.println("ES300_COMPILE_PASS desktop driver only; actual WebGL/GLES execution is separate");
    }
    private static float field(RenderSubmitSystem submit)throws Exception {
        java.lang.reflect.Field cf=RenderSubmitSystem.class.getDeclaredField("composition");cf.setAccessible(true);Object composition=cf.get(submit);
        java.lang.reflect.Field ff=composition.getClass().getDeclaredField("field");ff.setAccessible(true);FrameBuffer f=(FrameBuffer)ff.get(composition);
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,f.getFramebufferHandle());float value=pixel()[0];Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,0);return value;
    }
    private static float[] pixel(){FloatBuffer data=BufferUtils.newFloatBuffer(4);Gdx.gl.glReadPixels(16,16,1,1,GL20.GL_RGBA,GL20.GL_FLOAT,data);float[] result=new float[4];data.get(result);return result;}
}
