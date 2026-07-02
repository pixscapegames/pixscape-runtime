package games.pixscape.runtime.render;

import org.junit.Assert;
import org.junit.Test;

public class DrawListTest {

    @Test
    public void addEntriesStoresDomainAndSourceSlot() {
        DrawList drawList = new DrawList(4);

        drawList.addEcsSlot(10);
        drawList.addTiledSlot(200);
        drawList.addVfxSlot(2);

        Assert.assertEquals(3, drawList.size);
        Assert.assertEquals(RenderSourceDomain.SOURCE_ECS, drawList.sourceDomain[0]);
        Assert.assertEquals(10, drawList.sourceSlot[0]);
        Assert.assertEquals(RenderSourceDomain.SOURCE_TILED, drawList.sourceDomain[1]);
        Assert.assertEquals(200, drawList.sourceSlot[1]);
        Assert.assertEquals(RenderSourceDomain.SOURCE_VFX, drawList.sourceDomain[2]);
        Assert.assertEquals(2, drawList.sourceSlot[2]);
    }

    @Test
    public void clearDoesNotReallocateColumns() {
        DrawList drawList = new DrawList(4);
        drawList.addTiledSlot(20);
        byte[] domains = drawList.sourceDomain;
        int[] slots = drawList.sourceSlot;

        drawList.clear();

        Assert.assertSame(domains, drawList.sourceDomain);
        Assert.assertSame(slots, drawList.sourceSlot);
        Assert.assertEquals(0, drawList.size);
    }

    @Test
    public void ensureCapacityPreservesDomainsAndSlots() {
        DrawList drawList = new DrawList(2);
        drawList.addEcsSlot(1);
        drawList.addVfxSlot(0);

        drawList.ensureCapacity(5);

        Assert.assertTrue(drawList.sourceSlot.length >= 5);
        Assert.assertEquals(2, drawList.size);
        Assert.assertEquals(RenderSourceDomain.SOURCE_ECS, drawList.sourceDomain[0]);
        Assert.assertEquals(1, drawList.sourceSlot[0]);
        Assert.assertEquals(RenderSourceDomain.SOURCE_VFX, drawList.sourceDomain[1]);
        Assert.assertEquals(0, drawList.sourceSlot[1]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidCapacity() {
        new DrawList(0);
    }
}
