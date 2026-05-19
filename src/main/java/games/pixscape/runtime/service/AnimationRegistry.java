package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.animation.AnimationDef;
import games.pixscape.runtime.animation.AnimationDefData;

public final class AnimationRegistry {

    private final IntMap<AnimationDef> defsByAssetId = new IntMap<>();
    private final ObjectMap<String, AnimationDef> defsByName = new ObjectMap<>();

    public void put(AnimationDef def) {
        if (def == null) {
            throw new IllegalArgumentException("def must not be null");
        }
        defsByAssetId.put(def.assetId(), def);
        defsByName.put(def.name(), def);
    }

    public void put(AnimationDefData data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        put(new AnimationDef(data));
    }

    public AnimationDef getByAssetId(int assetId) {
        return defsByAssetId.get(assetId);
    }

    public AnimationDef getByName(String name) {
        if (name == null) return null;
        return defsByName.get(name);
    }

    public boolean containsAssetId(int assetId) {
        return defsByAssetId.containsKey(assetId);
    }

    public boolean containsName(String name) {
        return name != null && defsByName.containsKey(name);
    }

    public void clear() {
        defsByAssetId.clear();
        defsByName.clear();
    }

    public int size() {
        return defsByAssetId.size;
    }
}
