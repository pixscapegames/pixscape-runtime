package games.pixscape.runtime.spatial;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.*;
import org.junit.*;

/** Opt-in real GL30 render. Generated textures and images live only under build/. */
public class SpatialCornerVisualDiagnosticTest {
    @Test public void renderSixteenConfigurationsWithTheComposedOrderAndRealPointShader() {
        Assume.assumeTrue("Set PIXSCAPE_CORNER_DIAGNOSTIC=1 to render the contact sheet",
                "1".equals(System.getenv("PIXSCAPE_CORNER_DIAGNOSTIC")));
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Pixscape Spatial corner diagnostic");
        config.setWindowedMode(1280, 1160); config.setInitialVisible(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        config.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            SpriteBatch labels;
            BitmapFont font;
            ShapeRenderer overlay;
            TextureArrayMeshBatch batch;
            AtlasRuntimeService.TextureArrayBundle bundle;
            final Array<Texture> textures = new Array<>();
            final Array<Pixmap> sourcePixels = new Array<>();
            final int[] wallHandles = new int[4];
            int spriteHandle;
            final Matrix4 projection = new Matrix4().setToOrtho2D(0, 0, 1280, 1160);
            final RenderStats stats = new RenderStats();
            boolean rendered;

            @Override public void create() {
                try {
                    ShaderRegistry.initDefaults();
                    labels = new SpriteBatch(); labels.setProjectionMatrix(projection);
                    font = new BitmapFont(); overlay = new ShapeRenderer(); overlay.setProjectionMatrix(projection);
                    for (int opening=0; opening<4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening, false)) {
                        Pixmap p = new Pixmap(256, 256, Pixmap.Format.RGBA8888);
                        p.setColor(.38f, .52f, .64f, 1f);
                        // Rasterize all real compiled faces into one indivisible tile texture.
                        for (int face=0; face<f.faces.faceCount; face++) {
                            float x1=f.faces.screenMinX[face]-f.vx+90f;
                            float x2=f.faces.screenMaxX[face]-f.vx+90f;
                            float y1=f.faces.slope[face]*f.faces.screenMinX[face]+f.faces.intercept[face]-f.vy+35f;
                            float y2=f.faces.slope[face]*f.faces.screenMaxX[face]+f.faces.intercept[face]-f.vy+35f;
                            p.fillTriangle((int)x1,255-(int)y1,(int)x2,255-(int)y2,(int)x2,255-(int)(y2+80));
                            p.fillTriangle((int)x1,255-(int)y1,(int)x2,255-(int)(y2+80),(int)x1,255-(int)(y1+80));
                        }
                        Texture t = new Texture(p); sourcePixels.add(p); textures.add(t);
                        wallHandles[opening] = TextureRegistry.handleOf(t);
                    }
                    Pixmap sprite = new Pixmap(256,256,Pixmap.Format.RGBA8888);
                    sprite.setColor(.65f,.2f,.75f,1); sprite.fill();
                    sprite.setColor(.9f,.7f,.95f,1);
                    for(int y=0;y<256;y+=32)for(int x=0;x<256;x+=32)if((x+y)%64==0)sprite.fillRectangle(x,y,32,32);
                    Texture st = new Texture(sprite); sourcePixels.add(sprite); textures.add(st); spriteHandle=TextureRegistry.handleOf(st);
                    bundle=AtlasRuntimeService.buildTextureArrayFromTextures(textures,256,256,GLCaps.detect());
                    batch=new TextureArrayMeshBatch(128); batch.setTextureArrayBundle(bundle);
                } catch(Throwable t) { failure[0]=t; Gdx.app.exit(); }
            }

            @Override public void render() {
                if(rendered || failure[0]!=null) return;
                rendered=true;
                try {
                    Gdx.gl.glClearColor(.055f,.07f,.09f,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    labels.begin(); font.setColor(Color.WHITE);
                    font.draw(labels,"PIXSCAPE - 16 CORNERS / REAL GL30 / ONE TILE + ONE ACTOR PER VIEW",18,1140);
                    font.draw(labels,"B = actor behind | F = actor in front | X = unresolved, original bucket shown (no clipping)",18,1120);
                    labels.end();
                    for(int opening=0;opening<4;opening++)for(int sector=0;sector<4;sector++) {
                        float left=opening*320f, bottom=40f+(3-sector)*265f;
                        char expected=SpatialCornerFixture.EXPECTED[opening].charAt(sector);
                        overlay.begin(ShapeRenderer.ShapeType.Line); overlay.setColor(.22f,.27f,.33f,1);
                        overlay.rect(left+4,bottom+4,312,257); overlay.end();
                        labels.begin();font.setColor(expected=='X'?Color.ORANGE:Color.WHITE);
                        font.draw(labels,SpatialCornerFixture.OPENINGS[opening]+" / "+SpatialCornerFixture.SECTORS[sector],left+12,bottom+248);
                        font.draw(labels,"Expected: "+expected+(expected=='X'?" - NOT RESOLVED":""),left+12,bottom+229);
                        labels.end();
                        for(boolean light:new boolean[]{false,true})try(SpatialCornerFixture f=new SpatialCornerFixture(opening,light)) {
                            SpatialCornerFixture.Outcome result=f.run(sector,.5f,60f,1);
                            float cx=left+(light?240:80), cy=bottom+118;
                            // Render exactly the order emitted by the production composer.
                            for(int i=0;i<f.composer.composedSize;i++) {
                                boolean actor=f.composer.composedDomains[i]==RenderSourceDomain.SOURCE_ECS;
                                String shader=actor&&light?RuntimeFs.TEXTURE_ARRAY_POINTLIGHT:ShaderMode.TEXTURE_ARRAY.defaultShaderName();
                                int shaderIndex=ShaderRegistry.indexOf(shader);
                                batch.begin(projection,stats); batch.setShader(ShaderRegistry.getByIdx(shaderIndex),stats);
                                if (!(actor && light)) {
                                    ShaderRegistry.getByIdx(shaderIndex).setUniformf("u_ambientMul",1f,1f,1f);
                                    ShaderRegistry.getByIdx(shaderIndex).setUniformf("u_cutoutThreshold",-1f);
                                }
                                batch.setParameterLayout(ShaderRegistry.getParameterLayout(shaderIndex),stats);
                                batch.setEntityParameters(null,stats);
                                BlendMode blend=actor&&light?BlendMode.ADDITIVE:actor?BlendMode.OPAQUE:BlendMode.ALPHA;
                                batch.setBlendMode(blend.blending,blend.srcFactor,blend.dstFactor,stats);
                                batch.setColor(actor&&light?.95f:1,actor&&light?.55f:1,actor&&light?.12f:1,1);
                                if(actor) {
                                    float x=cx+SpatialCornerFixture.DX[sector]*.8f, y=cy+SpatialCornerFixture.DY[sector]*.8f;
                                    quad(light?InternalTextures.whiteHandle():spriteHandle,x-48,y-48,96,96,0,0,1,1);
                                } else quad(wallHandles[opening],cx-72,cy-28,144,100,0,131f/256f,180f/256f,1);
                                batch.end(stats);
                            }
                            overlay.begin(ShapeRenderer.ShapeType.Line);
                            overlay.setColor(.3f,.35f,.4f,1);
                            for(int x=-60;x<60;x+=10) {
                                overlay.line(cx+x,cy+x*.5f,cx+x+5,cy+(x+5)*.5f);
                                overlay.line(cx+x,cy-x*.5f,cx+x+5,cy-(x+5)*.5f);
                            }
                            overlay.setColor(Color.CYAN);
                            for(int face=0;face<f.faces.faceCount;face++) {
                                float x1=f.faces.screenMinX[face],x2=f.faces.screenMaxX[face];
                                overlay.line(cx+(x1-f.vx)*.8f,cy+(f.faces.slope[face]*x1+f.faces.intercept[face]-f.vy)*.8f,
                                        cx+(x2-f.vx)*.8f,cy+(f.faces.slope[face]*x2+f.faces.intercept[face]-f.vy)*.8f);
                            }
                            float ax=cx+SpatialCornerFixture.DX[sector]*.8f, ay=cy+SpatialCornerFixture.DY[sector]*.8f;
                            overlay.setColor(Color.GREEN); overlay.circle(ax,ay,.4f,16);
                            overlay.line(ax-4,ay,ax+4,ay);overlay.line(ax,ay-4,ax,ay+4);overlay.end();
                            labels.begin();font.setColor(Color.WHITE);
                            font.draw(labels,(light?"POINT":"SPRITE")+": "+result.side()+"  r=0.5",left+(light?168:12),bottom+47);
                            font.draw(labels,"C="+result.candidates+" R="+result.relations+" bucket="+result.bucket,left+(light?168:12),bottom+28);
                            labels.end();
                        }
                    }
                    labels.begin();font.setColor(Color.WHITE);
                    font.draw(labels,"Cyan: real branches | Grey dashed: support lines | Green cross: physical footprint (r=0.5 px); halo radius=60 px",18,25);
                    labels.end();
                    Pixmap capture=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
                    PixmapIO.writePNG(Gdx.files.local("build/corner-diagnostic/corners-gl.png"),capture,-1,true); capture.dispose();
                    System.out.println("CORNER_RENDER build/corner-diagnostic/corners-gl.png drawCalls="+stats.drawCalls);
                }catch(Throwable t){failure[0]=t;}finally{Gdx.app.exit();}
            }

            private void quad(int handle,float x,float y,float w,float h,float u,float v,float u2,float v2) {
                batch.draw(handle,x,y,x,y+h,x+w,y+h,x+w,y,u,v,u2,v2,stats);
            }

            @Override public void dispose() {
                if(batch!=null)batch.close(); if(bundle!=null)bundle.textureArray.dispose();
                if(labels!=null)labels.dispose();if(font!=null)font.dispose();if(overlay!=null)overlay.dispose();
                for(Texture t:textures)t.dispose(); ShaderRegistry.disposeAll();InternalTextures.dispose();TextureRegistry.clear();
                for(Pixmap p:sourcePixels)p.dispose();
            }
        },config);
        if(failure[0]!=null)throw new AssertionError("Corner GL render failed",failure[0]);
    }
}
