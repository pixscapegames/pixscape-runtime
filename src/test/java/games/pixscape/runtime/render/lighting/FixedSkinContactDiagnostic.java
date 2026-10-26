package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;

/** Headless contact measurements from the captured mesh/texture, independent of skin choice. */
public final class FixedSkinContactDiagnostic {
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]),out=new File(args[1]);out.mkdirs();
        JsonValue pose=new JsonReader().parse(new String(Files.readAllBytes(new File(root,"pose.json").toPath()),"UTF-8"));
        JsonValue caster=null;for(JsonValue c:pose.get("queue").get("lighting").get("casters"))if(c.get("surface").getBoolean("actor",false)){caster=c;break;}
        if(caster==null)throw new AssertionError("Missing actor");
        float[] mesh=caster.get("surface").get("shadowVertices").asFloatArray();int count=caster.get("surface").getInt("shadowVertexCount");
        int handle=caster.getInt("texture");String texture=null;for(JsonValue t:pose.get("textures"))if(t.getInt("handle")==handle)texture=t.getString("file");
        BufferedImage atlas=ImageIO.read(new File(root,texture));
        float u1=caster.getFloat("u1"),u2=caster.getFloat("u2"),v1=caster.getFloat("v1"),v2=caster.getFloat("v2");
        int w=Math.round(Math.abs(u2-u1)*atlas.getWidth()),h=Math.round(Math.abs(v2-v1)*atlas.getHeight());
        BufferedImage sprite=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        int minX=w,maxX=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int rgb=atlas.getRGB((int)((u1+(x+.5)/w*(u2-u1))*atlas.getWidth()),(int)((v1+(y+.5)/h*(v2-v1))*atlas.getHeight()));sprite.setRGB(x,y,rgb);
            if((rgb>>>24)>=131){minX=Math.min(minX,x);maxX=Math.max(maxX,x);}
        }
        int[] fx={0,0},fy={-1,-1};
        for(int y=0;y<h;y++)for(int x=minX;x<=maxX;x++)if((sprite.getRGB(x,y)>>>24)>=131){int half=x<(minX+maxX)/2?0:1;if(y>fy[half]){fy[half]=y;fx[half]=x;}}
        BufferedImage marked=new BufferedImage(w*4,h*4,BufferedImage.TYPE_INT_RGB);Graphics2D g=marked.createGraphics();g.setColor(Color.GRAY);g.fillRect(0,0,w*4,h*4);g.drawImage(sprite,0,0,w*4,h*4,null);
        JsonValue light=pose.get("queue").get("lighting").get("lights").child;double lx=light.getDouble("x"),ly=light.getDouble("y"),lz=light.getDouble("z");
        try(PrintWriter log=new PrintWriter(new File(out,"contact.txt"))){
            log.println("Captured alpha threshold .51; selected bottom opaque texel in each half of alpha bounds. Inspect marked feet; these are image samples, not authored skeletal foot markers.");
            for(int k=0;k<2;k++){
                g.setColor(k==0?Color.RED:Color.CYAN);g.drawOval(fx[k]*4-8,fy[k]*4-8,16,16);g.drawString("P"+k,fx[k]*4+10,fy[k]*4);
                double u=(fx[k]+.5)/w,v=1-(fy[k]+.5)/h;
                for(int skin=0;skin<3;skin++){
                    double[] rear=hit(mesh,count,u,v,true),front=hit(mesh,count,u,v,false);
                    double[] p=skin==0?rear:skin==1?front:rear==null||front==null?null:new double[]{(rear[0]+front[0])/2,(rear[1]+front[1])/2,(rear[2]+front[2])/2,-1};
                    if(p==null){log.println("No support");continue;}
                    double sx=p[0]-p[1],sy=(p[0]+p[1])*.5+p[2];
                    double expectedX=sy+sx*.5,expectedY=sy-sx*.5;
                    double scale=lz/(lz-p[2]),gx=lx+scale*(p[0]-lx),gy=ly+scale*(p[1]-ly);
                    double gap=Math.hypot(gx-gy-sx,(gx+gy)*.5-sy),worldGap=Math.hypot(gx-expectedX,gy-expectedY);
                    log.printf(java.util.Locale.ROOT,"P%d image=(%d,%d) uv=(%.6f,%.6f) %s triangle=%.0f P=(%.5f,%.5f,%.5f) expectedGround=(%.5f,%.5f,0) shadowGround=(%.5f,%.5f,0) groundDistance=%.5f screenDistance=%.5f framebufferDistance=%.5f groundTileGeometry=%s%n",k,fx[k],fy[k],u,v,skin==0?"rear":skin==1?"front":"midpoint",p[3],p[0],p[1],p[2],expectedX,expectedY,gx,gy,worldGap,gap,gap/pose.getDouble("zoom"),floor(pose,expectedX,expectedY));
                }
            }
        }
        g.dispose();ImageIO.write(marked,"png",new File(out,"feet.png"));
        BufferedImage cloud=new BufferedImage(1200,500,BufferedImage.TYPE_INT_RGB);g=cloud.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1200,500);
        for(int i=0;i<3;i++){g.setColor(Color.BLACK);g.drawString(new String[]{"Rear","Front","Midpoint (not production)"}[i]+" CPU ground points",400*i+10,20);}
        java.util.TreeMap<String,Integer> pairs=new java.util.TreeMap<String,Integer>();
        java.util.ArrayList<double[]> mids=new java.util.ArrayList<double[]>();double[][] normal=new double[3][4];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((sprite.getRGB(x,y)>>>24)>=131){
            double u=(x+.5)/w,v=1-(y+.5)/h;double[] r=hit(mesh,count,u,v,true),f=hit(mesh,count,u,v,false);if(r==null||f==null)continue;
            String pair=((int)r[3]/2)+"/"+((int)f[3]/2);pairs.put(pair,pairs.containsKey(pair)?pairs.get(pair)+1:1);
            double[] m={(r[0]+f[0])/2,(r[1]+f[1])/2,(r[2]+f[2])/2};mids.add(new double[]{u,v,m[2]});double[] row={1,u,v};
            for(int i=0;i<3;i++){for(int j=0;j<3;j++)normal[i][j]+=row[i]*row[j];normal[i][3]+=row[i]*m[2];}
            for(int i=0;i<3;i++){double[] p=i==0?r:i==1?f:m;double t=lz/(lz-p[2]),gx=lx+t*(p[0]-lx),gy=ly+t*(p[1]-ly);g.setColor(i==0?Color.BLUE:i==1?Color.RED:new Color(0,140,50));g.fillRect(400*i+40+(int)((gx-gy-40)*.9),430-(int)(((gx+gy)/2-3160)*.9),2,2);}
        }
        for(int i=0;i<3;i++){double pivot=normal[i][i];for(int j=i;j<4;j++)normal[i][j]/=pivot;for(int k=0;k<3;k++)if(k!=i){double factor=normal[k][i];for(int j=i;j<4;j++)normal[k][j]-=factor*normal[i][j];}}
        double residual=0;for(double[] p:mids)residual=Math.max(residual,Math.abs(p[2]-normal[0][3]-normal[1][3]*p[0]-normal[2][3]*p[1]));
        try(PrintWriter log=new PrintWriter(new File(out,"median.txt"))){log.println("Opaque paired UV samples="+mids.size()+" face pairs rear/front="+pairs);log.println("Least squares plane Z=a+b*u+c*v: a="+normal[0][3]+" b="+normal[1][3]+" c="+normal[2][3]+" max absolute residual="+residual);log.println("CPU point samples only; no GPU median surface constructed or integrated.");}
        g.dispose();ImageIO.write(cloud,"png",new File(out,"median-ground.png"));
    }
    private static String floor(JsonValue pose,double x,double y){
        for(JsonValue tile:pose.get("queue").get("lighting").get("tiles")){JsonValue vertices=tile.get("vertices");if(vertices==null)continue;float[] a=vertices.asFloatArray();
            for(int o=0;o+23<a.length;o+=24){if(Math.abs(a[o+2])+Math.abs(a[o+10])+Math.abs(a[o+18])>1e-4)continue;
                double ax=a[o+8]-a[o],ay=a[o+9]-a[o+1],bx=a[o+16]-a[o],by=a[o+17]-a[o+1],det=ax*by-ay*bx;if(Math.abs(det)<1e-9)continue;
                double dx=x-a[o],dy=y-a[o+1],s=(dx*by-dy*bx)/det,t=(ax*dy-ay*dx)/det;if(s>=0&&t>=0&&s+t<=1)return tile.name+":"+o/24;
            }
        }return "not found";
    }
    private static double[] hit(float[] a,int count,double u,double v,boolean rear){
        for(int o=0;o<count*8;o+=24){if((a[o+7]-a[o+5]-a[o+6]<0)!=rear)continue;
            double ax=a[o+11]-a[o+3],ay=a[o+12]-a[o+4],bx=a[o+19]-a[o+3],by=a[o+20]-a[o+4],det=ax*by-ay*bx;
            if(Math.abs(det)<1e-12)continue;double dx=u-a[o+3],dy=v-a[o+4],s=(dx*by-dy*bx)/det,t=(ax*dy-ay*dx)/det;
            if(s<0||t<0||s+t>1)continue;return new double[]{a[o]+s*(a[o+8]-a[o])+t*(a[o+16]-a[o]),a[o+1]+s*(a[o+9]-a[o+1])+t*(a[o+17]-a[o+1]),a[o+2]+s*(a[o+10]-a[o+2])+t*(a[o+18]-a[o+2]),o/24};
        }return null;
    }
}
