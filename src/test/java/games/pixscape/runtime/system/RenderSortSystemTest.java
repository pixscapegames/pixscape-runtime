package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.VfxRenderState;
import org.junit.Assert;
import org.junit.Test;

public class RenderSortSystemTest {

    @Test
    public void sortsMixedDomainsByResolvedSortKey() {
        DynamicEntityRenderState ecsState = new DynamicEntityRenderState(4);
        TiledMapRenderState tiledState = new TiledMapRenderState(4);
        VfxRenderState vfxState = new VfxRenderState(4);
        DrawList drawList = new DrawList(4);
        int tiledRef = tiledState.registerRef();

        int ecsSlot = ecsState.acquireSlotForEntity(120);
        ecsState.sortKey[ecsSlot] = 30L;
        tiledState.setRenderDataForRef(tiledRef, 1, 1, 1, 0, 0, 0, 10L,
                0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f,
                0f, 0f, 1f, 1f, 1f, 1f, (byte) 0);
        vfxState.addParticleQuad(1, 1, 1, 0, 0, 0, 0, 20L,
                0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f,
                0f, 0f, 1f, 1f, 1f, (byte) 0, -1);

        drawList.addEcsSlot(ecsSlot);
        drawList.addTiledSlot(tiledRef);
        drawList.addVfxSlot(0);

        World world = new World(new WorldConfigurationBuilder()
                .with(new RenderSortSystem(ecsState, tiledState, vfxState, drawList, -1, -1))
                .build());

        world.process();

        Assert.assertEquals(RenderSourceDomain.SOURCE_TILED, drawList.getDomain(0));
        Assert.assertEquals(tiledRef, drawList.get(0));
        Assert.assertNotEquals(120, drawList.get(0));
        Assert.assertEquals(RenderSourceDomain.SOURCE_VFX, drawList.getDomain(1));
        Assert.assertEquals(0, drawList.get(1));
        Assert.assertEquals(RenderSourceDomain.SOURCE_ECS, drawList.getDomain(2));
        Assert.assertEquals(ecsSlot, drawList.get(2));
    }
}
