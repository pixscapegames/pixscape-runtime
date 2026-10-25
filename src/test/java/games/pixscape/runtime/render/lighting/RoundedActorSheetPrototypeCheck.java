package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.spatial.ActorVolumeData;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;

/** Headless numerical gate and isolated archived-pose preparation, never a GPU validation. */
public final class RoundedActorSheetPrototypeCheck {
    static ActorVolumeData volume(){ActorVolumeData v=new ActorVolumeData();v.width=86.40844f;v.depth=32;v.height=82.98047f;v.offsetX=22.82808f;v.offsetY=10.003464f;v.orientation=343.33997f;return v;}
    public static void main(String[] args)throws Exception{
        Path source=Paths.get(args[0]),out=Paths.get(args[1]);Files.createDirectories(out);
        JsonValue pose=new JsonReader().parse(new String(Files.readAllBytes(source.resolve("pose.json")),"UTF-8"));
        JsonValue caster=null;for(JsonValue c:pose.get("queue").get("lighting").get("casters"))if(c.get("surface").getBoolean("actor",false)){caster=c;break;}
        if(caster==null)throw new AssertionError("Actor missing");
        float[] receiver=caster.get("surface").get("vertices").asFloatArray(),q=new float[8];
        for(int o=0;o<receiver.length;o+=8){int k=receiver[o+3]>.5f?(receiver[o+4]>.5f?2:3):(receiver[o+4]>.5f?1:0);q[k*2]=receiver[o]-receiver[o+1];q[k*2+1]=(receiver[o]+receiver[o+1])*.5f+receiver[o+2];}
        ActorVolumeData v=volume();RoundedActorSheetPrototype candidate=new RoundedActorSheetPrototype();
        candidate.update(v,3221.7805f,3157.8274f,0,q);check(candidate,q,0,v);
        int actual=candidate.vertexCount;float[] mesh=java.util.Arrays.copyOf(candidate.vertices,actual*8);
        double[] footZ=new double[2];int i=0;
        for(int[] p:new int[][]{{69,139},{123,121}})footZ[i++]=sample(candidate,(p[0]+.5)/180,1-(p[1]+.5)/180)[2];
        int updates=candidate.updates;for(i=0;i<10000;i++)candidate.update(v,3221.7805f,3157.8274f,0,q);
        if(candidate.updates!=updates)throw new AssertionError("Unchanged volume rebuilt");
        long start=System.nanoTime();int maxVertices=0;
        for(i=0;i<=720;i++){v.orientation=i*.5f;candidate.update(v,3221.7805f,3157.8274f,0,q);check(candidate,q,0,v);maxVertices=Math.max(maxVertices,candidate.vertexCount);}
        long nanos=System.nanoTime()-start;
        double maxPoleDelta=0;
        for(float pole:new float[]{45,225}){
            v.orientation=pole-.001f;candidate.update(v,3221.7805f,3157.8274f,0,q);double[] before=sample(candidate,.55,.65);
            v.orientation=pole+.001f;candidate.update(v,3221.7805f,3157.8274f,0,q);double[] after=sample(candidate,.55,.65);
            for(int k=0;k<3;k++)maxPoleDelta=Math.max(maxPoleDelta,Math.abs(before[k]-after[k]));
        }
        if(maxPoleDelta>.1)throw new AssertionError("Pole discontinuity "+maxPoleDelta);
        float[] frame45={-194.71706f,-64.858f,-194.71706f,194.576f,64.71706f,194.576f,64.71706f,-64.858f};
        v.orientation=320.83826f;candidate.update(v,-32.5f,32.5f,0,frame45);check(candidate,frame45,0,v);int count45=candidate.vertexCount;
        java.awt.image.BufferedImage alpha=javax.imageio.ImageIO.read(new File("../tiled-iso-demo/studio/orig/animations/hero_idle__a1303/hero_idle__a1303_0060.png"));
        int samples45=0,grazing45=0,ground45=0;double sumIncidence45=0;
        for(int y=0;y<alpha.getHeight();y++)for(int x=0;x<alpha.getWidth();x++)if((alpha.getRGB(x,y)>>>24)>=131){
            double[] p=sample(candidate,1-(x+.5)/alpha.getWidth(),1-(y+.5)/alpha.getHeight());
            int o=(int)p[3];double lx=203.2535-p[0],ly=-84.19035-p[1],lz=250-p[2];
            double cosine=Math.abs(lx*candidate.vertices[o+5]+ly*candidate.vertices[o+6]+lz*candidate.vertices[o+7])/Math.sqrt(lx*lx+ly*ly+lz*lz);
            samples45++;sumIncidence45+=cosine;if(cosine<.02)grazing45++;if(p[2]<=.001)ground45++;
        }
        JsonValue surface=caster.get("surface");surface.remove("shadowVertices");JsonValue array=new JsonValue(JsonValue.ValueType.array);
        for(float f:mesh)array.addChild(new JsonValue(f));surface.addChild("shadowVertices",array);surface.remove("shadowVertexCount");surface.addChild("shadowVertexCount",new JsonValue(actual));
        for(int k=0;k<3;k++){float lo=Float.POSITIVE_INFINITY,hi=-lo;for(int o=k;o<mesh.length;o+=8){lo=Math.min(lo,mesh[o]);hi=Math.max(hi,mesh[o]);}
            for(int end=0;end<2;end++){String field=(end==0?"min":"max")+"XYZ".charAt(k);caster.remove(field);caster.addChild(field,new JsonValue(end==0?lo:hi));}}
        for(JsonValue texture:pose.get("textures")){Path target=out.resolve(texture.getString("file"));if(!Files.exists(target))Files.copy(source.resolve(texture.getString("file")),target);}
        Files.write(out.resolve("pose.json"),pose.toJson(JsonWriter.OutputType.json).getBytes("UTF-8"));
        for(String name:new String[]{"READY","world.png"})if(!Files.exists(out.resolve(name)))Files.copy(source.resolve(name),out.resolve(name));
        try(PrintWriter log=new PrintWriter(out.resolve("numerical-check.txt").toFile())){
            log.println("TEST PROTOTYPE ONLY. No production selection. CPU mathematical checks, no GPU/render quality claim.");
            log.printf(Locale.ROOT,"Real pose triangles=%d versus12 original; frame45 triangles=%d; max in721 orientations=%d%n",actual/3,count45/3,maxVertices/3);
            log.printf(Locale.ROOT,"Foot samples Z=%.8f,%.8f; max delta across poles +/-0.001deg=%.8f%n",footZ[0],footZ[1],maxPoleDelta);
            log.printf(Locale.ROOT,"721 builds including assertions=%.3fms (not render performance);10,000 unchanged updates caused0 rebuilds%n",nanos/1e6);
            log.println("Every tested mesh: finite XYZ/normals; UV area1; projection matches explicit transverse bow within0.002; no vertex below base. Displayed sprite unchanged, caster projection intentionally bowed.");
            log.println("Flips remain texture UV transforms: prototype does not inspect or replace the frame/atlas mapping.");
            log.printf(Locale.ROOT,"Frame45 real texture60 flipX=true: opaqueSamples=%d grazingAbsNdotLbelow0.02=%d clampedGround=%d meanAbsNdotL=%.8f%n",samples45,grazing45,ground45,sumIncidence45/samples45);
            log.println("Existing caster identity and receivers unchanged. Copied textures and pose are isolated from source.");
            log.println("No new passes, framebuffer, atlas. Reserved output float storage="+candidate.vertices.length*4+" bytes; actual real mesh="+mesh.length*4+" bytes. CPU/allocation/GPU benchmarks not measured.");
        }
        System.out.println(new String(Files.readAllBytes(out.resolve("numerical-check.txt")),"UTF-8"));
    }
    static void check(RoundedActorSheetPrototype p,float[] q,double base,ActorVolumeData volume){double area=0;
        for(int o=0;o<p.vertexCount*8;o+=24){area+=Math.abs((p.vertices[o+11]-p.vertices[o+3])*(p.vertices[o+20]-p.vertices[o+4])-(p.vertices[o+12]-p.vertices[o+4])*(p.vertices[o+19]-p.vertices[o+3]))/2;
            for(int j=0;j<3;j++){int k=o+j*8;for(int a=0;a<8;a++)if(!Float.isFinite(p.vertices[k+a]))throw new AssertionError("Non-finite vertex");
                float sx=BaselineActorShadowImageMapping.x(q,p.vertices[k+3],p.vertices[k+4]),sy=BaselineActorShadowImageMapping.y(q,p.vertices[k+3],p.vertices[k+4]);
                double dx=Math.cos(Math.toRadians(volume.orientation)),dy=Math.sin(Math.toRadians(volume.orientation));
                double u=p.vertices[k+3],bow=volume.depth*.5*4*u*(1-u)*Math.min(1,(p.vertices[k+2]-base)/volume.height);
                sx+=(-dy-dx)*bow;sy+=(dx-dy)*bow*.5;
                if(Math.abs(p.vertices[k]-p.vertices[k+1]-sx)>.002||Math.abs((p.vertices[k]+p.vertices[k+1])/2+p.vertices[k+2]-sy)>.002)throw new AssertionError("Projection drift");
                if(p.vertices[k+2]<base-1e-5)throw new AssertionError("Below ground");}}
        if(Math.abs(area-1)>.00001)throw new AssertionError("UV area="+area);
    }
    static double[] sample(RoundedActorSheetPrototype p,double u,double v){
        for(int o=0;o<p.vertexCount*8;o+=24){float[] a=p.vertices;double ax=a[o+11]-a[o+3],ay=a[o+12]-a[o+4],bx=a[o+19]-a[o+3],by=a[o+20]-a[o+4],det=ax*by-ay*bx;
            double s=((u-a[o+3])*by-(v-a[o+4])*bx)/det,t=(ax*(v-a[o+4])-ay*(u-a[o+3]))/det;
            if(s< -1e-6||t< -1e-6||s+t>1.000001)continue;
            return new double[]{a[o]+s*(a[o+8]-a[o])+t*(a[o+16]-a[o]),a[o+1]+s*(a[o+9]-a[o+1])+t*(a[o+17]-a[o+1]),a[o+2]+s*(a[o+10]-a[o+2])+t*(a[o+18]-a[o+2]),o};
        }throw new AssertionError("Missing UV sample");
    }
}
