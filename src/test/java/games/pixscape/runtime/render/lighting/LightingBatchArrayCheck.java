package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.render.batch.GLCaps;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import org.junit.Assert;
import java.lang.reflect.*;
import java.nio.ByteBuffer;

/** Real GL shadow-array ordering, 2D transitions, publication, capacity and borrowed lifetime. */
public final class LightingBatchArrayCheck {
    public static void verify() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);cfg.setWindowedMode(32,32);cfg.disableAudio(true);
        cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){public void create(){
            try{check();}catch(Throwable t){failure[0]=t;}finally{Gdx.app.exit();}
        }},cfg);
        if(failure[0]!=null)throw new AssertionError("Shadow array",failure[0]);
    }
    private static void check() throws Exception {
        Texture[] textures=new Texture[5];Pixmap[] sourcePixmaps=new Pixmap[5];LightingBatch batch=new LightingBatch();
        AtlasRuntimeService.TextureArrayBundle bundle=null,replacement=null;
        ShaderProgram shader=new ShaderProgram("#version 330 core\nin vec3 a_position;in vec2 a_uv;in vec4 a_flags;out vec2 uv;flat out vec4 flags;void main(){uv=a_uv;flags=a_flags;gl_Position=vec4(a_position,1);}",
                "#version 330 core\nin vec2 uv;flat in vec4 flags;uniform sampler2D u_tex0,u_tex1,u_tex2,u_tex3;out vec4 result;void main(){vec4 c;if(flags.x<.5)c=texture(u_tex0,uv);else if(flags.x<1.5)c=texture(u_tex1,uv);else if(flags.x<2.5)c=texture(u_tex2,uv);else c=texture(u_tex3,uv);if(c.a<flags.w)discard;result=c;}");
        Assert.assertTrue(shader.getLog(),shader.isCompiled());shader.bind();for(int i=0;i<4;i++)shader.setUniformi("u_tex"+i,i);
        ShaderProgram arrayShader=new ShaderProgram(shader.getVertexShaderSource(),
                "#version 330 core\nin vec2 uv;flat in vec4 flags;uniform sampler2DArray u_array;out vec4 result;void main(){vec4 c=texture(u_array,vec3(uv,flags.x));if(c.a<flags.w)discard;result=c;}");
        Assert.assertTrue(arrayShader.getLog(),arrayShader.isCompiled());arrayShader.bind();arrayShader.setUniformi("u_array",0);
        GL30 original=Gdx.gl30;long[] counts=new long[4];
        GL30 proxy=(GL30)Proxy.newProxyInstance(GL30.class.getClassLoader(),new Class[]{GL30.class},(p,m,a)->{
            String n=m.getName();if(n.equals("glBufferData")||n.equals("glBufferSubData"))counts[0]++;
            if(n.equals("glDrawArrays"))counts[1]++;
            if(n.startsWith("glGenBuffer")||n.startsWith("glDeleteBuffer"))counts[2]++;
            if(n.equals("glTexImage2D")||n.equals("glTexSubImage2D"))counts[3]++;
            try{return m.invoke(original,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
        try {
            for(int i=0;i<5;i++){
                Pixmap p=new Pixmap(2,1,Pixmap.Format.RGBA8888);p.setColor((i+1)/5f,.2f,1-i/5f,1);p.fill();p.drawPixel(i%2,0,0);
                sourcePixmaps[i]=p;textures[i]=new Texture(p);TextureRegistry.handleOf(textures[i]);
                textures[i].setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            }
            Array<Texture> pages=new Array<Texture>(textures);
            bundle=AtlasRuntimeService.buildTextureArrayFromTextures(pages,2,1,GLCaps.detect());
            LightingFrame frame=new LightingFrame();
            float[] triangle={-.9f,-.9f,0,0,0,0,0,1, .9f,-.9f,0,1,0,0,0,1, 0,.9f,0,.5f,1,0,0,1};
            for(int i=0;i<13;i++)add(frame,textures[i%5],triangle);
            Assert.assertFalse(batch.canReplay(frame));Assert.assertTrue(batch.canReplayArray(frame,frame.casterIndex.candidates,false,bundle));
            byte[] reference=render(batch,shader,arrayShader,frame,null);
            render(batch,shader,arrayShader,frame,bundle); // Warm bindings before counting.
            Gdx.gl=Gdx.gl20=proxy;Gdx.gl30=proxy;
            byte[] actual=render(batch,shader,arrayShader,frame,bundle);
            Assert.assertArrayEquals("Depth ties retain the exact caster order and coverage",reference,actual);
            Assert.assertEquals("One upload, reused by six views",1,counts[0]);
            Assert.assertEquals(6,counts[1]);Assert.assertEquals(0,counts[2]);Assert.assertEquals(0,counts[3]);
            // UV, texture, pose and category mutations are rebuilt in the next generation.
            frame.casters.first().u1=.4f;frame.casters.first().texture=frame.casters.get(1).texture;
            frame.casters.get(1).surface=new LightingSurface(triangle.clone());frame.casters.get(1).surface.vertices[0]=-.2f;
            Assert.assertArrayEquals(render(batch,shader,arrayShader,frame,null),render(batch,shader,arrayShader,frame,bundle));
            Assert.assertFalse(batch.canReplayArray(frame,frame.casterIndex.candidates,false,null));
            Assert.assertFalse(batch.canReplayArray(frame,frame.casterIndex.candidates,false,new AtlasRuntimeService.TextureArrayBundle(bundle.textureArray,null)));
            IntIntMap missing=new IntIntMap(bundle.handle2layer);missing.remove(frame.casters.first().texture,-1);
            AtlasRuntimeService.TextureArrayBundle absent=new AtlasRuntimeService.TextureArrayBundle(bundle.textureArray,missing);
            Assert.assertFalse(batch.canReplayArray(frame,frame.casterIndex.candidates,false,absent));
            Assert.assertArrayEquals(reference=render(batch,shader,arrayShader,frame,null),render(batch,shader,arrayShader,frame,absent));
            Assert.assertArrayEquals(reference,render(batch,shader,arrayShader,frame,bundle));
            pages.reverse();replacement=AtlasRuntimeService.buildTextureArrayFromTextures(pages,2,1,GLCaps.detect());
            bundle.textureArray.dispose();
            Assert.assertFalse("Disposed bundles must not replay",batch.canReplayArray(frame,frame.casterIndex.candidates,false,bundle));
            Assert.assertArrayEquals("A new publication has its own layer addresses",reference,render(batch,shader,arrayShader,frame,replacement));
            IntIntMap invalid=new IntIntMap(replacement.handle2layer);invalid.put(frame.casters.first().texture,replacement.textureArray.getDepth());
            Assert.assertFalse(batch.canReplayArray(frame,frame.casterIndex.candidates,false,new AtlasRuntimeService.TextureArrayBundle(replacement.textureArray,invalid)));
            for(int i=0;i<frame.casters.size;i++)frame.casters.get(i).surface.actor=true;
            Assert.assertFalse("Empty decor does not replay old geometry",batch.canReplayArray(frame,frame.casterIndex.candidates,true,replacement));
            frame.casterIndex.candidates.clear();Assert.assertFalse(batch.canReplayArray(frame,frame.casterIndex.candidates,false,replacement));
            frame.casters.clear();float[] large=new float[(12288+6)*8];for(int i=0;i<large.length;i+=24)System.arraycopy(triangle,0,large,i,24);
            add(frame,textures[0],large);
            long created=counts[2];Assert.assertFalse("Capacity mutations use the existing 2D path",batch.canReplayArray(frame,frame.casterIndex.candidates,false,replacement));
            reference=render(batch,shader,arrayShader,frame,null);actual=render(batch,shader,arrayShader,frame,replacement);Assert.assertArrayEquals(reference,actual);
            Assert.assertEquals("Capacity changes must not create buffers",created,counts[2]);
            batch.dispose();batch=null;Assert.assertTrue("Only the owner disposes the borrowed array",replacement.textureArray.getTextureObjectHandle()!=0);
            Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        } finally {
            Gdx.gl=Gdx.gl20=original;Gdx.gl30=original;if(batch!=null)batch.dispose();shader.dispose();arrayShader.dispose();
            if(bundle!=null&&bundle.textureArray.getTextureObjectHandle()!=0)bundle.textureArray.dispose();
            if(replacement!=null)replacement.textureArray.dispose();for(Texture t:textures)if(t!=null)t.dispose();
            for(Pixmap p:sourcePixmaps)if(p!=null)p.dispose();
        }
    }
    private static void add(LightingFrame f,Texture texture,float[] v){
        LightingFrame.Caster c=new LightingFrame.Caster();c.surface=new LightingSurface(v);c.surface.alphaThreshold=.5f;
        c.texture=TextureRegistry.handleOf(texture);c.u2=c.v2=1;f.casters.add(c);f.casterIndex.candidates.add(f.casters.size-1);
    }
    private static byte[] render(LightingBatch b,ShaderProgram s,ShaderProgram arrayShader,LightingFrame f,AtlasRuntimeService.TextureArrayBundle bundle){
        boolean array=b.canReplayArray(f,f.casterIndex.candidates,false,bundle);
        Gdx.gl.glDisable(GL20.GL_BLEND);Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);Gdx.gl.glDepthMask(true);Gdx.gl.glDepthFunc(GL20.GL_LESS);
        for(int face=0;face<6;face++){
            Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
            if(array&&face>0)b.replayArray();else{if(array)b.beginArray(arrayShader,null,bundle);else b.begin(s,null);draw(b,f);b.flush();}
        }
        ByteBuffer read=BufferUtils.newByteBuffer(32*32*4);Gdx.gl.glReadPixels(0,0,32,32,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,read);
        byte[] pixels=new byte[read.remaining()];read.get(pixels);return pixels;
    }
    private static void draw(LightingBatch b,LightingFrame f){for(int i=0;i<f.casters.size;i++){
        LightingFrame.Caster c=f.casters.get(i);b.draw(c.surface,c.texture,c.u1,c.v1,c.u2,c.v2,Color.WHITE.toFloatBits(),0,null,0,0,true,true);
    }}
}
