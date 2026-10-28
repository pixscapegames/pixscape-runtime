package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class NormalMapBindingTest {
    @After public void reset(){TextureRegistry.clear();}
    @Test public void sourcePixelsAlignAcrossIndependentTrimsAndPages(){
        TestTexture colorPage=new TestTexture(),normalPage=new TestTexture();
        TextureAtlas.AtlasRegion color=region(colorPage,"color__a7",20,30,80,120);
        color.offsetX=10;color.offsetY=20;
        TextureAtlas.AtlasRegion normal=region(normalPage,"normal__a7",140,100,60,90);
        normal.offsetX=25;normal.offsetY=35;
        NormalMapBinding b=new NormalMapBinding(color,normal);
        // Original pixel (40,65), measured from the source bottom left.
        float u=(40-10)/80f,v=(65-20)/120f;
        Assert.assertEquals((140+40-25)/256f,b.u0+b.ux*u+b.uy*v,.00001f);
        Assert.assertEquals((100+90-(65-35))/256f,b.v0+b.vx*u+b.vy*v,.00001f);
        Assert.assertEquals(TextureRegistry.handleOf(normalPage),b.textureHandle);
        Assert.assertNotEquals(TextureRegistry.handleOf(colorPage),b.textureHandle);
    }
    @Test public void aliasesKeepIndependentIdentitiesAndReloadRetiresNormalPages(){
        TestTexture c=new TestTexture(),n=new TestTexture();
        TextureAtlas colors=atlas(region(c,"color__a7",0,0,16,16),region(c,"color__a8",0,0,16,16));
        TextureAtlas normals=atlas(region(n,"normal__a7",0,0,16,16),region(n,"normal__a8",32,0,16,16));
        NormalMapAtlas prepared=new NormalMapAtlas("scene",normals,colors);
        AtlasRuntimeService service=new AtlasRuntimeService();service.loadBorrowed("scene",colors,prepared);
        NormalMapBinding first=service.resolveNormal(7,"scene"),second=service.resolveNormal(8,"scene");
        Assert.assertNotEquals(first.u0,second.u0,0f);
        for(int i=0;i<1000;i++){Assert.assertSame(first,service.resolveNormal(7,"scene"));Assert.assertNull(service.resolveNormal(99,"scene"));}
        Assert.assertNull(service.resolveNormal(7,null));
        int retired=first.textureHandle;service.loadBorrowed("scene",colors);
        Assert.assertNull(service.resolveNormal(7,"scene"));Assert.assertNull(TextureRegistry.getByHandle(retired));
        Assert.assertTrue(n.disposed);Assert.assertFalse(c.disposed);service.unloadAll();
    }
    @Test public void replacementPublishesByIdAndInvalidCandidateKeepsPreviousBinding(){
        TestTexture color=new TestTexture(),first=new TestTexture(),next=new TestTexture(),bad=new TestTexture();
        TextureAtlas colors=atlas(region(color,"color__a7",0,0,16,16));
        AtlasRuntimeService service=new AtlasRuntimeService();
        service.loadBorrowed("scene",colors,new NormalMapAtlas("scene",atlas(region(first,"normal__a7",0,0,16,16)),colors));
        int retired=service.resolveNormal(7,"scene").textureHandle;
        service.loadBorrowed("scene",colors,new NormalMapAtlas("scene",atlas(region(next,"normal__a7",32,0,16,16)),colors));
        NormalMapBinding current=service.resolveNormal(7,"scene");
        Assert.assertNotEquals(retired,current.textureHandle);Assert.assertNull(TextureRegistry.getByHandle(retired));Assert.assertTrue(first.disposed);
        TextureAtlas.AtlasRegion invalid=region(bad,"normal__a7",0,0,16,16);invalid.originalWidth=128;
        try{new NormalMapAtlas("scene",atlas(invalid),colors);Assert.fail();}catch(IllegalArgumentException expected){Assert.assertTrue(expected.getMessage().contains("dimensions"));}
        Assert.assertTrue(bad.disposed);Assert.assertSame(current,service.resolveNormal(7,"scene"));
        Assert.assertSame(color,service.resolveBinding(7,"scene").firstRegion().getTexture());service.unloadAll();
    }
    @Test public void storageRotationOnlyChangesUvsAndKeepsSourceRegistration(){
        TextureAtlas.AtlasRegion color=region(new TestTexture(),"color__a7",20,30,80,120);
        color.offsetX=10;color.offsetY=20;
        TextureAtlas.AtlasRegion normal=region(new TestTexture(),"normal__a7",140,100,90,60);
        normal.rotate=true;normal.offsetX=25;normal.offsetY=35;
        NormalMapBinding binding=new NormalMapBinding(color,normal);
        float u=(40-10)/80f,v=(65-20)/120f;
        Assert.assertEquals((140+65-35)/256f,binding.u0+binding.ux*u+binding.uy*v,.00001f);
        Assert.assertEquals((100+40-25)/256f,binding.v0+binding.vx*u+binding.vy*v,.00001f);
        Assert.assertEquals(80,binding.colorWidth,0);Assert.assertEquals(120,binding.colorHeight,0);
    }
    @Test public void overlappingBorrowedGenerationsKeepSharedPageHandleAlive(){
        TestTexture c=new TestTexture(),n=new TestTexture();
        TextureAtlas colors=atlas(region(c,"color__a7",0,0,16,16));
        TextureAtlas normals=atlas(region(n,"normal__a7",0,0,16,16));
        NormalMapAtlas first=new NormalMapAtlas("scene",normals,colors,false);
        int handle=first.resolve(7).textureHandle;
        TextureAtlas.AtlasRegion invalid=region(n,"normal__a7",0,0,16,16);invalid.originalWidth=128;
        try{new NormalMapAtlas("scene",atlas(invalid),colors,false);Assert.fail();}catch(IllegalArgumentException expected){}
        Assert.assertSame(n,TextureRegistry.getByHandle(handle));
        NormalMapAtlas next=new NormalMapAtlas("scene",normals,colors,false);
        first.dispose();first.dispose();
        Assert.assertEquals(handle,next.resolve(7).textureHandle);
        Assert.assertSame(n,TextureRegistry.getByHandle(handle));Assert.assertFalse(n.disposed);
        next.dispose();Assert.assertNull(TextureRegistry.getByHandle(handle));Assert.assertFalse(n.disposed);
        normals.dispose();colors.dispose();
    }
    private static TextureAtlas atlas(TextureAtlas.AtlasRegion...regions){TextureAtlas a=new TextureAtlas();for(TextureAtlas.AtlasRegion r:regions){a.getRegions().add(r);a.getTextures().add(r.getTexture());}return a;}
    private static TextureAtlas.AtlasRegion region(Texture t,String name,int x,int y,int w,int h){TextureAtlas.AtlasRegion r=new TextureAtlas.AtlasRegion(t,x,y,w,h);r.name=name;r.originalWidth=r.originalHeight=256;return r;}
    private static final class TestTexture extends Texture {
        boolean disposed;TestTexture(){super();}
        public int getWidth(){return 256;}public int getHeight(){return 256;}
        public void setFilter(TextureFilter a,TextureFilter b){}public void setWrap(TextureWrap a,TextureWrap b){}
        public void dispose(){disposed=true;}
    }
}
