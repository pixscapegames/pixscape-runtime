package games.pixscape.runtime.render.lighting;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** Pixel crops from GPU captures, without recoloring or contrast adjustment. */
public final class RoundedActorSheetComparison {
    public static final class ActorExchange {
        public static void main(String[] args)throws Exception{
            BufferedImage alpha=ImageIO.read(new File("../tiled-iso-demo/studio/orig/animations/hero_idle__a1303/hero_idle__a1303_0056.png"));
            try(PrintWriter log=new PrintWriter(new File(args[0],"actor-exchange.txt"))){
                for(String renderer:new String[]{"legacy","forward"}){
                    BufferedImage both=ImageIO.read(new File(args[0],renderer+"-rounded-sheet-frame-42.png"));
                    BufferedImage isolated=ImageIO.read(new File(args[0],renderer+"-rounded-sheet-isolated-frame-42.png"));
                    int changed=0,samples=0;long darkening=0;
                    for(int y=0;y<180;y++)for(int x=0;x<180;x++)if((alpha.getRGB(x,y)>>>24)>250){
                        double sx=-194.71706+(1-(x+.5)/180)*259.43412,sy=-64.858+(1-(y+.5)/180)*259.434;
                        int px=(int)((sx+480)*1280/960),py=959-(int)((sy+270)*960/720);
                        int a=both.getRGB(px,py),b=isolated.getRGB(px,py);
                        int d=(b>>16&255)+(b>>8&255)+(b&255)-(a>>16&255)-(a>>8&255)-(a&255);
                        if(d>6){changed++;darkening+=d;}samples++;
                    }
                    log.println(renderer+" opaque actor0 samples="+samples+" darkened by actor1="+changed+" summedRGBdarkening="+darkening);
                }
            }
        }
    }
    public static void main(String[] args)throws Exception{
        File out=new File(args[0]);out.mkdirs();
        BufferedImage real=new BufferedImage(1200,600,BufferedImage.TYPE_INT_RGB);Graphics2D g=real.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,1200,600);g.setFont(new Font("SansSerif",Font.PLAIN,18));
        for(int i=0;i<4;i++){BufferedImage p=ImageIO.read(new File(args[i+1],"images/mode-0.png"));
            int x=i%2*600,y=i/2*300;g.setColor(Color.BLACK);g.drawString((i<2?"LEGACY":"FORWARD")+(i%2==0?" - volume complet":" - prototype courbe"),x+8,y+23);
            g.drawImage(p.getSubimage(630,400,250,110),x,y+30,600,264,null);}
        g.dispose();ImageIO.write(real,"png",new File(out,"real-comparison.png"));
        File dynamic=new File(args[5]);int[] frames={0,9,18,27,36,45};
        BufferedImage sheet=new BufferedImage(1600,frames.length*320,BufferedImage.TYPE_INT_RGB);g=sheet.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        for(int row=0;row<frames.length;row++)for(int i=0;i<4;i++){
            String name=(i<2?"legacy":"forward")+"-"+(i%2==0?"skin-0":"rounded-sheet")+"-frame-"+frames[row]+".png";
            BufferedImage p=ImageIO.read(new File(dynamic,name));g.setColor(Color.BLACK);g.drawString(name,i*400+4,row*320+15);
            g.drawImage(p,i*400,row*320+20,400,300,null);
        }
        g.dispose();ImageIO.write(sheet,"png",new File(out,"dynamic-comparison.png"));
        BufferedImage frame45=new BufferedImage(1400,640,BufferedImage.TYPE_INT_RGB);g=frame45.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,1400,640);
        for(int i=0;i<4;i++){
            String name=(i<2?"legacy":"forward")+"-"+(i%2==0?"skin-0":"rounded-sheet")+"-frame-45.png";
            BufferedImage p=ImageIO.read(new File(args.length>6?new File(args[6]):dynamic,name));int x=i%2*700,y=i/2*320;g.setColor(Color.BLACK);g.drawString(name,x+8,y+18);
            g.drawImage(p.getSubimage(150,380,850,350),x,y+25,700,288,null);
        }
        g.dispose();ImageIO.write(frame45,"png",new File(out,"frame45-comparison.png"));
        if(args.length>7)try(PrintWriter log=new PrintWriter(new File(out,"frame45-reference-check.txt"))){
            for(String renderer:new String[]{"legacy","forward"}){
                String name=renderer+"-skin-0-frame-45.png";
                BufferedImage a=ImageIO.read(new File(args[6],name)),b=ImageIO.read(new File(args[7],name));long changed=0;
                for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;
                log.println(renderer+" reference pixel differences="+changed);
            }
        }
    }
}
