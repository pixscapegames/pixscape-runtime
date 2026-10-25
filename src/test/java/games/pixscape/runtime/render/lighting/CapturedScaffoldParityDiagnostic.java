package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.spatial.ActorVolumeData;
import java.io.*;
import java.nio.file.Files;

/** All indexed contributions from the real frozen scaffold, not a single-tile substitute. */
public final class CapturedScaffoldParityDiagnostic {
    public static void main(String[] args)throws Exception{
        JsonValue pose=new JsonReader().parse(new String(Files.readAllBytes(new File(args[0],"pose.json").toPath()),"UTF-8"));
        JsonValue queue=pose.get("queue");int[] slots=queue.get("sourceSlot").asIntArray(),textures=queue.get("textureHandle").asIntArray();
        String[] keys={"x1","y1","x2","y2","x3","y3","x4","y4"};float[][] positions=new float[8][];
        for(int k=0;k<8;k++)positions[k]=queue.get(keys[k]).asFloatArray();
        File out=new File(args[1]);out.mkdirs();int contributions=0,total=0;
        try(PrintWriter report=new PrintWriter(new File(out,"parity.txt"));PrintWriter data=new PrintWriter(new File(out,"triangles.tsv"))){
            data.println("ref\ttriangle\tvertex\tX\tY\tZ\tu\tv\tnx\tny\tnz");
            for(JsonValue c:queue.get("lighting").get("casters")){JsonValue s=c.get("surface");if(s.getInt("pocSourceBlockId",0)!=29)continue;
                int ref=c.getInt("tiledRef"),index=-1;for(int i=0;i<queue.getInt("size");i++)if(slots[i]==ref&&textures[i]==c.getInt("texture")){index=i;break;}
                if(index<0)throw new AssertionError("Missing drawn quad for "+ref);float[] q=new float[8];for(int k=0;k<8;k++)q[k]=positions[k][index];
                float[] a=s.get("vertices").asFloatArray();ActorVolumeData v=new ActorVolumeData();v.width=128;v.depth=384;v.height=147.89038f;
                ActorVolumeCaster actor=new ActorVolumeCaster();actor.update(v,3072,4480,0,q);
                VolumeSilhouetteParityTest.assertEquivalent(a,actor.vertices,actor.vertexCount);
                report.println("ref="+ref+" slot="+index+" texture="+c.getInt("texture")+" group="+s.getInt("shadowGroupId")+" tileTriangles="+a.length/24+" actorTriangles="+actor.vertexCount/3+" XYZ/UV coverage=PASS quad="+java.util.Arrays.toString(q));
                report.println("atlas="+c.getFloat("u1")+","+c.getFloat("v1")+","+c.getFloat("u2")+","+c.getFloat("v2"));
                for(int o=0;o<a.length;o+=8)data.println(ref+"\t"+o/24+"\t"+o/8+"\t"+a[o]+"\t"+a[o+1]+"\t"+a[o+2]+"\t"+a[o+3]+"\t"+a[o+4]+"\t"+a[o+5]+"\t"+a[o+6]+"\t"+a[o+7]);
                contributions++;total+=a.length/24;
            }
            if(contributions!=3)throw new AssertionError("Incomplete scaffold");report.println("All "+contributions+" contributions / "+total+" triangles have equivalent physical XYZ/UV through ActorVolumeCaster. Face ordering/fan may differ.");
        }
    }
}
