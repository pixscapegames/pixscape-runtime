package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.MultiTextureMeshBatch;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Real framebuffer oracle: disabled maps must equal the existing renderer with absent bindings. */
final class NormalMapsDisabledGlCheck {
    static void verify(Texture color,Texture belowCutout,Texture aboveCutout,AtlasRuntimeService.TextureArrayBundle bundle)throws Exception {
        Pixmap pixels=new Pixmap(1,1,Pixmap.Format.RGBA8888);pixels.setColor(1,.5f,.5f,1);pixels.fill();
        Texture normal=new Texture(pixels);pixels.dispose();
        var ctor=NormalMapBinding.class.getDeclaredConstructor(TextureAtlas.AtlasRegion.class,TextureAtlas.AtlasRegion.class);ctor.setAccessible(true);
        NormalMapBinding binding=ctor.newInstance(new TextureAtlas.AtlasRegion(color,0,0,1,1),new TextureAtlas.AtlasRegion(normal,0,0,1,1));
        try {for(boolean forward:new boolean[]{false,true})verifyRenderer(forward,color,belowCutout,aboveCutout,bundle,binding);}
        finally {normal.dispose();}
    }
    private static void verifyRenderer(boolean forward,Texture color,Texture low,Texture high,
                                        AtlasRuntimeService.TextureArrayBundle bundle,NormalMapBinding binding)throws Exception {
        TextureArrayMeshBatch arrayBatch=forward?new TextureArrayMeshBatch(64):null;
        MultiTextureMeshBatch legacyBatch=forward?null:new MultiTextureMeshBatch(64);
        if(arrayBatch!=null)arrayBatch.setTextureArrayBundle(bundle);
        FrameRenderQueue q=new FrameRenderQueue(4);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
        OrthographicCamera camera=new OrthographicCamera(64,32);camera.position.set(32,16,0);camera.update();
        RenderStats stats=new RenderStats();
        RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,camera,.2f,.3f,.4f,
                forward?arrayBatch:legacyBatch,stats,new RenderStatsSink(100),forward);
        World world=new World(new WorldConfigurationBuilder().with(submit).build());submit.setEnabled(false);
        world.getMapper(PointLightComponent.class).create(world.create());world.process();submit.prepareComposition();submit.setEnabled(true);
        int shader=ShaderRegistry.indexOf((forward?ShaderMode.TEXTURE_ARRAY:ShaderMode.MULTI_TEXTURE).defaultShaderName());
        q.addQuad(TextureRegistry.handleOf(color),shader,0,0,0,0,0,0,0,0,32,64,32,64,0,0,0,1,1,
                Color.toFloatBits(.3f,.6f,.8f,.5f),(byte)0,FrameRenderQueue.SOURCE_TILED,0,7);
        float[] vertices=new float[48],curve=new float[18];int[] corner={0,1,2,0,2,3};float[] x={0,0,64,64},y={0,32,32,0};
        for(int i=0;i<6;i++){int c=corner[i],o=i*8;vertices[o]=LightingCoordinates.worldX(x[c],y[c],0);vertices[o+1]=LightingCoordinates.worldY(x[c],y[c],0);
            vertices[o+3]=c>=2?1:0;vertices[o+4]=c==1||c==2?1:0;vertices[o+7]=1;curve[i*3+1]=.6f;curve[i*3+2]=.8f;}
        LightingSurface surface=new LightingSurface(vertices);surface.curveNormals=curve;surface.forwardTopology=ForwardTopology.NATIVE;
        q.lighting.tiles.put(0,surface);q.lighting.entities.put(7,surface);
        LightingFrame.Light light=new LightingFrame.Light();light.x=-30;light.y=50;light.z=80;light.r=light.g=light.b=light.intensity=1;
        light.radius=512;light.falloff=1;light.resolution=512;q.lighting.lights.add(light);
        float[] casterVertices=vertices.clone();for(int i=0;i<6;i++)casterVertices[i*8+2]=32;
        LightingFrame.Caster caster=new LightingFrame.Caster();caster.surface=new LightingSurface(casterVertices);
        caster.surface.shadowGroupId=17;caster.texture=TextureRegistry.handleOf(color);caster.u2=caster.v2=1;caster.bounds();q.lighting.casters.add(caster);
        List<byte[]> reference=new ArrayList<>();List<Integer> draws=new ArrayList<>(),flushes=new ArrayList<>(),uploads=new ArrayList<>();
        try {
            for(int pass=0;pass<2;pass++){
                submit.setUseNormalMaps(pass==0);submit.prepareComposition();surface.normalMap=pass==0?null:binding;
                if(forward && pass==1){
                    Mesh mesh=(Mesh)field(submit.forwardRenderer(),"mesh");Assert.assertEquals(76,mesh.getVertexSize());
                    Assert.assertNull(find(mesh,"a_normalUV"));
                    Assert.assertNull(find(mesh,"a_normalBasis"));Assert.assertNotNull(find(mesh,"a_curveNormal"));
                    com.badlogic.gdx.graphics.glutils.ShaderProgram program=(com.badlogic.gdx.graphics.glutils.ShaderProgram)field(submit.forwardRenderer(),"shader");
                    for(String uniform:program.getUniforms())Assert.assertFalse(uniform,uniform.startsWith("u_normal"));
                }
                int sample=0;
                for(byte domain:new byte[]{FrameRenderQueue.SOURCE_TILED,FrameRenderQueue.SOURCE_ECS})
                    for(boolean shade:new boolean[]{false,true})for(int quality:new int[]{-1,0,1,2})
                        for(int mode:new int[]{0,1,2,7,3,4}){
                            q.sourceDomain[0]=mode==3||mode==4?FrameRenderQueue.SOURCE_VFX:domain;
                            surface.actor=domain==FrameRenderQueue.SOURCE_ECS;surface.twoSided=!shade;
                            surface.shadowGroupId=shade?17:0;
                            q.blend[0]=mode;q.ignoreAmbientLighting[0]=mode==3||mode==4;
                            q.textureHandle[0]=TextureRegistry.handleOf(mode==7?(shade?low:high):color);
                            q.lighting.lights.clear();if(quality>=0){light.quality=quality;q.lighting.lights.add(light);}
                            submit.lightingRenderer().prepareFrame(q.lighting);
                            int d=stats.drawCalls,f=stats.flushes,u=stats.bufferUploads;byte[] image=capture(world);
                            if(quality>0){
                                // Legacy rejects an additive-only queue before shadow production.
                                int expectedCasters=!forward&&(mode==3||mode==4)?0:1;
                                Assert.assertEquals("selected casters: forward="+forward+", blend="+mode,
                                        expectedCasters,submit.lightingRenderer().selectedCasters);
                                Assert.assertEquals(0,submit.lightingRenderer().twoDepthShadowUploads);
                            }
                            if(pass==0){reference.add(image);draws.add(stats.drawCalls-d);flushes.add(stats.flushes-f);uploads.add(stats.bufferUploads-u);}
                            else {
                                String label=(forward?"Forward":"Legacy")+" domain="+domain+" shade="+shade+" quality="+quality+" blend="+mode;
                                Assert.assertArrayEquals(label,reference.get(sample),image);
                                Assert.assertEquals(label,draws.get(sample).intValue(),stats.drawCalls-d);
                                Assert.assertEquals(label,flushes.get(sample).intValue(),stats.flushes-f);
                                Assert.assertEquals(label,uploads.get(sample).intValue(),stats.bufferUploads-u);
                                if(forward){Assert.assertEquals(6L*76,submit.forwardRenderer().uploadedBytes);Assert.assertEquals(0,submit.forwardRenderer().normalFlushes);}
                            }
                            sample++;
                        }
            }
            // Re-enable using the same normal asset; preparation is idempotent between frames.
            q.sourceDomain[0]=FrameRenderQueue.SOURCE_TILED;q.blend[0]=0;q.ignoreAmbientLighting[0]=false;
            q.textureHandle[0]=TextureRegistry.handleOf(color);surface.actor=false;surface.twoSided=true;
            light.quality=0;q.lighting.lights.clear();q.lighting.lights.add(light);
            byte[] unmapped=capture(world);submit.setUseNormalMaps(true);submit.prepareComposition();byte[] mapped=capture(world);
            Assert.assertFalse("The mapped witness must actually change lighting",java.util.Arrays.equals(unmapped,mapped));
            Object prepared=forward?field(submit.forwardRenderer(),"mesh"):field(submit.lightingRenderer(),"mappedReceiver");
            submit.prepareComposition();Assert.assertSame(prepared,forward?field(submit.forwardRenderer(),"mesh"):field(submit.lightingRenderer(),"mappedReceiver"));
            Assert.assertArrayEquals(mapped,capture(world));
            if(forward)Assert.assertEquals(6L*104,submit.forwardRenderer().uploadedBytes);
            System.out.println((forward?"Forward":"Legacy")+" normal-map option: "+reference.size()+" RGBA comparisons, equal draws/flushes/uploads; reactivation passed");
            Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        }finally {world.dispose();if(arrayBatch!=null)arrayBatch.close();if(legacyBatch!=null)legacyBatch.close();}
    }
    private static VertexAttribute find(Mesh mesh,String name){for(VertexAttribute a:mesh.getVertexAttributes())if(a.alias.equals(name))return a;return null;}
    private static byte[] capture(World world){
        Gdx.gl.glClearColor(.125f,.25f,.375f,.5f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
        ByteBuffer pixels=BufferUtils.newByteBuffer(64*32*4);Gdx.gl.glReadPixels(0,0,64,32,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixels);
        byte[] result=new byte[pixels.capacity()];pixels.get(result);return result;
    }
    private static Object field(Object object,String name)throws Exception {var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
}
