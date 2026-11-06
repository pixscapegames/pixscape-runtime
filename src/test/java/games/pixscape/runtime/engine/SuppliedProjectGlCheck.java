package games.pixscape.runtime.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.loading.SceneLoadHandle;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;

/** Existing saved-project fixture, real ECS materials and shadow pipeline on any GL3 backend. */
public final class SuppliedProjectGlCheck {
    public static void run(PixscapeEngine engine,FileHandle evidence) {
        for(String scene:new String[]{"off","legacy","forward"})for(String name:new String[]{"glow_pulse","wave_uv","outline_soft"}) {
            engine.resize(320,240);Gdx.gl.glViewport(0,0,320,240);
            SceneLoadHandle load=engine.beginLoadScene(scene);
            while(!load.isReady()&&!load.isFailed())load.update();if(load.isFailed())throw new IllegalStateException(load.failure());
            engine.getCamera().viewportWidth=320;engine.getCamera().viewportHeight=240;engine.getCamera().position.set(160,120,0);engine.getCamera().update();
            EntityRef image=entity(engine,2),caster=entity(engine,5),light=entity(engine,6);
            image.shader().use(name).clearFloats();caster.shader().use(name).clearFloats();
            light.light().setEnabled(false);engine.setLightingEnabled(false);
            engine.getWorld().getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).get(light.entityId()).shadowQuality=1;
            int direct=sample(engine);Assert.assertTrue(name+" Off produces pixels",(direct&255)>0);
            if(!scene.equals("off")) {
                Assert.assertTrue(engine.setLightingEnabled(true));engine.setAmbientLight(0,0,0);
                int ambient=sample(engine);
                // Direct RGBA8 and HDR composition can round to neighbouring 8-bit values on GLES.
                if(name.equals("glow_pulse"))for(int shift:new int[]{24,16,8,0})
                    Assert.assertEquals("Emissive glow stays Unlit",(direct>>>shift)&255,(ambient>>>shift)&255,1);
                else Assert.assertEquals(name+" receives black ambient",0,ambient>>>8);
                light.light().setEnabled(true);
                int lit=sample(engine);Assert.assertTrue(name+" active light produces pixels",(lit>>>8)!=0);
                Assert.assertEquals(scene.equals("forward"),engine.isForwardLighting());
                Texture atlas=engine.system(RenderSubmitSystem.class).lightingRenderer().shadowTexture();
                Pixmap withEffect=shadow(atlas);
                caster.shader().use(ShaderMode.TEXTURE_ARRAY.defaultShaderName());sample(engine);
                Pixmap original=shadow(atlas);
                try {
                    Assert.assertEquals(withEffect.getWidth(),original.getWidth());
                    for(int y=0;y<withEffect.getHeight();y++)for(int x=0;x<withEffect.getWidth();x++)
                        Assert.assertEquals(name+" keeps original caster alpha",original.getPixel(x,y),withEffect.getPixel(x,y));
                } finally {withEffect.dispose();original.dispose();}
            }
            sample(engine);Pixmap imagePixels=Pixmap.createFromFrameBuffer(0,0,320,240);
            try {PixmapIO.writePNG(evidence.child(name+"-"+scene+".png"),imagePixels,-1,true);}finally{imagePixels.dispose();}
            Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        }
        System.out.println("SUPPLIED_PROJECT_PASS each effect Off/Legacy/Forward, active lights, original shadow alpha");
    }
    private static EntityRef entity(PixscapeEngine engine,int id){return engine.api().entities().requireEntityId(engine.findEntityByStableId(id));}
    private static int sample(PixscapeEngine engine){Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);engine.update(0);engine.render();Pixmap p=Pixmap.createFromFrameBuffer(40,70,1,1);try{return p.getPixel(0,0);}finally{p.dispose();}}
    private static Pixmap shadow(Texture texture){
        FrameBuffer capture=new FrameBuffer(Pixmap.Format.RGBA8888,texture.getWidth(),texture.getHeight(),false);SpriteBatch batch=new SpriteBatch();
        try {capture.begin();Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_CULL_FACE);Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            batch.getProjectionMatrix().setToOrtho2D(0,0,texture.getWidth(),texture.getHeight());batch.disableBlending();batch.begin();batch.draw(texture,0,0);batch.end();
            Pixmap result=Pixmap.createFromFrameBuffer(0,0,texture.getWidth(),texture.getHeight());capture.end();Gdx.gl.glViewport(0,0,320,240);return result;
        } finally {batch.dispose();capture.dispose();}
    }
}
