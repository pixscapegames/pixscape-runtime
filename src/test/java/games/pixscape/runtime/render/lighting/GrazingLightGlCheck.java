package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.render.batch.MultiTextureMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.NormalMapAtlas;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
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
                    RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,camera,.2f,.2f,.2f,batch,stats,new RenderStatsSink(100),false);
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
                    for(float[] n:new float[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{.6f,.8f,0}})for(boolean mapped:new boolean[]{false,true})for(boolean curved:new boolean[]{false,true}){
                        surface.curveNormals=curved?new float[18]:null;
                        for(int o=0;o<48;o+=8){vertices[o+5]=(curved?-7:7)*n[0];vertices[o+6]=(curved?-7:7)*n[1];vertices[o+7]=0;
                            if(curved){surface.curveNormals[o/8*3]=n[0];surface.curveNormals[o/8*3+1]=n[1];}}
                        surface.normalMap=mapped?normals.resolve(1):null;
                        for(int step=-24;step<=80;step++){
                            float angle=step*.25f;
                            double a=Math.toRadians(angle);light.x=100*n[0]*(float)Math.sin(a);light.y=100*n[1]*(float)Math.sin(a);light.z=100*(float)Math.cos(a);
                            surface.actor=true;surface.twoSided=false;draw(world);float actor=field(submit);
                            surface.actor=false;draw(world);float decor=field(submit);
                            Assert.assertTrue("Actor keeps direct minimum",actor>0);
                            // Decode the actual 8-bit tangent texel and the documented image
                            // basis, independently of the shader source. Macro orientation
                            // additionally rejects back-facing decor, without replacing relief.
                            double mappedX=.7071067812/255+.4082482905/255-.5773502692;
                            double mappedY=-.7071067812/255+.4082482905/255-.5773502692;
                            double mappedZ=.8164965809/255+.5773502692;
                            double length=Math.sqrt(mappedX*mappedX+mappedY*mappedY+mappedZ*mappedZ);
                            double cosine=mapped?(mappedX*light.x+mappedY*light.y+mappedZ*light.z)/(100*length):Math.sin(a);
                            double relief=smoothstep(.052335956,.087155743,cosine);
                            double facade=smoothstep(0,.052335956,Math.sin(a));
                            if(angle<=0||relief==0)Assert.assertEquals("No direct behind the facade or rejected by relief",0,decor,0);
                            else if(relief==1&&facade==1)Assert.assertEquals("Front-lit relief unchanged",actor,decor,0);
                            else Assert.assertEquals("Continuous independent facade and relief gates",actor*relief*facade,decor,.0005);
                            surface.twoSided=true;draw(world);float twoSidedDecor=field(submit);
                            surface.actor=true;draw(world);Assert.assertEquals("Two-sided decor/actor reception remains independent",twoSidedDecor,field(submit),0);
                            surface.actor=false;surface.twoSided=false;
                        }
                    }
                    surface.curveNormals=null;surface.normalMap=null;for(int o=0;o<48;o+=8){vertices[o+5]=1;vertices[o+6]=vertices[o+7]=0;}
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
    private static double smoothstep(double lo,double hi,double x){double t=Math.max(0,Math.min(1,(x-lo)/(hi-lo)));return t*t*(3-2*t);}
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
