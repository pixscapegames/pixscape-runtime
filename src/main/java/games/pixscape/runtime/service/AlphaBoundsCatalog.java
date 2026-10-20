package games.pixscape.runtime.service;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.render.lighting.AlphaBounds;

/** Logical asset/frame metadata. Built once at project loading; lookups, including misses, are O(1). */
public final class AlphaBoundsCatalog {
    public static final String FILE="alpha-bounds.json";
    private final IntMap<IntMap<AlphaBounds>> assets=new IntMap<IntMap<AlphaBounds>>();
    public AlphaBounds get(int assetId,int frame,float threshold) {
        IntMap<AlphaBounds> frames=assets.get(assetId);AlphaBounds b=frames==null?null:frames.get(frame);
        return b!=null&&b.usable(threshold)?b:null;
    }
    public static AlphaBoundsCatalog load(FileHandle project) {
        AlphaBoundsCatalog result=new AlphaBoundsCatalog();FileHandle file=project.child(FILE);
        if(!file.exists())return result;
        JsonValue entries=new JsonReader().parse(file).get("assets");if(entries==null)return result;
        for(JsonValue asset=entries.child;asset!=null;asset=asset.next){
            int id=asset.getInt("assetId");if(id<=0||result.assets.containsKey(id))throw new IllegalArgumentException("Invalid or duplicate alpha bounds asset");
            IntMap<AlphaBounds> frames=new IntMap<AlphaBounds>();result.assets.put(id,frames);
            JsonValue regions=asset.get("regions");if(regions==null)continue;
            for(JsonValue region=regions.child;region!=null;region=region.next){
                int frame=region.getInt("frame",0);JsonValue v=region.get("bounds");if(v==null)continue;
                AlphaBounds b=new AlphaBounds();b.minU=v.getFloat("minU");b.minV=v.getFloat("minV");b.maxU=v.getFloat("maxU");b.maxV=v.getFloat("maxV");b.threshold=v.getFloat("threshold");b.empty=v.getBoolean("empty");b.sourceWidth=v.getInt("sourceWidth");b.sourceHeight=v.getInt("sourceHeight");
                if(frame<0||!b.usable(b.threshold)||!Float.isFinite(b.threshold)||b.threshold<0||b.threshold>1||frames.put(frame,b)!=null)throw new IllegalArgumentException("Invalid or duplicate alpha bounds frame");
            }
        }return result;
    }
}
