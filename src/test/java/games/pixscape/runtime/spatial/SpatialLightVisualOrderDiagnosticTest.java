package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Reproduces the former one-pixel contradiction from demo blocks 19/34 and
 * their three decisive tiles. The canonical compiler must provide a valid
 * actor insertion interval at both positions.
 */
public class SpatialLightVisualOrderDiagnosticTest {
    private static final float SOURCE_X = 113.63916f;
    private static final float SOURCE_Y = 2920.444f;
    private static final float HALO_RADIUS = 357.77878f;

    @Test public void threeTileFixtureKeepsPointAndOpaqueSpriteWithinValidIntervals() {
        Fixture f = new Fixture();
        f.ecs.blend[f.actor.actorSlot[0]] = BlendMode.ADDITIVE.id;
        Outcome left = f.run(SOURCE_X + 40f, HALO_RADIUS);
        Outcome right = f.run(SOURCE_X + 41f, HALO_RADIUS);
        Outcome idle = f.run(SOURCE_X + 41f, HALO_RADIUS);
        Assert.assertEquals(3, f.order.tileOrderNodeCount);
        Assert.assertTrue(f.order.rank(23,26) < f.order.rank(24,20));
        Assert.assertEquals(2, left.candidates);
        Assert.assertEquals(3, right.candidates);
        Assert.assertEquals(0, left.conflicts);
        Assert.assertEquals(0, right.conflicts);
        assertValidPlacement(left);
        assertValidPlacement(right);
        Assert.assertEquals(right, idle);
        // Opaque sprite with the same submitted quad and physical footprint uses this same path.
        f.ecs.blend[f.actor.actorSlot[0]] = BlendMode.OPAQUE.id;
        Assert.assertEquals(left, f.run(SOURCE_X + 40f, HALO_RADIUS));
        Assert.assertEquals(right, f.run(SOURCE_X + 41f, HALO_RADIUS));
        Outcome small = f.run(SOURCE_X + 41f, 20f);
        Assert.assertEquals(0, small.conflicts);
        Assert.assertEquals(0, small.candidates);
        Assert.assertEquals(small, f.run(SOURCE_X + 41f, 20f));
    }

    @Test public void coneDarkHalfOfSubmittedQuadStillCreatesAConstraint() {
        TiledMapLayerData map = new TiledMapLayerData(10,10,100,100,2,TiledProjection.ORTHO);
        map.setTile(2,3,7);
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        blocks.blocks.add(block(1,1,2f,3f,1f,1f,100f,new int[][]{{2,3,7}}));
        TiledMapRenderState tiled = new TiledMapRenderState(16);
        for (com.badlogic.gdx.utils.IntMap.Values<TileChunk> chunks=map.getChunks();chunks.hasNext();) {
            TileChunk chunk=chunks.next();chunk.renderRefStartIndex=tiled.registerRefs(chunk.cellCount());
            chunk.renderRefCount=chunk.cellCount();
        }
        int ref=map.tiledRenderRefForTile(2,3);
        tiled.enabled[ref]=tiled.visible[ref]=true;
        tiled.x1[ref]=tiled.x4[ref]=200f;tiled.x2[ref]=tiled.x3[ref]=300f;
        tiled.y1[ref]=tiled.y2[ref]=300f;tiled.y3[ref]=tiled.y4[ref]=400f;
        SpatialCompiledLayerCache compiled=new SpatialCompiledLayerCache();compiled.ensure(blocks);
        SpatialProjectedFaceCache faces=new SpatialProjectedFaceCache();faces.ensure(compiled,map);
        DynamicEntityRenderState ecs=new DynamicEntityRenderState(1);
        SpatialActorCollector actor=new SpatialActorCollector();actor.actorCount=1;
        actor.actorSlot=new int[]{ecs.acquireSlotForEntity(1)};
        actor.actorCircleX=new float[]{450f};actor.actorCircleY=new float[]{450f};
        actor.actorCircleRadius=new float[]{5f};actor.actorAltitude=new float[]{0f};
        actor.actorHeight=new float[]{1f};
        int slot=actor.actorSlot[0];
        ecs.x1[slot]=ecs.x4[slot]=250f;ecs.x2[slot]=ecs.x3[slot]=650f;
        ecs.y1[slot]=ecs.y2[slot]=250f;ecs.y3[slot]=ecs.y4[slot]=650f;
        int[] refToDraw=new int[tiled.getCapacity()];Arrays.fill(refToDraw,-1);refToDraw[ref]=0;
        new SpatialFaceAnchorResolver().resolve(faces,refToDraw,new int[]{0,1},new int[]{1,1},2);
        SpatialFaceRelationSolver solver=new SpatialFaceRelationSolver();
        solver.solveVisual(actor,faces,new SpatialVisualAnchorSelector(),ecs,tiled,map,0f,0f);
        SpatialBucketPlanner planner=new SpatialBucketPlanner();
        planner.begin(actor,new int[]{1},2);planner.addRelations(actor,faces,solver,1);planner.finish(actor);
        Assert.assertEquals(1,solver.visualCandidateCount);
        Assert.assertTrue(solver.relationCount>0);
        Assert.assertTrue(planner.actorHasConstraint[0]);
        // For a +X cone with half-angle 45 degrees, every point in this tile has
        // a negative direction dot product and fails the shader's positive cone cosine.
        for (float x : new float[]{200f,300f}) for (float y : new float[]{300f,400f}) {
            float dx=x-450f,dy=y-450f;
            float directionDot=dx/(float)Math.sqrt(dx*dx+dy*dy);
            Assert.assertTrue(directionDot<Math.cos(Math.toRadians(45d)));
        }
        System.out.println("CONE darkQuad candidates="+solver.visualCandidateCount
                +" relations="+solver.relationCount+" L="+planner.actorLowerBound[0]
                +" U="+planner.actorUpperBound[0]+" bucket="+planner.actorBucket[0]);
    }

