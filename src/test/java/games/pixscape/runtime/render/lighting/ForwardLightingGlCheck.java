package games.pixscape.runtime.render.lighting;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.*;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.service.*;
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
        AtlasRuntimeService.TextureArrayBundle bundle=AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(texture),1,1,GLCaps.detect());
        p.dispose();
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
        // GLSL ES is compiled by this desktop driver; this is not a GLES/WebGL execution.
        String es="#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler2DArray;\n";
        ShaderProgram esProgram=new ShaderProgram(ForwardLightingRenderer.vertexSource(es),ForwardLightingRenderer.fragmentSource(es));
        Assert.assertTrue("GLSL ES compile: "+esProgram.getLog(),esProgram.isCompiled());esProgram.dispose();
        Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        world.dispose();batch.close();bundle.textureArray.dispose();texture.dispose();
        ForwardParticleGlCheck.verify();
    }
    private static void clear(){Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);}
    private static LightingSurface neutralFacet(boolean left){
        float[] v=new float[48],n=new float[18];int[] corners={0,1,2,0,2,3};
        for(int i=0;i<6;i++){int c=corners[i];boolean right=c>=2;v[i*8+3]=right?1:0;v[i*8+4]=c==1||c==2?1:0;v[i*8+(left?5:6)]=1;
            boolean shared=left?right:!right;n[i*3]=shared?.70710677f:left?1:0;n[i*3+1]=shared?.70710677f:left?0:1;}
        LightingSurface s=new LightingSurface(v);s.forwardTopology=ForwardTopology.NATIVE;s.curveNormals=n;return s;
    }
    private static int red(int x,int y){Pixmap p=Pixmap.createFromFrameBuffer(x,y,1,1);try{return p.getPixel(0,0)>>>24;}finally{p.dispose();}}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
}
