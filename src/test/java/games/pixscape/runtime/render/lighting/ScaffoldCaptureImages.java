package games.pixscape.runtime.render.lighting;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
/** Comparison of captured pixels, not an oracle of geometrically legitimate shadows. */
public final class ScaffoldCaptureImages {
 public static void main(String[] args)throws Exception{
  File out=new File(args[2]);out.mkdirs();
  try(PrintWriter log=new PrintWriter(new File(out,"pixel-influence.txt"))){
   log.println("All framebuffer pixels, max RGB difference >=8 versus scaffold-off. Counts include all receivers; overlap is not a ground-only correctness metric.");
   for(int renderer=0;renderer<2;renderer++){
    File root=new File(args[renderer]);BufferedImage sheet=new BufferedImage(1600,12*260,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
    for(int step=0;step<12;step++){
     BufferedImage[] images=new BufferedImage[4];int[][] rgb=new int[4][];String[] names={"full","rear","front","off"};
     for(int mode=0;mode<4;mode++){images[mode]=ImageIO.read(new File(root,"scaffold-"+step+"-"+names[mode]+".png"));rgb[mode]=images[mode].getRGB(0,0,1280,720,null,0,1280);
      g.setColor(Color.BLACK);g.drawString((renderer==0?"Legacy":"Forward")+" "+step*30+" deg "+names[mode],mode*400+5,step*260+16);
      g.drawImage(images[mode].getSubimage(315,200,600,360),mode*400,step*260+20,400,240,null);
     }
     int full=0,rear=0,front=0,both=0;for(int p=0;p<rgb[0].length;p++){boolean r=diff(rgb[1][p],rgb[3][p]),f=diff(rgb[2][p],rgb[3][p]);if(diff(rgb[0][p],rgb[3][p]))full++;if(r)rear++;if(f)front++;if(r&&f)both++;}
     log.println((renderer==0?"legacy":"forward")+" angle="+step*30+" full="+full+" rear="+rear+" front="+front+" overlap="+both);
    }
    g.dispose();ImageIO.write(sheet,"png",new File(out,(renderer==0?"legacy":"forward")+"-comparison.png"));
   }
  }
 }
 private static boolean diff(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))>=8||Math.abs((a>>8&255)-(b>>8&255))>=8||Math.abs((a&255)-(b&255))>=8;}
}