    private static final class Fixture {
        final TiledMapLayerData map = new TiledMapLayerData(50, 50, 256, 128, 16, TiledProjection.ISO);
        final SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        final SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        final SpatialTileOrderCache order = new SpatialTileOrderCache();
        final SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        final SpatialActorCollector actor = new SpatialActorCollector();
        final DynamicEntityRenderState ecs = new DynamicEntityRenderState(1);
        final TiledMapRenderState tiled = new TiledMapRenderState(4096);
        final SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
        final SpatialVisualAnchorSelector selector = new SpatialVisualAnchorSelector();
        final SpatialBucketPlanner planner = new SpatialBucketPlanner();
        final SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
        final DrawList draw = new DrawList(8);
        final SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();

        Fixture() {
            map.setTile(24,20,146); map.setTile(23,20,162); map.setTile(23,26,9);
            map.setVisualPadding(0f, 0f, 384f, 0f);
            blocks.blocks.add(block(19,6,18f,20.765005f,8f,.23499489f,155.93677f,
                    new int[][]{{23,20,162},{24,20,146}}));
            blocks.blocks.add(block(34,16,23.27519f,26f,.72480965f,3f,71.97803f,
                    new int[][]{{23,26,9}}));
            for (com.badlogic.gdx.utils.IntMap.Values<TileChunk> chunks=map.getChunks();chunks.hasNext();) {
                TileChunk chunk=chunks.next(); chunk.renderRefStartIndex=tiled.registerRefs(chunk.cellCount());
                chunk.renderRefCount=chunk.cellCount();
            }
            compiled.ensure(blocks); order.ensure(8,map,blocks,compiled); faces.ensure(compiled,map);
            for (int gy=0;gy<50;gy++) for(int gx=0;gx<50;gx++) if(map.getTile(gx,gy)>0) {
                int ref=map.tiledRenderRefForTile(gx,gy); tiled.enabled[ref]=tiled.visible[ref]=true;
                float x=map.tileToWorldX(gx,gy)+128f, y=map.tileToWorldY(gx,gy);
                tiled.x1[ref]=tiled.x4[ref]=x-128f; tiled.x2[ref]=tiled.x3[ref]=x+128f;
                tiled.y1[ref]=tiled.y2[ref]=y; tiled.y3[ref]=tiled.y4[ref]=y+512f;
            }
            actor.actorCount=1; actor.actorSlot=new int[]{ecs.acquireSlotForEntity(83)};
            actor.actorCircleX=new float[1];actor.actorCircleY=new float[]{SOURCE_Y};
            actor.actorCircleRadius=new float[]{5f};actor.actorAltitude=new float[]{0f};
            actor.actorHeight=new float[]{1f};actor.actorFootX=new float[1];actor.actorFootY=new float[]{SOURCE_Y};
            for(int rank=0;rank<3;rank++) for(int gy=0;gy<50;gy++) for(int gx=0;gx<50;gx++)
                if(order.rank(gx,gy)==rank) draw.addTiledSlot(map.tiledRenderRefForTile(gx,gy));
            draw.addEcsSlot(actor.actorSlot[0]);
            resolve();
        }

