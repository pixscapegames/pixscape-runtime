package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.spatial.ActorVolumeData;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Headless point-sampled geometry diagnostic. Not a shadow-map or GPU oracle. */
public final class Frame45Diagnostic {
    private static final Color[] COLORS={Color.RED,Color.ORANGE,Color.BLUE,Color.MAGENTA,new Color(0,150,80),Color.CYAN};
    public static void main(String[] args) throws Exception {
        File dir=new File(args[0]);dir.mkdirs();
        ActorVolumeData v=new ActorVolumeData();v.width=86.40844f;v.depth=32;v.height=82.98047f;
        v.offsetX=22.82808f;v.offsetY=10.003464f;v.orientation=343.33826f+45*7.5f;v.validate();
        float[] q={-194.71706f,-64.858f,-194.71706f,194.576f,64.71706f,194.576f,64.71706f,-64.858f};
        ActorVolumeCaster c=new ActorVolumeCaster();c.update(v,-32.5f,32.5f,0,q);
        if(c.vertexCount!=36)throw new AssertionError("Face indexing requires this exact unclipped box");
        double lx=220*Math.cos(Math.toRadians(337.5)),ly=220*Math.sin(Math.toRadians(337.5)),lz=250;
        BufferedImage alpha=ImageIO.read(new File("../tiled-iso-demo/studio/orig/animations/hero_idle__a1303/hero_idle__a1303_0060.png"));
        ArrayList<double[]> points=new ArrayList<double[]>();float[] a=c.vertices;
        try(PrintWriter out=new PrintWriter(new File(dir,"faces.tsv"))){
            out.println("face\tnx\tny\tnz\treferenceSide\tlightPlaneDistance\tvertex\tx\ty\tz\tu\tv");
            for(int o=0;o<c.vertexCount*8;o+=8){int f=o/48,b=f*48;
                double d=a[b+5]*(lx-a[b])+a[b+6]*(ly-a[b+1])+a[b+7]*(lz-a[b+2]);
                out.println(f+"\t"+a[o+5]+"\t"+a[o+6]+"\t"+a[o+7]+"\t"+(a[o+7]-a[o+5]-a[o+6])+"\t"+d+"\t"+o/8+"\t"+a[o]+"\t"+a[o+1]+"\t"+a[o+2]+"\t"+a[o+3]+"\t"+a[o+4]);
            }
        }
        for(int y=0;y<alpha.getHeight();y++)for(int x=0;x<alpha.getWidth();x++){
            if((alpha.getRGB(x,y)>>>24)<128)continue;
            double u=1-(x+.5)/alpha.getWidth(),vv=1-(y+.5)/alpha.getHeight();boolean[] found=new boolean[6];
            for(int o=0;o<c.vertexCount*8;o+=24){int face=o/48;if(found[face])continue;
                double ax=a[o+11]-a[o+3],ay=a[o+12]-a[o+4],bx=a[o+19]-a[o+3],by=a[o+20]-a[o+4];
                double det=ax*by-ay*bx;if(Math.abs(det)<1e-10)continue;
                double px=u-a[o+3],py=vv-a[o+4],s=(px*by-py*bx)/det,t=(ax*py-ay*px)/det;
                if(s<0||t<0||s+t>1)continue;found[face]=true;
                double wx=a[o]+s*(a[o+8]-a[o])+t*(a[o+16]-a[o]);
                double wy=a[o+1]+s*(a[o+9]-a[o+1])+t*(a[o+17]-a[o+1]);
                double wz=a[o+2]+s*(a[o+10]-a[o+2])+t*(a[o+18]-a[o+2]);
                double k=lz/(lz-wz),gx=lx+k*(wx-lx),gy=ly+k*(wy-ly);
                double side=a[o+7]-a[o+5]-a[o+6],light=a[o+5]*(lx-wx)+a[o+6]*(ly-wy)+a[o+7]*(lz-wz);
                points.add(new double[]{face,u,vv,gx-gy,(gx+gy)/2,wz,side,light,wx,wy,y*alpha.getWidth()+x});
            }
        }
        BufferedImage image=new BufferedImage(1500,950,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1500,950);
        String[] labels={"Full", "Reference rear", "Reference front", "Light facing", "Light away"};
        for(int mode=0;mode<5;mode++){
            int ox=mode*300;g.setColor(Color.BLACK);g.drawString(labels[mode]+" / CPU samples",ox+10,20);
            g.drawString("UV after flip",ox+10,45);g.drawString("Ground (same scale)",ox+10,390);
            int n=0;double minZ=1e9;int[] faces=new int[6];HashSet<Integer> unique=new HashSet<Integer>();
            for(double[] p:points){if(!selected(mode,p))continue;n++;faces[(int)p[0]]++;minZ=Math.min(minZ,p[5]);g.setColor(COLORS[(int)p[0]]);
                g.fillRect(ox+10+(int)(p[1]*270),60+(int)((1-p[2])*270),2,2);
                unique.add((int)p[10]);g.fillRect(ox+240+(int)(p[3]*.8),620-(int)(p[4]*.8),2,2);
            }
            g.setColor(Color.BLACK);g.drawString("n="+n+" minZ="+String.format(Locale.ROOT,"%.3f",minZ),ox+10,820);
            g.drawString(Arrays.toString(faces),ox+10,840);
            System.out.println(labels[mode]+" samples="+n+" uniqueUV="+unique.size()+" duplicatedUV="+(n-unique.size())+" minZ="+minZ+" faces="+Arrays.toString(faces));
        }
        for(int f=0;f<6;f++){g.setColor(COLORS[f]);g.fillRect(20+f*240,900,15,15);g.drawString("face "+f+" triangles "+(2*f)+","+(2*f+1),40+f*240,913);}
        g.dispose();ImageIO.write(image,"png",new File(dir,"comparison.png"));
        // Isolate each face; same UV/ground scale as the overview.
        BufferedImage detail=new BufferedImage(1800,800,BufferedImage.TYPE_INT_RGB);g=detail.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1800,800);
        for(int f=0;f<6;f++){int ox=f*300;g.setColor(Color.BLACK);g.drawString("Face "+f+" UV / ground",ox+10,20);
            for(int o=f*48;o<(f+1)*48;o+=24){Polygon poly=new Polygon();for(int j=0;j<3;j++)poly.addPoint(ox+10+(int)(a[o+j*8+3]*270),40+(int)((1-a[o+j*8+4])*270));g.drawPolygon(poly);}
            for(double[] p:points)if((int)p[0]==f){g.setColor(COLORS[f]);g.fillRect(ox+10+(int)(p[1]*270),40+(int)((1-p[2])*270),2,2);g.fillRect(ox+240+(int)(p[3]*.8),600-(int)(p[4]*.8),2,2);}
        }
        g.dispose();ImageIO.write(detail,"png",new File(dir,"faces.png"));
        if(args.length>1){
            String[] modes={"Full", "Front", "Rear", "Light facing", "Light away"};
            BufferedImage sheet=new BufferedImage(1900,520,BufferedImage.TYPE_INT_RGB);g=sheet.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1900,520);
            for(int r=0;r<2;r++)for(int mode=0;mode<5;mode++){
                String name=(r==0?"legacy":"forward")+"-skin-"+mode+"-frame-45.png";
                BufferedImage current=ImageIO.read(new File(args[1],name));
                g.setColor(Color.BLACK);g.drawString((r==0?"Legacy ":"Forward ")+modes[mode],mode*380+5,r*260+16);
                g.drawImage(current.getSubimage(280,420,380,240),mode*380,r*260+20,null);
                if(mode<3){File previous=new File("build/local-validation/actor-shadows-20261024T221445Z",name);
                    if(previous.exists()){BufferedImage old=ImageIO.read(previous);int different=0;for(int y=0;y<old.getHeight();y++)for(int x=0;x<old.getWidth();x++)if(old.getRGB(x,y)!=current.getRGB(x,y))different++;
                        System.out.println("ARCHIVE_COMPARE "+name+" differentPixels="+different);}
                }
            }
            g.dispose();ImageIO.write(sheet,"png",new File(dir,"gpu-comparison.png"));
        }
    }
    private static boolean selected(int mode,double[] p){return mode==0||mode==1&&p[6]<0||mode==2&&p[6]>0||mode==3&&p[7]>0||mode==4&&p[7]<0;}
}
