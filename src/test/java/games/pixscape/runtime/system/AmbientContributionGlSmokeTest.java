package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Targeted actual-material replay and contribution lifecycle checks in desktop GL. */
public class AmbientContributionGlSmokeTest {
    @Test public void customVerticesAndCalculatedCoverageMaskTheSameOrderedContribution() {
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(64, 32); config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void create() {
                try { exercise(); } catch (Throwable error) { failure[0] = error; }
                finally { Gdx.app.exit(); }
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Ambient material replay", failure[0]);
    }

    private static void exercise() throws Exception {
        ShaderRegistry.initDefaults();
        Pixmap pixels = new Pixmap(4, 1, Pixmap.Format.RGBA8888);
        pixels.setColor(Color.WHITE); pixels.fill();
        Texture white = new Texture(pixels);
        Pixmap maskPixels = new Pixmap(4, 1, Pixmap.Format.RGBA8888);
        maskPixels.setColor(Color.WHITE);maskPixels.fill();
        maskPixels.setBlending(Pixmap.Blending.None);maskPixels.setColor(1, 1, 1, 0); maskPixels.drawPixel(0, 0); maskPixels.drawPixel(2, 0);
        Texture cutout = new Texture(maskPixels);
        Array<Texture> pages = new Array<>(); pages.add(white); pages.add(cutout);
        AtlasRuntimeService.TextureArrayBundle bundle = AtlasRuntimeService.buildTextureArrayFromTextures(pages, 4, 1, GLCaps.detect());
        pixels.dispose();maskPixels.dispose();
        TextureArrayMeshBatch batch = new TextureArrayMeshBatch(64); batch.setTextureArrayBundle(bundle);
        FrameRenderQueue queue = new FrameRenderQueue();
        LayerStateSOA layers = new LayerStateSOA(1); layers.enabled[0] = true; layers.touchLayerIndex(0);
        OrthographicCamera camera = new OrthographicCamera(64, 32); camera.position.set(32, 16, 0); camera.update();
        RenderStats stats = new RenderStats();
        RenderSubmitSystem submit = new RenderSubmitSystem(layers, queue, camera, .2f, .2f, .2f, batch, stats, new RenderStatsSink(100));
        World world = new World(new WorldConfigurationBuilder().with(submit).build()); submit.setEnabled(false);
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(64, 32);
        builder.addFloatAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT, true);
        FrameBuffer target = builder.build();
        try {
            int standard = ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());
            String vertex = Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString();
            String movingVertex = vertex.replace("uniform mat4 u_projTrans;", "uniform mat4 u_projTrans;uniform float u_time;")
                    .replace("vec4(a_position, 0.0, 1.0)", "vec4(a_position+vec2(16.*u_time,0.), 0.0, 1.0)");
            String alpha = "#version 330 core\nin vec2 v_uv;flat in int v_paramId;uniform sampler2D u_entityParams;"
                    + "uniform sampler2DArray u_array;flat in int v_layer;out vec4 color;void main(){"
                    + "if(v_uv.x>.5)discard;float a=texelFetch(u_entityParams,ivec2(0,v_paramId),0).r;"
                    + "color=vec4(.6,.1,.4,a*texture(u_array,vec3(v_uv,v_layer)).a);}";
            int moving = register("ambient-moving", movingVertex, "#version 330 core\nout vec4 color;void main(){color=vec4(.6,.1,.4,.25);}");
            int calculatedAlpha = register("ambient-alpha", vertex, alpha);
            Array<ShaderFloatParam> defaults = ShaderParamsComponent.newShaderFloatArray(); defaults.add(new ShaderFloatParam("opacity", .75f));
            ShaderRegistry.registerParameterLayout("ambient-alpha", defaults);
            ShaderParamsComponent prepared = new ShaderParamsComponent(); prepared.floats.add(new ShaderFloatParam("opacity", .25f));
            java.lang.reflect.Field time = RenderSubmitSystem.class.getDeclaredField("time"); time.setAccessible(true); time.setFloat(submit, 1);
            target.begin(); submit.prepareComposition(); target.end();
            AmbientContributionRenderer auxiliary = auxiliary(submit);
            for(float value:render(submit,target,stats)) Assert.assertEquals("A fresh empty World retains no coverage",0,value,0);
            Assert.assertEquals(0,auxiliaryPasses(submit));
            // Ordinary deformed image over the exempt effect: its actual TR differs from BL+BR+TL.
            add(queue,white,standard,0,0,64,Color.toFloatBits(.2f,.3f,.4f,1));
            add(queue,white,standard,1,0,64,Color.toFloatBits(.8f,.2f,.1f,.5f));
            add(queue,white,standard,1,0,64,Color.toFloatBits(.3f,.4f,.5f,.25f));
            queue.sourceDomain[2]=FrameRenderQueue.SOURCE_ECS;
            queue.x1[2]=12;queue.y1[2]=0;queue.x2[2]=0;queue.y2[2]=32;
            queue.x3[2]=40;queue.y3[2]=24;queue.x4[2]=64;queue.y4[2]=0;
            float[] oldDeformed=render(submit,target,stats);
            queue.ignoreAmbientLighting[1]=true;
            float[] exemptDeformed=render(submit,target,stats);
            for(int x:new int[]{0,16,32,52}) {
                float retain=x==16||x==32?.75f:1;
                Assert.assertEquals("Deformed image uses all four corners for E at x="+x,
                        .8f*.5f*.8f*retain,exemptDeformed[x*4]-oldDeformed[x*4],.009f);
            }
            Assert.assertEquals(1,auxiliaryPasses(submit));
            for (int material : new int[]{moving, calculatedAlpha, standard}) {
                queue.clear();
                add(queue, white, standard, 0, 0, 64, Color.toFloatBits(.2f,.3f,.4f,1));
                add(queue, white, standard, 1, 0, 64, Color.toFloatBits(.8f,.2f,.1f,.5f));
                add(queue, material == standard ? cutout : white, material, material == standard ? 7 : 1, 0, material == moving ? 16 : 64, Color.WHITE.toFloatBits());
                queue.shaderParameters[2] = material == calculatedAlpha ? prepared : null;
                if (material != moving) { queue.u1[2] = 1; queue.u2[2] = 0; }
                float[] old = render(submit, target, stats);
                Assert.assertEquals(0, auxiliaryPasses(submit)); int inactiveDraws = stats.drawCalls;
                queue.ignoreAmbientLighting[1] = true;
                float[] exempt = render(submit, target, stats);
                Assert.assertEquals(1, auxiliaryPasses(submit));
                Assert.assertEquals("Only one E traversal; material switches add draws", material == standard ? 1 : 2, stats.drawCalls - inactiveDraws);
                Assert.assertEquals(0, submit.lightingRenderer().shadowDraws);
                for (int x : new int[]{8,24,40,56}) {
                    float retain;
                    if (material == moving) retain = x >= 16 && x < 32 ? .75f : 1;
                    else if (material == calculatedAlpha) retain = x >= 32 ? .75f : 1;
                    else retain = x == 24 || x == 56 ? 1 : 0;
                    Assert.assertEquals("Calculated shader mask material="+material+" x="+x+" old="+old[x*4]+" exempt="+exempt[x*4], .8f*.5f*.8f*retain, exempt[x*4]-old[x*4], .009f);
                    Assert.assertEquals("E must preserve original coverage", old[x*4+3], exempt[x*4+3], .001f);
                }
                setAmbient(submit,0);
                float[] zeroAmbient=render(submit,target,stats);
                for(int x:new int[]{8,24,40,56}) {
                    float retain=material==moving?(x>=16&&x<32?.75f:1):material==calculatedAlpha?(x>=32?.75f:1):(x==24||x==56?1:0);
                    Assert.assertEquals("Own contribution survives zero ambient",.8f*.5f*retain,zeroAmbient[x*4],.009f);
                }
                setAmbient(submit,.2f);
                queue.ignoreAmbientLighting[1] = false;
                Assert.assertArrayEquals("No stale E on the following inactive frame", old, render(submit, target, stats), 0);
            }
            // No visible exempt entry means no auxiliary pass, including hidden layers and off-camera quads.
            queue.ignoreAmbientLighting[1] = true;
            layers.enabled[0] = false; render(submit, target, stats); Assert.assertEquals(0, auxiliaryPasses(submit));
            layers.enabled[0] = true; queue.x1[1] = queue.x2[1] = 1000; queue.x3[1] = queue.x4[1] = 1064;
            render(submit, target, stats); Assert.assertEquals(0, auxiliaryPasses(submit));
            queue.x1[1] = queue.x2[1] = 0; queue.x3[1] = queue.x4[1] = 64;

            // An exception after E begins discards buffered vertices and restores the caller's full target state.
            WorldLightComposition composition = composition(submit);
            target.begin(); Gdx.gl.glViewport(2,3,60,28); Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST); Gdx.gl.glScissor(4,5,50,20);
            Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_REVERSE_SUBTRACT, GL20.GL_FUNC_ADD);
            Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA,GL20.GL_ONE,GL20.GL_ZERO,GL20.GL_ONE);
            composition.prepare(); composition.beginOriginal();
            queue.ambientMaterial[0] = 1; queue.ambientMaterial[1] = 1; queue.ambientMaterial[2] = 2;
            RuntimeException expected = new RuntimeException("injected E replay failure");
            try {
                auxiliary.render(queue,camera,composition,stats,(first,end)->{throw expected;});
                Assert.fail("Expected injected E failure");
            } catch (RuntimeException error) {
                Assert.assertSame(expected,error); auxiliary.abort(); composition.end(error);
            }
            IntBuffer state = BufferUtils.newIntBuffer(4);
            assertInteger(state,GL20.GL_FRAMEBUFFER_BINDING,target.getFramebufferHandle());
            assertInteger(state,GL20.GL_BLEND_SRC_RGB,GL20.GL_SRC_ALPHA);
            assertInteger(state,GL20.GL_BLEND_DST_RGB,GL20.GL_ONE);
            assertInteger(state,GL20.GL_BLEND_SRC_ALPHA,GL20.GL_ZERO);
            assertInteger(state,GL20.GL_BLEND_EQUATION_RGB,GL20.GL_FUNC_REVERSE_SUBTRACT);
            state.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,state);Assert.assertEquals(2,state.get(0));Assert.assertEquals(60,state.get(2));
            state.clear();Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,state);Assert.assertEquals(4,state.get(0));Assert.assertEquals(50,state.get(2));
            Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_BLEND));
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);Gdx.gl.glViewport(0,0,64,32);target.end();
            target.begin();submit.prepareComposition();target.end();render(submit,target,stats);
            Assert.assertEquals(1,auxiliaryPasses(submit));Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
            // A fresh managed shader compilation must restore sampler units on the next E pass.
            float[] beforeRecompile=render(submit,target,stats);
            ShaderProgram.invalidateAllShaderPrograms(Gdx.app);
            Assert.assertArrayEquals(beforeRecompile,render(submit,target,stats),.001f);
            Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        } finally { world.dispose(); batch.close(); target.dispose(); bundle.textureArray.dispose(); white.dispose();cutout.dispose();ShaderRegistry.disposeAll(); }
    }
    private static void add(FrameRenderQueue q, Texture texture, int shader, int blend, float x, float width, float color) {
        q.addQuad(TextureRegistry.handleOf(texture),shader,blend,0,0,0,0,x,0,x,32,x+width,32,x+width,0,0,0,1,1,color,(byte)0,FrameRenderQueue.SOURCE_VFX,0,-1);
    }
    private static int register(String name,String vertex,String fragment) {
        ShaderProgram program=new ShaderProgram(vertex,fragment);Assert.assertTrue(program.getLog(),program.isCompiled());
        return ShaderRegistry.register(name,program,ShaderMode.TEXTURE_ARRAY);
    }
    private static void setAmbient(RenderSubmitSystem submit,float ambient)throws Exception {
        games.pixscape.runtime.engine.PixscapeEngine engine=new games.pixscape.runtime.engine.PixscapeEngine();
        java.lang.reflect.Field owner=com.artemis.BaseSystem.class.getDeclaredField("world");owner.setAccessible(true);
        java.lang.reflect.Field target=games.pixscape.runtime.engine.PixscapeEngine.class.getDeclaredField("world");target.setAccessible(true);
        target.set(engine,owner.get(submit));
        Object composition=composition(submit);
        engine.setAmbientLight(ambient,ambient,ambient);
        Assert.assertSame("Ambient API retains prepared composition",composition,composition(submit));
    }
    private static float[] render(RenderSubmitSystem submit,FrameBuffer target,RenderStats stats) {
        stats.reset();target.begin();Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        submit.render();FloatBuffer pixel=BufferUtils.newFloatBuffer(64*4);Gdx.gl.glReadPixels(0,16,64,1,GL20.GL_RGBA,GL20.GL_FLOAT,pixel);
        float[] result=new float[64*4];pixel.get(result);target.end();return result;
    }
    private static AmbientContributionRenderer auxiliary(RenderSubmitSystem submit)throws Exception {
        java.lang.reflect.Field field=RenderSubmitSystem.class.getDeclaredField("ambientContribution");field.setAccessible(true);return (AmbientContributionRenderer)field.get(submit);
    }
    private static WorldLightComposition composition(RenderSubmitSystem submit)throws Exception {
        java.lang.reflect.Field field=RenderSubmitSystem.class.getDeclaredField("composition");field.setAccessible(true);return (WorldLightComposition)field.get(submit);
    }
    private static int auxiliaryPasses(RenderSubmitSystem submit)throws Exception {
        java.lang.reflect.Field field=WorldLightComposition.class.getDeclaredField("hasAmbientContribution");
        field.setAccessible(true);return field.getBoolean(composition(submit))?1:0;
    }
    private static void assertInteger(IntBuffer state,int name,int value) {state.clear();Gdx.gl.glGetIntegerv(name,state);Assert.assertEquals(value,state.get(0));}
}
