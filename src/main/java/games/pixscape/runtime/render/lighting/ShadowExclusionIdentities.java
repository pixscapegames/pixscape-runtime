package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.LongMap;

/**
 * Cold, frame-publication identity registry for the two-depth POC.
 *
 * <p>Zero is permanently invalid. Valid values are compact exact integers in an R32F texel;
 * the key includes the runtime map entity and the authored block id, so two maps cannot alias.
 * This is intentionally not a serialized ID and actors are never admitted to this registry.</p>
 */
public final class ShadowExclusionIdentities {
    public static final int INVALID = 0;
    /** Largest positive integer preserved exactly by the R32F identity attachment. */
    public static final int MAX_EXACT_FLOAT_ID = 16_777_215;
    private final LongMap<Integer> values = new LongMap<Integer>();
    private final com.badlogic.gdx.utils.IntMap<LongMap<Integer>> corners = new com.badlogic.gdx.utils.IntMap<LongMap<Integer>>();
    private int next = 1;

    public void clear() { values.clear(); corners.clear(); next = 1; }

    public int idForCorner(int mapEntity,long cellKey){
        if(mapEntity<0)return INVALID;
        LongMap<Integer> map=corners.get(mapEntity);
        if(map==null){map=new LongMap<Integer>();corners.put(mapEntity,map);}
        Integer existing=map.get(cellKey);if(existing!=null)return existing;
        if(next>MAX_EXACT_FLOAT_ID)throw new LightingAdmissionException("Shadow exclusion exhausted exact float identities");
        int value=next++;map.put(cellKey,value);return value;
    }

    public int idFor(int mapEntity, int blockId) {
        if (mapEntity < 0 || blockId <= 0) return INVALID;
        long key = ((long) mapEntity << 32) ^ (blockId & 0xffffffffL);
        Integer existing = values.get(key);
        if (existing != null) return existing;
        if (next > MAX_EXACT_FLOAT_ID)
            throw new LightingAdmissionException("Two-depth shadow exclusion exhausted exact float identities");
        int value = next++;
        values.put(key, value);
        return value;
    }
}
