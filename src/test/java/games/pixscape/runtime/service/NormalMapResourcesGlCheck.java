package games.pixscape.runtime.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import org.junit.Assert;

/** Resource ownership and actual GL upload checks inside the isolated Forward fixture context. */
public final class NormalMapResourcesGlCheck {
    public static void verify()throws Exception {
        FileHandle root=new FileHandle(java.nio.file.Files.createTempDirectory("normal-map-session").toFile());
        Pixmap pixels=new Pixmap(2,2,Pixmap.Format.RGBA8888);pixels.setColor(1,1,1,1);pixels.fill();
        PixmapIO.writePNG(root.child("color.png"),pixels);pixels.setColor(.5f,.5f,1,1);pixels.fill();
        PixmapIO.writePNG(root.child("normal.png"),pixels);pixels.dispose();
        String metadata="\nsize:2,2\nformat:RGBA8888\nfilter:Linear,Linear\nrepeat:none\n";
        for(String tag:new String[]{"A","B"}){
            root.child(tag+".atlas").writeString("color.png"+metadata+"color__a7\nbounds:0,0,2,2\n",false,"UTF-8");
            root.child(tag+".normals.atlas").writeString("normal.png"+metadata+"normal__a7\nbounds:0,0,2,2\n",false,"UTF-8");
        }
        final GL20 original=Gdx.gl;final GL30 original30=Gdx.gl30;int[] uploads={0};
        GL30 counted=(GL30)java.lang.reflect.Proxy.newProxyInstance(GL30.class.getClassLoader(),new Class[]{GL30.class},(proxy,method,args)->{
            if(method.getName().equals("glTexImage2D"))uploads[0]++;
            try{return method.invoke(original30,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });
        AtlasRuntimeService service=new AtlasRuntimeService();
        try {
            Gdx.gl=Gdx.gl20=Gdx.gl30=counted;
            service.load("A",root.child("A.atlas"),false);
            Assert.assertEquals("Only the color page is uploaded",1,uploads[0]);
            Assert.assertNull(service.resolveNormal(7,"A"));Assert.assertEquals(0,service.normalTextureBytes());
            var colors=service.getAtlas("A");int revision=service.publicationRevision("A");
            service.load("B",root.child("B.atlas"),true);
            Assert.assertEquals("The enabled independent scene uploads both pages",3,uploads[0]);
            NormalMapBinding b=service.resolveNormal(7,"B");Assert.assertNotNull(b);
            service.replaceNormalMaps("A",root.child("A.normals.atlas"));
            Assert.assertEquals("Re-enable uploads only the normal page",4,uploads[0]);
            Assert.assertSame(colors,service.getAtlas("A"));Assert.assertTrue(service.publicationRevision("A")>revision);
            NormalMapBinding a=service.resolveNormal(7,"A");Assert.assertNotNull(a);
            Texture aPage=TextureRegistry.getByHandle(a.textureHandle),bPage=TextureRegistry.getByHandle(b.textureHandle);
            service.replaceNormalMaps("A",null);
            Assert.assertEquals(4,uploads[0]);Assert.assertNull(service.resolveNormal(7,"A"));
            Assert.assertEquals(0,aPage.getTextureObjectHandle());Assert.assertTrue(bPage.getTextureObjectHandle()!=0);
            Assert.assertSame(b,service.resolveNormal(7,"B"));Assert.assertSame(colors,service.getAtlas("A"));
            Assert.assertEquals(16,service.normalTextureBytes());
            // Releasing a borrowed wrapper must not dispose AssetManager-owned pages.
            var borrowedColors=service.getAtlas("B");
            var borrowedAtlas=new com.badlogic.gdx.graphics.g2d.TextureAtlas();
            var region=borrowedAtlas.addRegion("normal__a7",bPage,0,0,2,2);region.originalWidth=region.originalHeight=2;
            NormalMapAtlas lease=new NormalMapAtlas("borrowed",borrowedAtlas,borrowedColors,false);
            lease.dispose();Assert.assertTrue(bPage.getTextureObjectHandle()!=0);
            Assert.assertSame(bPage,TextureRegistry.getByHandle(b.textureHandle));
            Assert.assertTrue(root.child("normal.png").exists());Assert.assertTrue(root.child("A.normals.atlas").exists());
            Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        } finally {
            service.unloadAll();Gdx.gl=Gdx.gl20=original;Gdx.gl30=original30;
            root.deleteDirectory();
        }
    }
}
