package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import games.pixscape.runtime.render.FrameRenderQueue;
import java.util.Random;
import org.junit.Assert;

/** Samples barycentric receiver points across altitude, display transforms, zoom and camera rotation. */
public final class LightingInfluenceBoundsGlTest {
    @org.junit.Test public void conservativeUnderReceiverDisplayTransforms() {
        final Throwable[] failure={null};
        com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration c=new com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration();
        c.setInitialVisible(false);c.setWindowedMode(64,32);c.disableAudio(true);
        c.setOpenGLEmulation(com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application(new com.badlogic.gdx.ApplicationAdapter(){public void create(){
            try {games.pixscape.runtime.service.ShaderRegistry.initDefaults();verify(games.pixscape.runtime.service.ShaderRegistry.indexOf(games.pixscape.runtime.render.ShaderMode.MULTI_TEXTURE.defaultShaderName()));}
            catch(Throwable t){failure[0]=t;}finally{games.pixscape.runtime.service.ShaderRegistry.disposeAll();com.badlogic.gdx.Gdx.app.exit();}
        }},c);
        if(failure[0]!=null)throw new AssertionError("Conservative influence bounds",failure[0]);
    }
    public static void verify(int shader){
        Random random=new Random(189357);
        FrameRenderQueue q=new FrameRenderQueue(1);q.size=1;q.textureHandle[0]=1;q.shader[0]=shader;
        q.sourceDomain[0]=FrameRenderQueue.SOURCE_ECS;q.sourceEntity[0]=7;
        float[] vertices=new float[48];LightingSurface surface=new LightingSurface(vertices);q.lighting.entities.put(7,surface);
        LightingInfluenceBounds bounds=new LightingInfluenceBounds();LightingFrame.Light light=new LightingFrame.Light();
        OrthographicCamera camera=new OrthographicCamera(640,360);Vector3 pixel=new Vector3();
        for(int trial=0;trial<300;trial++){
            camera.up.set(0,1,0);camera.direction.set(0,0,-1);camera.position.set(0,0,0);
            camera.zoom=.3f+random.nextFloat()*2;camera.rotate(random.nextFloat()*360);camera.update();
            float ox=random.nextFloat()*400-200,oy=random.nextFloat()*200-100;
            float ax=50+random.nextFloat()*150,ay=random.nextFloat()*50,bx=random.nextFloat()*80,by=50+random.nextFloat()*150;
            q.x1[0]=ox;q.y1[0]=oy;q.x2[0]=ox+bx;q.y2[0]=oy+by;
            q.x3[0]=ox+ax+bx;q.y3[0]=oy+ay+by;q.x4[0]=ox+ax;q.y4[0]=oy+ay;
            surface.imageQuad=(trial&1)==0;
            for(int i=0;i<6;i++){
                int c=SpriteSurfaceSupport.corner(i),o=i*8;float u=c>=2?1:0,v=c==1||c==2?1:0;
                float sx=u*120-60,sy=v*160-80,z=random.nextFloat()*800-100;
                vertices[o]=LightingCoordinates.worldX(sx,sy,z);vertices[o+1]=LightingCoordinates.worldY(sx,sy,z);vertices[o+2]=z;
                vertices[o+3]=u;vertices[o+4]=v;vertices[o+7]=1;
            }
            bounds.prepare(q,camera);
            for(int sample=0;sample<20;sample++){
                float a=random.nextFloat(),b=random.nextFloat()*(1-a),c=1-a-b;
                float px=a*vertices[0]+b*vertices[8]+c*vertices[16];
                float py=a*vertices[1]+b*vertices[9]+c*vertices[17];
                float pz=a*vertices[2]+b*vertices[10]+c*vertices[18];
                float u=a*vertices[3]+b*vertices[11]+c*vertices[19],v=a*vertices[4]+b*vertices[12]+c*vertices[20];
                camera.project(pixel.set(ox+ax*u+bx*v,oy+ay*u+by*v,0),0,0,640,360);
                light.radius=1+random.nextFloat()*300;light.x=px+light.radius*.5f;light.y=py;light.z=pz;
                boolean visible=bounds.compute(light,640,360);
                if(pixel.x>=0&&pixel.x<640&&pixel.y>=0&&pixel.y<360){
                    Assert.assertTrue("A light reaching a visible receiver must be retained",visible);
                    Assert.assertTrue(pixel.x>=bounds.x&&pixel.x<bounds.x+bounds.width);
                    Assert.assertTrue(pixel.y>=bounds.y&&pixel.y<bounds.y+bounds.height);
                }
            }
        }
        q.shader[0]=-1;bounds.prepare(q,camera);Assert.assertTrue(bounds.compute(light,640,360));
        Assert.assertEquals(640,bounds.width);Assert.assertEquals(360,bounds.height);
        q.shader[0]=shader;q.size=0;bounds.prepare(q,camera);Assert.assertFalse(bounds.compute(light,640,360));
    }
}
