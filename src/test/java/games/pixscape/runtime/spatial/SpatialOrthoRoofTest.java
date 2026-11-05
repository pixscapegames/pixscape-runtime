package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpatialOrthoRoofTest {
    @Test public void unassignedBlocksDoNotCreateRoofStructures() {
        Fixture f = new Fixture();
        f.wall.structureId = 0;
        f.blocks.revision++;
        f.compiled.ensure(f.blocks);
        f.faces.ensure(f.compiled, f.map);
        assertEquals(0, f.faces.structureCount);
        assertEquals(0, f.faces.anchorCount);
        assertEquals(0, SpatialRoofSupports.collect(f.compiled, f.map).size);
    }

    @Test public void centralImagesUseFiniteRoofPatchesWithoutTurningEdgeOnFacesIntoSlopes() {
        Fixture f = new Fixture();
        int anchor = f.faces.anchorForCell(6,5);
        assertTrue(anchor >= 0);
        int face = f.faces.membershipFace[f.faces.anchorMembershipHead[anchor]];
        assertTrue(f.faces.roofSupport[face]);
        assertEquals(-1,f.faces.faceCompiledIndex[face]);
        assertEquals(108,f.faces.screenMinX[face],0);
        assertEquals(112,f.faces.screenMaxX[face],0);
        assertEquals(80,f.faces.surfaceMinY[face],0);
        assertEquals(96,f.faces.surfaceMaxY[face],0);
        assertEquals(32,f.faces.intercept[face],0); // front of the continuous roof, not the image cell
        for(int i=0;i<f.faces.faceCount;i++)assertTrue(Float.isFinite(f.faces.slope[i]));
        assertEquals(-1,f.faces.anchorForCell(5,5)); // floor is not made into a wall anchor
    }

    @Test public void bothSidesUseTheAuthoredEnvelopeAndLeaveTheWallAtFiniteLimits() {
        Fixture f = new Fixture();
        for(float x:new float[]{100,120}) {
            f.solve(x,84,0);
            assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,f.relation(6,5));
            assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,f.relation(6,6));
            // Footprint is well outside the thin wall; the visual envelope is not enlarged.
            assertEquals(1,f.actor.actorCircleRadius[0],0);
        }
        for(float x:new float[]{90,125}){f.solve(x,84,0);assertEquals(0,f.relation(6,5));}
        f.solve(100,40,0);assertEquals(0,f.relation(6,5));
        f.solve(100,97,0);assertEquals(0,f.relation(6,5));
        f.solve(100,84,16);assertEquals(0,f.solver.relationCount); // above the roof
        f.solve(100,84,-25);assertEquals(0,f.solver.relationCount); // below the volume
    }

    @Test public void floorColumnsStayBeforeOccludersAndTheWholeRoofHasAFeasibleActorBucket() {
        Fixture f = new Fixture();
        int upper=f.order.rank(6,6),lower=f.order.rank(6,5);
        assertTrue(upper<lower);
        for(int x:new int[]{0,5,7,11})for(int y:new int[]{0,5,10})
            assertTrue("floor "+x+","+y,f.order.rank(x,y)<upper);
        f.solve(100,84,0);
        SpatialBucketPlanner planner=new SpatialBucketPlanner();
        planner.begin(f.actor,new int[]{144},144);planner.addRelations(f.actor,f.faces,f.solver,1);planner.finish(f.actor);
        assertEquals(0,planner.unresolvedConstraintCount());
        assertTrue(planner.actorBucket[0]<=upper);
        assertTrue(planner.actorBucket[0]>f.order.rank(5,5));
    }

    @Test public void imageCellBoundariesCannotSplitOneContinuousRoofIntoOppositeActorRelations() {
        Fixture f = new Fixture();
        for (float y : new float[]{68, 76, 84, 92, 100}) {
            f.solve(100, y, 0);
            int roofs = 0;
            for (int r = 0; r < f.solver.relationCount; r++) {
                if (!f.faces.roofSupport[f.solver.relationFaceIndex[r]]) continue;
                roofs++;
                assertEquals("All touched portions of this roof must mask the actor",
                        SpatialFaceRelationSolver.ACTOR_BEHIND_FACE, f.solver.relationType[r]);
            }
            assertTrue(roofs >= 2);
        }
        f.solve(100, 28, 0);
        assertEquals(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE, f.relation(6,3));
        f.solve(100, 36, 0);
        assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE, f.relation(6,3));
    }

    @Test public void projectionInvalidationAndReferenceOrderPreserveAbsoluteAltitude() {
        Fixture f=new Fixture();int count=f.faces.projectionCount();
        assertFalse(f.faces.ensure(f.compiled,f.map));assertEquals(count,f.faces.projectionCount());
        f.wall.linkedTileRefs.reverse();f.blocks.revision++;
        f.compiled.ensure(f.blocks);f.faces.ensure(f.compiled,f.map);
        assertEquals(32,f.faces.intercept[f.roof(6,5)],0);
        f.map.defaultTileAltitude=4;f.faces.ensure(f.compiled,f.map);
        int face=f.roof(6,5);
        // Image cell stays [80,96]; only the actually covered roof intersection changes.
        assertEquals(28,f.faces.intercept[face],0);
        assertEquals(0,f.faces.faceAltitude[face],0);
        assertEquals(16,f.faces.faceHeight[face],0);
        f.wall.actorOccluder=false;f.blocks.revision++;f.compiled.ensure(f.blocks);f.faces.ensure(f.compiled,f.map);
        assertEquals(0,f.faces.anchorCount);
    }

    @Test public void isoDoesNotAcquireRoofRelationsAndHorizontalCapsKeepTheirMemberships() {
        Fixture f=new Fixture();
        f.map.projection=TiledProjection.ISO;f.faces.ensure(f.compiled,f.map);
        for(int i=0;i<f.faces.faceCount;i++)assertFalse(f.faces.roofSupport[i]);
        assertTrue(f.faces.anchorForCell(6,5)>=0); // original ISO side faces still participate
        f.map.projection=TiledProjection.ORTHO;
        f.wall.x=2;f.wall.y=2;f.wall.width=6;f.wall.depth=.25f;
        f.wall.beginAuthoredLinkedTileRefs();for(int x=2;x<8;x++){f.wall.addLinkedTileRef(x,2,1);f.wall.addLinkedTileRef(x,3,1);}
        f.blocks.revision++;f.compiled.ensure(f.blocks);f.faces.ensure(f.compiled,f.map);
        assertEquals(2,f.faces.faceCount);
        for(int i=0;i<f.faces.faceCount;i++)assertFalse(f.faces.roofSupport[i]);
        assertEquals(12,f.faces.anchorCount);
    }

    @Test public void horizontalJoinsKeepTheirExposedSupportsAtBothEnds() {
        for (float y : new float[]{2, 7.75f}) {
            Fixture f = new Fixture();
            SpatialBlockData branch = new SpatialBlockData();
            branch.id = 2; branch.structureId = 1;
            branch.x = 2; branch.y = y; branch.width = 5; branch.depth = .25f; branch.height = 16;
            branch.beginAuthoredLinkedTileRefs();
            int row = (int)y;
            for (int x = 2; x < 7; x++) {
                branch.addLinkedTileRef(x, row, 1);
                branch.addLinkedTileRef(x, row + 1, 1);
            }
            f.blocks.blocks.add(branch); f.blocks.nextSpatialBlockId = 3; f.blocks.revision++;
            f.compiled.ensure(f.blocks); f.faces.ensure(f.compiled, f.map);
            assertTrue(f.faces.roofSupport[f.roof(6, 5)]);
            int corner = f.faces.anchorForCell(6, row + 1);
            assertTrue(corner >= 0);
            for (int m = f.faces.anchorMembershipHead[corner]; m >= 0; m = f.faces.membershipNext[m])
                assertFalse("Existing corner must retain its exposed face", f.faces.roofSupport[f.faces.membershipFace[m]]);
            f.order.ensure(1, f.map, f.blocks, f.compiled);
            assertTrue(f.order.rank(6, 5) >= 0);
        }
    }

    static final class Fixture {
        final TiledMapLayerData map=new TiledMapLayerData(12,12,16,16,4,TiledProjection.ORTHO);
        final SpatialBlocksComponent blocks=new SpatialBlocksComponent();
        final SpatialBlockData wall=new SpatialBlockData();
        final SpatialCompiledLayerCache compiled=new SpatialCompiledLayerCache();
        final SpatialProjectedFaceCache faces=new SpatialProjectedFaceCache();
        final SpatialTileOrderCache order=new SpatialTileOrderCache();
        final SpatialActorCollector actor=new SpatialActorCollector();
        final DynamicEntityRenderState ecs=new DynamicEntityRenderState(4);
        final TiledMapRenderState tiles=new TiledMapRenderState(32);
        final SpatialFaceRelationSolver solver=new SpatialFaceRelationSolver();
        final SpatialVisualAnchorSelector selector=new SpatialVisualAnchorSelector();
        Fixture(){
            for(int y=0;y<12;y++)for(int x=0;x<12;x++)map.setTile(x,y,1);
            wall.id=wall.structureId=1;wall.x=6.75f;wall.y=2;wall.width=.25f;wall.depth=6;wall.height=16;
            wall.beginAuthoredLinkedTileRefs();for(int y=2;y<=9;y++)wall.addLinkedTileRef(6,y,1);
            blocks.blocks.add(wall);blocks.nextSpatialBlockId=2;compiled.ensure(blocks);faces.ensure(compiled,map);order.ensure(1,map,blocks,compiled);
            tiles.registerRefs(faces.anchorCount);
            for(int a=0;a<faces.anchorCount;a++){
                int x=faces.anchorGx[a],y=faces.anchorGy[a];faces.anchorResolved[a]=true;faces.anchorTiledRef[a]=a;
                faces.anchorBeforeBucket[a]=order.rank(x,y);faces.anchorAfterBucket[a]=order.rank(x,y)+1;
                tiles.enabled[a]=tiles.visible[a]=true;
                tiles.x1[a]=tiles.x4[a]=x*16;tiles.x2[a]=tiles.x3[a]=(x+1)*16;
                tiles.y1[a]=tiles.y2[a]=y*16;tiles.y3[a]=tiles.y4[a]=(y+1)*16;
            }
            actor.actorCount=1;actor.actorSlot=new int[]{ecs.acquireSlotForEntity(1)};
            actor.actorCircleX=new float[1];actor.actorCircleY=new float[1];actor.actorCircleRadius=new float[]{1};
            actor.actorAltitude=new float[1];actor.actorHeight=new float[]{24};actor.influenceBounds=new float[4];
        }
        void solve(float x,float y,float z){actor.actorCircleX[0]=x;actor.actorCircleY[0]=y;actor.actorAltitude[0]=z;
            actor.influenceBounds[0]=x-12;actor.influenceBounds[1]=y;actor.influenceBounds[2]=x+12;actor.influenceBounds[3]=y+24;
            solver.solveVisual(actor,faces,selector,ecs,tiles,map,0,0);
        }
        int relation(int x,int y){int a=faces.anchorForCell(x,y);for(int i=0;i<solver.relationCount;i++)if(solver.relationAnchorIndex[i]==a)return solver.relationType[i];return 0;}
        int roof(int x,int y){return faces.membershipFace[faces.anchorMembershipHead[faces.anchorForCell(x,y)]];}
    }
}
