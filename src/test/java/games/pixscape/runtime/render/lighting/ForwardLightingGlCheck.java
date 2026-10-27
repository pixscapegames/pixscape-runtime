package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.NormalMapBinding;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;

/** Integration checks in the existing desktop GL task; captures belong to the external device. */
public final class ForwardLightingGlCheck {
    public static void verify(){
        final Throwable[] failure={null};Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.disableAudio(true);config.setWindowedMode(64,32);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){@Override public void create(){
            try{run();}catch(Throwable t){failure[0]=t;}finally{ShaderRegistry.disposeAll();InternalTextures.dispose();TextureRegistry.clear();Gdx.app.exit();}
        }},config);
        if(failure[0]!=null)throw new AssertionError("Forward GL integration",failure[0]);
    }
    private static void run()throws Exception {
        ShaderRegistry.initDefaults();Gdx.gl.glViewport(0,0,64,32);
        Pixmap p=new Pixmap(1,1,Pixmap.Format.RGBA8888);p.setColor(1,.5f,.25f,1);p.fill();Texture texture=new Texture(p);
        Pixmap low=new Pixmap(1,1,Pixmap.Format.RGBA8888);low.setColor(1,.5f,.25f,.49f);low.fill();Texture belowCutout=new Texture(low);
        Pixmap high=new Pixmap(1,1,Pixmap.Format.RGBA8888);high.setColor(1,.5f,.25f,.51f);high.fill();Texture aboveCutout=new Texture(high);
        AtlasRuntimeService.TextureArrayBundle bundle=AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(texture,belowCutout,aboveCutout),1,1,GLCaps.detect());
        Assert.assertEquals(1,bundle.handle2layer.get(TextureRegistry.handleOf(texture),-1));
        Assert.assertEquals(2,bundle.handle2layer.get(TextureRegistry.handleOf(belowCutout),-1));
        Assert.assertEquals(3,bundle.handle2layer.get(TextureRegistry.handleOf(aboveCutout),-1));
        p.dispose();low.dispose();high.dispose();
        TextureArrayMeshBatch batch=new TextureArrayMeshBatch(64);batch.setTextureArrayBundle(bundle);
        FrameRenderQueue q=new FrameRenderQueue(16);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
        OrthographicCamera camera=new OrthographicCamera(64,32);camera.position.set(32,16,0);camera.update();
        RenderStats stats=new RenderStats();RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,camera,.01f,.01f,.01f,batch,stats,new RenderStatsSink(100),true);
        World world=new World(new WorldConfigurationBuilder().with(submit).build());submit.setEnabled(false);
        world.getMapper(PointLightComponent.class).create(world.create());world.process();
        submit.prepareComposition();
        Assert.assertNull("No historical scratch target",field(submit.lightingRenderer(),"scratch"));
        Assert.assertNull("No historical receiver program",field(submit.lightingRenderer(),"receiver"));
        Object historical=field(submit,"composition");Assert.assertNull("No original target",field(historical,"original"));
        Assert.assertEquals(64L*32*8,submit.forwardRenderer().textureBytes());
        int shader=ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());
        assertCompactColorLayout(submit.forwardRenderer());
        submit.setEnabled(true);
        assertPackedColorSubmission(world,q,submit,texture,belowCutout,aboveCutout,shader,stats);
        q.addQuad(TextureRegistry.handleOf(texture),shader,4,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_VFX,0,10);
        q.ignoreAmbientLighting[0]=true;submit.setEnabled(true);
        clear();world.process();int exempt=red(32,16);Assert.assertTrue("Exempt VFX survives nearly black ambient",exempt>240);
        // Synthetic ADDITIVE policy witness; native emitters express ONE emission through
        // PREMULT_ALPHA with zero coverage (covered by ForwardParticleGlCheck below).
        q.blend[0]=BlendMode.ADDITIVE.id;q.colorPacked[0]=Color.toFloatBits(.5f,.25f,.125f,0);
        clear();world.process();Assert.assertTrue("ONE emission must not be discarded at zero alpha",red(32,16)>120);
        q.blend[0]=BlendMode.ADDITIVE_ALPHA.id;q.colorPacked[0]=Color.WHITE.toFloatBits();
        q.ignoreAmbientLighting[0]=false;clear();world.process();Assert.assertTrue(red(32,16)<5);
        q.ignoreAmbientLighting[0]=true;
        q.addQuad(TextureRegistry.handleOf(texture),shader,0,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_ECS,0,11);
        clear();world.process();Assert.assertTrue("Opaque nonreceiver still masks exempt VFX",red(32,16)<5);
        // All entries are admitted before the target is touched, including an unsupported tail.
        q.blend[1]=5;clear();int before=red(32,16);
        try{world.process();Assert.fail("Destination-dependent material must be rejected");}catch(IllegalArgumentException expected){Assert.assertTrue(expected.getMessage().contains("entity 11"));}
        Assert.assertEquals(before,red(32,16));q.blend[1]=0;
        q.lighting.lights.add(new LightingFrame.Light());q.lighting.lights.add(new LightingFrame.Light());
        try{world.process();Assert.fail("Second light must be rejected");}catch(IllegalArgumentException expected){Assert.assertTrue(expected.getMessage().contains("active lights"));}
        q.lighting.lights.clear();
        assertDrawFailureRestoresState(world);
        Object atlas=field(submit.lightingRenderer(),"atlas");Object topology=ForwardTopology.NATIVE;
        for(int i=0;i<3;i++){camera.position.x+=.25f;camera.update();submit.prepareComposition();clear();world.process();Assert.assertSame(atlas,field(submit.lightingRenderer(),"atlas"));Assert.assertSame(topology,ForwardTopology.NATIVE);}
        // Macro orientation replaces the geometric horizon AND the no-map Lambert,
        // while the original geometric normal remains available to shadow bias/PCF.
        q.size=1;q.blend[0]=0;q.ignoreAmbientLighting[0]=false;
        q.sourceDomain[0]=FrameRenderQueue.SOURCE_TILED;q.sourceSlot[0]=0;
        float[] physical=new float[48],curve=new float[18];
        for(int i=0;i<6;i++){physical[i*8+3]=i==2||i==4||i==5?1:0;physical[i*8+4]=i==1||i==2||i==4?1:0;physical[i*8+7]=1;curve[i*3]=1;}
        LightingSurface support=new LightingSurface(physical);support.forwardTopology=ForwardTopology.NATIVE;support.curveNormals=curve;q.lighting.tiles.put(0,support);
        LightingFrame.Light light=new LightingFrame.Light();light.x=20;light.z=-5;light.r=light.g=light.b=light.intensity=1;light.radius=1000;light.falloff=1;light.resolution=512;q.lighting.lights.add(light);
        submit.forwardRenderer().setRoundedLightingEnabled(false);clear();world.process();int faceted=red(32,16);
        submit.forwardRenderer().setRoundedLightingEnabled(true);clear();world.process();Assert.assertTrue("Continuous horizon must not be cut by planar E2",red(32,16)>faceted+150);
        light.x=-20;clear();world.process();Assert.assertEquals("Dark side keeps ambient only",faceted,red(32,16));
        q.lighting.lights.clear();clear();world.process();int ambient=red(32,16);
        submit.forwardRenderer().setRoundedLightingEnabled(false);clear();world.process();Assert.assertEquals("Ambient is independent",ambient,red(32,16));
        // Common neutral material: two planar facets share a continuous macro-normal.
        // Sample many pixels along their vertical join, not just the endpoints.
        camera.position.set(32,16,0);camera.update();q.x3[0]=q.x4[0]=32;
        q.addQuad(TextureRegistry.handleOf(texture),shader,0,0,0,0,0,32,0,32,32,64,32,64,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_TILED,1,12);
        q.lighting.tiles.put(0,neutralFacet(true));q.lighting.tiles.put(1,neutralFacet(false));
        light.x=100;light.y=light.z=0;light.radius=10000;q.lighting.lights.add(light);
        clear();world.process();Assert.assertTrue("Faceted control has a discontinuity",Math.abs(red(31,16)-red(32,16))>100);
        submit.forwardRenderer().setRoundedLightingEnabled(true);clear();world.process();
        for(int y=3;y<30;y+=3)Assert.assertTrue("Continuous neutral-material join at y="+y,Math.abs(red(31,y)-red(32,y))<8);
        light.x=-100;clear();world.process();
        for(int x=4;x<60;x+=4)Assert.assertTrue("No clipped-factor light leaking onto the dark side",red(x,16)<5);
        assertUnmappedNormalBatching(world,q,submit,texture,shader);
        assertCompactCapacityUpload(world,q,submit,texture,shader,stats,26);
        submit.setUseNormalMaps(false);submit.prepareComposition();
        assertCompactCapacityUpload(world,q,submit,texture,shader,stats,19);
        submit.setUseNormalMaps(true);submit.prepareComposition();
        // GLSL ES is compiled by this desktop driver; this is not a GLES/WebGL execution.
        String es="#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler2DArray;\n";
        ShaderProgram esProgram=new ShaderProgram(ForwardLightingRenderer.vertexSource(es),ForwardLightingRenderer.fragmentSource(es));
        Assert.assertTrue("GLSL ES compile: "+esProgram.getLog(),esProgram.isCompiled());esProgram.dispose();
        Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        NormalMapsDisabledGlCheck.verify(texture,belowCutout,aboveCutout,bundle);
        games.pixscape.runtime.service.NormalMapResourcesGlCheck.verify();
        world.dispose();batch.close();bundle.textureArray.dispose();texture.dispose();belowCutout.dispose();aboveCutout.dispose();
        ForwardParticleGlCheck.verify();
    }
    private static void clear(){Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);}
    private static void assertCompactColorLayout(ForwardLightingRenderer renderer)throws Exception{
        Mesh mesh=(Mesh)field(renderer,"mesh");VertexAttributes attributes=mesh.getVertexAttributes();
        Assert.assertEquals("Forward vertices use 26 four-byte slots",104,attributes.vertexSize);
        VertexAttribute color=null;
        for(int i=0;i<attributes.size();i++)if("a_color".equals(attributes.get(i).alias))color=attributes.get(i);
        Assert.assertNotNull(color);Assert.assertEquals(VertexAttributes.Usage.ColorPacked,color.usage);
        Assert.assertEquals(GL20.GL_UNSIGNED_BYTE,color.type);Assert.assertTrue(color.normalized);
        Assert.assertEquals(4,color.numComponents);Assert.assertEquals(44,color.offset);
    }
    private static void assertPackedColorSubmission(World world,FrameRenderQueue q,RenderSubmitSystem submit,Texture texture,Texture belowCutout,Texture aboveCutout,int shader,RenderStats stats)throws Exception{
        float[] colors={Color.toFloatBits(.25f,.5f,.75f,0),Color.toFloatBits(.25f,.5f,.75f,.5f),Color.WHITE.toFloatBits()};
        for(float color:colors){
            q.size=0;q.lighting.lights.clear();q.lighting.tiles.clear();
            q.addQuad(TextureRegistry.handleOf(texture),shader,BlendMode.OPAQUE.id,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,color,(byte)0,FrameRenderQueue.SOURCE_VFX,0,10);
            q.ignoreAmbientLighting[0]=true;int uploadsBefore=stats.bufferUploads;clear();world.process();
            float[] staging=(float[])field(submit.forwardRenderer(),"data");
            for(int vertex=0;vertex<6;vertex++)Assert.assertEquals("Packed ABGR is copied unchanged",Float.floatToRawIntBits(color),Float.floatToRawIntBits(staging[vertex*ForwardLightingRenderer.VERTEX_STRIDE+ForwardLightingRenderer.COLOR_OFFSET]));
            int bits=Float.floatToRawIntBits(color),pixel=rgba(32,16);
            assertChannel("red",(pixel>>>24)&255,bits&255,255);assertChannel("green",(pixel>>>16)&255,(bits>>>8)&255,127);assertChannel("blue",(pixel>>>8)&255,(bits>>>16)&255,63);
            Assert.assertEquals("Opaque output keeps its coverage",255,pixel&255);
            Assert.assertEquals(6L*ForwardLightingRenderer.VERTEX_STRIDE*4,submit.forwardRenderer().uploadedBytes);
            Assert.assertEquals(1,submit.forwardRenderer().worldDraws);Assert.assertEquals(uploadsBefore+1,stats.bufferUploads);
        }
        float tint=Color.toFloatBits(.25f,.5f,.75f,.5f);q.size=0;
        q.addQuad(TextureRegistry.handleOf(belowCutout),shader,BlendMode.CUTOUT.id,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,tint,(byte)0,FrameRenderQueue.SOURCE_VFX,0,10);
        q.ignoreAmbientLighting[0]=true;clear();world.process();Assert.assertEquals("Texture alpha below the cutout threshold is discarded",0,rgba(32,16));
        q.textureHandle[0]=TextureRegistry.handleOf(aboveCutout);clear();world.process();Assert.assertTrue("Texture alpha above the cutout threshold retains the tinted receiver",((rgba(32,16)>>>24)&255)>0);
        q.size=0;
        q.addQuad(TextureRegistry.handleOf(texture),shader,BlendMode.ALPHA.id,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,tint,(byte)0,FrameRenderQueue.SOURCE_VFX,0,10);
        q.ignoreAmbientLighting[0]=true;clear();world.process();int pixel=rgba(32,16),bits=Float.floatToRawIntBits(tint),alpha=bits>>>24;
        assertAlphaChannel("red",(pixel>>>24)&255,bits&255,255,alpha);assertAlphaChannel("green",(pixel>>>16)&255,(bits>>>8)&255,127,alpha);
        assertAlphaChannel("blue",(pixel>>>8)&255,(bits>>>16)&255,63,alpha);Assert.assertEquals("ALPHA output keeps the old decoded tint alpha",alpha,pixel&255,1);
        q.size=0;
    }
    private static void assertChannel(String label,int actual,int tint,int texel){
        int expected=(tint*texel+127)/255;Assert.assertTrue(label+": expected "+expected+", got "+actual,Math.abs(actual-expected)<=1);
    }
    private static void assertAlphaChannel(String label,int actual,int tint,int texel,int alpha){
        int expected=(tint*texel*alpha+32512)/(255*255);Assert.assertTrue(label+": expected "+expected+", got "+actual,Math.abs(actual-expected)<=1);
    }
    private static int rgba(int x,int y){Pixmap p=Pixmap.createFromFrameBuffer(x,y,1,1);try{return p.getPixel(0,0);}finally{p.dispose();}}
    private static void assertCompactCapacityUpload(World world,FrameRenderQueue q,RenderSubmitSystem submit,Texture texture,int shader,RenderStats stats,int stride){
        int quads=2049,uploadsBefore=stats.bufferUploads,verticesBefore=stats.flushedVertices;q.size=0;q.lighting.lights.clear();q.lighting.tiles.clear();
        for(int i=0;i<quads;i++){
            q.addQuad(TextureRegistry.handleOf(texture),shader,BlendMode.OPAQUE.id,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_VFX,i,i);
            q.ignoreAmbientLighting[i]=true;
        }
        clear();world.process();long vertices=6L*quads,bytes=vertices*stride*4;
        Assert.assertEquals("Packed color does not change the vertex count",vertices,stats.flushedVertices-verticesBefore);
        Assert.assertEquals(bytes,submit.forwardRenderer().uploadedBytes);Assert.assertEquals(2,submit.forwardRenderer().worldDraws);
        Assert.assertEquals(1,submit.forwardRenderer().capacityFlushes);Assert.assertEquals(1,submit.forwardRenderer().endFlushes);
        Assert.assertEquals(uploadsBefore+2,stats.bufferUploads);q.size=0;
    }
    private static void assertUnmappedNormalBatching(World world,FrameRenderQueue q,RenderSubmitSystem submit,Texture color,int shader)throws Exception{
        Pixmap pix=new Pixmap(1,1,Pixmap.Format.RGBA8888);pix.setColor(.5f,.5f,1,1);pix.fill();
        Texture a=new Texture(pix),b=new Texture(pix);pix.dispose();
        java.lang.reflect.Constructor<NormalMapBinding> ctor=NormalMapBinding.class.getDeclaredConstructor(
                com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion.class,com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion.class);
        ctor.setAccessible(true);
        com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion region=new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(color,0,0,1,1);
        NormalMapBinding na=ctor.newInstance(region,new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(a,0,0,1,1));
        NormalMapBinding nb=ctor.newInstance(region,new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(b,0,0,1,1));
        try{
            q.size=0;q.lighting.lights.clear();q.lighting.tiles.clear();
            float tint=Color.toFloatBits(.25f,.5f,.75f,.5f);
            for(int i=0;i<6;i++){
                q.addQuad(TextureRegistry.handleOf(color),shader,1,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,tint,(byte)0,FrameRenderQueue.SOURCE_TILED,i,100+i);
                LightingSurface s=neutralFacet(true);s.normalMap=i==1||i==3?na:i==4?nb:null;q.lighting.tiles.put(i,s);
            }
            for(int frame=0;frame<3;frame++){
                clear();world.process();
                Assert.assertEquals("Initial unmapped then two real normal pages",3,submit.forwardRenderer().worldDraws);
                Assert.assertEquals("No cuts for unmapped entries between/after real pages",2,submit.forwardRenderer().normalFlushes);
                Assert.assertEquals(0,submit.forwardRenderer().blendFlushes);
                float[] staging=(float[])field(submit.forwardRenderer(),"data");
                Assert.assertEquals("Normal-map batches retain the packed tint",Float.floatToRawIntBits(tint),Float.floatToRawIntBits(staging[ForwardLightingRenderer.COLOR_OFFSET]));
            }
            // A new frame starts with no normal page retained from the previous one.
            q.size=1;clear();world.process();Assert.assertEquals(1,submit.forwardRenderer().worldDraws);
            Assert.assertEquals(0,submit.forwardRenderer().normalFlushes);
            Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        }finally{a.dispose();b.dispose();}
    }
    private static void assertDrawFailureRestoresState(World world){
        final GL20 original=Gdx.gl;final GL30 original30=Gdx.gl30;
        java.nio.IntBuffer state=com.badlogic.gdx.utils.BufferUtils.newIntBuffer(4);
        original.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);int framebuffer=state.get(0);
        original.glEnable(GL20.GL_DEPTH_TEST);original.glDepthFunc(GL20.GL_GREATER);original.glDepthMask(false);
        original.glEnable(GL20.GL_SCISSOR_TEST);original.glScissor(2,3,20,15);
        GL30 rejecting=(GL30)java.lang.reflect.Proxy.newProxyInstance(GL30.class.getClassLoader(),new Class[]{GL30.class},(p,m,a)->{
            if(m.getName().equals("glDrawArrays")){
                original.glDepthFunc(GL20.GL_LESS);original.glDepthMask(true);
                throw new LightingAdmissionException("Injected recoverable draw refusal");
            }
            try{return m.invoke(original30,a);}catch(java.lang.reflect.InvocationTargetException ex){throw ex.getCause();}
        });
        try{
            Gdx.gl=Gdx.gl20=Gdx.gl30=rejecting;
            try{world.process();Assert.fail("Draw refusal must propagate to the viewport boundary");}
            catch(LightingAdmissionException expected){Assert.assertTrue(expected.getMessage().contains("Injected"));}
        }finally{Gdx.gl=Gdx.gl20=original;Gdx.gl30=original30;}
        original.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);Assert.assertEquals(framebuffer,state.get(0));
        original.glGetIntegerv(GL20.GL_DEPTH_FUNC,state);Assert.assertEquals(GL20.GL_GREATER,state.get(0));
        original.glGetIntegerv(GL20.GL_DEPTH_WRITEMASK,state);Assert.assertEquals(0,state.get(0));
        Assert.assertTrue(original.glIsEnabled(GL20.GL_DEPTH_TEST));Assert.assertTrue(original.glIsEnabled(GL20.GL_SCISSOR_TEST));
        original.glGetIntegerv(GL20.GL_SCISSOR_BOX,state);Assert.assertEquals(2,state.get(0));Assert.assertEquals(3,state.get(1));Assert.assertEquals(20,state.get(2));Assert.assertEquals(15,state.get(3));
        original.glGetIntegerv(GL20.GL_VIEWPORT,state);Assert.assertEquals(64,state.get(2));Assert.assertEquals(32,state.get(3));
        original.glDisable(GL20.GL_DEPTH_TEST);original.glDisable(GL20.GL_SCISSOR_TEST);original.glDepthFunc(GL20.GL_LESS);original.glDepthMask(true);
        clear();world.process(); // No stale aborted batch survives the correction.
    }
    private static LightingSurface neutralFacet(boolean left){
        float[] v=new float[48],n=new float[18];int[] corners={0,1,2,0,2,3};
        for(int i=0;i<6;i++){int c=corners[i];boolean right=c>=2;v[i*8+3]=right?1:0;v[i*8+4]=c==1||c==2?1:0;v[i*8+(left?5:6)]=1;
            boolean shared=left?right:!right;n[i*3]=shared?.70710677f:left?1:0;n[i*3+1]=shared?.70710677f:left?0:1;}
        LightingSurface s=new LightingSurface(v);s.forwardTopology=ForwardTopology.NATIVE;s.curveNormals=n;return s;
    }
    private static int red(int x,int y){Pixmap p=Pixmap.createFromFrameBuffer(x,y,1,1);try{return p.getPixel(0,0)>>>24;}finally{p.dispose();}}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
}
