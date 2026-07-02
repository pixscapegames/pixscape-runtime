package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.VfxRenderState;
import org.junit.Assert;
import org.junit.Test;

public class RenderSortSystemTest {

    @Test
    public void sortsMixedDomainsByResolvedSortKey() {
        RenderStateSOA state = new RenderStateSOA(256);
        TiledMapRenderState tiledState = new TiledMapRenderState(4);
        VfxRenderState vfxState = new VfxRenderState(4);
        DrawList drawList = new DrawList(4);
        int tiledRef = tiledState.registerLegacySlot(120);

        state.sortKey[5] = 30L;
        state.sortKey[120] = 10L;
        vfxState.addParticleQuad(1, 1, 1, 0, 0, 0, 0, 20L,
                0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f,
                0f, 0f, 1f, 1f, 1f, (byte) 0, -1);

        drawList.addEcsSlot(5);
        drawList.addTiledSlot(tiledRef);
        drawList.addVfxSlot(0);

        World world = new World(new WorldConfigurationBuilder()
                .with(new RenderSortSystem(state, tiledState, vfxState, drawList, -1, -1))
                .build());

        world.process();

        Assert.assertEquals(RenderSourceDomain.SOURCE_TILED, drawList.getDomain(0));
        Assert.assertEquals(tiledRef, drawList.get(0));
        Assert.assertNotEquals(120, drawList.get(0));
        Assert.assertEquals(RenderSourceDomain.SOURCE_VFX, drawList.getDomain(1));
        Assert.assertEquals(0, drawList.get(1));
        Assert.assertEquals(RenderSourceDomain.SOURCE_ECS, drawList.getDomain(2));
        Assert.assertEquals(5, drawList.get(2));
    }
}
