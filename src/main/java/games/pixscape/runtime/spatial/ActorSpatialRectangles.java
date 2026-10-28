package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;

/** Per-occurrence authored shadow rectangles, independent of physics shapes and actor alpha sheets. */
public final class ActorSpatialRectangles {
    public ActorSpatialRectangleData staticRectangle;
    public IntMap<ObjectMap<String,ActorSpatialRectangleData>> clips = new IntMap<>();

    public ActorSpatialRectangleData get(int asset,String clip) {
        if(clip==null)return staticRectangle;
        ObjectMap<String,ActorSpatialRectangleData> map=clips.get(asset);
        return map==null?null:map.get(clip);
    }
    public void put(int asset,String clip,ActorSpatialRectangleData rectangle) {
        rectangle.validate();
        if(clip==null){staticRectangle=rectangle;return;}
        if(asset<=0 || clip.length()==0)throw new IllegalArgumentException("Actor spatial rectangle needs an animation asset and clip");
        ObjectMap<String,ActorSpatialRectangleData> map=clips.get(asset);
        if(map==null){map=new ObjectMap<>();clips.put(asset,map);}
        map.put(clip,rectangle);
    }
    public void validate() {
        if(staticRectangle==null && clips.size==0)throw new IllegalArgumentException("Spatial actor rectangles are missing; reconfigure the actor");
        if(staticRectangle!=null)staticRectangle.validate();
        for(IntMap.Entry<ObjectMap<String,ActorSpatialRectangleData>> asset:clips) {
            if(asset.key<=0 || asset.value==null || asset.value.size==0)throw new IllegalArgumentException("Invalid actor spatial rectangle asset");
            for(ObjectMap.Entry<String,ActorSpatialRectangleData> clip:asset.value) {
                if(clip.key==null || clip.key.length()==0 || clip.value==null)throw new IllegalArgumentException("Invalid actor spatial rectangle clip");
                clip.value.validate();
            }
        }
    }
    public ActorSpatialRectangles copy() {
        ActorSpatialRectangles result=new ActorSpatialRectangles();
        result.staticRectangle=staticRectangle==null?null:staticRectangle.copy();
        for(IntMap.Entry<ObjectMap<String,ActorSpatialRectangleData>> asset:clips)
            for(ObjectMap.Entry<String,ActorSpatialRectangleData> clip:asset.value)result.put(asset.key,clip.key,clip.value.copy());
        return result;
    }
}
