package games.pixscape.runtime.spatial;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.system.RenderExtractFrameQueueSystem;
import games.pixscape.runtime.tiled.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Assume;
import java.io.InputStreamReader;
import java.util.Arrays;

/** Historical candidate sets protect downstream locality rules; new selection is tested separately. */
public class SpatialJunctionLocalityTest {
    @Test public void twoWallsAndTwoTilesReproduce605BeforeAndAfterThroughFrameQueue() {
        for (int original : new int[]{0, 1}) try (Fixture f = new Fixture(605, true, original, 0)) {
            int[] junctions = f.faces.membershipJunctionCount.clone();
            Arrays.fill(f.faces.membershipJunctionCount, 0);
            f.run();
            Assert.assertEquals(2, f.solver.visualCandidateCount);
            Assert.assertEquals(6, f.solver.relationCount);
            Assert.assertEquals(1, f.planner.unresolvedConstraintCount());
            Assert.assertEquals(1, f.planner.actorLowerBound[0]);
            Assert.assertEquals(0, f.planner.actorUpperBound[0]);
            Assert.assertTrue(f.hasRelation(30,24, SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            f.faces.membershipJunctionCount = junctions;
            f.run();
            Assert.assertEquals(2, f.blocks.blocks.size);
            Assert.assertEquals(2, f.solver.visualCandidateCount);
            Assert.assertEquals(4, f.solver.relationCount);
            Assert.assertEquals(2, f.solver.rejectedCount);
            Assert.assertFalse(f.hasRelation(30,24, SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertTrue(f.hasRelation(28,24, SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
            Assert.assertEquals(0, f.planner.unresolvedConstraintCount());
            Assert.assertTrue(f.planner.actorLowerBound[0] <= f.planner.actorUpperBound[0]);
            Assert.assertEquals(0, f.planner.actorBucket[0]);
            Assert.assertEquals(3, f.queue.size);
            Assert.assertEquals(RenderSourceDomain.SOURCE_ECS, f.queue.sourceDomain[0]);
            Assert.assertTrue(f.queuePosition(f.ref(28,24)) > 0);
        }
    }

    @Test public void real612And615RetainMaskingBehindAndRejectRemoteFront() {
        for (int id : new int[]{612,615}) for (int original : new int[]{0,1})
            try (Fixture f = new Fixture(id,false,original,0)) {
                f.run();
                int gx = id == 612 ? 30 : 18, gy = id == 612 ? 16 : 20;
                Assert.assertFalse(f.hasRelation(gx,gy,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
                int maskX = id == 612 ? 25 : 18, maskY = id == 612 ? 16 : 18;
                Assert.assertTrue(f.hasRelation(maskX,maskY,SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
                Assert.assertTrue(f.solver.rejectedCount > 0);
                Assert.assertFalse(f.planner.hasUnresolvedCorner(0));
                Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
                Assert.assertTrue(f.actorQueuePosition() < f.queuePosition(f.ref(maskX,maskY)));
            }
    }

    @Test public void penetrating614RemainsAnOldMultiTileConflict() {
        try (Fixture f = new Fixture(614,false,1,0)) {
            f.run();
            Assert.assertEquals(1,f.planner.unresolvedConstraintCount());
            Assert.assertTrue(f.hasRelation(18,20,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertTrue(f.hasRelation(18,21,SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
            Assert.assertEquals("MULTI_TILE",f.planner.conflictKind(0));
            Assert.assertFalse(f.planner.hasUnresolvedCorner(0));
            // Keep the actual radius/pose: fallback is characterized, not declared ideal.
            Assert.assertEquals(38.79492f,f.actors.circleRadius(0),.001f);
            Assert.assertEquals(-91.13536f,f.actors.actorCircleX[0],.001f);
        }
    }

    @Test public void nonzeroAbsoluteAltitudeAndMapPlanePreserveLocality() {
        try (Fixture f = new Fixture(605,true,1,128)) {
            f.run();
            Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
            Assert.assertEquals(4,f.solver.relationCount);
            Assert.assertEquals(128,f.faces.faceAltitude[0],0);
            Assert.assertEquals(128,f.map.defaultTileAltitude,0);
        }
    }

    @Test public void commonAndDifferentDisplayOffsetsPreserveJunctionQualification() {
        try(Fixture f=new Fixture(605,true,1,0)) {
            f.run();
            f.ecs.offsetX[f.slot]=123;f.ecs.offsetY[f.slot]=-87;
            f.solver.solveVisual(f.actors,f.faces,new HistoricalVisualAnchorSelector(),f.ecs,f.tiled,f.map,123,-87);
            Assert.assertEquals(4,f.solver.relationCount);Assert.assertEquals(2,f.solver.rejectedCount);
            f.actors.actorCircleX[0]-=15;f.actors.actorCircleY[0]+=9;
            f.ecs.x1[f.slot]-=15;f.ecs.x2[f.slot]-=15;f.ecs.x3[f.slot]-=15;f.ecs.x4[f.slot]-=15;
            f.ecs.y1[f.slot]+=9;f.ecs.y2[f.slot]+=9;f.ecs.y3[f.slot]+=9;f.ecs.y4[f.slot]+=9;
            f.ecs.offsetX[f.slot]+=15;f.ecs.offsetY[f.slot]-=9;
            f.solver.solveVisual(f.actors,f.faces,new HistoricalVisualAnchorSelector(),f.ecs,f.tiled,f.map,123,-87);
            Assert.assertEquals(4,f.solver.relationCount);Assert.assertEquals(2,f.solver.rejectedCount);
            Assert.assertFalse(f.hasRelation(30,24,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
        }
    }

    @Test public void qualificationIsIndependentOfRawRelationEnumeration() {
        try (Fixture f = new Fixture(605,true,1,0)) {
            f.run(); int expected = f.solver.relationCount;
            int[] junctions = f.faces.membershipJunctionCount.clone();
            Arrays.fill(f.faces.membershipJunctionCount,0); f.run();
            f.faces.membershipJunctionCount = junctions;
            for (int a=0,b=f.solver.relationCount-1;a<b;a++,b--) {
                swap(f.solver.relationFaceIndex,a,b); swap(f.solver.relationAnchorIndex,a,b);
                swap(f.solver.relationMembershipIndex,a,b);
                byte t=f.solver.relationType[a]; f.solver.relationType[a]=f.solver.relationType[b]; f.solver.relationType[b]=t;
            }
            f.solver.qualifyJunctionFronts(0,0,f.faces,f.actors.actorCircleX[0],f.actors.circleRadius(0));
            Assert.assertEquals(expected,f.solver.relationCount);
            Assert.assertFalse(f.hasRelation(30,24,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            for(int r=0;r<f.solver.relationCount;r++) Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,f.solver.relationType[r]);
        }
    }

    private static void swap(int[] a,int i,int j) { int t=a[i];a[i]=a[j];a[j]=t; }

    @Test public void wideSpriteWithSmallFootprintKeepsLateralFrontOnStraightWall() {
        try (Fixture f = new Fixture(605,true,1,0,1)) {
            f.world.getMapper(SpatialPhysicsFootprintComponent.class).get(f.actor).radiusPx=.5f;
            f.run();
            Assert.assertTrue(f.hasRelation(30,24,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertEquals(0,f.solver.rejectedCount);
            Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
            Assert.assertTrue(f.actorQueuePosition() > f.queuePosition(f.ref(30,24)));
        }
    }

    @Test public void branchesJoinedOnlyThroughFarConnectorAreNotADirectJunction() {
        try (Fixture f = new Fixture(605,true,1,0,2)) {
            f.run();
            Assert.assertTrue(f.hasRelation(30,24,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertTrue(f.hasRelation(28,24,SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
            Assert.assertEquals(0,f.solver.rejectedCount);
            // Both are in one valid connected structure, but the gap/opening is real.
            Assert.assertEquals(1,f.compiled.structureCount());
            Assert.assertFalse(f.planner.hasUnresolvedCorner(0));
        }
    }

    @Test public void distantFootprintTangencyUsesExplicitClosedProjectionTolerance() {
        try (Fixture f = new Fixture(605,true,1,0)) {
            f.run();
            int m=f.solver.rejectedMembership[0];
            float edge=f.faces.faceAnchorScreenMinX[m];
            int face=f.faces.membershipFace[m];
            // Isolate one FRONT and its direct BEHIND witness without changing geometry.
            int witness=f.solver.rejectedWitnessFace[0];
            for(float gap:new float[]{0,SpatialLineRelation.EPSILON*.5f,SpatialLineRelation.EPSILON*4f}) {
                f.solver.relationCount=2;
                f.solver.relationFaceIndex[0]=face;f.solver.relationMembershipIndex[0]=m;f.solver.relationType[0]=SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE;
                f.solver.relationFaceIndex[1]=witness;f.solver.relationMembershipIndex[1]=-1;f.solver.relationType[1]=SpatialFaceRelationSolver.ACTOR_BEHIND_FACE;
                f.solver.qualifyJunctionFronts(0,0,f.faces,edge-1-gap,1);
                Assert.assertEquals(gap <= SpatialLineRelation.EPSILON ? 2:1,f.solver.relationCount);
            }
        }
    }

    @Test public void tJunctionIncludesTheContinuousOuterBoundaryWithoutInventingAXCorner() {
        try (Fixture f=new Fixture(605,true,1,0)) {
            SpatialBlockData vertical=f.blocks.blocks.get(0),horizontal=f.blocks.blocks.get(1);
            Assert.assertTrue(vertical.y < horizontal.y);
            Assert.assertTrue(vertical.y+vertical.depth > horizontal.y+horizontal.depth);
            f.run();
            Assert.assertEquals(2,f.solver.rejectedCount);
            Assert.assertFalse(f.planner.hasUnresolvedCorner(0));
            Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
        }
    }

    @Test public void mergedBehindExtensionOutsideTheDirectWallCannotQualifyAJunction() {
        try(Fixture f=new Fixture(605,true,1,0,3)) {
            TransformComponent pose=f.world.getMapper(TransformComponent.class).get(f.actor);
            pose.x=-200;pose.y=3800;
            // Deliberately large sprite still selects the two audited tiles.
            f.ecs.x1[f.slot]=f.ecs.x4[f.slot]=-1100;
            f.ecs.x2[f.slot]=f.ecs.x3[f.slot]=1200;
            f.ecs.y1[f.slot]=f.ecs.y2[f.slot]=3000;
            f.ecs.y3[f.slot]=f.ecs.y4[f.slot]=4100;
            f.run();
            Assert.assertTrue(f.hasRelation(30,24,SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertTrue(f.hasRelation(28,24,SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
            Assert.assertFalse(f.planner.hasUnresolvedCorner(0));
        }
    }

    @Test public void remoteLateralPositionAndEndpointCapAreNotLocalX() {
        for(int opening:new int[]{2,3}) try(SpatialCornerFixture f=new SpatialCornerFixture(opening)) {
            f.runAt(opening==2 ? -120:120,0,.5f,180,1);
            f.solver.solveVisual(f.actors, f.faces, new HistoricalVisualAnchorSelector(),
                    f.ecs, f.tiled, f.map, 0, 0);
            Assert.assertEquals(1,f.solver.visualCandidateCount);
            Assert.assertTrue(f.solver.relationCount>0);
            Assert.assertFalse(f.planner.hasUnresolvedCorner(0));
            Assert.assertEquals(0,f.planner.unresolvedConstraintCount());
        }
    }

    @Test public void warmedNormalSolverDoesNotAllocateOrRetainRejectedDiagnostics() {
        java.lang.management.ThreadMXBean base=java.lang.management.ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(base instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;
        Assume.assumeTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        try(Fixture f=new Fixture(605,true,1,0)) {
            f.run();f.solver.setCaptureCandidates(false);
            SpatialVisualAnchorSelector selector=new HistoricalVisualAnchorSelector();
            for(int i=0;i<4000;i++)f.solver.solveVisual(f.actors,f.faces,selector,f.ecs,f.tiled,f.map,0,0);
            long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread);
            for(int i=0;i<1000;i++)f.solver.solveVisual(f.actors,f.faces,selector,f.ecs,f.tiled,f.map,0,0);
            long allocated=bean.getThreadAllocatedBytes(thread)-before;
            Assert.assertEquals(0,allocated);
            Assert.assertEquals(0,f.solver.rejectedCount);
            Assert.assertEquals(4,f.solver.relationCount);
        }
    }

    static final class Fixture implements AutoCloseable {
        final SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        final TiledMapLayerData map = new TiledMapLayerData(50,50,256,128,16,TiledProjection.ISO);
        final SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        final SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        final SpatialTileOrderCache ranks = new SpatialTileOrderCache();
        final TiledMapRenderState tiled = new TiledMapRenderState();
        final DynamicEntityRenderState ecs = new DynamicEntityRenderState(1);
        final SpatialActorCollector actors = new SpatialActorCollector();
        final SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
        final SpatialBucketPlanner planner = new SpatialBucketPlanner();
        final SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        final SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
        final DrawList draw = new DrawList(128);
        final FrameRenderQueue queue = new FrameRenderQueue(128);
        final World world;
        final int actor,slot,original;
        final IntArray refs = new IntArray();
        Fixture(int id,boolean minimal,int original,float plane) {
            this(id,minimal,original,plane,0);
        }
        Fixture(int id,boolean minimal,int original,float plane,int geometry) {
            this.original=original;
            JsonValue data = new JsonReader().parse(new InputStreamReader(getClass().getResourceAsStream("/spatial/locality-audit-20261006.json")));
            Json json = new Json();
            for(JsonValue b=data.get("blocks").child;b!=null;b=b.next) {
                if(minimal && b.getInt("id")!=16 && b.getInt("id")!=22) continue;
                SpatialBlockData wall=json.readValue(SpatialBlockData.class,b);wall.altitude+=plane;
                if(minimal) for(int i=wall.linkedTileRefs.size-1;i>=0;i--) {
                    SpatialBlockData.LinkedTileRef ref=wall.linkedTileRefs.get(i);
                    if(ref.gy!=24 || ref.gx!=28 && ref.gx!=30) wall.linkedTileRefs.removeIndex(i);
                }
                blocks.blocks.add(wall);
            }
            if(geometry==1) blocks.blocks.removeIndex(1);
            if(geometry==2) {
                blocks.blocks.get(1).width=3f;
                wall(100,27,16,.23499489f,8.23499489f);
                wall(101,27,16,4,.23499489f);
            }
            if(geometry==3) {
                blocks.blocks.get(0).depth=24;
                wall(100,20,24,7.1f,.23499489f);
            }
            map.defaultTileAltitude=plane;
            for(IntMap.Values<TileChunk> it=map.getChunks();it.hasNext();) {
                TileChunk c=it.next();c.renderRefStartIndex=tiled.registerRefs(c.cellCount());c.renderRefCount=c.cellCount();
            }
            for(JsonValue t=data.get("tiles").child;t!=null;t=t.next) {
                int gx=t.getInt("gx"),gy=t.getInt("gy");
                if(minimal && (gy!=24 || gx!=28 && gx!=30)) continue;
                map.setTile(gx,gy,1);int ref=ref(gx,gy);refs.add(ref);
                float[] q=t.get("quad").asFloatArray();
                tiled.enabled[ref]=tiled.visible[ref]=true;tiled.textureHandle[ref]=1;
                tiled.x1[ref]=q[0];tiled.y1[ref]=q[1];tiled.x2[ref]=q[2];tiled.y2[ref]=q[3];
                tiled.x3[ref]=q[4];tiled.y3[ref]=q[5];tiled.x4[ref]=q[6];tiled.y4[ref]=q[7];
            }
            compiled.ensure(blocks);ranks.ensure(8,map,blocks,compiled);faces.ensure(compiled,map);
            // Same static ISO/spatial ordering as production, no actor-dependent tile ordering.
            for(int i=1;i<refs.size;i++) for(int j=i;j>0 && rank(refs.get(j))<rank(refs.get(j-1));j--) swap(refs.items,j,j-1);
            world=new World(new WorldConfigurationBuilder().with(new RenderExtractFrameQueueSystem(ecs,tiled,draw,queue,new RenderStats(),1,-1,-1)).build());
            actor=world.create();slot=ecs.acquireSlotForEntity(actor);
            world.getMapper(EntityIndexComponent.class).create(actor);
            TransformComponent transform=world.getMapper(TransformComponent.class).create(actor);
            SpatialPhysicsFootprintComponent foot=world.getMapper(SpatialPhysicsFootprintComponent.class).create(actor);
            SpatialHeightComponent height=world.getMapper(SpatialHeightComponent.class).create(actor);
            JsonValue pose=data.get("actors").child;while(pose.getInt("id")!=id)pose=pose.next;
            transform.x=pose.getFloat("x");transform.y=pose.getFloat("y");height.altitude=plane;
            foot.valid=true;foot.radiusPx=pose.getFloat("radius");height.height=pose.getFloat("height");
            float[] q=pose.get("quad").asFloatArray();ecs.x1[slot]=q[0];ecs.y1[slot]=q[1];ecs.x2[slot]=q[2];ecs.y2[slot]=q[3];
            ecs.x3[slot]=q[4];ecs.y3[slot]=q[5];ecs.x4[slot]=q[6];ecs.y4[slot]=q[7];
            ecs.layerIndex[slot]=0;ecs.kind[slot]=RenderKind.SPRITE;ecs.textureHandle[slot]=1;ecs.enabled[slot]=ecs.visible[slot]=true;
            solver.setCaptureCandidates(true);world.process();
        }
        int rank(int ref) { for(int i=0;i<faces.anchorCount;i++) if(faces.anchorTiledRef[i]==ref)return ranks.rank(faces.anchorGx[i],faces.anchorGy[i]);return Integer.MAX_VALUE; }
        void wall(int id,float x,float y,float w,float d) { SpatialBlockData b=new SpatialBlockData();b.id=id;b.structureId=6;b.x=x;b.y=y;b.width=w;b.depth=d;b.height=155.93677f;blocks.blocks.add(b); }
        int ref(int gx,int gy) { return map.tiledRenderRefForTile(gx,gy); }
        void run() {
            draw.clearEntries();if(original==0)draw.addEcsSlot(slot);for(int i=0;i<refs.size;i++)draw.addTiledSlot(refs.get(i));if(original==1)draw.addEcsSlot(slot);
            actors.collect(draw,ecs,new boolean[]{true},world.getEntityManager(),world.getMapper(EntityIndexComponent.class),world.getMapper(TransformComponent.class),world.getMapper(SpatialHeightComponent.class),world.getMapper(SpatialPhysicsFootprintComponent.class));
            snapshot.build(draw,ecs.getRenderCapacity(),actors);
            int[] indices=new int[tiled.getCapacity()];Arrays.fill(indices,-1);
            for(int i=0;i<draw.size;i++)if(draw.sourceDomain[i]==RenderSourceDomain.SOURCE_TILED)indices[draw.sourceSlot[i]]=i;
            new SpatialFaceAnchorResolver().resolve(faces,indices,snapshot.drawIndexToBucketBefore,snapshot.drawIndexToBucketAfter,draw.size);
            solver.solveVisual(actors,faces,new HistoricalVisualAnchorSelector(),ecs,tiled,map,0,0);
            planner.begin(actors,snapshot.actorOriginalBucket,snapshot.bucketCount);planner.addRelations(actors,faces,solver,8);planner.finish(actors);
            composer.compose(draw,actors,planner,snapshot);
            System.arraycopy(composer.composedSlots,0,draw.sourceSlot,0,draw.size);
            System.arraycopy(composer.composedDomains,0,draw.sourceDomain,0,draw.size);
            world.process();
        }
        boolean hasRelation(int gx,int gy,byte type) { for(int r=0;r<solver.relationCount;r++){int a=solver.relationAnchorIndex[r];if(faces.anchorGx[a]==gx && faces.anchorGy[a]==gy && solver.relationType[r]==type)return true;}return false; }
        int actorQueuePosition() { for(int i=0;i<queue.size;i++)if(queue.sourceDomain[i]==RenderSourceDomain.SOURCE_ECS)return i;return -1; }
        int queuePosition(int ref) { for(int i=0;i<queue.size;i++)if(queue.sourceDomain[i]==RenderSourceDomain.SOURCE_TILED && queue.sourceSlot[i]==ref)return i;return -1; }
        public void close() { world.dispose(); }
    }
}
