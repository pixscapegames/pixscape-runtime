package games.pixscape.runtime.render.lighting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ShadowExclusionIdentitiesTest {
    @Test public void actorsAndRebuiltMapsShareOneMonotonicDomain(){
        ShadowExclusionIdentities ids=new ShadowExclusionIdentities();
        int a=ids.idForActor(1),b=ids.idForActor(2),wall=ids.idFor(1,1),corner=ids.idForCorner(1,1);
        org.junit.Assert.assertEquals(4,new java.util.HashSet<Integer>(java.util.Arrays.asList(a,b,wall,corner)).size());
        for(int i=0;i<100;i++){assertEquals(a,ids.idForActor(1));assertEquals(wall,ids.idFor(1,1));}
        ids.removeEntity(1);
        org.junit.Assert.assertTrue(ids.idForActor(1)>corner);
        org.junit.Assert.assertTrue(ids.idFor(1,1)>corner);
        assertEquals(b,ids.idForActor(2));
        assertEquals(0,ids.idForActor(-1));
    }
    @Test public void curvedOccurrenceIsDistinctFromBothWallsAndFromOtherMaps(){
        ShadowExclusionIdentities ids=new ShadowExclusionIdentities();long cell=((long)27<<32)^24;
        int curve=ids.idForCorner(215,cell);
        assertEquals(curve,ids.idForCorner(215,cell));
        assertNotEquals(curve,ids.idFor(215,22));assertNotEquals(curve,ids.idFor(215,23));
        assertNotEquals(curve,ids.idForCorner(216,cell));assertNotEquals(curve,ids.idForCorner(215,cell+1));
    }
    @Test public void zeroIsInvalidAndMapBlockPairsAreUnique() {
        ShadowExclusionIdentities ids=new ShadowExclusionIdentities();
        assertEquals(ShadowExclusionIdentities.INVALID,ids.idFor(215,0));
        int barrel=ids.idFor(6,44);
        assertEquals(barrel,ids.idFor(6,44));
        assertNotEquals(barrel,ids.idFor(7,44));
        assertNotEquals(barrel,ids.idFor(6,45));
    }
    @Test public void validRangeIsExactlyRepresentableByR32F() {
        assertEquals(16_777_215,ShadowExclusionIdentities.MAX_EXACT_FLOAT_ID);
        assertEquals(ShadowExclusionIdentities.MAX_EXACT_FLOAT_ID,(int)(float)ShadowExclusionIdentities.MAX_EXACT_FLOAT_ID);
    }
}
