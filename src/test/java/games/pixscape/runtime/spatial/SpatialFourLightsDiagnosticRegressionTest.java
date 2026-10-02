package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Small, checkout-independent snapshot of the four observed Point Light placements. */
public final class SpatialFourLightsDiagnosticRegressionTest {
    @Test
    public void currentExtendedFaceRelationsReproduceAllFourPlacements() {
        Fixture f = new Fixture();
        Result one = f.run(1, false);
        Result two = f.run(2, false);
        Result three = f.run(3, false);
        Result four = f.run(4, false);

        Assert.assertEquals(one, f.run(1, false));
        Assert.assertEquals(two, f.run(2, false));
        Assert.assertEquals(three, f.run(3, false));
        Assert.assertEquals(four, f.run(4, false));
        Assert.assertTrue(one.lower <= one.upper);
        Assert.assertTrue("The light remains before the curved tile", one.bucket <= f.before(27, 24));
        Assert.assertTrue(two.lower > two.upper);
        Assert.assertEquals(two.original, two.bucket);
        Assert.assertTrue(three.lower <= three.upper);
        Assert.assertTrue(f.after(27, 24) <= three.bucket);
        Assert.assertTrue(three.bucket <= f.before(25, 20));
        Assert.assertTrue(four.lower > four.upper);
        Assert.assertEquals(four.original, four.bucket);
        Assert.assertEquals(0, one.orderingFallbacks + two.orderingFallbacks
                + three.orderingFallbacks + four.orderingFallbacks);

        Assert.assertTrue(one.hasRelation(28, 24, 6, 15, SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
        Assert.assertTrue(one.hasRelation(27, 24, 6, 3, SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
        Assert.assertTrue(one.hasRelation(27, 24, 6, 15, SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
        Assert.assertTrue(two.hasRelation(29, 24, 6, 15, SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
        Assert.assertTrue(three.hasRelation(27, 24, 6, 15, SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
        Assert.assertTrue(four.hasRelation(25, 19, 6, 2, SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
        Assert.assertTrue(four.hasRelation(25, 20, 6, 14, SpatialFaceRelationSolver.ACTOR_BEHIND_FACE));
        Assert.assertTrue(f.isOutsideFiniteFace(1, 15));
        Assert.assertTrue(f.isOutsideFiniteFace(2, 15));
        Assert.assertTrue(f.isOutsideFiniteFace(4, 2));
    }

    @Test
    public void finiteFaceCounterfactualRemovesTheDisputedConstraints() {
        Fixture f = new Fixture();
        for (int number = 1; number <= 4; number++) {
            Result result = f.run(number, true);
            Assert.assertTrue("diagnostic-" + number, result.lower <= result.upper);
            Assert.assertTrue("diagnostic-" + number, f.after(27, 24) <= result.bucket);
            Assert.assertTrue("diagnostic-" + number,
                    result.bucket <= f.before(number == 1 ? 24 : 25, 20));
            Assert.assertEquals(0, result.orderingFallbacks);
        }
    }

    @Test
    public void removingOnlyTheEmptyQuadCandidatesDoesNotRepairTheCurvedCorner() {
        Fixture f = new Fixture();
        f.setCandidateVisible(28, 24, false);
        Result one = f.run(1, false);
        Assert.assertTrue(one.bucket <= f.before(27, 24));
        f.setCandidateVisible(28, 24, true);
        f.setCandidateVisible(29, 24, false);
        Result two = f.run(2, false);
        Assert.assertTrue(two.lower <= two.upper);
        Assert.assertTrue(two.bucket <= f.before(27, 24));
        f.setCandidateVisible(29, 24, true);
        f.setCandidateVisible(25, 19, false);
        Result four = f.run(4, false);
        Assert.assertTrue(four.lower <= four.upper);
        Assert.assertTrue(f.after(27, 24) <= four.bucket);
        Assert.assertTrue(four.bucket <= f.before(25, 20));
    }

    private static final class Fixture {
        final JsonValue root = readFixture();
        final TiledMapLayerData map = new TiledMapLayerData(
                root.getInt("mapWidth"), root.getInt("mapHeight"),
                root.getInt("tileWidth"), root.getInt("tileHeight"),
                root.getInt("chunkSize"), TiledProjection.ISO);
        final SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        final SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        final SpatialTileOrderCache order = new SpatialTileOrderCache();
        final SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        final TiledMapRenderState tiled = new TiledMapRenderState(1024);
        final DynamicEntityRenderState ecs = new DynamicEntityRenderState(1);
        final SpatialActorCollector actor = new SpatialActorCollector();
        final SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        final SpatialFaceAnchorResolver resolver = new SpatialFaceAnchorResolver();
        final SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
        final SpatialVisualAnchorSelector selector = new SpatialVisualAnchorSelector();
        final SpatialBucketPlanner planner = new SpatialBucketPlanner();
        final SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
        final DrawList draw = new DrawList(64);
        final int slot = ecs.acquireSlotForEntity(1);

        Fixture() {
            map.setVisualPadding(0f, 0f, 384f, 0f);
            for (JsonValue cell = root.get("tiles").child; cell != null; cell = cell.next)
                map.setTile(cell.getInt("gx"), cell.getInt("gy"), cell.getInt("asset"));
            for (JsonValue data = root.get("blocks").child; data != null; data = data.next) {
                SpatialBlockData block = new SpatialBlockData();
                block.id = data.getInt("id");
                block.structureId = data.getInt("structureId");
                block.x = data.getFloat("x");
                block.y = data.getFloat("y");
                block.width = data.getFloat("width");
                block.depth = data.getFloat("depth");
                block.altitude = data.getFloat("altitude");
                block.height = data.getFloat("height");
                block.beginAuthoredLinkedTileRefs();
                for (JsonValue ref = data.get("refs").child; ref != null; ref = ref.next)
                    block.addLinkedTileRef(ref.getInt("gx"), ref.getInt("gy"), ref.getInt("tileAssetId"));
                blocks.blocks.add(block);
            }
            compiled.ensure(blocks);
            order.ensure(8, map, blocks, compiled);
            for (IntMap.Values<TileChunk> chunks = map.getChunks(); chunks.hasNext();) {
                TileChunk chunk = chunks.next();
                chunk.renderRefStartIndex = tiled.registerRefs(chunk.cellCount());
                chunk.renderRefCount = chunk.cellCount();
            }
            faces.ensure(compiled, map);
            int[] refByRank = new int[order.tileOrderNodeCount];
            Arrays.fill(refByRank, -1);
            for (JsonValue cell = root.get("tiles").child; cell != null; cell = cell.next) {
                int gx = cell.getInt("gx"), gy = cell.getInt("gy");
                int rank = order.rank(gx, gy);
                int ref = map.tiledRenderRefForTile(gx, gy);
                refByRank[rank] = ref;
                tiled.enabled[ref] = tiled.visible[ref] = true;
                float x = map.tileToWorldX(gx, gy), y = map.tileToWorldY(gx, gy);
                tiled.x1[ref] = tiled.x4[ref] = x;
                tiled.x2[ref] = tiled.x3[ref] = x + 256f;
                tiled.y1[ref] = tiled.y2[ref] = y;
                tiled.y3[ref] = tiled.y4[ref] = y + 512f;
            }
            for (int ref : refByRank) {
                Assert.assertTrue(ref >= 0);
                draw.addTiledSlot(ref);
            }
            draw.addEcsSlot(slot);
            actor.actorCount = 1;
            actor.actorSlot = new int[]{slot};
            actor.actorCircleX = new float[1];
            actor.actorCircleY = new float[1];
            actor.actorCircleRadius = new float[1];
            actor.actorAltitude = new float[1];
            actor.actorHeight = new float[1];
            actor.actorFootX = new float[1];
            actor.actorFootY = new float[1];
            snapshot.build(draw, ecs.getRenderCapacity(), actor);
            int[] refToDraw = new int[tiled.getCapacity()];
            Arrays.fill(refToDraw, -1);
            for (int i = 0; i < draw.size - 1; i++) refToDraw[draw.get(i)] = i;
            resolver.resolve(faces, refToDraw, snapshot.drawIndexToBucketBefore,
                    snapshot.drawIndexToBucketAfter, draw.size);
        }

        Result run(int number, boolean finiteOnly) {
            JsonValue light = light(number);
            float x = light.getFloat("x"), y = light.getFloat("y");
            float radius = light.getFloat("radius");
            actor.actorCircleX[0] = actor.actorFootX[0] = x;
            actor.actorCircleY[0] = actor.actorFootY[0] = y;
            actor.actorCircleRadius[0] = light.getFloat("footprintRadiusM")
                    * root.getFloat("pixelsPerMeter");
            actor.actorAltitude[0] = light.getFloat("altitude");
            actor.actorHeight[0] = light.getFloat("height");
            ecs.x1[slot] = ecs.x4[slot] = x - radius;
            ecs.x2[slot] = ecs.x3[slot] = x + radius;
            ecs.y1[slot] = ecs.y2[slot] = y - radius;
            ecs.y3[slot] = ecs.y4[slot] = y + radius;
            solver.solveVisual(actor, faces, selector, ecs, tiled, map, 0f, 0f);
            Result result = new Result();
            result.candidates = solver.visualCandidateCount;
            result.relations = solver.relationCount;
            result.relationSignature = new StringBuilder();
            for (int i = 0; i < solver.relationCount; i++) {
                int anchor = solver.relationAnchorIndex[i], face = solver.relationFaceIndex[i];
                result.relationSignature.append('/').append(faces.anchorGx[anchor]).append(',')
                        .append(faces.anchorGy[anchor]).append(':')
                        .append(faces.faceStructureId[face]).append(':')
                        .append(faces.faceCompiledIndex[face]).append(':')
                        .append(solver.relationType[i]).append('/');
            }
            if (finiteOnly) {
                // Diagnostic counterfactual only: production intentionally uses infinite lines.
                int write = 0;
                for (int i = 0; i < solver.relationCount; i++) {
                    int face = solver.relationFaceIndex[i];
                    if (x + actor.actorCircleRadius[0] < faces.screenMinX[face]
                            || x - actor.actorCircleRadius[0] > faces.screenMaxX[face]) continue;
                    solver.relationFaceIndex[write] = solver.relationFaceIndex[i];
                    solver.relationAnchorIndex[write] = solver.relationAnchorIndex[i];
                    solver.relationMembershipIndex[write] = solver.relationMembershipIndex[i];
                    solver.relationType[write++] = solver.relationType[i];
                }
                solver.relationCount = solver.actorRelationCount[0] = write;
            }
            planner.begin(actor, snapshot.actorOriginalBucket, snapshot.bucketCount);
            planner.addRelations(actor, faces, solver, 8);
            planner.finish(actor);
            composer.compose(draw, actor, planner, snapshot);
            result.lower = planner.actorLowerBound[0];
            result.upper = planner.actorUpperBound[0];
            result.original = planner.actorOriginalBucket[0];
            result.bucket = planner.actorBucket[0];
            result.conflicts = planner.unresolvedConstraintCount();
            result.orderingFallbacks = planner.actorOrderingFallbackCount();
            result.finalDraw = planner.finalActorDrawIndex[0];
            return result;
        }

        boolean isOutsideFiniteFace(int number, int compiledFace) {
            JsonValue light = light(number);
            float x = light.getFloat("x");
            float radius = light.getFloat("footprintRadiusM") * root.getFloat("pixelsPerMeter");
            for (int face = 0; face < faces.faceCount; face++) {
                if (faces.faceStructureId[face] != 6 || faces.faceCompiledIndex[face] != compiledFace)
                    continue;
                return x + radius < faces.screenMinX[face]
                        || x - radius > faces.screenMaxX[face];
            }
            throw new AssertionError("Missing compiled face " + compiledFace);
        }

        int before(int gx, int gy) { return faces.anchorBeforeBucket[faces.anchorForCell(gx, gy)]; }
        int after(int gx, int gy) { return faces.anchorAfterBucket[faces.anchorForCell(gx, gy)]; }
        void setCandidateVisible(int gx, int gy, boolean visible) {
            tiled.visible[map.tiledRenderRefForTile(gx, gy)] = visible;
        }

        JsonValue light(int number) {
            for (JsonValue light = root.get("lights").child; light != null; light = light.next)
                if (light.getString("name").equals("diagnostic-" + number)) return light;
            throw new AssertionError("Missing diagnostic-" + number);
        }
    }

    private static JsonValue readFixture() {
        InputStream in = SpatialFourLightsDiagnosticRegressionTest.class.getResourceAsStream(
                "/spatial/four-point-lights-diagnostic.json");
        Assert.assertNotNull(in);
        try (InputStream stream = in) {
            return new JsonReader().parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("Cannot load spatial diagnostic fixture", e);
        }
    }

    private static final class Result {
        int candidates, relations, lower, upper, original, bucket, conflicts, orderingFallbacks, finalDraw;
        StringBuilder relationSignature;

        boolean hasRelation(int gx, int gy, int structure, int face, byte type) {
            return relationSignature.indexOf("/" + gx + "," + gy + ":" + structure + ":" + face
                    + ":" + type + "/") >= 0;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Result)) return false;
            Result r = (Result) other;
            return candidates == r.candidates && relations == r.relations && lower == r.lower
                    && upper == r.upper && original == r.original && bucket == r.bucket
                    && conflicts == r.conflicts && orderingFallbacks == r.orderingFallbacks
                    && finalDraw == r.finalDraw
                    && relationSignature.toString().equals(r.relationSignature.toString());
        }

        @Override public int hashCode() { return bucket; }
    }
}
