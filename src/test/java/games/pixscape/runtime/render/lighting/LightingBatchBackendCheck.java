package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public final class LightingBatchBackendCheck {
    private static final int REPLAY_CAPACITY=12288;
    public static void assertHtmlAndNativeWriteIdenticalActiveVertices() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);cfg.setWindowedMode(32,32);cfg.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter(){
            @Override public void create(){
                Application nativeApp=Gdx.app;
                Application htmlApp=null;
                LightingBatch nativeBatch=null,htmlBatch=null;
                try {
                    nativeBatch=new LightingBatch();
                    Gdx.app=(Application)Proxy.newProxyInstance(Application.class.getClassLoader(),
                            new Class<?>[]{Application.class},(proxy,method,args)->{
                                if(method.getName().equals("getType"))return Application.ApplicationType.WebGL;
                                if(method.getName().equals("equals"))return proxy==args[0];
                                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                                return method.invoke(nativeApp,args);
                            });
                    htmlApp=Gdx.app;
                    htmlBatch=new LightingBatch();
                    Gdx.app=nativeApp;
                    Field backend=LightingBatch.class.getDeclaredField("htmlVertexCopies");backend.setAccessible(true);
                    Assert.assertFalse(backend.getBoolean(nativeBatch));
                    Assert.assertTrue("Backend is captured at construction",backend.getBoolean(htmlBatch));
                    float[] vertices=new float[48];
                    for(int i=0;i<vertices.length;i++)vertices[i]=(i-21)*.125f;
                    LightingSurface surface=new LightingSurface(vertices);
                    surface.twoSided=true;surface.actor=true;surface.approximate=true;surface.alphaThreshold=.37f;
                    float[] quad={-7,3,-4,19,25,19,22,3};
                    Field data=LightingBatch.class.getDeclaredField("data");data.setAccessible(true);
                    Field count=LightingBatch.class.getDeclaredField("vertices");count.setAccessible(true);
                    for(boolean shadow:new boolean[]{false,true})for(boolean screen:new boolean[]{false,true}){
                        nativeBatch.abort();htmlBatch.abort();
                        nativeBatch.begin(null,null);htmlBatch.begin(null,null);
                        float color=new Color(.3f,.5f,.7f,.6f).toFloatBits();
                        nativeBatch.draw(surface,17,.1f,.2f,.8f,.9f,color,2,screen?quad:null,11,-9,shadow,true);
                        htmlBatch.draw(surface,17,.1f,.2f,.8f,.9f,color,2,screen?quad:null,11,-9,shadow,true);
                        Assert.assertEquals(6,count.getInt(nativeBatch));Assert.assertEquals(6,count.getInt(htmlBatch));
                        float[] a=(float[])data.get(nativeBatch),b=(float[])data.get(htmlBatch);
                        Assert.assertEquals(1f,a[14],0);
                        for(int i=0;i<6*21;i++)Assert.assertEquals("Vertex field "+i,Float.floatToRawIntBits(a[i]),Float.floatToRawIntBits(b[i]));
                    }
                    assertDeformedImageVertices(nativeBatch);
                    assertDeformedImageVertices(htmlBatch);
                    assertReceiverProvenance(nativeBatch);
                    assertReceiverProvenance(htmlBatch);
                }catch(Throwable error){failure[0]=error;}
                finally {
                    Gdx.app=htmlApp==null?nativeApp:htmlApp;if(htmlBatch!=null)htmlBatch.dispose();
                    Gdx.app=nativeApp;if(nativeBatch!=null)nativeBatch.dispose();Gdx.app.exit();
                }
            }
        },cfg);
        if(failure[0]!=null)throw new AssertionError("Backend vertex equivalence failed",failure[0]);
    }
    private static void assertReceiverProvenance(LightingBatch batch)throws Exception {
        Field dataField=LightingBatch.class.getDeclaredField("data");dataField.setAccessible(true);
        LightingSurface surface=new LightingSurface(new float[48]);
        for(boolean actor:new boolean[]{false,true})for(boolean twoSided:new boolean[]{false,true})for(boolean shadow:new boolean[]{false,true}){
            surface.actor=actor;surface.twoSided=twoSided;batch.abort();batch.begin(null,null);
            batch.draw(surface,17,0,0,1,1,Color.WHITE.toFloatBits(),0,null,0,0,shadow,true);
            float[] vertices=(float[])dataField.get(batch);
            float expected=twoSided?1:actor&&!shadow?-1:0;
            for(int i=0;i<6;i++)Assert.assertEquals("Receiver provenance, actor="+actor+", twoSided="+twoSided+", shadow="+shadow,expected,vertices[i*21+14],0);
        }
    }
    private static void assertDeformedImageVertices(LightingBatch batch)throws Exception {
        Field dataField=LightingBatch.class.getDeclaredField("data");dataField.setAccessible(true);
        Field count=LightingBatch.class.getDeclaredField("vertices");count.setAccessible(true);
        // The final queue includes a presentation offset; reception XYZ must keep its original pose.
        float[] base={-7,3,-4,19,25,19,22,3};int[] corners={0,1,2,0,2,3};
        for(int moved=-1;moved<4;moved++)for(boolean mapped:new boolean[]{false,true}){
            float[] q=base.clone();if(moved>=0){q[moved*2]+=7;q[moved*2+1]-=5;}
            LightingSurface s=new LightingSurface(new float[48]);s.twoSided=true;
            SpriteSurfaceSupport.sheet(s.vertices,q,.5f,0,13,1,-1);
            float[] original=s.vertices.clone(),presented=q.clone();
            for(int c=0;c<4;c++){presented[c*2]+=11;presented[c*2+1]-=9;}
            int stride=mapped?28:21;
            batch.abort();if(mapped)batch.beginMapped(null,null);else batch.begin(null,null);
            batch.draw(s,17,.2f,.3f,.7f,.9f,Color.WHITE.toFloatBits(),1,presented,0,0,false,true);
            float[] legacy=java.util.Arrays.copyOf((float[])dataField.get(batch),6*stride);
            s.imageQuad=true;
            batch.abort();if(mapped)batch.beginMapped(null,null);else batch.begin(null,null);
            batch.draw(s,17,.2f,.3f,.7f,.9f,Color.WHITE.toFloatBits(),1,presented,0,0,false,true);
            float[] data=(float[])dataField.get(batch);Assert.assertEquals(6,count.getInt(batch));
            for(int i=0;i<6;i++){
                int o=i*stride,c=corners[i]*2;
                Assert.assertEquals(presented[c],data[o+16],0);Assert.assertEquals(presented[c+1],data[o+17],0);
                Assert.assertEquals(LightingCoordinates.screenX(original[i*8],original[i*8+1])+11,data[o+16],.0001f);
                Assert.assertEquals(LightingCoordinates.screenY(original[i*8],original[i*8+1],original[i*8+2])-9,data[o+17],.0001f);
                for(int k=0;k<stride;k++)if(k!=16 && k!=17)
                    Assert.assertEquals("Only screen placement may change",Float.floatToRawIntBits(legacy[o+k]),Float.floatToRawIntBits(data[o+k]));
            }
            Assert.assertArrayEquals(original,s.vertices,0);
            // Casters ignore the image topology flag and continue using their prepared XYZ.
            batch.abort();batch.begin(null,null);
            batch.draw(s,17,0,0,1,1,Color.WHITE.toFloatBits(),0,null,0,0,true,true);
            float[] caster=java.util.Arrays.copyOf((float[])dataField.get(batch),6*21);
            s.imageQuad=false;batch.abort();batch.begin(null,null);
            batch.draw(s,17,0,0,1,1,Color.WHITE.toFloatBits(),0,null,0,0,true,true);
            for(int i=0;i<caster.length;i++)Assert.assertEquals(caster[i],((float[])dataField.get(batch))[i],0);
        }
        batch.abort();
    }
    public static void assertReplayEligibilityUsesBatchLimits() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);cfg.setWindowedMode(32,32);cfg.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter(){
            @Override public void create(){
                LightingBatch batch=null;Texture[] textures=new Texture[9];
                try {
                    TextureRegistry.clear();batch=new LightingBatch();
                    for(int i=0;i<textures.length;i++){
                        Pixmap pixmap=new Pixmap(1,1,Pixmap.Format.RGBA8888);
                        textures[i]=new Texture(pixmap);pixmap.dispose();TextureRegistry.handleOf(textures[i]);
                    }
                    LightingFrame atLimit=frame(textures[0],REPLAY_CAPACITY);
                    Assert.assertEquals(1,batch.requiredReplayBatches(atLimit,atLimit.casterIndex.candidates));
                    LightingFrame aboveLimit=frame(textures[0],REPLAY_CAPACITY+3);
                    Assert.assertEquals(2,batch.requiredReplayBatches(aboveLimit,aboveLimit.casterIndex.candidates));
                    LightingFrame fourTextures=new LightingFrame();
                    for(int i=0;i<4;i++)add(fourTextures,textures[i],3);
                    Assert.assertEquals(1,batch.requiredReplayBatches(fourTextures,fourTextures.casterIndex.candidates));
                    add(fourTextures,textures[4],3);
                    Assert.assertEquals(2,batch.requiredReplayBatches(fourTextures,fourTextures.casterIndex.candidates));
                    com.badlogic.gdx.utils.IntArray subset=new com.badlogic.gdx.utils.IntArray(new int[]{0,1});
                    Assert.assertEquals(1,batch.requiredReplayBatches(fourTextures,subset));
                    subset.clear();Assert.assertEquals(0,batch.requiredReplayBatches(fourTextures,subset));
                    assertGpuReplay(batch,atLimit);
                    assertGpuReplay(batch,aboveLimit);
                    assertGpuReplay(batch,fourTextures);
                    fourTextures.casters.peek().surface.actor=true;
                    assertGpuReplay(batch,fourTextures);
                    assertGpuReplay(batch,new LightingFrame());
                    Assert.assertEquals("Empty scene releases GPU replay storage",0,batch.replayBytes());
                    batch.dispose();batch=new LightingBatch(8);
                    LightingFrame eightTextures=new LightingFrame();
                    for(int i=0;i<8;i++)add(eightTextures,textures[i],3);
                    Assert.assertEquals(1,batch.requiredReplayBatches(eightTextures,eightTextures.casterIndex.candidates));
                    assertGpuReplay(batch,eightTextures);
                    add(eightTextures,textures[8],3);
                    Assert.assertEquals(2,batch.requiredReplayBatches(eightTextures,eightTextures.casterIndex.candidates));
                    assertGpuReplay(batch,eightTextures);
                    assertGpuReplay(batch,aboveLimit);
                }catch(Throwable error){failure[0]=error;}
                finally {
                    if(batch!=null)batch.dispose();for(Texture texture:textures)if(texture!=null)texture.dispose();
                    TextureRegistry.clear();Gdx.app.exit();
                }
            }
        },cfg);
        if(failure[0]!=null)throw new AssertionError("P3 replay eligibility failed",failure[0]);
    }
    private static void assertGpuReplay(LightingBatch batch,LightingFrame frame)throws Exception{
        int count=batch.requiredReplayBatches(frame,frame.casterIndex.candidates);
        batch.prepareReplayCapacity(count);
        com.badlogic.gdx.graphics.glutils.ShaderProgram shader=new com.badlogic.gdx.graphics.glutils.ShaderProgram(
                "attribute vec3 a_position;void main(){gl_Position=vec4(a_position,1.);}",
                "void main(){gl_FragColor=vec4(1.);}");
        Assert.assertTrue(shader.getLog(),shader.isCompiled());
        com.badlogic.gdx.graphics.GL20 original=Gdx.gl;
        int[] uploads={0},draws={0},creates={0};
        com.badlogic.gdx.graphics.GL20 proxy=(com.badlogic.gdx.graphics.GL20)Proxy.newProxyInstance(
                com.badlogic.gdx.graphics.GL20.class.getClassLoader(),new Class[]{com.badlogic.gdx.graphics.GL20.class},(p,m,a)->{
                    if(m.getName().equals("glBufferData")||m.getName().equals("glBufferSubData"))uploads[0]++;
                    if(m.getName().equals("glDrawArrays"))draws[0]++;
                    if(m.getName().equals("glGenBuffer"))creates[0]++;
                    try{return m.invoke(original,a);}catch(InvocationTargetException ex){throw ex.getCause();}
                });
        try{
            games.pixscape.runtime.render.batch.performance.RenderStats stats=new games.pixscape.runtime.render.batch.performance.RenderStats();
            Gdx.gl=Gdx.gl20=proxy;batch.begin(shader,stats,true);
            for(LightingFrame.Caster c:frame.casters)
                batch.draw(c.surface,c.texture,0,0,1,1,Color.WHITE.toFloatBits(),0,null,0,0,true,true);
            batch.flush();for(int i=1;i<6;i++)batch.replay();
            Assert.assertEquals("Upload once per GPU batch, not per face",count,uploads[0]);
            Assert.assertEquals(count*6,draws[0]);Assert.assertEquals(0,creates[0]);
            Assert.assertEquals(draws[0],stats.drawCalls);Assert.assertEquals(count,stats.flushes);
            Assert.assertEquals(count*5,stats.replayDraws);Assert.assertEquals(uploads[0],stats.bufferUploads);
            int flushes=stats.flushes;batch.flush();Assert.assertEquals(flushes,stats.flushes);
            stats.reset();Assert.assertEquals(0,stats.bufferUploads);Assert.assertEquals(0,stats.replayDraws);
            batch.abort();int before=draws[0];batch.replay();Assert.assertEquals("Abort cannot replay stale geometry",before,draws[0]);
        }finally{Gdx.gl=Gdx.gl20=original;shader.dispose();}
    }
    private static LightingFrame frame(Texture texture,int vertices){
        LightingFrame frame=new LightingFrame();add(frame,texture,vertices);return frame;
    }
    private static void add(LightingFrame frame,Texture texture,int vertices){
        LightingFrame.Caster caster=new LightingFrame.Caster();
        caster.surface=new LightingSurface(new float[vertices*8]);caster.texture=TextureRegistry.handleOf(texture);
        frame.casters.add(caster);frame.casterIndex.candidates.add(frame.casters.size-1);
    }
}
