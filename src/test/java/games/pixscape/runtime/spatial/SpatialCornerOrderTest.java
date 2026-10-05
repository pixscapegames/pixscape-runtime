package games.pixscape.runtime.spatial;

import org.junit.Assert;
import org.junit.Test;

public class SpatialCornerOrderTest {
    @Test public void derivedCornerIndexInvalidatesWhenAnLBecomesStraight() {
        try (SpatialCornerFixture f = new SpatialCornerFixture(2, false)) {
            f.run(2,.5f,60f,1);
            Assert.assertTrue(f.planner.lateralCornerApproximationCount()>0);
            SpatialBlockData removed=f.blocks.blocks.removeIndex(1);f.blocks.revision++;
            Assert.assertTrue(f.compiled.ensure(f.blocks));Assert.assertTrue(f.faces.ensure(f.compiled,f.map));
            f.run(2,.5f,60f,1);Assert.assertEquals(0,f.planner.lateralCornerApproximationCount());
            for(int a=0;a<f.faces.anchorCount;a++)Assert.assertEquals(-1,f.faces.anchorCornerHead[a]);
            Assert.assertFalse(f.faces.ensure(f.compiled,f.map));
            f.blocks.blocks.add(removed);f.blocks.revision++;
            f.compiled.ensure(f.blocks);f.faces.ensure(f.compiled,f.map);
            f.run(2,.5f,60f,1);Assert.assertTrue(f.planner.lateralCornerApproximationCount()>0);
        }
    }
    @Test public void authoredCornersKeepFourteenRulesAndTwoBehindApproximationsForSpritesAndPointLights() {
        StringBuilder failures = new StringBuilder();
        for (boolean light : new boolean[]{false, true}) for (int opening=0; opening<4; opening++) {
            try (SpatialCornerFixture f = new SpatialCornerFixture(opening, light)) {
                for (int sector=0; sector<4; sector++) {
                    char expected = SpatialCornerFixture.EXPECTED[opening].charAt(sector);
                    for (int original=0; original<2; original++) {
                        SpatialCornerFixture.Outcome result = f.run(sector, .5f, 60f, original);
                        String row = (light ? "POINT" : "SPRITE")+" "+SpatialCornerFixture.OPENINGS[opening]
                                +" "+SpatialCornerFixture.SECTORS[sector]+" expected="+expected
                                +" original="+original+" "+result;
                        System.out.println(row);
                        if (result.side()!=expected || result.fallbacks!=0) failures.append(row).append('\n');
                        if (opening==2 && sector==2 || opening==3 && sector==3)
                            Assert.assertTrue(row, f.planner.lateralCornerApproximationCount()>0);
                        Assert.assertEquals(1, result.candidates);
                        Assert.assertEquals(2, f.composer.composedSize);
                        int actorPosition = expected == 'B' ? 0 : 1;
                        Assert.assertEquals(row, games.pixscape.runtime.render.RenderSourceDomain.SOURCE_ECS,
                                f.composer.composedDomains[actorPosition]);
                        Assert.assertEquals(row, f.slot, f.composer.composedSlots[actorPosition]);
                    }
                }
            }
        }
        Assert.assertEquals(failures.toString(), 0, failures.length());
    }

