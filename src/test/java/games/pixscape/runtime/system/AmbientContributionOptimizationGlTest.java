package games.pixscape.runtime.system;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.*;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.service.*;
import org.junit.*;
import java.nio.*;

/** Exact HDR checks of the two independent optimizations, with real ordered occlusion. */
public class AmbientContributionOptimizationGlTest {
    @Test public void orderedHdrAndCoverageAreExactAndLastEffectLeavesNoStaleContribution() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.setWindowedMode(64,32);config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){@Override public void create(){
            try{exercise();}catch(Throwable t){failure[0]=t;}finally{Gdx.app.exit();}
        }},config);
        if(failure[0]!=null)throw new AssertionError("Particle HDR equivalence",failure[0]);
    }
    private static void exercise() throws Exception {
        ShaderRegistry.initDefaults();
        Pixmap pixels=new Pixmap(1,1,Pixmap.Format.RGBA8888);pixels.setColor(Color.WHITE);pixels.fill();
        Texture white=new Texture(pixels);pixels.dispose();
        Array<Texture> pages=new Array<>();pages.add(white);
        AtlasRuntimeService.TextureArrayBundle bundle=AtlasRuntimeService.buildTextureArrayFromTextures(pages,1,1,GLCaps.detect());
        TextureArrayMeshBatch batch=new TextureArrayMeshBatch(64);batch.setTextureArrayBundle(bundle);
        FrameRenderQueue q=new FrameRenderQueue();LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;layers.touchLayerIndex(0);
        OrthographicCamera cam=new OrthographicCamera(64,32);cam.position.set(32,16,0);cam.update();
        RenderStats stats=new RenderStats();RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,cam,.2f,.2f,.2f,batch,stats,new RenderStatsSink(100));
        World world=new World(new WorldConfigurationBuilder().with(submit).build());submit.setEnabled(false);
        GLFrameBuffer.FrameBufferBuilder fb=new GLFrameBuffer.FrameBufferBuilder(64,32);fb.addFloatAttachment(GL30.GL_RGBA16F,GL20.GL_RGBA,GL20.GL_FLOAT,true);FrameBuffer target=fb.build();
        try {
            int standard=ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());
            add(q,white,standard,0,false,1);
            for(int i=0;i<5;i++){add(q,white,standard,4,true,.75f);add(q,white,standard,1,false,.1f);}
            target.begin();submit.prepareComposition();target.end();
            float[][] optimized=render(submit,target,stats);
            int optimizedDraws=stats.drawCalls;
            float max=0;for(int i=0;i<optimized[3].length;i+=4)max=Math.max(max,optimized[3][i]);
            Assert.assertTrue("Exercises unclamped HDR",max>1);
            // ECS sources intentionally exercise the retained original-draw guard fallback.
            // Their standard E material, blend, order and geometry remain exactly the same.
            for(int i=0;i<q.size;i++)q.sourceDomain[i]=FrameRenderQueue.SOURCE_ECS;
            float[][] fallback=render(submit,target,stats);
            for(int part=0;part<4;part++)Assert.assertArrayEquals("Exact identity-draw suppression target="+part,fallback[part],optimized[part],0);
            Assert.assertTrue("Identity quads do not split the original batch",optimizedDraws<stats.drawCalls);
            for(int i=0;i<q.size;i++)q.sourceDomain[i]=FrameRenderQueue.SOURCE_VFX;
            // Successful submission must restore all caller state as well as the failure path above.
            target.begin();
            Gdx.gl.glViewport(2,3,60,28);Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(4,5,50,20);
            Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_REVERSE_SUBTRACT,GL20.GL_FUNC_ADD);
            Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA,GL20.GL_ONE,GL20.GL_ZERO,GL20.GL_ONE);
            submit.render();
            IntBuffer state=BufferUtils.newIntBuffer(4);
            integer(state,GL20.GL_FRAMEBUFFER_BINDING,target.getFramebufferHandle());
            integer(state,GL20.GL_BLEND_SRC_RGB,GL20.GL_SRC_ALPHA);integer(state,GL20.GL_BLEND_DST_RGB,GL20.GL_ONE);
            integer(state,GL20.GL_BLEND_SRC_ALPHA,GL20.GL_ZERO);integer(state,GL20.GL_BLEND_DST_ALPHA,GL20.GL_ONE);
            integer(state,GL20.GL_BLEND_EQUATION_RGB,GL20.GL_FUNC_REVERSE_SUBTRACT);integer(state,GL20.GL_BLEND_EQUATION_ALPHA,GL20.GL_FUNC_ADD);
            state.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,state);Assert.assertEquals(2,state.get(0));Assert.assertEquals(60,state.get(2));
            state.clear();Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,state);Assert.assertEquals(4,state.get(0));Assert.assertEquals(50,state.get(2));
            Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_BLEND));
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);target.end();
            q.clear();float[][] empty=render(submit,target,stats);for(float value:empty[0])Assert.assertEquals(0,value,0);
            java.lang.reflect.Field active=WorldLightComposition.class.getDeclaredField("hasAmbientContribution");active.setAccessible(true);
            Assert.assertFalse(active.getBoolean(composition(submit)));Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        } finally {world.dispose();batch.close();target.dispose();bundle.textureArray.dispose();white.dispose();ShaderRegistry.disposeAll();}
    }
    private static void add(FrameRenderQueue q,Texture white,int shader,int blend,boolean exempt,float alpha){
        q.addQuad(TextureRegistry.handleOf(white),shader,blend,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,Color.toFloatBits(1,1,1,alpha),(byte)0,FrameRenderQueue.SOURCE_VFX,0,-1);q.ignoreAmbientLighting[q.size-1]=exempt;
    }
    private static WorldLightComposition composition(RenderSubmitSystem submit)throws Exception{java.lang.reflect.Field f=RenderSubmitSystem.class.getDeclaredField("composition");f.setAccessible(true);return(WorldLightComposition)f.get(submit);}
    private static float[][] render(RenderSubmitSystem submit,FrameBuffer target,RenderStats stats)throws Exception{
        stats.reset();target.begin();Gdx.gl.glViewport(0,0,64,32);Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);submit.render();
        float[][] values=new float[4][];values[0]=read();WorldLightComposition composition=composition(submit);
        String[] names={"original","field","ambientContribution"};for(int i=0;i<3;i++){java.lang.reflect.Field f=WorldLightComposition.class.getDeclaredField(names[i]);f.setAccessible(true);FrameBuffer b=(FrameBuffer)f.get(composition);Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,b.getFramebufferHandle());values[i+1]=read();}
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,target.getFramebufferHandle());target.end();return values;
    }
    private static float[] read(){FloatBuffer b=BufferUtils.newFloatBuffer(64*4);Gdx.gl.glReadPixels(0,16,64,1,GL20.GL_RGBA,GL20.GL_FLOAT,b);float[] v=new float[64*4];b.get(v);return v;}
    private static void integer(IntBuffer state,int name,int expected){state.clear();Gdx.gl.glGetIntegerv(name,state);Assert.assertEquals(expected,state.get(0));}
}
