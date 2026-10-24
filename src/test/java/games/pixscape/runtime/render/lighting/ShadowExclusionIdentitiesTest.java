package games.pixscape.runtime.render.lighting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ShadowExclusionIdentitiesTest {
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
