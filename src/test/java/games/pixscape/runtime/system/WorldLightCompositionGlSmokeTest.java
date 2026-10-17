package games.pixscape.runtime.system;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.*;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.*;
import org.junit.*;
import java.nio.ByteBuffer;

public class WorldLightCompositionGlSmokeTest {
    @Test public void sceneReadyIncludesIdempotentRenderResourcesWithoutGameplay() {
        games.pixscape.runtime.engine.SceneRenderPreparationGlCheck.verify();
    }
    @Test public void separatedShadowsKeepNestedTargetsFailuresAndCoverageContracts() {
        ShadowActorSeparation.enabled=true;
        try {litFractionalEdgesAndIndependentLightsRestoreTarget();}
        finally {ShadowActorSeparation.enabled=false;}
    }
    @Test public void htmlAndNativeWriteIdenticalActiveVertices() {
        LightingBatchBackendCheck.assertHtmlAndNativeWriteIdenticalActiveVertices();
    }
    @Test public void shadowReplayEligibilityUsesBatchLimits() {
        LightingBatchBackendCheck.assertReplayEligibilityUsesBatchLimits();
    }
    @Test public void litFractionalEdgesAndIndependentLightsRestoreTarget() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.setWindowedMode(64,32);config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){
            World world;MultiTextureMeshBatch batch;Texture texture;
            @Override public void create(){try{
                ShaderRegistry.initDefaults();Gdx.gl.glViewport(0,0,64,32);
                Pixmap pix=new Pixmap(1,1,Pixmap.Format.RGBA8888);pix.setColor(.4f,.2f,.1f,.5f);pix.fill();
                texture=new Texture(pix);pix.dispose();int handle=TextureRegistry.handleOf(texture);
                FrameRenderQueue q=new FrameRenderQueue(4);LayerStateSOA layers=new LayerStateSOA(1);
                layers.enabled[0]=true;
                OrthographicCamera camera=new OrthographicCamera(64,32);camera.position.set(32,16,0);camera.update();
                RenderStats stats=new RenderStats();batch=new MultiTextureMeshBatch(64);
                RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,camera,.2f,.2f,.2f,batch,stats,new RenderStatsSink(100));
                world=new World(new WorldConfigurationBuilder().with(submit).build());submit.setEnabled(false);
                int entity=world.create();world.getMapper(PointLightComponent.class).create(entity);world.process();
                q.addQuad(handle,ShaderRegistry.indexOf(ShaderMode.MULTI_TEXTURE.defaultShaderName()),1,0,0,0,0,
                        0,0,0,32,64,32,64,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_ECS,0,entity);
                float[] v=new float[48];int[] corner={0,1,2,0,2,3};float[] sx={0,0,64,64},sy={0,32,32,0};
                for(int i=0;i<6;i++){int c=corner[i],o=i*8;v[o]=LightingCoordinates.worldX(sx[c],sy[c],0);
                    v[o+1]=LightingCoordinates.worldY(sx[c],sy[c],0);v[o+3]=c>=2?1:0;v[o+4]=c==1||c==2?1:0;v[o+7]=1;}
                q.lighting.entities.put(entity,new LightingSurface(v));LightingFrame.Light light=new LightingFrame.Light();
                light.x=32;light.y=0;light.z=128;light.radius=512;light.falloff=2;light.intensity=1;
                light.r=light.g=light.b=1;light.quality=0;light.resolution=512;q.lighting.lights.add(light);
                submit.prepareComposition();submit.setEnabled(true);
                Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                ByteBuffer pixel=BufferUtils.newByteBuffer(4);Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                int one=pixel.get(0)&255;Assert.assertTrue("Partial alpha must receive light, red="+one,one>10);
                q.lighting.lights.add(light);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                pixel.clear();Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                int two=pixel.get(0)&255;Assert.assertTrue("Independent light must increase contribution",two>one);
                // A strictly empty frame clears the field, skips light passes, and restores the next frame.
                final int[] lightPasses={0};
                q.lighting.timer=new LightingPassTimer(){
                    public void begin(int pass){if(pass==SHADOW||pass==RECEIVERS||pass==ACCUMULATION)lightPasses[0]++;}
                    public void end(int pass){}
                };
                for(int transition=0;transition<3;transition++){
                    q.size=0;lightPasses[0]=0;
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                    Assert.assertEquals("Empty queue must skip light passes",0,lightPasses[0]);
                    assertFieldCleared(submit);
                    pixel.clear();Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                    for(int c=0;c<4;c++)Assert.assertEquals("Empty frame must not retain coverage",0,pixel.get(c)&255);
                    for(int diagnostic=1;diagnostic<=3;diagnostic++){
                        q.lighting.diagnostic=diagnostic;lightPasses[0]=0;world.process();
                        Assert.assertTrue("Diagnostics retain their pass lifecycle",lightPasses[0]>0);
                    }
                    q.lighting.diagnostic=0;q.size=1;
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                    pixel.clear();Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                    Assert.assertEquals("Immediate return restores both lights",two,pixel.get(0)&255);
                }
                q.lighting.entities.get(entity).receiveLight=false;lightPasses[0]=0;
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                Assert.assertEquals("No lit receiver makes every light ineffective",0,lightPasses[0]);
                Assert.assertEquals(0,submit.lightingRenderer().receiverDraws);
                assertFieldCleared(submit);
                q.lighting.entities.get(entity).receiveLight=true;q.lighting.timer=null;
                // This caster projects entirely left of the camera, but shadows the receiver center.
                q.lighting.lights.clear();q.lighting.lights.add(light);
                light.x=-32;light.y=64;light.z=64;light.quality=1;
                float[] occluderVertices=new float[48];
                float[] ox={-12,-12,12,12},oy={20,44,44,20};
                for(int i=0;i<6;i++){
                    int c=corner[i],o=i*8;occluderVertices[o]=ox[c];occluderVertices[o+1]=oy[c];
                    occluderVertices[o+2]=32;occluderVertices[o+3]=c>=2?1:0;
                    occluderVertices[o+4]=c==1||c==2?1:0;occluderVertices[o+7]=1;
                    Assert.assertTrue("Whole caster is outside the camera",LightingCoordinates.screenX(ox[c],oy[c])<0);
                }
                LightingFrame.Caster offscreen=new LightingFrame.Caster();
                offscreen.surface=new LightingSurface(occluderVertices);offscreen.surface.alphaThreshold=.1f;
                offscreen.surface.actor=ShadowActorSeparation.enabled;
                offscreen.texture=handle;offscreen.u2=offscreen.v2=1;offscreen.bounds();
                q.lighting.casters.add(offscreen);
                final Throwable[] replayFailure={null};
                q.lighting.timer=new LightingPassTimer(){
                    public void begin(int pass){}
                    public void end(int pass){if(pass==SHADOW)try {assertReplayPrepared(submit);}catch(Throwable failure){replayFailure[0]=failure;}}
                };
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();pixel.clear();
                if(replayFailure[0]!=null)throw new AssertionError("Eligible shadow casters must prepare one reusable batch",replayFailure[0]);
                q.lighting.timer=null;
                Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                int withOffscreenCaster=pixel.get(0)&255;
                Assert.assertEquals(1,submit.lightingRenderer().selectedCasters);
                q.lighting.casters.clear();
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();pixel.clear();
                Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                Assert.assertTrue("Offscreen caster must actually darken the visible receiver",(pixel.get(0)&255)>withOffscreenCaster+3);
                LightingFrame.Caster invalidCaster=new LightingFrame.Caster();
                invalidCaster.surface=new LightingSurface(occluderVertices);invalidCaster.texture=0;invalidCaster.bounds();
                invalidCaster.surface.actor=ShadowActorSeparation.enabled;
                q.lighting.casters.add(invalidCaster);
                try {world.process();Assert.fail("An invalid shadow texture must not be skipped by P3 preflight");}
                catch(IllegalStateException expected){Assert.assertTrue(expected.getMessage().contains("texture handle"));}
                q.lighting.casters.clear();Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                Assert.assertEquals("A rejected shadow batch must recover on the next frame",GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                light.x=32;light.y=0;light.z=128;
                light.quality=2;Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                Assert.assertEquals(128,pixel.get(3)&255,3);
                light.quality=0;q.lighting.lights.clear();q.lighting.lights.add(light);
                Pixmap cutoutPixmap=new Pixmap(1,1,Pixmap.Format.RGBA8888);cutoutPixmap.setColor(.4f,.2f,.1f,.75f);cutoutPixmap.fill();
                Texture cutoutTexture=new Texture(cutoutPixmap);cutoutPixmap.dispose();
                for(int blend:new int[]{1,2,0,7}){
                    q.textureHandle[0]=blend==7?TextureRegistry.handleOf(cutoutTexture):handle;
                    q.blend[0]=blend;Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();pixel.clear();
                    Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                    Assert.assertEquals("Coverage/color rule for blend "+blend,blend==1?39:78,pixel.get(0)&255,3);
                    Assert.assertEquals("Alpha rule for blend "+blend,blend==0||blend==7?255:128,pixel.get(3)&255,3);
                }
                q.blend[0]=1;q.textureHandle[0]=handle;cutoutTexture.dispose();
                // Unsupported custom coverage fails explicitly, aborts, then permits a new frame.
                int originalShader=q.shader[0];
                com.badlogic.gdx.graphics.glutils.ShaderProgram custom=new com.badlogic.gdx.graphics.glutils.ShaderProgram(
                        "#version 330 core\nin vec2 a_position;uniform mat4 u_projTrans;void main(){gl_Position=u_projTrans*vec4(a_position,0,1);}",
                        "#version 330 core\nout vec4 c;void main(){c=vec4(.4,.2,.1,.5);}");
                Assert.assertTrue(custom.getLog(),custom.isCompiled());
                q.shader[0]=ShaderRegistry.register("lighting-unsupported-custom",custom,ShaderMode.MULTI_TEXTURE);
                com.badlogic.gdx.graphics.glutils.FrameBuffer nested=new com.badlogic.gdx.graphics.glutils.FrameBuffer(Pixmap.Format.RGBA8888,64,32,false);
                nested.begin();Gdx.gl.glViewport(2,3,60,28);Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(4,5,52,20);
                Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA,GL20.GL_ONE,GL20.GL_ZERO,GL20.GL_ONE);
                Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_REVERSE_SUBTRACT,GL20.GL_FUNC_ADD);
                submit.prepareComposition();
                try{world.process();Assert.fail("Custom coverage needs an explicit adapter");}
                catch(IllegalArgumentException expected){Assert.assertTrue(expected.getMessage().contains("unsupported shader"));}
                java.nio.IntBuffer state=BufferUtils.newIntBuffer(4);
                Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);Assert.assertEquals(nested.getFramebufferHandle(),state.get(0));
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,state);Assert.assertEquals(2,state.get(0));Assert.assertEquals(60,state.get(2));
                Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
                Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_BLEND));
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_BLEND_SRC_RGB,state);Assert.assertEquals(GL20.GL_SRC_ALPHA,state.get(0));
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_BLEND_DST_RGB,state);Assert.assertEquals(GL20.GL_ONE,state.get(0));
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_BLEND_SRC_ALPHA,state);Assert.assertEquals(GL20.GL_ZERO,state.get(0));
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_BLEND_EQUATION_RGB,state);Assert.assertEquals(GL20.GL_FUNC_REVERSE_SUBTRACT,state.get(0));
                q.shader[0]=originalShader;Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glViewport(0,0,64,32);
                submit.prepareComposition();world.process();Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                q.size=0;Gdx.gl.glViewport(2,3,60,28);Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                submit.prepareComposition();world.process();
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);
                Assert.assertEquals("Empty frame restores the nested target",nested.getFramebufferHandle(),state.get(0));
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,state);
                Assert.assertEquals(2,state.get(0));Assert.assertEquals(3,state.get(1));
                Assert.assertEquals(60,state.get(2));Assert.assertEquals(28,state.get(3));
                Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
                q.size=1;submit.prepareComposition();world.process();
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,state);
                Assert.assertEquals("Clipped light restores caller scissor x",4,state.get(0));
                Assert.assertEquals(5,state.get(1));Assert.assertEquals(52,state.get(2));Assert.assertEquals(20,state.get(3));
                Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
                nested.end();nested.dispose();
                world.dispose();world=null;batch.close();texture.dispose();ShaderRegistry.disposeAll();
            }catch(Throwable error){failure[0]=error;}finally{Gdx.app.exit();}}
        },config);
        if(failure[0]!=null)throw new AssertionError("Real GL 2.5D test failed",failure[0]);
    }
    private static void assertFieldCleared(RenderSubmitSystem submit) throws Exception {
        java.lang.reflect.Field compositionField=RenderSubmitSystem.class.getDeclaredField("composition");
        compositionField.setAccessible(true);
        java.lang.reflect.Field targetField=WorldLightComposition.class.getDeclaredField("field");
        targetField.setAccessible(true);
        com.badlogic.gdx.graphics.glutils.FrameBuffer target=(com.badlogic.gdx.graphics.glutils.FrameBuffer)
                targetField.get(compositionField.get(submit));
        java.nio.IntBuffer binding=BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,binding);
        java.nio.FloatBuffer pixel=BufferUtils.newFloatBuffer(4);
        try {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,target.getFramebufferHandle());
            Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_FLOAT,pixel);
            for(int c=0;c<4;c++)Assert.assertEquals("Empty frame clears the reused light field",0f,pixel.get(c),0f);
        } finally {Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,binding.get(0));}
    }
    private static void assertReplayPrepared(RenderSubmitSystem submit) throws Exception {
        java.lang.reflect.Field batchField=games.pixscape.runtime.render.lighting.LightingRenderer.class.getDeclaredField("batch");
        batchField.setAccessible(true);Object lightingBatch=batchField.get(submit.lightingRenderer());
        java.lang.reflect.Field replayField=lightingBatch.getClass().getDeclaredField("replayVertices");
        replayField.setAccessible(true);
        Assert.assertTrue("Eligible shadow casters must prepare one reusable batch",replayField.getInt(lightingBatch)>0);
    }
}
