package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.DrawList;
import org.junit.Assert;
import org.junit.Test;

public class SpatialFrameSnapshotBuilderPerformanceTest {

    @Test
    public void actorMaskCleanupVisitsPreviousActorsInsteadOfSlotCapacity() {
        SpatialActorCollector actors = new SpatialActorCollector();
        actors.actorCount = 2;
        actors.actorSlot = new int[]{7, 149999};
        DrawList drawList = new DrawList(2);
        drawList.addEcsSlot(7);
        drawList.addEcsSlot(149999);
        SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        snapshot.build(drawList, 150000, actors);

        actors.actorCount = 1;
        actors.actorSlot[0] = 7;
        drawList.clear();
        drawList.addEcsSlot(7);
        snapshot.build(drawList, 150000, actors);

        Assert.assertEquals(2, snapshot.lastClearedActorSlotCount());
        Assert.assertTrue(snapshot.isActorSlot(7));
        Assert.assertFalse(snapshot.isActorSlot(149999));
    }
}
