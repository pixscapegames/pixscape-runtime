package games.pixscape.runtime.physics;

import org.junit.Test;
import static org.junit.Assert.*;

public class JointDependencyIndexTest {
    @Test public void swapUnlinkTracksBothSlotsAndReusedIdsWithoutDuplicateLinks() {
        JointDependencyIndex index = new JointDependencyIndex();
        index.publish(10, 1, 2, 30, 31);
        index.publish(11, 2, 1, 31, 30);
        index.publish(12, 1, 1, 30, 30);
        index.remove(10);
        index.remove(12); // moved into 10's slot in both reverse buckets
        assertArrayEquals(new int[]{11}, index.jointsForBody(1).toArray());
        assertArrayEquals(new int[]{11}, index.gearsForSource(30).toArray());
        index.publish(11, 1000, 1001, -1, -1);
        assertEquals(0, index.jointsForBody(1).size);
        assertEquals(0, index.jointsForBody(2).size);
        assertEquals(0, index.gearsForSource(30).size);
        index.publish(12, 1000, 1000, 50, 50);
        index.publish(12, 1000, 1000, 50, 50);
        assertEquals(2, index.jointsForBody(1000).size);
        assertArrayEquals(new int[]{12}, index.gearsForSource(50).toArray());
        index.remove(12); index.remove(12); index.remove(11);
        assertEquals(0, index.jointsForBody(1000).size);
        assertNull(index.jointsForBody(Integer.MAX_VALUE));
    }
}
