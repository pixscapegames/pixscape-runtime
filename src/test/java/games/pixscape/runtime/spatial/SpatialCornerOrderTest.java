package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.TileCornerSplit;
import org.junit.Assert;
import org.junit.Test;

/**
 * The historical small-footprint oracle, using ordinary sprites only.
 */
public class SpatialCornerOrderTest {
    @Test
    public void fourteenDeterminedCornersAndTwoUnresolvedSplitsPreserveEveryEntry() {
        for (int opening = 0; opening < 4; opening++)
            try (SpatialCornerFixture f = new SpatialCornerFixture(opening)) {
                for (int sector = 0; sector < 4; sector++)
                    for (int original = 0; original < 2; original++) {
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
                        Assert.assertEquals(row, RenderSourceDomain.SOURCE_TILED, f.composer.composedDomains[1 - position]);
                        Assert.assertEquals(row, f.ref, f.composer.composedSlots[1 - position]);
                        Assert.assertEquals(row, expected == 'X', f.planner.hasUnresolvedCorner(0));
                        Assert.assertEquals(row, expected == 'X', f.planner.hasLocalFallback(0));
                    }
            }
    }

    @Test
    public void onlyChangedCornerCellIsDirtiedAcrossSpatialPublications() {
        try (SpatialCornerFixture f = new SpatialCornerFixture(2)) {
            games.pixscape.runtime.render.TiledRenderOrder order = f.tiled.prepareOrderForMap(8, f.map);
            games.pixscape.runtime.tiled.TileChunk chunk = f.map.getChunk(1, 1);
            order.updateCornerGeometry(f.faces);
            chunk.dirtyState = games.pixscape.runtime.tiled.TileChunk.DirtyState.CLEAN;
            chunk.dirtyLocalIndices.clear();
            f.blocks.blocks.first().name = "Renamed";
            f.blocks.revision++;
            Assert.assertFalse(f.compiled.ensure(f.blocks));
            order.updateCornerGeometry(f.faces);
            Assert.assertEquals(0, chunk.dirtyLocalIndices.size);
            for (boolean enabled : new boolean[]{false, true}) {
                f.blocks.blocks.first().actorOccluder = enabled;
                f.blocks.revision++;
                Assert.assertTrue(f.compiled.ensure(f.blocks));
                f.faces.ensure(f.compiled, f.map);
                order.updateCornerGeometry(f.faces);
                Assert.assertEquals(1, chunk.dirtyLocalIndices.size);
                Assert.assertEquals(0, chunk.dirtyLocalIndices.first());
                chunk.dirtyLocalIndices.clear();
            }
        }
    }

    @Test
    public void preparedLateralPiecesFollowEachWallInBothDirections() {
        for (int opening : new int[]{2, 3})
            try (SpatialCornerFixture f = new SpatialCornerFixture(opening)) {
                int face = f.faces.frontCornerFace(f.faces.anchorForCell(4, 4));
                Assert.assertTrue(face >= 0);
                float[] quad = {f.vx - 80, f.vy - 40, f.vx - 80, f.vy + 180,
                        f.vx + 80, f.vy + 180, f.vx + 80, f.vy - 40};
                games.pixscape.runtime.render.TileCornerSplit split = games.pixscape.runtime.render.TileCornerSplit
                        .prepare(f.faces, face, quad, .25f, .75f, null);
                Assert.assertNotNull(split);
                Assert.assertEquals(split.back.y[0], split.front.y[1], 0f);
                Assert.assertEquals(split.back.y[3], split.front.y[2], 0f);
                Assert.assertEquals(split.back.v[0], split.front.v[1], 0f);
                Assert.assertEquals(split.back.v[3], split.front.v[2], 0f);
                f.tiled.cornerSplits.put(f.ref, split);
                int[] sectors = {0, opening, 1, opening, 0};
                int[] buckets = {0, 1, 2, 1, 0};
                for (int i = 0; i < sectors.length; i++) {
                    SpatialCornerFixture.Outcome result = f.run(sectors[i], .5f, 60f, 1);
                    Assert.assertEquals(0, result.conflicts);
                    Assert.assertEquals(buckets[i], result.bucket);
                    Assert.assertEquals(RenderSourceDomain.SOURCE_ECS, f.composer.composedDomains[buckets[i]]);
                    // A neighbouring unsplit wall can contradict itself while the actor crosses its thickness.
                    f.planner.actorLowerBound[0] = 2;
                    f.planner.actorUpperBound[0] = 0;
                    f.planner.finish(f.actors);
                    Assert.assertEquals(buckets[i], f.planner.actorBucket[0]);
                }
            }
    }

    @Test
    public void actorPassesBetweenWallsWhenTheFrontWallContinuesThroughTheSharedCell() {
        try (SpatialCornerFixture f = new SpatialCornerFixture(2)) {
            SpatialBlockData frontWall = f.blocks.blocks.first();
            frontWall.x = 2f;
            frontWall.width = 5f;
            f.blocks.revision++;
            f.compiled.ensure(f.blocks);
            f.faces.ensure(f.compiled, f.map);
            int front = f.faces.frontCornerFace(f.faces.anchorForCell(4, 4));
            Assert.assertTrue("The two local branches share a corner on the through wall", front >= 0);
            float[] quad = {f.vx - 80, f.vy - 40, f.vx - 80, f.vy + 180,
                    f.vx + 80, f.vy + 180, f.vx + 80, f.vy - 40};
            TileCornerSplit split = TileCornerSplit.prepare(f.faces, front, quad, 0f, 1f, null);
            Assert.assertNotNull(split);
            f.tiled.cornerSplits.put(f.ref, split);
            int[] sectors = {0, 2, 1, 2, 0};
            int[] expectedBuckets = {0, 1, 2, 1, 0};
            for (int i = 0; i < sectors.length; i++) {
                Assert.assertEquals(expectedBuckets[i], f.run(sectors[i], .5f, 60f, 1).bucket);
            }
        }
    }

