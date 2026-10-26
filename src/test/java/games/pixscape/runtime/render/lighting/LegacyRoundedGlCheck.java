package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;

/** Real historical receiver shader: spatial seam continuity during a full light tour. */
public final class LegacyRoundedGlCheck {
    public static void verify() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.disableAudio(true);config.setWindowedMode(64,32);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){@Override public void create(){
            try{run();}catch(Throwable t){failure[0]=t;}finally{ShaderRegistry.disposeAll();InternalTextures.dispose();TextureRegistry.clear();Gdx.app.exit();}
        }},config);
        if(failure[0]!=null)throw new AssertionError("Historical rounded reception",failure[0]);
    }
    private static void run() throws Exception {
        ShaderRegistry.initDefaults();
        Pixmap pixel=new Pixmap(1,1,Pixmap.Format.RGBA8888);pixel.setColor(Color.WHITE);pixel.fill();
        Texture texture=new Texture(pixel);pixel.dispose();
        AtlasRuntimeService.TextureArrayBundle bundle=AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(texture),1,1,GLCaps.detect());
        TextureArrayMeshBatch batch=new TextureArrayMeshBatch(64);batch.setTextureArrayBundle(bundle);
        FrameRenderQueue q=new FrameRenderQueue(4);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
        OrthographicCamera camera=new OrthographicCamera(64,32);camera.position.set(32,16,0);camera.update();
        RenderStats stats=new RenderStats();
        RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,camera,0,0,0,batch,stats,new RenderStatsSink(100),false);
        World world=new World(new WorldConfigurationBuilder().with(submit).build());submit.setEnabled(false);
        world.getMapper(PointLightComponent.class).create(world.create());world.process();submit.prepareComposition();
        int shader=ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());
        for(int side=0;side<2;side++) {
            float x=side*32;
            q.addQuad(TextureRegistry.handleOf(texture),shader,0,0,0,0,0,x,0,x,32,x+32,32,x+32,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_TILED,side,10+side);
            float[] v=new float[48],n=new float[18];int[] corners={0,1,2,0,2,3};
            for(int i=0;i<6;i++) {int c=corners[i];boolean right=c>=2;
                v[i*8+3]=right?1:0;v[i*8+4]=c==1||c==2?1:0;v[i*8+(side==0?5:6)]=1;
                boolean shared=side==0?right:!right;n[i*3]=shared?.70710677f:side==0?1:0;n[i*3+1]=shared?.70710677f:side==0?0:1;
            }
            LightingSurface s=new LightingSurface(v);s.curveNormals=n;q.lighting.tiles.put(side,s);
        }
        LightingFrame.Light light=new LightingFrame.Light();light.x=100;light.y=5;light.r=light.g=light.b=light.intensity=1;light.radius=1000;light.falloff=1;light.resolution=512;q.lighting.lights.add(light);
        submit.setEnabled(true);
        for(int angle=0;angle<360;angle+=5) {
            light.x=(float)Math.cos(Math.toRadians(angle))*100;light.y=(float)Math.sin(Math.toRadians(angle))*100;
            stats.reset();world.process();
            Pixmap shot=Pixmap.createFromFrameBuffer(31,16,2,1);
            try{Assert.assertTrue("No facade seam at light angle "+angle,Math.abs((shot.getPixel(0,0)>>>24)-(shot.getPixel(1,0)>>>24))<9);}finally{shot.dispose();}
            Assert.assertEquals(stats.drawCalls,stats.colorDraws+stats.receiverDraws+stats.accumulationDraws+stats.shadowDraws+stats.ambientDraws+stats.presentationDraws);
        }
        Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        world.dispose();batch.close();bundle.textureArray.dispose();texture.dispose();
    }
}
