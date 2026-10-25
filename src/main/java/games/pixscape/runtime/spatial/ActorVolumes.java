package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;

/** Per-occurrence authored volumes, independent of physics shapes and actor alpha sheets. */
public final class ActorVolumes {
    public ActorVolumeData staticVolume;
    public IntMap<ObjectMap<String,ActorVolumeData>> clips = new IntMap<>();

    public ActorVolumeData get(int asset,String clip) {
        if(clip==null)return staticVolume;
        ObjectMap<String,ActorVolumeData> map=clips.get(asset);
        return map==null?null:map.get(clip);
    }
    public void put(int asset,String clip,ActorVolumeData volume) {
        volume.validate();
        if(clip==null){staticVolume=volume;return;}
        if(asset<=0 || clip.length()==0)throw new IllegalArgumentException("Actor volume needs an animation asset and clip");
        ObjectMap<String,ActorVolumeData> map=clips.get(asset);
        if(map==null){map=new ObjectMap<>();clips.put(asset,map);}
        map.put(clip,volume);
    }
    public void validate() {
        if(staticVolume==null && clips.size==0)throw new IllegalArgumentException("Spatial actor volumes are missing; reconfigure the actor");
        if(staticVolume!=null)staticVolume.validate();
        for(IntMap.Entry<ObjectMap<String,ActorVolumeData>> asset:clips) {
            if(asset.key<=0 || asset.value==null || asset.value.size==0)throw new IllegalArgumentException("Invalid actor volume asset");
            for(ObjectMap.Entry<String,ActorVolumeData> clip:asset.value) {
                if(clip.key==null || clip.key.length()==0 || clip.value==null)throw new IllegalArgumentException("Invalid actor volume clip");
                clip.value.validate();
            }
        }
    }
    public ActorVolumes copy() {
        ActorVolumes result=new ActorVolumes();
        result.staticVolume=staticVolume==null?null:staticVolume.copy();
        for(IntMap.Entry<ObjectMap<String,ActorVolumeData>> asset:clips)
            for(ObjectMap.Entry<String,ActorVolumeData> clip:asset.value)result.put(asset.key,clip.key,clip.value.copy());
        return result;
    }
}