    @Test
    public void offAxisPositionsUseObliqueSectors() {
        for (int opening = 0; opening < 4; opening++)
            try (SpatialCornerFixture f = new SpatialCornerFixture(opening)) {
                for (int sector = 0; sector < 4; sector++)
                    for (float offset : new float[]{-3f, 3f}) {
                        float dx = SpatialCornerFixture.DX[sector] + (sector < 2 ? offset : 0);
                        float dy = SpatialCornerFixture.DY[sector] + (sector < 2 ? 0 : offset);
                        Assert.assertEquals(SpatialCornerFixture.EXPECTED[opening].charAt(sector), f.runAt(dx, dy, .5f, 60f, 1).side());
                    }
            }
    }

    @Test
    public void branchAndVertexBoundariesKeepHistoricalEpsilons() {
        for (int opening = 0; opening < 4; opening++)
            try (SpatialCornerFixture f = new SpatialCornerFixture(opening)) {
                for (float distance : new float[]{.3f, 1f, 3f}) {
                    Assert.assertEquals('B', f.runAt(0, distance, .01f, 60, 1).side());
                    Assert.assertEquals('F', f.runAt(0, -distance, .01f, 60, 0).side());
                }
                for (float x : new float[]{-25, 25}) {
                    char lateral = SpatialCornerFixture.EXPECTED[opening].charAt(x < 0 ? 2 : 3);
                    float boundary = Math.abs(x) * .5f;
                    Assert.assertEquals('B', f.runAt(x, boundary + .3f, .01f, 60, 1).side());
                    Assert.assertEquals(lateral, f.runAt(x, boundary - .3f, .01f, 60, 1).side());
                    Assert.assertEquals(lateral, f.runAt(x, -boundary + .3f, .01f, 60, 0).side());
                    Assert.assertEquals('F', f.runAt(x, -boundary - .3f, .01f, 60, 0).side());
                }
                for (float x : new float[]{-.101f, -.1f, 0, .1f, .101f}) {
                    Assert.assertEquals(f.runAt(x, 1, .01f, 60, 1).toString(), f.runAt(x, 1, .01f, 60, 1).toString());
                }
            }
    }

    @Test
    public void removingAndRestoringCornerInvalidatesUnresolvedDiagnostic() {
        try (SpatialCornerFixture f = new SpatialCornerFixture(2)) {
            f.run(2, .5f, 60, 1);
            Assert.assertTrue(f.planner.hasUnresolvedCorner(0));
            SpatialBlockData removed = f.blocks.blocks.removeIndex(1);
            f.blocks.revision++;
            f.compiled.ensure(f.blocks);
            f.faces.ensure(f.compiled, f.map);
            f.run(2, .5f, 60, 1);
            Assert.assertFalse(f.planner.hasUnresolvedCorner(0));
            f.blocks.blocks.add(removed);
            f.blocks.revision++;
            f.compiled.ensure(f.blocks);
            f.faces.ensure(f.compiled, f.map);
            f.run(2, .5f, 60, 1);
            Assert.assertTrue(f.planner.hasUnresolvedCorner(0));
        }
    }

    @Test
    public void commonAndDifferentDisplayOffsetsDoNotChangeRelations() {
        try (SpatialCornerFixture f = new SpatialCornerFixture(0)) {
            f.run(0, .5f, 60, 1);
            int count = f.solver.relationCount;
            byte[] baseline = java.util.Arrays.copyOf(f.solver.relationType, count);
            f.ecs.offsetX[f.slot] = 123;
            f.ecs.offsetY[f.slot] = -87;
            f.solver.solveVisual(f.actors, f.faces, new SpatialVisualAnchorSelector(), f.ecs, f.tiled, f.map, 123, -87);
            Assert.assertEquals(count, f.solver.relationCount);
            for (int i = 0; i < count; i++) Assert.assertEquals(baseline[i], f.solver.relationType[i]);
            // Different actor/map offsets: compensate actor world pose, leaving displayed pose identical.
            f.actors.actorCircleX[0] -= 15;
            f.actors.actorCircleY[0] += 9;
            f.ecs.x1[f.slot] -= 15;
            f.ecs.x2[f.slot] -= 15;
            f.ecs.x3[f.slot] -= 15;
            f.ecs.x4[f.slot] -= 15;
            f.ecs.y1[f.slot] += 9;
            f.ecs.y2[f.slot] += 9;
            f.ecs.y3[f.slot] += 9;
            f.ecs.y4[f.slot] += 9;
            f.ecs.offsetX[f.slot] += 15;
            f.ecs.offsetY[f.slot] -= 9;
            f.solver.solveVisual(f.actors, f.faces, new SpatialVisualAnchorSelector(), f.ecs, f.tiled, f.map, 123, -87);
            Assert.assertEquals(count, f.solver.relationCount);
            for (int i = 0; i < count; i++) Assert.assertEquals(baseline[i], f.solver.relationType[i]);
        }
    }
}
