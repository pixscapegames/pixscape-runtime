package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.spatial.ActorVolumeData;
import org.junit.Test;
import org.junit.Assume;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.Assert.*;

/** CPU geometry explanation of the real-sprite GPU stress pose, not a GPU oracle. */
public class RearSkinCounterexampleTest {
    @Test public void referenceCoverageDoesNotGuaranteeACompleteGroundProjection() throws Exception {
        check(28);check(60);
    }
    private void check(int frame) throws Exception {
        File file=new File("../tiled-iso-demo/studio/orig/animations/hero_idle__a1303/hero_idle__a1303_"+String.format("%04d",frame)+".png");
        Assume.assumeTrue(file.exists());BufferedImage image=ImageIO.read(file);
        ActorVolumeData v=new ActorVolumeData();v.width=86.40844f;v.depth=32;v.height=82.98047f;
        v.offsetX=22.82808f;v.offsetY=10.003464f;v.orientation=343.33826f+45*7.5f;
        float[] q={-194.71706f,-64.858f,-194.71706f,194.576f,64.71706f,194.576f,64.71706f,-64.858f};
        ActorVolumeCaster caster=new ActorVolumeCaster();caster.update(v,-32.5f,32.5f,0,q);
        double angle=Math.toRadians(337.5),lx=220*Math.cos(angle),ly=220*Math.sin(angle),lz=250;
        Stats[] stats={new Stats(),new Stats()};int[] facePixels=new int[6];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            if((image.getRGB(x,y)>>>24)<128)continue;
            // The stress pose uses a horizontal flip, as does the visible sprite.
            double u=1-(x+.5)/image.getWidth(),vv=1-(y+.5)/image.getHeight();
            boolean[] found=new boolean[2];float[] a=caster.vertices;
            for(int o=0;o<caster.vertexCount*8;o+=24){
                int skin=a[o+7]-a[o+5]-a[o+6]<0?0:1;if(found[skin])continue;
                double ax=a[o+11]-a[o+3],ay=a[o+12]-a[o+4],bx=a[o+19]-a[o+3],by=a[o+20]-a[o+4];
                double det=ax*by-ay*bx;if(Math.abs(det)<1e-10)continue;
                double px=u-a[o+3],py=vv-a[o+4],s=(px*by-py*bx)/det,t=(ax*py-ay*px)/det;
                if(s<0||t<0||s+t>1)continue;found[skin]=true;
                double wx=a[o]+s*(a[o+8]-a[o])+t*(a[o+16]-a[o]);
                double wy=a[o+1]+s*(a[o+9]-a[o+1])+t*(a[o+17]-a[o+1]);
                double wz=a[o+2]+s*(a[o+10]-a[o+2])+t*(a[o+18]-a[o+2]);
                double scale=lz/(lz-wz),gx=lx+scale*(wx-lx),gy=ly+scale*(wy-ly);
                stats[skin].add(gx-gy,(gx+gy)*.5,wz);
                if(skin==0)facePixels[o/48]++;
            }
        }
        assertEquals(stats[0].n,stats[1].n);
        System.out.println("COUNTEREXAMPLE frame="+frame+" samples="+stats[0].n+" rearMinorStd="+stats[0].minor()+" frontMinorStd="+stats[1].minor()+" rearMeanZ="+stats[0].z/stats[0].n+" rearFaceSamples="+java.util.Arrays.toString(facePixels));
        assertTrue("Nonempty projected samples",stats[0].n>0);
        assertTrue("Projected width is not preserved by reference coverage",Math.abs(stats[0].minor()-stats[1].minor())>1);
    }
    private static class Stats {
        int n;double x,y,xx,xy,yy,z;
        void add(double a,double b,double c){n++;x+=a;y+=b;xx+=a*a;xy+=a*b;yy+=b*b;z+=c;}
        double minor(){double a=xx/n-x*x/n/n,b=yy/n-y*y/n/n,c=xy/n-x*y/n/n;return Math.sqrt(Math.max(0,(a+b-Math.sqrt((a-b)*(a-b)+4*c*c))*.5));}
    }
}
