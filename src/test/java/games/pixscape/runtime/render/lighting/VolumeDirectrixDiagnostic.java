package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Locale;

/** Cold diagnostic only: reconstruct the captured sprite on the authored longitudinal plane. */
public final class VolumeDirectrixDiagnostic {
    // Authored volume and anchor recorded in scaffold-hero-20261025/REPORT.txt.
    static final double CX=3244.60858, CY=3167.830864, CZ=0;
    static final double WIDTH=86.40844, DEPTH=32, HEIGHT=82.98047, YAW=343.33997;
    static final double DX=Math.cos(Math.toRadians(YAW)), DY=Math.sin(Math.toRadians(YAW));
    static double[] point(double sx,double sy) {
        double t=(sx-(CX-CY))/(DX-DY);
        double x=CX+t*DX,y=CY+t*DY;
        return new double[]{x,y,sy-(x+y)/2,t};
    }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]),out=new File(args[1]);out.mkdirs();
        JsonValue pose=new JsonReader().parse(new String(Files.readAllBytes(new File(root,"pose.json").toPath()),"UTF-8"));
        JsonValue c=null;for(JsonValue v:pose.get("queue").get("lighting").get("casters"))if(v.get("surface").getBoolean("actor",false)){c=v;break;}
        if(c==null)throw new AssertionError("Missing captured actor");
        float[] q=new float[8],receiver=c.get("surface").get("vertices").asFloatArray();
        // Recover the displayed quad by projection only; never use receiver depth as geometry.
        for(int o=0;o<receiver.length;o+=8){int k=receiver[o+3]>.5f?(receiver[o+4]>.5f?2:3):(receiver[o+4]>.5f?1:0);
            q[k*2]=receiver[o]-receiver[o+1];q[k*2+1]=(receiver[o]+receiver[o+1])*.5f+receiver[o+2];}
        String file=null;for(JsonValue v:pose.get("textures"))if(v.getInt("handle")==c.getInt("texture"))file=v.getString("file");
        BufferedImage atlas=ImageIO.read(new File(root,file));
        double u1=c.getDouble("u1"),u2=c.getDouble("u2"),v1=c.getDouble("v1"),v2=c.getDouble("v2");
        int w=(int)Math.round(Math.abs(u2-u1)*atlas.getWidth()),h=(int)Math.round(Math.abs(v2-v1)*atlas.getHeight());
        double slope=(DX+DY)/2/(DX-DY), screenCX=CX-CY,screenCY=(CX+CY)/2+CZ;
        float leftV=(float)((screenCY+slope*(q[0]-screenCX)-q[1])/(q[3]-q[1]));
        float rightV=(float)((screenCY+slope*(q[6]-screenCX)-q[1])/(q[3]-q[1]));
        BaselineActorShadowGeometry old=new BaselineActorShadowGeometry();
        old.calibrate(q,new BaselineActorShadowProfile(leftV,rightV,0),0,false,false);
        double maxError=0;for(int i=0;i<old.vertexCount;i++){
            int o=i*8;double[] p=point(BaselineActorShadowImageMapping.x(q,old.calibrated[o+3],old.calibrated[o+4]),BaselineActorShadowImageMapping.y(q,old.calibrated[o+3],old.calibrated[o+4]));
            for(int k=0;k<3;k++)maxError=Math.max(maxError,Math.abs(p[k]-old.calibrated[o+k]));
        }
        if(maxError>.002)throw new AssertionError("Historical reconstruction mismatch: "+maxError);
        BufferedImage figure=new BufferedImage(1440,700,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=figure.createGraphics();g.setColor(new Color(245,246,248));g.fillRect(0,0,1440,700);
        g.setFont(new Font("SansSerif",Font.PLAIN,15));g.setColor(Color.DARK_GRAY);
        g.drawString("CAPTURE REELLE - diagnostic CPU, sans modification de la scene / sans rendu GPU",20,25);
        g.drawString("1. Vue selon l'axe transversal",20,60);g.drawString("2. Projection ecran de la boite et du sprite",500,60);
        g.drawString("3. Ombre sur Z=0 : points CPU",990,60);
        g.drawString("Orange : directrice au centre de base",20,650);
        g.drawString("Rouge : alpha reconstruit sous le sol",20,677);
        g.drawString("Bleu : volume complet (deux intersections)",990,650);
        g.drawString("Vert : feuille orientee, points Z >= 0 seulement",990,677);
        JsonValue light=pose.get("queue").get("lighting").get("lights").child;
        double lx=light.getDouble("x"),ly=light.getDouble("y"),lz=light.getDouble("z");
        float[] mesh=c.get("surface").get("shadowVertices").asFloatArray();int count=c.get("surface").getInt("shadowVertexCount");
        int opaque=0,below=0,outside=0;double minZ=Double.POSITIVE_INFINITY,maxZ=-minZ;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double u=(x+.5)/w,v=1-(y+.5)/h;
            int rgb=atlas.getRGB((int)((u1+u*(u2-u1))*atlas.getWidth()),(int)((v2+v*(v1-v2))*atlas.getHeight()));
            if((rgb>>>24)<131)continue;
            opaque++;double sx=BaselineActorShadowImageMapping.x(q,(float)u,(float)v),sy=BaselineActorShadowImageMapping.y(q,(float)u,(float)v);
            double[] p=point(sx,sy);minZ=Math.min(minZ,p[2]);maxZ=Math.max(maxZ,p[2]);
            if(p[2]<0)below++;if(Math.abs(p[3])>WIDTH/2||p[2]<0||p[2]>HEIGHT)outside++;
            g.setColor(p[2]<0?Color.RED:new Color(rgb));
            g.fillRect((int)(250+p[3]*3),(int)(520-p[2]*3),4,4);
            g.fillRect((int)(720+(sx-screenCX)*2),(int)(520-(sy-screenCY)*2),3,3);
            for(int side=0;side<2;side++){
                double[] hit=hit(mesh,count,u,v,side==0);if(hit!=null)ground(g,hit,lx,ly,lz,new Color(80,110,220));
            }
        }
        // Draw the single sheet last; negative-height samples deliberately do not cast onto the floor.
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double u=(x+.5)/w,v=1-(y+.5)/h;
            int rgb=atlas.getRGB((int)((u1+u*(u2-u1))*atlas.getWidth()),(int)((v2+v*(v1-v2))*atlas.getHeight()));
            if((rgb>>>24)<131)continue;
            double[] p=point(BaselineActorShadowImageMapping.x(q,(float)u,(float)v),BaselineActorShadowImageMapping.y(q,(float)u,(float)v));
            if(p[2]>=0)ground(g,p,lx,ly,lz,new Color(0,150,70));
        }
        g.setStroke(new BasicStroke(2));g.setColor(Color.GRAY);
        g.drawRect((int)(250-WIDTH*1.5),(int)(520-HEIGHT*3),(int)(WIDTH*3),(int)(HEIGHT*3));
        double[][] corners=new double[8][];
        for(int i=0;i<8;i++){
            double t=(i%4==0||i%4==3?-.5:.5)*WIDTH,r=(i%4<2?-.5:.5)*DEPTH,z=i<4?0:HEIGHT;
            double px=CX+DX*t-DY*r,py=CY+DY*t+DX*r;
            corners[i]=new double[]{720+(px-py-screenCX)*2,520-((px+py)/2+z-screenCY)*2};
        }
        for(int i=0;i<8;i++){int j=(i/4)*4+(i+1)%4;line(g,corners[i],corners[j]);if(i<4)line(g,corners[i],corners[i+4]);}
        g.setColor(new Color(230,120,0));g.drawLine(90,520,410,520);
        line(g,new double[]{720-(DX-DY)*WIDTH,520+(DX+DY)*WIDTH/2},new double[]{720+(DX-DY)*WIDTH,520-(DX+DY)*WIDTH/2});
        g.fillOval(716,516,8,8);g.drawString("C",730,540);
        g.setColor(new Color(40,70,180));
        double[] anchorScreen={720+((3221.7805-3157.8274)-screenCX)*2,520-((3221.7805+3157.8274)/2-screenCY)*2};
        line(g,anchorScreen,new double[]{720,520});g.fillOval((int)anchorScreen[0]-3,(int)anchorScreen[1]-3,6,6);
        g.drawString("A : ancre",(int)anchorScreen[0]-85,(int)anchorScreen[1]+14);
        g.setColor(Color.BLACK);g.drawString("Boite : 86.408 x 32 x 82.980 ; yaw 343.340 deg",500,610);
        g.drawString("Z alpha : "+String.format(Locale.ROOT,"%.2f .. %.2f ; sous sol : %d / %d",minZ,maxZ,below,opaque),20,610);
        g.dispose();ImageIO.write(figure,"png",new File(out,"directrix-capture.png"));
        try(PrintWriter log=new PrintWriter(new File(out,"measurements.txt"))){
            log.println("Source="+root.getCanonicalPath());
            log.printf(Locale.ROOT,"C=(%.8f,%.8f,0) D=(%.8f,%.8f,0) transverse=(-Dy,Dx,0)%n",CX,CY,DX,DY);
            log.printf(Locale.ROOT,"Historical profile derived from box: leftV=%.8f rightV=%.8f; maximum XYZ difference=%.8f%n",leftV,rightV,maxError);
            log.printf(Locale.ROOT,"Opaque=%d belowGround=%d outsideLongitudinalRectangle=%d Z=[%.8f,%.8f]%n",opaque,below,outside,minZ,maxZ);
            for(int[] pixel:new int[][]{{69,139},{123,121}}){double u=(pixel[0]+.5)/w,v=1-(pixel[1]+.5)/h;double[] p=point(BaselineActorShadowImageMapping.x(q,(float)u,(float)v),BaselineActorShadowImageMapping.y(q,(float)u,(float)v));log.printf(Locale.ROOT,"Foot image=(%d,%d) P=(%.8f,%.8f,%.8f) localLongitudinal=%.8f transverseNeededForGround=%.8f (box +/-16)%n",pixel[0],pixel[1],p[0],p[1],p[2],p[3],p[2]*(DX-DY));}
            log.println("Thickness not invented: historical one-sided bend moves projection; preserving projection requires solving longitudinal position and height again.");
            log.println("At yaw=45 or225 deg, Dx-Dy=0: no unique intersection with the longitudinal vertical plane for arbitrary sprite pixels.");
            log.println("No GPU validation or production integration claimed. Alpha samples below ground violate expected planted-foot anchoring before thickness.");
        }
    }
    static void line(Graphics2D g,double[] a,double[] b){g.drawLine((int)a[0],(int)a[1],(int)b[0],(int)b[1]);}
    static void ground(Graphics2D g,double[] p,double lx,double ly,double lz,Color color){
        if(p[2]>=lz)return;double t=lz/(lz-p[2]),x=lx+t*(p[0]-lx),y=ly+t*(p[1]-ly);
        g.setColor(color);g.fillRect((int)(1030+(x-y-40)*.95),(int)(560-((x+y)/2-3160)*.95),2,2);
    }
    static double[] hit(float[] a,int count,double u,double v,boolean rear){
        for(int o=0;o<count*8;o+=24){if((a[o+7]-a[o+5]-a[o+6]<0)!=rear)continue;
            double ax=a[o+11]-a[o+3],ay=a[o+12]-a[o+4],bx=a[o+19]-a[o+3],by=a[o+20]-a[o+4],det=ax*by-ay*bx;
            if(Math.abs(det)<1e-12)continue;double dx=u-a[o+3],dy=v-a[o+4],s=(dx*by-dy*bx)/det,t=(ax*dy-ay*dx)/det;
            if(s<0||t<0||s+t>1)continue;return new double[]{a[o]+s*(a[o+8]-a[o])+t*(a[o+16]-a[o]),a[o+1]+s*(a[o+9]-a[o+1])+t*(a[o+17]-a[o+1]),a[o+2]+s*(a[o+10]-a[o+2])+t*(a[o+18]-a[o+2])};
        }return null;
    }
}