    @Test public void offAxisPositionsStillUseObliqueSectors() {
        for (int opening=0; opening<4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening, false)) {
            for (int sector=0; sector<4; sector++) for (float offset : new float[]{-3f, 3f}) {
                float dx = SpatialCornerFixture.DX[sector] + (sector < 2 ? offset : 0f);
                float dy = SpatialCornerFixture.DY[sector] + (sector < 2 ? 0f : offset);
                Assert.assertEquals(SpatialCornerFixture.EXPECTED[opening].charAt(sector),
                        f.runAt(dx, dy, .5f, 60f, 1).side());
            }
        }
    }

    @Test public void radiusOnlyTransitionsAreReportedWithoutInventingAnExtendedMatrix() {
        for (int opening=0; opening<4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening, false)) {
            // Unequal distances to the two oblique supports: entirely inside, tangent, crossing one, crossing both.
            float dx = -20f, dy = 5f;
            float tangent = 5f / (float)Math.sqrt(1.25f);
            int compileCount = f.ranks.tileOrderCompileCount;
            for (float radius : new float[]{.5f, tangent, tangent + .01f, 25f}) {
                SpatialCornerFixture.Outcome r = f.runAt(dx, dy, radius, 60f, 1);
                System.out.println("EXTENDED "+SpatialCornerFixture.OPENINGS[opening]+" center=(-20,5) radius="+radius+" "+r);
                Assert.assertEquals(1, r.candidates);
                Assert.assertEquals(compileCount, f.ranks.tileOrderCompileCount);
                Assert.assertEquals(r.toString(), f.runAt(dx, dy, radius, 60f, 1).toString());
                // The near support crossed here is a prolongation outside the opposite branch's
                // finite X reach. It must not turn these three unique placements into conflicts.
                if (radius < 25f) Assert.assertEquals(new char[]{'F','B','B','F'}[opening], r.side());
                Assert.assertEquals(f.vx + dx, f.actors.actorCircleX[0], 0f);
                Assert.assertEquals(f.vy + dy, f.actors.actorCircleY[0], 0f);
                Assert.assertEquals(f.vx + dx - 60f, f.ecs.x1[f.slot], 0f);
            }
        }
    }

    @Test public void lightHaloDoesNotReplaceTheTechnicalFootprint() {
        for (int opening=0; opening<4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening, true)) {
            for (int sector=0; sector<4; sector++) for (float halo : new float[]{40f, 60f, 100f}) {
                SpatialCornerFixture.Outcome r = f.run(sector, .5f, halo, 1);
                Assert.assertEquals(SpatialCornerFixture.EXPECTED[opening].charAt(sector), r.side());
                Assert.assertEquals(.5f, f.actors.actorCircleRadius[0], 0f);
            }
        }
    }

    @Test public void crossingTheActualNorthBranchChangesOrderWithoutAFakeConflict() {
        try (SpatialCornerFixture f = new SpatialCornerFixture(0, false)) {
            for (float x : new float[]{5f, 25f, 40f}) {
                Assert.assertEquals('B', f.runAt(x, x*.5f + .3f, .01f, 60f, 1).side());
                Assert.assertEquals('F', f.runAt(x, x*.5f - .3f, .01f, 60f, 0).side());
            }
        }
    }

    @Test public void supportLineEqualityAndFiniteEndpointTangencyAreDeterministic() {
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                SpatialLineRelation.circleRelation(0f, 0f, 1f, .5f));
        for (int opening=0; opening<4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening, false)) {
            for (float x : new float[]{-.101f, -.1f, 0f, .1f, .101f}) {
                SpatialCornerFixture.Outcome a = f.runAt(x, 1f, .01f, 60f, 1);
                Assert.assertEquals(a.toString(), f.runAt(x, 1f, .01f, 60f, 1).toString());
                Assert.assertEquals(0, a.fallbacks);
            }
        }
    }

    @Test public void crossingTheVertexFromNorthToSouthUsesEveryOpening() {
        for (boolean light : new boolean[]{false, true}) for (int opening=0; opening<4; opening++)
            try (SpatialCornerFixture f = new SpatialCornerFixture(opening, light)) {
                for (float distance : new float[]{.3f, 1f, 3f}) {
                    Assert.assertEquals('B', f.runAt(0f, distance, .01f, 60f, 1).side());
                    Assert.assertEquals('F', f.runAt(0f, -distance, .01f, 60f, 0).side());
                }
            }
    }

    @Test public void obliqueBoundaryTransitionsMatchTheAdjacentSectorsForEveryOpening() {
        for (boolean light : new boolean[]{false, true}) for (int opening=0; opening<4; opening++)
            try (SpatialCornerFixture f = new SpatialCornerFixture(opening, light)) {
                for (float x : new float[]{-25f, 25f}) {
                    char lateral = SpatialCornerFixture.EXPECTED[opening].charAt(x < 0f ? 2 : 3);
                    float boundary = Math.abs(x) * .5f;
                    Assert.assertEquals('B', f.runAt(x, boundary + .3f, .01f, 60f, 1).side());
                    Assert.assertEquals(lateral, f.runAt(x, boundary - .3f, .01f, 60f, 1).side());
                    Assert.assertEquals(lateral, f.runAt(x, -boundary + .3f, .01f, 60f, 0).side());
                    Assert.assertEquals('F', f.runAt(x, -boundary - .3f, .01f, 60f, 0).side());
                }
            }
    }

    @Test public void coneDirectionChangesDoNotChangeQuadCandidatesOrTechnicalFootprint() {
        for (int opening=0; opening<4; opening++) try (SpatialCornerFixture f = new SpatialCornerFixture(opening, true)) {
            f.world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).remove(f.actor);
            games.pixscape.runtime.component.light.ConeLightComponent cone = f.world
                    .getMapper(games.pixscape.runtime.component.light.ConeLightComponent.class).create(f.actor);
            cone.radius = 60f; cone.coneAngleDeg = 30f;
            f.world.process();
            for (int sector=0; sector<4; sector++) {
                String baseline = f.run(sector, .5f, 60f, 1).toString();
                for (float angle : new float[]{0f, 90f, 180f, 270f}) {
                    cone.rotationDeg = angle;
                    Assert.assertEquals(baseline, f.run(sector, .5f, 60f, 1).toString());
                    Assert.assertEquals(.5f, f.actors.actorCircleRadius[0], 0f);
                }
            }
        }
    }
}