        private void resolve() {
            snapshot.build(draw,ecs.getRenderCapacity(),actor);
            int[] refToDraw=new int[tiled.getCapacity()];Arrays.fill(refToDraw,-1);
            for(int i=0;i<draw.size-1;i++)refToDraw[draw.get(i)]=i;
            new SpatialFaceAnchorResolver().resolve(faces,refToDraw,
                    snapshot.drawIndexToBucketBefore,snapshot.drawIndexToBucketAfter,draw.size);
        }

        Outcome run(float sourceX,float visualRadius) {
            actor.actorCircleX[0]=actor.actorFootX[0]=sourceX;
            int slot=actor.actorSlot[0];
            ecs.x1[slot]=ecs.x4[slot]=sourceX-visualRadius;
            ecs.x2[slot]=ecs.x3[slot]=sourceX+visualRadius;
            ecs.y1[slot]=ecs.y2[slot]=SOURCE_Y-visualRadius;
            ecs.y3[slot]=ecs.y4[slot]=SOURCE_Y+visualRadius;
            solver.solveVisual(actor,faces,selector,ecs,tiled,map,0f,0f);
            planner.begin(actor,snapshot.actorOriginalBucket,snapshot.bucketCount);
            planner.addRelations(actor,faces,solver,8); planner.finish(actor);
            composer.compose(draw,actor,planner,snapshot);
            int tile=0;
            for(int i=0;i<composer.composedSize;i++) {
                if(composer.composedDomains[i]!=games.pixscape.runtime.render.RenderSourceDomain.SOURCE_TILED)continue;
                Assert.assertEquals(draw.get(tile++),composer.composedSlots[i]);
            }
            Assert.assertEquals(3,tile);
            byte[] anchorIntent=new byte[faces.anchorCount];
            for(int relation=0;relation<solver.relationCount;relation++) {
                int anchor=solver.relationAnchorIndex[relation];
                if(anchor<0)continue;
                byte intent=solver.relationType[relation]==SpatialFaceRelationSolver.ACTOR_BEHIND_FACE
                        ? (byte)2 : (byte)1;
                anchorIntent[anchor]=(byte)Math.max(anchorIntent[anchor],intent);
            }
            for(int anchor=0;anchor<faces.anchorCount;anchor++) {
                if(anchorIntent[anchor]==1)
                    Assert.assertTrue(planner.actorBucket[0]>=faces.anchorAfterBucket[anchor]);
                else if(anchorIntent[anchor]==2)
                    Assert.assertTrue(planner.actorBucket[0]<=faces.anchorBeforeBucket[anchor]);
            }
            return new Outcome(solver.visualCandidateCount,planner.actorLowerBound[0],
                    planner.actorUpperBound[0],planner.actorBucket[0],planner.unresolvedConstraintCount());
        }
    }

    private static void assertValidPlacement(Outcome outcome) {
        Assert.assertTrue(outcome.lower<=outcome.upper);
        Assert.assertTrue(outcome.bucket>=outcome.lower);
        Assert.assertTrue(outcome.bucket<=outcome.upper);
    }

    private static SpatialBlockData block(int id,int structure,float x,float y,float width,float depth,
                                          float height,int[][] refs) {
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.structureId=structure;
        b.x=x;b.y=y;b.width=width;b.depth=depth;b.height=height;
        b.beginAuthoredLinkedTileRefs();
        for(int[] ref:refs)b.addLinkedTileRef(ref[0],ref[1],ref[2]);
        return b;
    }

    private static final class Outcome {
        final int candidates,lower,upper,bucket,conflicts;
        Outcome(int candidates,int lower,int upper,int bucket,int conflicts) {
            this.candidates=candidates;this.lower=lower;this.upper=upper;this.bucket=bucket;this.conflicts=conflicts;
        }
        @Override public boolean equals(Object object) {
            if(!(object instanceof Outcome))return false;
            Outcome o=(Outcome)object;
            return candidates==o.candidates&&lower==o.lower&&upper==o.upper&&bucket==o.bucket&&conflicts==o.conflicts;
        }
        @Override public int hashCode(){return candidates+31*lower+127*upper+257*bucket+8191*conflicts;}
        @Override public String toString(){return "candidates="+candidates+" L="+lower+" U="+upper+" bucket="+bucket+" conflicts="+conflicts;}
    }
}
