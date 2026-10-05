package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.RenderSourceDomain;
import org.junit.Assert;
import org.junit.Test;

public class SpatialJunctionOrderTest {
    @Test public void lateralCornerCompromiseKeepsBothProvenancesAndNeighbourBounds() {
        try(SpatialJunctionFixture f=new SpatialJunctionFixture()) {
            for(int id:new int[]{606,609}) {
                f.run(id);
                Assert.assertTrue("id="+id,f.planner.lateralCornerApproximationCount()>0);
                Assert.assertTrue(f.hasRelation(30,16,SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
                Assert.assertTrue(f.hasRelation(30,16,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
                Assert.assertTrue(f.planner.actorBucket[0]<=f.before(30,16));
                for(int gx:new int[]{27,28,29})Assert.assertTrue(f.planner.actorBucket[0]<=f.before(gx,16));
                Assert.assertTrue(f.planner.actorBucket[0]>=f.after(30,id==606?17:19));
                Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
            }
        }
    }
    @Test public void fusedBranchCannotAssignFrontToDistantLocalSegment() {
        try(SpatialJunctionFixture f=new SpatialJunctionFixture()) {
            f.run(608);
            Assert.assertFalse(f.hasRelation(30,23,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertTrue(f.hasRelation(30,25,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertTrue(f.hasRelation(30,24,SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
            Assert.assertTrue(f.planner.actorBucket[0]>=f.after(30,25));
            Assert.assertTrue(f.planner.actorBucket[0]<=f.before(30,24));
            Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
        }
    }
    @Test public void tJunctionUsesLocalSupportAndNeverTheLCompromise() {
        try(SpatialJunctionFixture f=new SpatialJunctionFixture()) {
            f.run(610);
            Assert.assertEquals(0,f.planner.lateralCornerApproximationCount());
            Assert.assertFalse(f.hasRelation(30,24,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertTrue(f.hasRelation(27,27,SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
            Assert.assertTrue(f.planner.actorBucket[0]<=f.before(27,27));
            Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
        }
    }
    @Test public void controlsAndSeparateUnresolvedPlacementsRemainIdentified() {
        try(SpatialJunctionFixture f=new SpatialJunctionFixture()) {
            for(int id:new int[]{603,611}) {
                f.run(id);Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
                Assert.assertTrue(f.planner.actorBucket[0]<f.tiles.size);
            }
            f.run(604);Assert.assertEquals(1,f.planner.unresolvedConstraintCount());
            Assert.assertEquals(137,f.planner.actorLowerBound[0]);Assert.assertEquals(134,f.planner.actorUpperBound[0]);
            f.run(new int[]{603},-2,0,Float.NaN);
            Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
            Assert.assertEquals(Integer.MAX_VALUE,f.planner.actorUpperBound[0]);
            Assert.assertEquals(f.tiles.size,f.planner.actorBucket[0]);
        }
    }
    @Test public void elevenActorsComposeTogetherWithoutChangingTheTileSubsequence() {
        try(SpatialJunctionFixture f=new SpatialJunctionFixture()) {
            f.run(SpatialJunctionFixture.IDS,0,0,Float.NaN);
            Assert.assertEquals(11,f.actors.actorCount);Assert.assertEquals(153,f.composer.composedSize);
            Assert.assertEquals(1,f.planner.unresolvedConstraintCount());Assert.assertEquals(0,f.planner.actorOrderingFallbackCount());
            int tile=0;
            for(int i=0;i<f.composer.composedSize;i++)if(f.composer.composedDomains[i]==RenderSourceDomain.SOURCE_TILED)
                Assert.assertEquals(f.tiles.get(tile++),f.composer.composedSlots[i]);
            Assert.assertEquals(142,tile);
            for(int actor=0;actor<f.actors.actorCount;actor++)if(f.actors.actorEntityId[actor]!=f.entities.get(604))
                Assert.assertTrue(f.planner.actorLowerBound[actor]<=f.planner.actorUpperBound[actor]);
        }
    }
    @Test public void smallMovementsKeepCorrectedIntervalsAndStaticGeometry() {
        try(SpatialJunctionFixture f=new SpatialJunctionFixture()) {
            int compileCount=f.ranks.tileOrderCompileCount,projectionCount=f.faces.projectionCount();
            for(int id:new int[]{606,608,609,610,611})for(float d:new float[]{-2,-1,-.1f,.1f,1,2})
                for(boolean horizontal:new boolean[]{true,false}) {
                    f.run(new int[]{id},horizontal?d:0,horizontal?0:d,Float.NaN);
                    Assert.assertEquals("id="+id+" delta="+d,0,f.planner.unresolvedConstraintCount());
                    Assert.assertEquals(0,f.planner.actorOrderingFallbackCount());
                }
            Assert.assertEquals(compileCount,f.ranks.tileOrderCompileCount);
            Assert.assertEquals(projectionCount,f.faces.projectionCount());
        }
    }
    @Test public void footprintRadiusVariesIndependentlyOfTheCentreAndQuad() {
        try(SpatialJunctionFixture f=new SpatialJunctionFixture()) {
            float x=0,y=0,qx=0,qy=0;
            for(float radius:new float[]{5,30,62.0372f,120,250,500,1000}) {
                f.run(new int[]{610},0,0,radius);
                if(radius==5) { x=f.actors.actorCircleX[0];y=f.actors.actorCircleY[0];qx=f.ecs.x1[f.actors.actorSlot[0]];qy=f.ecs.y1[f.actors.actorSlot[0]]; }
                Assert.assertEquals(x,f.actors.actorCircleX[0],0);Assert.assertEquals(y,f.actors.actorCircleY[0],0);
                Assert.assertEquals(qx,f.ecs.x1[f.actors.actorSlot[0]],0);Assert.assertEquals(qy,f.ecs.y1[f.actors.actorSlot[0]],0);
                Assert.assertEquals(radius,f.actors.circleRadius(0),.0001f);
                int l=f.planner.actorLowerBound[0],u=f.planner.actorUpperBound[0],b=f.planner.actorBucket[0];
                System.out.println("RADIUS_ONLY 610 r="+radius+" L="+l+" U="+u+" bucket="+b);
                f.run(new int[]{610},0,0,radius);
                Assert.assertEquals(l,f.planner.actorLowerBound[0]);Assert.assertEquals(u,f.planner.actorUpperBound[0]);
                Assert.assertEquals(b,f.planner.actorBucket[0]);
                // Larger ground emprises may genuinely straddle walls: no invented side oracle.
            }
        }
    }
    @Test public void localSegmentEndpointsIncludeTangencyWithoutMovingTheVisualQuad() {
        try(SpatialJunctionFixture f=new SpatialJunctionFixture()) {
            f.run(608);
            int anchor=f.faces.anchorForCell(30,25),membership=-1,face=-1;
            for(int m=f.faces.anchorMembershipHead[anchor];m>=0;m=f.faces.membershipNext[m]) {
                int candidate=f.faces.membershipFace[m];
                if(f.faces.faceStructureId[candidate]==6&&f.faces.faceCompiledIndex[candidate]==11) {
                    membership=m;face=candidate;break;
                }
            }
            Assert.assertTrue(membership>=0);
            float radius=f.actors.circleRadius(0),qx=f.ecs.x1[f.actors.actorSlot[0]];
            for(boolean left:new boolean[]{true,false})for(float d:new float[]{-.001f,0,.001f}) {
                f.actors.actorCircleX[0]=left?f.faces.faceAnchorScreenMinX[membership]-radius+d
                        :f.faces.faceAnchorScreenMaxX[membership]+radius+d;
                f.actors.actorCircleY[0]=3530;
                f.solver.solveVisual(f.actors,f.faces,new SpatialVisualAnchorSelector(),f.ecs,f.tiled,f.map,0,0);
                boolean emitted=false;
                for(int r=0;r<f.solver.relationCount;r++)if(f.solver.relationFaceIndex[r]==face
                        &&f.solver.relationAnchorIndex[r]==anchor
                        &&f.solver.relationType[r]==SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE)emitted=true;
                Assert.assertEquals("left="+left+" delta="+d,left?d>=0:d<=0,emitted);
                Assert.assertEquals(qx,f.ecs.x1[f.actors.actorSlot[0]],0);
            }
        }
    }
}
