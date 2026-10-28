package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongMap;

/** Cold static broad phase. Moving sheets remain a separate bounded dynamic domain. */
public final class CasterIndex {
    private static final float CELL=256;
    private final LongMap<IntArray> cells=new LongMap<IntArray>();
    private final IntArray large=new IntArray();
    public final IntArray candidates=new IntArray();
    private int[] seen=new int[0];private int stamp;
    public void rebuild(LightingFrame frame,int count){
        cells.clear();large.clear();seen=new int[count];stamp=0;
        candidates.ensureCapacity(frame.casters.size);
        for(int i=0;i<count;i++){
            LightingFrame.Caster c=frame.casters.get(i);
            int x0=cell(c.minX),x1=cell(c.maxX),y0=cell(c.minY),y1=cell(c.maxY);
            if((long)(x1-x0+1)*(y1-y0+1)>4096){large.add(i);continue;}
            for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++){
                long key=key(x,y);IntArray list=cells.get(key);
                if(list==null){list=new IntArray(false,4);cells.put(key,list);}list.add(i);
            }
        }
    }
    public void query(LightingFrame frame,LightingFrame.Light light,float margin,int staticCount){
        candidates.clear();if(++stamp==0){java.util.Arrays.fill(seen,0);stamp=1;}
        float radius=light.radius+margin;int x0=cell(light.x-radius),x1=cell(light.x+radius),y0=cell(light.y-radius),y1=cell(light.y+radius);
        if((long)(x1-x0+1)*(y1-y0+1)>4096){
            for(int i=0;i<staticCount;i++)add(frame,light,margin,i);
        }else{
            for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++){
                IntArray list=cells.get(key(x,y));if(list==null)continue;
                for(int i=0;i<list.size;i++){int index=list.get(i);if(seen[index]!=stamp){seen[index]=stamp;add(frame,light,margin,index);}}
            }
            for(int i=0;i<large.size;i++)add(frame,light,margin,large.get(i));
        }
        for(int i=staticCount;i<frame.casters.size;i++)add(frame,light,margin,i);
    }
    private void add(LightingFrame frame,LightingFrame.Light light,float margin,int i){if(frame.casters.get(i).affects(light,margin))candidates.add(i);}
    private static int cell(float x){return (int)Math.floor(x/CELL);}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
}
