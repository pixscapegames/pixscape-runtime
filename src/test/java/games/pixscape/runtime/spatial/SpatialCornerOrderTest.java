package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.RenderSourceDomain;
import org.junit.Assert;
import org.junit.Test;

/** The historical small-footprint oracle, using ordinary sprites only. */
public class SpatialCornerOrderTest {
    @Test public void fourteenDeterminedCornersAndTwoUnresolvedSplitsPreserveEveryEntry() {
        for (int opening = 0; opening < 4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening)) {
            for (int sector = 0; sector < 4; sector++) for (int original = 0; original < 2; original++) {
                char expected = SpatialCornerFixture.EXPECTED[opening].charAt(sector);
                SpatialCornerFixture.Outcome r = f.run(sector, .5f, 60f, original);
                String row = SpatialCornerFixture.OPENINGS[opening] + " " + SpatialCornerFixture.SECTORS[sector] + " " + r;
                Assert.assertEquals(row, expected, r.side());
                Assert.assertEquals(row, 0, r.fallbacks);
                Assert.assertEquals(1, r.candidates);
                Assert.assertEquals(2, f.composer.composedSize);
                int position = expected == 'X' ? original : expected == 'B' ? 0 : 1;
                Assert.assertEquals(row, RenderSourceDomain.SOURCE_ECS, f.composer.composedDomains[position]);
                Assert.assertEquals(row, f.slot, f.composer.composedSlots[position]);
                Assert.assertEquals(row, RenderSourceDomain.SOURCE_TILED, f.composer.composedDomains[1-position]);
                Assert.assertEquals(row, f.ref, f.composer.composedSlots[1-position]);
                Assert.assertEquals(row, expected == 'X', f.planner.hasUnresolvedCorner(0));
                Assert.assertEquals(row, expected == 'X', f.planner.hasLocalFallback(0));
            }
        }
    }

    @Test public void offAxisPositionsUseObliqueSectors() {
        for (int opening=0; opening<4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening)) {
            for (int sector=0; sector<4; sector++) for (float offset : new float[]{-3f,3f}) {
                float dx = SpatialCornerFixture.DX[sector] + (sector<2 ? offset : 0);
                float dy = SpatialCornerFixture.DY[sector] + (sector<2 ? 0 : offset);
                Assert.assertEquals(SpatialCornerFixture.EXPECTED[opening].charAt(sector), f.runAt(dx,dy,.5f,60f,1).side());
            }
        }
    }

    @Test public void branchAndVertexBoundariesKeepHistoricalEpsilons() {
        for (int opening=0; opening<4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening)) {
            for (float distance : new float[]{.3f,1f,3f}) {
                Assert.assertEquals('B', f.runAt(0,distance,.01f,60,1).side());
                Assert.assertEquals('F', f.runAt(0,-distance,.01f,60,0).side());
            }
            for (float x : new float[]{-25,25}) {
                char lateral = SpatialCornerFixture.EXPECTED[opening].charAt(x<0 ? 2:3);
                float boundary = Math.abs(x)*.5f;
                Assert.assertEquals('B', f.runAt(x,boundary+.3f,.01f,60,1).side());
                Assert.assertEquals(lateral, f.runAt(x,boundary-.3f,.01f,60,1).side());
                Assert.assertEquals(lateral, f.runAt(x,-boundary+.3f,.01f,60,0).side());
                Assert.assertEquals('F', f.runAt(x,-boundary-.3f,.01f,60,0).side());
            }
            for (float x : new float[]{-.101f,-.1f,0,.1f,.101f}) {
                Assert.assertEquals(f.runAt(x,1,.01f,60,1).toString(),f.runAt(x,1,.01f,60,1).toString());
            }
        }
    }

    @Test public void removingAndRestoringCornerInvalidatesUnresolvedDiagnostic() {
        try (SpatialCornerFixture f = new SpatialCornerFixture(2)) {
            f.run(2,.5f,60,1); Assert.assertTrue(f.planner.hasUnresolvedCorner(0));
            SpatialBlockData removed = f.blocks.blocks.removeIndex(1); f.blocks.revision++;
            f.compiled.ensure(f.blocks); f.faces.ensure(f.compiled,f.map);
            f.run(2,.5f,60,1); Assert.assertFalse(f.planner.hasUnresolvedCorner(0));
            f.blocks.blocks.add(removed); f.blocks.revision++;
            f.compiled.ensure(f.blocks); f.faces.ensure(f.compiled,f.map);
            f.run(2,.5f,60,1); Assert.assertTrue(f.planner.hasUnresolvedCorner(0));
        }
    }

    @Test public void commonAndDifferentDisplayOffsetsDoNotChangeRelations() {
        try (SpatialCornerFixture f = new SpatialCornerFixture(0)) {
            f.run(0,.5f,60,1);
            int count = f.solver.relationCount;
            byte[] baseline = java.util.Arrays.copyOf(f.solver.relationType,count);
            f.ecs.offsetX[f.slot]=123; f.ecs.offsetY[f.slot]=-87;
            f.solver.solveVisual(f.actors,f.faces,new SpatialVisualAnchorSelector(),f.ecs,f.tiled,f.map,123,-87);
            Assert.assertEquals(count,f.solver.relationCount);
            for(int i=0;i<count;i++) Assert.assertEquals(baseline[i],f.solver.relationType[i]);
            // Different actor/map offsets: compensate actor world pose, leaving displayed pose identical.
            f.actors.actorCircleX[0]-=15; f.actors.actorCircleY[0]+=9;
            f.ecs.x1[f.slot]-=15; f.ecs.x2[f.slot]-=15; f.ecs.x3[f.slot]-=15; f.ecs.x4[f.slot]-=15;
            f.ecs.y1[f.slot]+=9; f.ecs.y2[f.slot]+=9; f.ecs.y3[f.slot]+=9; f.ecs.y4[f.slot]+=9;
            f.ecs.offsetX[f.slot]+=15; f.ecs.offsetY[f.slot]-=9;
            f.solver.solveVisual(f.actors,f.faces,new SpatialVisualAnchorSelector(),f.ecs,f.tiled,f.map,123,-87);
            Assert.assertEquals(count,f.solver.relationCount);
            for(int i=0;i<count;i++) Assert.assertEquals(baseline[i],f.solver.relationType[i]);
        }
    }
}
