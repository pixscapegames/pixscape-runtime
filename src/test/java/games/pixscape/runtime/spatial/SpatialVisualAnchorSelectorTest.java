package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

public class SpatialVisualAnchorSelectorTest {

    @Test
    public void openingAndDistantCornerBranchContributeOnlyOverlappingAnchors() {
        Fixture f = new Fixture(new TiledMapLayerData(10, 10, 16, 16, 2, TiledProjection.ISO), 3);
        f.anchor(0, 0, 0, 0f, 0f, 16f, 16f);
        f.anchor(2, 0, 1, 16f, 0f, 32f, 16f); // cell (1,0) is an opening.
        f.anchor(8, 8, 2, 200f, 200f, 216f, 216f); // distant corner branch.
        f.actor(-2f, -2f, 34f, 18f);

        f.select();

        Assert.assertArrayEquals(new int[]{0, 1}, f.selected());
        int[] warmedCandidates = f.selector.candidateAnchors;
        f.select();
        Assert.assertSame(warmedCandidates, f.selector.candidateAnchors);
        Assert.assertEquals(-1, f.faces.anchorForCell(1, 0));
        Assert.assertEquals(2, f.faces.anchorForCell(8, 8));
    }

    @Test
    public void tileImageOverhangIsFoundOutsideItsLogicalCell() {
        TiledMapLayerData map = new TiledMapLayerData(10, 10, 16, 16, 2, TiledProjection.ORTHO);
        map.setVisualPadding(40f, 0f, 0f, 0f);
        Fixture f = new Fixture(map, 1);
        f.anchor(5, 5, 0, 40f, 80f, 96f, 96f);
        f.actor(35f, 82f, 45f, 92f);

        f.select();

        Assert.assertArrayEquals(new int[]{0}, f.selected());
    }

    @Test
    public void aabbContactWithoutQuadContactIsRejected() {
        Fixture f = new Fixture(new TiledMapLayerData(3, 3, 16, 16, 1), 1);
        f.anchor(0, 0, 0, 0f, 0f, 10f, 10f);
        f.actor(8f, 8f, 18f, 18f);
        // Place a diamond wholly within the AABB, but outside the actor's top-right corner.
        f.tiled.x1[0] = 0f; f.tiled.y1[0] = 5f;
        f.tiled.x2[0] = 5f; f.tiled.y2[0] = 0f;
        f.tiled.x3[0] = 10f; f.tiled.y3[0] = 5f;
        f.tiled.x4[0] = 5f; f.tiled.y4[0] = 10f;

        f.select();

        Assert.assertEquals(0, f.selector.candidateCount);
    }

    @Test
    public void actorAndMapDisplayOffsetsAreAppliedOnce() {
        Fixture f = new Fixture(new TiledMapLayerData(3, 3, 16, 16, 1), 1);
        f.anchor(0, 0, 0, 10f, 0f, 20f, 10f);
        f.actor(0f, 0f, 10f, 10f);
        f.ecs.offsetX[f.actors.actorSlot[0]] = 20f;

        f.selector.select(0, f.actors, f.ecs, f.tiled, f.map, f.faces, 10f, 0f);
        Assert.assertArrayEquals(new int[]{0}, f.selected());

        f.ecs.offsetX[f.actors.actorSlot[0]] = 40f;
        f.selector.select(0, f.actors, f.ecs, f.tiled, f.map, f.faces, 10f, 0f);
        Assert.assertEquals(0, f.selector.candidateCount);
    }

    private static final class Fixture {
        final TiledMapLayerData map;
        final SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        final SpatialActorCollector actors = new SpatialActorCollector();
        final DynamicEntityRenderState ecs = new DynamicEntityRenderState(4);
        final TiledMapRenderState tiled = new TiledMapRenderState(4);
        final SpatialVisualAnchorSelector selector = new SpatialVisualAnchorSelector();

        Fixture(TiledMapLayerData map, int anchors) {
            this.map = map;
            faces.anchorCount = anchors;
            faces.anchorGx = new int[anchors];
            faces.anchorGy = new int[anchors];
            faces.anchorTiledRef = new int[anchors];
            faces.anchorResolved = new boolean[anchors];
            tiled.registerRefs(anchors);
            actors.actorCount = 1;
            actors.actorSlot = new int[]{ecs.acquireSlotForEntity(1)};
        }

        void anchor(int gx, int gy, int ref, float minX, float minY, float maxX, float maxY) {
            faces.anchorGx[ref] = gx; faces.anchorGy[ref] = gy;
            faces.anchorTiledRef[ref] = ref; faces.anchorResolved[ref] = true;
            tiled.enabled[ref] = tiled.visible[ref] = true;
            tiled.x1[ref] = tiled.x4[ref] = minX;
            tiled.x2[ref] = tiled.x3[ref] = maxX;
            tiled.y1[ref] = tiled.y2[ref] = minY;
            tiled.y3[ref] = tiled.y4[ref] = maxY;
        }

        void actor(float minX, float minY, float maxX, float maxY) {
            int slot = actors.actorSlot[0];
            ecs.x1[slot] = ecs.x4[slot] = minX;
            ecs.x2[slot] = ecs.x3[slot] = maxX;
            ecs.y1[slot] = ecs.y2[slot] = minY;
            ecs.y3[slot] = ecs.y4[slot] = maxY;
        }

        void select() { selector.select(0, actors, ecs, tiled, map, faces, 0f, 0f); }
        int[] selected() {
            int[] out = new int[selector.candidateCount];
            System.arraycopy(selector.candidateAnchors, 0, out, 0, out.length);
            return out;
        }
    }
}
