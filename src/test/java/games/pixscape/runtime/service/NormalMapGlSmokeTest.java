package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.render.batch.MultiTextureMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class NormalMapGlSmokeTest {
    @Test public void spriteAxesAliasesAndGeometricFacesRenderIndependently(){
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);cfg.setWindowedMode(64,32);cfg.disableAudio(true);
        cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){public void create(){
            World world=null;MultiTextureMeshBatch batch=null;Texture color=null;NormalMapAtlas normals=null;Pixmap cp=null;
            try {
                ShaderRegistry.initDefaults();TextureRegistry.clear();Gdx.gl.glClearColor(0,0,0,0);
                cp=new Pixmap(1,1,Pixmap.Format.RGBA8888);cp.setColor(Color.WHITE);cp.fill();color=new Texture(cp);
                Pixmap np=new Pixmap(4,1,Pixmap.Format.RGBA8888);
                np.drawPixel(0,0,0x8080ffff);np.drawPixel(1,0,0xff8080ff);np.drawPixel(2,0,0x80ff80ff);
                np.drawPixel(3,0,0xda80daff);
                Texture normal=new Texture(np);np.dispose();
                TextureAtlas ca=new TextureAtlas(),na=new TextureAtlas();
                for(int i=0;i<4;i++){TextureAtlas.AtlasRegion c=ca.addRegion("color__a"+(i+1),color,0,0,1,1);c.originalWidth=c.originalHeight=1;
                    TextureAtlas.AtlasRegion n=na.addRegion("normal__a"+(i+1),normal,i,0,1,1);n.originalWidth=n.originalHeight=1;}
                normals=new NormalMapAtlas("scene",na,ca);
                normal.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
                FrameRenderQueue q=new FrameRenderQueue(4);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
                OrthographicCamera camera=new OrthographicCamera(64,32);camera.position.set(32,16,0);camera.update();
                RenderStats stats=new RenderStats();batch=new MultiTextureMeshBatch(64);
                RenderSubmitSystem submit=new RenderSubmitSystem(layers,q,camera,0,0,0,batch,stats,new RenderStatsSink(100));
                world=new World(new WorldConfigurationBuilder().with(submit).build());submit.setEnabled(false);world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).create(world.create());world.process();
                int actorEntity = world.create();
                q.addQuad(TextureRegistry.handleOf(color),ShaderRegistry.indexOf(ShaderMode.MULTI_TEXTURE.defaultShaderName()),1,0,0,0,0,
                        0,0,0,32,64,32,64,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_ECS,0,actorEntity);
                float[] vertices=new float[48];int[] corner={0,1,2,0,2,3};float[] x={0,0,64,64},y={0,32,32,0};
                for(int i=0;i<6;i++){int c=corner[i],o=i*8;vertices[o]=LightingCoordinates.worldX(x[c],y[c],0);vertices[o+1]=LightingCoordinates.worldY(x[c],y[c],0);
                    vertices[o+3]=c>=2?1:0;vertices[o+4]=c==1||c==2?1:0;vertices[o+7]=1;}
                LightingSurface s=new LightingSurface(vertices);q.lighting.entities.put(actorEntity,s);q.lighting.diagnostic=2;
                submit.prepareComposition();submit.setEnabled(true);
                float[][] expected={{-.57735f,-.57735f,.57735f},{.7071f,-.7071f,0},{.40825f,.40825f,.81650f}};
                for(boolean actor:new boolean[]{false,true})for(int id=1;id<=3;id++)for(int face=0;face<2;face++){
                    s.actor=actor;
                    s.normalMap=normals.resolve(id);
                    for(int i=0;i<6;i++){vertices[i*8+5]=face;vertices[i*8+7]=1-face;}
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                    assertPixel(expected[id-1]);
                }
                s.normalMap=normals.resolve(4);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                // 64x32 drawing of a square source: X slope halves, depth follows image-up scale.
                assertPixel(new float[]{-.20017f,-.83263f,.51640f});
                // Flip the drawn sprite horizontally: camera-facing Z stays fixed, sprite X changes sign.
                q.x1[0]=q.x2[0]=64;q.x3[0]=q.x4[0]=0;s.normalMap=normals.resolve(2);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();assertPixel(new float[]{-.7071f,.7071f,0});
                q.x1[0]=q.x2[0]=0;q.x3[0]=q.x4[0]=64;s.normalMap=null;
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();assertPixel(new float[]{1,0,0});
                games.pixscape.runtime.tiled.TiledMapLayerData map=new games.pixscape.runtime.tiled.TiledMapLayerData(1,1,64,32,1,games.pixscape.runtime.tiled.TiledProjection.ISO);
                float[] quad=new float[8];
                float[][] transformedX={{.7071f,-.7071f,0},{-.7071f,.7071f,0},{.7071f,-.7071f,0},{-.7071f,.7071f,0},
                        {-.40825f,-.40825f,-.8165f},{-.40825f,-.40825f,-.8165f},{.40825f,.40825f,.8165f},{.40825f,.40825f,.8165f}};
                for(byte flags=0;flags<8;flags++) {
                    games.pixscape.runtime.tiled.TileQuadTransforms.buildSpriteQuad(map,0,0,64,32,flags,quad);
                    float cx=(quad[0]+quad[4])/2,cy=(quad[1]+quad[5])/2;
                    q.x1[0]=quad[0]-cx+32;q.y1[0]=quad[1]-cy+16;q.x2[0]=quad[2]-cx+32;q.y2[0]=quad[3]-cy+16;
                    q.x3[0]=quad[4]-cx+32;q.y3[0]=quad[5]-cy+16;q.x4[0]=quad[6]-cx+32;q.y4[0]=quad[7]-cy+16;
                    s.normalMap=normals.resolve(2);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();assertPixel(transformedX[flags]);
                    s.normalMap=normals.resolve(1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();assertPixel(expected[0]);
                }
                // Moving TR alone changes coverage, without inventing a new image-space normal convention.
                s.imageQuad=true;s.normalMap=normals.resolve(2);
                q.x1[0]=q.x2[0]=0;q.x4[0]=64;q.x3[0]=44;
                q.y1[0]=q.y4[0]=0;q.y2[0]=32;q.y3[0]=22;
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();assertPixel(expected[1]);
                // A normal-mapped ground receiver keeps its original geometric positions.
                q.x1[0]=q.x2[0]=0;q.x3[0]=q.x4[0]=64;q.y1[0]=q.y4[0]=0;q.y2[0]=q.y3[0]=32;q.lighting.diagnostic=0;
                var receiverMap=new games.pixscape.runtime.tiled.TiledMapLayerData(4,4,8,4,4,games.pixscape.runtime.tiled.TiledProjection.ISO);
                MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(receiverMap,null);float[] receiverQuad={0,0,0,32,64,32,64,0};
                LightingSurface complete=geometry.receiver(receiverMap,0,0,1,(byte)0,receiverQuad);complete.normalMap=normals.resolve(1);q.lighting.entities.put(actorEntity,complete);
                LightingFrame.Light light=new LightingFrame.Light();light.falloff=1;light.intensity=.4f;light.r=light.g=light.b=1;light.resolution=512;q.lighting.lights.add(light);
                int pixelWidth=Gdx.graphics.getBackBufferWidth(),pixelHeight=Gdx.graphics.getBackBufferHeight();
                // A neighboring/offscreen caster still occludes the continuous mapped receiver.
                light.x=-32;light.y=64;light.z=64;light.radius=512;light.quality=1;
                float[] casterVertices=new float[48];float[] ox={-12,-12,12,12},oy={20,44,44,20};
                for(int i=0;i<6;i++){int c=corner[i],o=i*8;casterVertices[o]=ox[c];casterVertices[o+1]=oy[c];casterVertices[o+2]=32;casterVertices[o+3]=c>=2?1:0;casterVertices[o+4]=c==1||c==2?1:0;casterVertices[o+7]=1;}
                LightingFrame.Caster caster=new LightingFrame.Caster();caster.surface=new LightingSurface(casterVertices);caster.surface.alphaThreshold=.1f;caster.texture=TextureRegistry.handleOf(color);caster.u2=caster.v2=1;caster.bounds();
                // A casting surface may also carry a different reception support; shadows must ignore it.
                caster.surface.normalMap=normals.resolve(1);caster.surface.receiverVertices=complete.vertices;
                submit.prepareComposition();q.lighting.casters.add(caster);submit.lightingRenderer().prepareFrame(q.lighting);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
                ByteBuffer sample=BufferUtils.newByteBuffer(4);Gdx.gl.glReadPixels(Gdx.graphics.getBackBufferWidth()/2,Gdx.graphics.getBackBufferHeight()/2,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,sample);int shadowed=sample.get(0)&255;
                Assert.assertEquals(1,submit.lightingRenderer().selectedCasters);q.lighting.casters.clear();
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();sample.clear();Gdx.gl.glReadPixels(Gdx.graphics.getBackBufferWidth()/2,Gdx.graphics.getBackBufferHeight()/2,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,sample);
                Assert.assertTrue("Neighbor must still occlude normal-mapped reception",(sample.get(0)&255)>shadowed+3);
                // The upright wall support receives a neighboring obstacle's shadow as well.
                LightingSurface upright=new LightingSurface(new float[48]);SpriteSurfaceSupport.sheet(upright.vertices,receiverQuad,.5f,0,0,1,-1);
                upright.normalMap=normals.resolve(1);q.lighting.entities.put(actorEntity,upright);
                for(int i=0;i<6;i++)casterVertices[i*8+1]-=20;
                caster.bounds();q.lighting.casters.add(caster);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();sample.clear();Gdx.gl.glReadPixels(pixelWidth/2,pixelHeight/2,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,sample);shadowed=sample.get(0)&255;
                byte[] wallShadow=renderBytes(world,pixelWidth,pixelHeight);
                caster.surface.actor=true;
                Assert.assertArrayEquals("An actor caster casts the same shadow onto a decor wall",wallShadow,renderBytes(world,pixelWidth,pixelHeight));
                q.lighting.casters.clear();Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();sample.clear();Gdx.gl.glReadPixels(pixelWidth/2,pixelHeight/2,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,sample);
                Assert.assertTrue("Neighbor must occlude the upright wall",(sample.get(0)&255)>shadowed+3);
                upright.actor=true;
                upright.forwardTopology=ForwardTopology.NATIVE;
                var identities = new ShadowExclusionIdentities();
                upright.selfShadowExclusionId = identities.idForActor(actorEntity);
                caster.surface.selfShadowExclusionId = identities.idForActor(world.create());
                verifyActorShadows(world,submit,q,normals,upright,caster,light);
                verifyForwardActorShadows(layers,q,camera,cp,color,normals,upright,caster,light);
                // Actual alpha opening in a decor support transmits the direct contribution.
                Pixmap holeImage=new Pixmap(16,16,Pixmap.Format.RGBA8888);holeImage.setColor(Color.WHITE);holeImage.fill();
                holeImage.setBlending(Pixmap.Blending.None);holeImage.setColor(0,0,0,0);holeImage.fillRectangle(4,4,8,8);Texture hole=new Texture(holeImage);holeImage.dispose();
                try{
                    caster.surface.actor=false;light.quality=1;upright.normalMap=null;q.blend[0]=1;light.intensity=1;q.lighting.casters.add(caster);
                    byte[] opaque=renderBytes(world,pixelWidth,pixelHeight);caster.texture=TextureRegistry.handleOf(hole);
                    byte[] opening=renderBytes(world,pixelWidth,pixelHeight);int brightened=0,maxGain=0;
                    for(int i=0;i<opening.length;i+=4){maxGain=Math.max(maxGain,(opening[i]&255)-(opaque[i]&255));if((opening[i]&255)>(opaque[i]&255)+3)brightened++;}
                    Assert.assertTrue("Light through transparent wall alpha must illuminate actor pixels; maxGain="+maxGain,brightened>0);
                }finally{q.lighting.casters.clear();hole.dispose();}
                Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
            }catch(Throwable e){failure[0]=e;}finally{
                if(cp!=null)cp.dispose();
                if(world!=null)world.dispose();if(batch!=null)batch.close();if(normals!=null)normals.dispose();if(color!=null)color.dispose();
                ShaderRegistry.disposeAll();TextureRegistry.clear();Gdx.app.exit();
            }
        }},cfg);
        if(failure[0]!=null)throw new AssertionError("Normal GL integration",failure[0]);
    }
    private static void verifyForwardActorShadows(LayerStateSOA layers, FrameRenderQueue queue,
            OrthographicCamera camera, Pixmap colorPixels, Texture color, NormalMapAtlas normals,
            LightingSurface receiver, LightingFrame.Caster other, LightingFrame.Light light) {
        var bundle = AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(color),
                colorPixels.getWidth(), colorPixels.getHeight(), GLCaps.detect());
        var batch = new TextureArrayMeshBatch(64);
        batch.setTextureArrayBundle(bundle);
        var submit = new RenderSubmitSystem(layers, queue, camera, 0, 0, 0, batch,
                new RenderStats(), new RenderStatsSink(100), true);
        World world = new World(new WorldConfigurationBuilder().with(submit).build());
        int previousShader = queue.shader[0];
        try {
            submit.setEnabled(false);
            world.getMapper(PointLightComponent.class).create(world.create());
            world.process();
            queue.shader[0] = ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());
            submit.prepareComposition();
            Assert.assertTrue(submit.isForwardEffective());
            submit.setEnabled(true);
            verifyActorShadows(world, submit, queue, normals, receiver, other, light);
        } finally {
            queue.shader[0] = previousShader;
            world.dispose();
            batch.close();
            bundle.textureArray.dispose();
        }
    }

    private static void verifyActorShadows(World world, RenderSubmitSystem submit, FrameRenderQueue queue,
            NormalMapAtlas normals, LightingSurface receiver, LightingFrame.Caster other, LightingFrame.Light light) {
        int receiverId = receiver.selfShadowExclusionId;
        int otherId = other.surface.selfShadowExclusionId;
        Assert.assertNotEquals(receiverId, otherId);
        // The same interposed opaque sheet represents each actor in turn. Its shadow with
        // the foreign identity is the positive control for the self-exclusion comparison.
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        for (boolean mapped : new boolean[]{false, true}) {
            receiver.normalMap = mapped ? normals.resolve(1) : null;
            for (int blend : new int[]{0, 1, 2, 7}) {
                queue.blend[0] = blend;
                light.quality = 0;
                byte[] lit = renderBytes(world, width, height);
                for (int quality : new int[]{1, 2}) {
                    String mode = (submit.isForwardEffective() ? "forward" : "legacy")
                            + ", blend=" + blend + ", quality=" + quality + ", normalMap=" + mapped;
                    light.quality = quality;
                    other.surface.actor = true;
                    other.surface.selfShadowExclusionId = receiverId;
                    queue.lighting.casters.add(other);
                    submit.lightingRenderer().prepareFrame(queue.lighting);
                    Assert.assertArrayEquals("Own caster must leave the actor lit: " + mode,
                            lit, renderBytes(world, width, height));

                    other.surface.selfShadowExclusionId = otherId;
                    byte[] shadow = renderBytes(world, width, height);
                    boolean darker = false;
                    for (int i = 0; i < shadow.length; i += 4) {
                        if ((shadow[i] & 255) < (lit[i] & 255)) { darker = true; break; }
                    }
                    Assert.assertTrue("Interposed actor must darken the receiver: " + mode, darker);
                    other.surface.actor = false;
                    Assert.assertArrayEquals("Opaque decor with the same geometry: " + mode,
                            shadow, renderBytes(world, width, height));

                    // TwoDepthShadowFixtureGlCheck covers combined own and exterior casters.
                    queue.lighting.casters.clear();
                    Assert.assertArrayEquals("Removing the last caster must clear its shadow: " + mode,
                            lit, renderBytes(world, width, height));
                }
            }
        }
    }

    private static byte[] renderBytes(World world,int width,int height){
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);world.process();
        ByteBuffer pixels=BufferUtils.newByteBuffer(width*height*4);
        Gdx.gl.glReadPixels(0,0,width,height,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixels);
        byte[] result=new byte[pixels.capacity()];pixels.get(result);return result;
    }
    private static void assertPixel(float[] expected){ByteBuffer pixel=BufferUtils.newByteBuffer(4);Gdx.gl.glReadPixels(Gdx.graphics.getBackBufferWidth()/2,Gdx.graphics.getBackBufferHeight()/2,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
        for(int i=0;i<3;i++)Assert.assertEquals("World normal component "+i,Math.round((expected[i]*.5f+.5f)*255),pixel.get(i)&255,3);}
}
