package games.pixscape.runtime.render;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;

/** Real supplied materials: relocated atlas regions, neighbours, flips, alpha and entity rows. */
public final class SuppliedMaterialGlCheck {
    public static void verify() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.disableAudio(true);config.setWindowedMode(128,32);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
        new Lwjgl3Application(new ApplicationAdapter(){public void create(){
            try {run(new FileHandle("build/surface-validation"),new TextureArrayMeshBatch(8));}
            catch(Throwable error){failure[0]=error;}finally{Gdx.app.exit();}
        }},config);
        if(failure[0]!=null)throw new AssertionError("Supplied material regions",failure[0]);
    }
    public static void run(FileHandle evidence,MetricsBatch batch) {
        InternalTextures.dispose();TextureRegistry.clear();ShaderRegistry.disposeAll();ShaderRegistry.initDefaults();
        Pixmap pixels=new Pixmap(128,64,Pixmap.Format.RGBA8888);pixels.setBlending(Pixmap.Blending.None);
        pixels.setColor(1,0,1,1);pixels.fill();
        for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
            float alpha=x<4||y<4||x>=28||y>=28?0f:(x<7?.4f:1f);
            int color=Color.rgba8888(.1f+x/80f,.2f+y/64f,.15f,alpha);
            pixels.drawPixel(4+x,4+y,color);pixels.drawPixel(64+x,16+y,color);
        }
        Texture texture=new Texture(pixels);
        Array<Texture> textures=new Array<>();textures.add(texture);
        AtlasRuntimeService.TextureArrayBundle bundle=AtlasRuntimeService.buildTextureArrayFromTextures(textures,128,64,GLCaps.detect());pixels.dispose();bundle.textureArray.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        FrameBuffer target=new FrameBuffer(Pixmap.Format.RGBA8888,128,32,false);
        batch.setTextureArrayBundle(bundle);int handle=TextureRegistry.handleOf(texture);
        try {
            for(String name:new String[]{"glow_pulse","wave_uv","outline_soft"}) {
                int index=ShaderRegistry.indexOf(name);SurfaceShader surface=ShaderRegistry.getSurface(index);
                Assert.assertNotNull(name,surface);Assert.assertNull("No implicit enlarged/deformed shadow: "+name,surface.maskBody);
                Assert.assertEquals(name.equals("glow_pulse")?SurfaceShader.Lighting.UNLIT:SurfaceShader.Lighting.LIT,surface.lighting);
                ShaderRegistry.testCompileSurface(name,surface);
                Array<ShaderFloatParam> on=new Array<>(),off=new Array<>();
                String parameter=name.equals("glow_pulse")?"u_boost":name.equals("wave_uv")?"u_amplitude":"u_outlineIntensity";
                on.add(new ShaderFloatParam(parameter,name.equals("wave_uv")?.15f:1f));off.add(new ShaderFloatParam(parameter,0));
                if(name.equals("outline_soft")){on.add(new ShaderFloatParam("u_outlineSize",.09f));off.add(new ShaderFloatParam("u_outlineSize",.09f));}
                Pixmap reference=draw(batch,target,handle,index,on,on);
                try {
                    for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
                        int first=reference.getPixel(x,y);
                        close(name+" atlas placement",first,reference.getPixel(32+x,y),1);
                        close(name+" horizontal flip",first,reference.getPixel(64+31-x,y),1);
                        close(name+" vertical flip",first,reference.getPixel(96+x,31-y),1);
                    }
                    Assert.assertEquals(name+" must not sample magenta neighbour",0,reference.getPixel(0,0)&255);
                    PixmapIO.writePNG(evidence.child(name+"-regions.png"),reference,-1,true);
                    Pixmap changed=draw(batch,target,handle,index,on,off);
                    try {
                        int differences=0;
                        for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
                            close(name+" first entity is unchanged",reference.getPixel(x,y),changed.getPixel(x,y),0);
                            if(reference.getPixel(32+x,y)!=changed.getPixel(32+x,y))differences++;
                        }
                        Assert.assertTrue(name+" keeps its actual effect and per-entity parameter",differences>20);
                        if(name.equals("outline_soft"))Assert.assertTrue("Outline changes coverage inside the region",covered(reference,32)>covered(changed,32));
                        if(name.equals("glow_pulse"))for(int y=0;y<32;y++)for(int x=0;x<32;x++)Assert.assertEquals("Glow preserves alpha",reference.getPixel(x,y)&255,changed.getPixel(32+x,y)&255);
                    } finally {changed.dispose();}
                } finally {reference.dispose();}
                Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
            }
            System.out.println("SUPPLIED_MATERIAL_REGIONS_PASS glow_pulse/wave_uv/outline_soft relocation flips alpha neighbours parameters");
        } finally {batch.close();target.dispose();bundle.textureArray.dispose();texture.dispose();ShaderRegistry.disposeAll();InternalTextures.dispose();TextureRegistry.clear();}
    }
    private static Pixmap draw(MetricsBatch batch,FrameBuffer target,int texture,int shader,Array<ShaderFloatParam> left,Array<ShaderFloatParam> right) {
        target.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        RenderStats stats=new RenderStats();ShaderProgram program=ShaderRegistry.getByIdx(shader);
        program.bind();program.setUniformf("u_time",.75f);program.setUniformf("u_ambientMul",1,1,1);program.setUniformf("u_cutoutThreshold",-1);program.setUniformf("u_worldCoverage",0);
        batch.begin(new Matrix4().setToOrtho2D(0,0,128,32),stats);batch.setShader(program,stats);batch.setParameterLayout(ShaderRegistry.getParameterLayout(shader),stats);
        batch.setBlendMode(false,GL20.GL_ONE,GL20.GL_ZERO,stats);batch.setColor(.8f,.7f,1,.7f);
        for(int i=0;i<4;i++) {
            batch.setEntityParameters(i==1?right:left,stats);
            float u=i==1?64f/128:4f/128,v=i==1?16f/64:4f/64,u2=u+32f/128,v2=v+32f/64;
            if(i==2){float t=u;u=u2;u2=t;}if(i==3){float t=v;v=v2;v2=t;}
            float x=i*32;batch.draw(texture,x,0,x,32,x+32,32,x+32,0,u,v,u2,v2,stats);
        }
        batch.end(stats);Pixmap result=Pixmap.createFromFrameBuffer(0,0,128,32);target.end();return result;
    }
    private static int covered(Pixmap image,int offset){int n=0;for(int y=0;y<32;y++)for(int x=0;x<32;x++)if((image.getPixel(offset+x,y)&255)>0)n++;return n;}
    private static void close(String label,int a,int b,int tolerance){for(int shift:new int[]{0,8,16,24})Assert.assertEquals(label,(a>>>shift)&255,(b>>>shift)&255,tolerance);}
}
