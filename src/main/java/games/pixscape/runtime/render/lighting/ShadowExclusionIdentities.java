package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.LongMap;

/**
 * Cold, frame-publication identity registry for the two-depth POC.
 *
 * <p>Zero is permanently invalid. Valid values are compact exact integers in an R32F texel;
 * the key includes the runtime map entity and the authored block id, so two maps cannot alias.
 * This is intentionally not a serialized ID and actors share this allocator but never a map's construction group.</p>
 */
public final class ShadowExclusionIdentities {
    public static final int INVALID = 0;
    /** Largest positive integer preserved exactly by the R32F identity attachment. */
    public static final int MAX_EXACT_FLOAT_ID = 16_777_215;
    private final com.badlogic.gdx.utils.IntMap<com.badlogic.gdx.utils.IntIntMap> maps = new com.badlogic.gdx.utils.IntMap<com.badlogic.gdx.utils.IntIntMap>();
    private final com.badlogic.gdx.utils.IntIntMap actors = new com.badlogic.gdx.utils.IntIntMap();
    private final com.badlogic.gdx.utils.IntMap<LongMap<Integer>> corners = new com.badlogic.gdx.utils.IntMap<LongMap<Integer>>();
    private int next = 1;

    /** Retire a deleted ECS owner without recycling any allocated value. */
    public void removeEntity(int entity) { actors.remove(entity,0); maps.remove(entity); corners.remove(entity); }
    public int idForActor(int entity) {
        if(entity<0)return INVALID;
        int id=actors.get(entity,0);
        if(id==0){id=allocate();actors.put(entity,id);}
        return id;
    }
    private int allocate() {
        if(next>MAX_EXACT_FLOAT_ID)throw new LightingAdmissionException("Shadow exclusion exhausted exact float identities");
        return next++;
    }

    public int idForCorner(int mapEntity,long cellKey){
        if(mapEntity<0)return INVALID;
        LongMap<Integer> map=corners.get(mapEntity);
        if(map==null){map=new LongMap<Integer>();corners.put(mapEntity,map);}
        Integer existing=map.get(cellKey);if(existing!=null)return existing;
        int value=allocate();map.put(cellKey,value);return value;
    }

    public int idFor(int mapEntity, int blockId) {
        if (mapEntity < 0 || blockId <= 0) return INVALID;
        com.badlogic.gdx.utils.IntIntMap map=maps.get(mapEntity);
        if(map==null){map=new com.badlogic.gdx.utils.IntIntMap();maps.put(mapEntity,map);}
        int value=map.get(blockId,0);
        if(value==0){value=allocate();map.put(blockId,value);}
        return value;
    }
}
