package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TileChunk;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SpatialCompiledFacePipelineTest {
    @Test
    public void rectangleFacesOwnOnlyTheirBoundaryCells() {
        Array<SpatialBlockData> walls = new Array<>(SpatialBlockData[]::new);
        walls.add(wall(7, 1, 0f, 0f, 2f, 2f, cells(0, 0, 2, 2)));
        CompiledSpatialStructure.FaceSet faces = SpatialStructureCompiler.compile(walls, 1).actorOccluder();
        Assert.assertEquals(4, faces.faceCount());
        assertCells(faces, face(faces, CompiledSpatialStructure.MIN_X), 0, 0, 0, 1);
        assertCells(faces, face(faces, CompiledSpatialStructure.MAX_X), 1, 0, 1, 1);
        assertCells(faces, face(faces, CompiledSpatialStructure.MIN_Y), 0, 0, 1, 0);
        assertCells(faces, face(faces, CompiledSpatialStructure.MAX_Y), 0, 1, 1, 1);
    }

    @Test
    public void mergedFaceUnionsAndDeduplicatesLocalSupportIndependentOfWallOrder() {
        SpatialBlockData first = wall(20, 4, 0f, 0f, 2f, 1f, cells(0, 0, 2, 1));
        SpatialBlockData second = wall(10, 4, 1f, 0f, 2f, 1f, cells(1, 0, 2, 1));
        Array<SpatialBlockData> a = new Array<>(SpatialBlockData[]::new);
        a.add(first); a.add(second);
        Array<SpatialBlockData> b = new Array<>(SpatialBlockData[]::new);
        b.add(second); b.add(first);
        CompiledSpatialStructure.FaceSet one = SpatialStructureCompiler.compile(a, 4).actorOccluder();
        CompiledSpatialStructure.FaceSet two = SpatialStructureCompiler.compile(b, 4).actorOccluder();
        assertEquivalent(one, two);
        assertCells(one, face(one, CompiledSpatialStructure.MIN_Y), 0, 0, 1, 0, 2, 0);
    }

    @Test
    public void oneLinkedCellPerpendicularBranchesKeepFaceSupportInAllDirections() {
        assertOneCellBranch(
                wall(1, 8, 0f, .4f, 3f, .2f, cells(0, 0, 3, 1)),
                wall(2, 8, 1.4f, 0f, .2f, 1f, cells(1, 0, 1, 1)));
        assertOneCellBranch(
                wall(1, 8, 0f, .4f, 3f, .2f, cells(0, 0, 3, 1)),
                wall(2, 8, 1.4f, 0f, .2f, .5f, cells(1, 0, 1, 1)));
        assertOneCellBranch(
                wall(1, 8, .4f, 0f, .2f, 3f, cells(0, 0, 1, 3)),
                wall(2, 8, 0f, 1.4f, 1f, .2f, cells(0, 1, 1, 1)));
        assertOneCellBranch(
                wall(1, 8, .4f, 0f, .2f, 3f, cells(0, 0, 1, 3)),
                wall(2, 8, 0f, 1.4f, .5f, .2f, cells(0, 1, 1, 1)));
    }

    @Test
    public void nestedClosedStructureRelationsIgnoreDistantWallsAndAuthoredOrder() {
        SpatialBlockData[] inner = ring(10, 100, 2, 2, 4, 4);
        SpatialBlockData[] outer = ring(20, 200, 0, 0, 8, 8);
        SpatialBlocksComponent baseline = component(join(inner, outer));
        TiledMapLayerData map = map();
        int[] expected = innerRelationSignature(baseline, map);

        SpatialBlockData distant = wall(300, 30, 6f, 6f, 1f, 1f, cells(6, 6, 1, 1));
        SpatialBlockData[] reordered = new SpatialBlockData[inner.length + outer.length + 1];
        int at = 0;
        reordered[at++] = distant;
        for (int i = outer.length - 1; i >= 0; i--) reordered[at++] = outer[i];
        for (int i = inner.length - 1; i >= 0; i--) reordered[at++] = inner[i];
        SpatialBlocksComponent changed = component(reordered);
        int[] actual = innerRelationSignature(changed, map);

        Assert.assertArrayEquals(expected, actual);
        Assert.assertArrayEquals(exactPlanningSignature(baseline, map), exactPlanningSignature(changed, map));
        Assert.assertArrayEquals(tileOrderSignature(baseline, map), tileOrderSignature(changed, map));
        int relations = 0;
        for (int i = 0; i < expected.length; i++) relations += Integer.bitCount(expected[i]);
        Assert.assertTrue(relations > 0);
    }

    @Test
    public void nestedClosedStructuresProduceNoResidualExactAnchorContradiction() {
        SpatialBlocksComponent component = component(join(ring(10, 100, 2, 2, 4, 4),
                ring(20, 200, 0, 0, 8, 8)));
        TiledMapLayerData map = map();
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(component);
        SpatialProjectedFaceCache projected = new SpatialProjectedFaceCache();
        projected.ensure(compiled, map);

        int tileCount = map.mapWidth * map.mapHeight;
        int[] refToDraw = new int[tileCount];
        Arrays.fill(refToDraw, -1);
        int draw = 0;
        for (int diagonal = map.mapWidth + map.mapHeight - 2; diagonal >= 0; diagonal--) {
            for (int gx = 0; gx < map.mapWidth; gx++) {
                int gy = diagonal - gx;
                if (gy < 0 || gy >= map.mapHeight) continue;
                refToDraw[map.tiledRenderRefForTile(gx, gy)] = draw++;
            }
        }
        int[] before = new int[tileCount];
        int[] after = new int[tileCount];
        for (int i = 0; i < tileCount; i++) { before[i] = i; after[i] = i + 1; }
        new SpatialFaceAnchorResolver().resolve(projected, refToDraw, before, after, tileCount);

        float[] grid = {3f, 3f, 4f, 3f, 5f, 3f, 3f, 4f, 4f, 4f, 5f, 4f, 3f, 5f, 4f, 5f, 5f, 5f};
        int actorCount = grid.length / 2;
        SpatialActorCollector actors = projectedActors(map, grid);
        SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
        solver.solve(actors, projected);
        int[] originals = new int[actorCount];
        Arrays.fill(originals, tileCount / 2);
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, originals, tileCount + 1);
        planner.addRelations(actors, projected, solver);
        planner.finish(actors);

        Assert.assertEquals(0, planner.unresolvedConstraintCount());
    }

    @Test
    public void projectionIsRevisionDrivenAndStoresLeftToRightLine() {
        SpatialBlocksComponent component = component(wall(1, 1, .25f, .5f, 1.5f, 1f, cells(0, 0, 2, 2)));
        TiledMapLayerData map = map();
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        SpatialProjectedFaceCache projected = new SpatialProjectedFaceCache();
        Assert.assertTrue(compiled.ensure(component));
        Assert.assertTrue(projected.ensure(compiled, map));
        int revision = projected.revision();
        int projections = projected.projectionCount();
        Assert.assertFalse(projected.ensure(compiled, map));
        Assert.assertEquals(revision, projected.revision());
        Assert.assertEquals(projections, projected.projectionCount());
        for (int face = 0; face < projected.faceCount; face++) {
            Assert.assertTrue(projected.screenMinX[face] < projected.screenMaxX[face]);
            Assert.assertTrue(Float.isFinite(projected.slope[face]));
            Assert.assertTrue(Float.isFinite(projected.intercept[face]));
        }
        map.originX += 3f;
        Assert.assertTrue(projected.ensure(compiled, map));
        Assert.assertEquals(projections + 1, projected.projectionCount());
    }

    @Test
    public void projectedLayerUsesOneSortedCanonicalAnchorPerCellAndSharedFacesReuseIt() {
        SpatialBlocksComponent component = component(wall(1, 1, 0f, 0f, 2f, 2f, cells(0, 0, 2, 2)));
        TiledMapLayerData map = map();
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(component);
        SpatialProjectedFaceCache projected = new SpatialProjectedFaceCache();
        projected.ensure(compiled, map);

        Assert.assertEquals(4, projected.anchorCount);
        int[][] expected = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
        int sharedAnchor = -1;
        int sharedMemberships = 0;
        for (int anchor = 0; anchor < projected.anchorCount; anchor++) {
            Assert.assertArrayEquals(expected[anchor], new int[]{projected.anchorGx[anchor], projected.anchorGy[anchor]});
            Assert.assertEquals(map.tiledRenderRefForTile(expected[anchor][0], expected[anchor][1]),
                    projected.anchorTiledRef[anchor]);
            if (expected[anchor][0] == 0 && expected[anchor][1] == 0) sharedAnchor = anchor;
        }
        for (int face = 0; face < projected.faceCount; face++) {
            int start = projected.faceAnchorIndexStart[face];
            int end = start + projected.faceAnchorIndexCount[face];
            int previous = -1;
            for (int membership = start; membership < end; membership++) {
                int anchor = projected.faceAnchorIndices[membership];
                Assert.assertNotEquals("duplicate face membership", previous, anchor);
                if (anchor == sharedAnchor) sharedMemberships++;
                previous = anchor;
            }
        }
        Assert.assertEquals(2, sharedMemberships);
    }

    @Test
    public void faceAnchorsUseResolvedSubsetAndSolverEvaluatesEveryFace() {
        SpatialBlocksComponent component = component(wall(1, 1, 0f, 0f, 2f, 2f, cells(0, 0, 2, 2)));
        TiledMapLayerData map = map();
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(component);
        SpatialProjectedFaceCache projected = new SpatialProjectedFaceCache();
        projected.ensure(compiled, map);
        int[] refs = new int[map.mapWidth * map.mapHeight];
        Arrays.fill(refs, -1);
        int firstRef = map.tiledRenderRefForTile(0, 0);
        refs[firstRef] = 0;
        new SpatialFaceAnchorResolver().resolve(projected, refs, new int[]{1}, new int[]{2}, 1);
        int resolved = 0;
        for (int anchor = 0; anchor < projected.anchorCount; anchor++) if (projected.anchorResolved[anchor]) resolved++;
        Assert.assertEquals(1, resolved);

        SpatialActorCollector actors = new SpatialActorCollector();
        actors.actorCount = 1;
        actors.actorCircleX = new float[]{map.tileWidth * .5f};
        actors.actorCircleY = new float[]{0f};
        actors.actorAltitude = new float[]{0f};
        actors.actorHeight = new float[]{32f};
        SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
        solver.solve(actors, projected);
        Assert.assertTrue(solver.relationCount >= 1);
        for (int i = 1; i < solver.relationCount; i++) {
            Assert.assertTrue(solver.relationFaceIndex[i - 1] < solver.relationFaceIndex[i]);
        }
    }

    private static TiledMapLayerData map() {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 64, 32, 4, SceneMetaRuntime.TiledProjection.ISO);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) map.setTile(x, y, 1);
        int nextRef = 0;
        for (int cy = 0; cy < 2; cy++) for (int cx = 0; cx < 2; cx++) {
            TileChunk chunk = map.getChunk(cx, cy);
            chunk.renderRefStartIndex = nextRef;
            chunk.renderRefCount = chunk.cellCount();
            nextRef += chunk.cellCount();
        }
        return map;
    }

    private static SpatialBlocksComponent component(SpatialBlockData... walls) {
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        for (int i = 0; i < walls.length; i++) component.blocks.add(walls[i]);
        component.revision = 1;
        return component;
    }

    private static void assertOneCellBranch(SpatialBlockData host, SpatialBlockData branch) {
        Array<SpatialBlockData> forward = new Array<>(SpatialBlockData[]::new);
        forward.add(host); forward.add(branch);
        Array<SpatialBlockData> reverse = new Array<>(SpatialBlockData[]::new);
        reverse.add(branch); reverse.add(host);
        CompiledSpatialStructure.FaceSet one = SpatialStructureCompiler.compile(forward, 8).actorOccluder();
        CompiledSpatialStructure.FaceSet two = SpatialStructureCompiler.compile(reverse, 8).actorOccluder();
        assertEquivalent(one, two);
        for (int face = 0; face < one.faceCount(); face++) {
            Assert.assertTrue("face " + face + " is missing its local anchor", one.anchorCellCount(face) > 0);
        }
        SpatialBlocksComponent component = component(host, branch);
        TiledMapLayerData map = map();
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(component);
        SpatialTileOrderCache order = new SpatialTileOrderCache();
        order.ensure(8, map, component, compiled);
        Assert.assertEquals(0, order.tileOrderCycleCount);
    }

    private static int[] innerRelationSignature(SpatialBlocksComponent component, TiledMapLayerData map) {
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(component);
        SpatialProjectedFaceCache projected = new SpatialProjectedFaceCache();
        projected.ensure(compiled, map);
        for (int anchor = 0; anchor < projected.anchorCount; anchor++) projected.anchorResolved[anchor] = true;

        float[] grid = new float[]{1.75f,1.75f, 2.25f,1.75f, 5.75f,1.75f, 6.25f,2.25f,
                6.25f,5.75f, 5.75f,6.25f, 2.25f,6.25f, 1.75f,5.75f, 4f,4f};
        int actorCount = grid.length / 2;
        SpatialActorCollector actors = projectedActors(map, grid);
        SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
        solver.solve(actors, projected);
        int[] signature = new int[actorCount];
        for (int actor = 0; actor < actorCount; actor++) {
            int start = solver.actorRelationStart[actor];
            int end = start + solver.actorRelationCount[actor];
            for (int relation = start; relation < end; relation++) {
                int face = solver.relationFaceIndex[relation];
                if (projected.faceStructureId[face] != 10) continue;
                int bit = face * 2 + (solver.relationType[relation]
                        == SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE ? 1 : 0);
                signature[actor] |= 1 << bit;
            }
        }
        return signature;
    }

    private static int[] exactPlanningSignature(SpatialBlocksComponent component, TiledMapLayerData map) {
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(component);
        SpatialProjectedFaceCache projected = new SpatialProjectedFaceCache();
        projected.ensure(compiled, map);
        int tileCount = map.mapWidth * map.mapHeight;
        int[] refToDraw = new int[tileCount];
        Arrays.fill(refToDraw, -1);
        int draw = 0;
        for (int diagonal = map.mapWidth + map.mapHeight - 2; diagonal >= 0; diagonal--) {
            for (int gx = 0; gx < map.mapWidth; gx++) {
                int gy = diagonal - gx;
                if (gy >= 0 && gy < map.mapHeight) {
                    refToDraw[map.tiledRenderRefForTile(gx, gy)] = draw++;
                }
            }
        }
        int[] before = new int[tileCount];
        int[] after = new int[tileCount];
        for (int i = 0; i < tileCount; i++) { before[i] = i; after[i] = i + 1; }
        new SpatialFaceAnchorResolver().resolve(projected, refToDraw, before, after, tileCount);

        float[] grid = {4f, 4f};
        SpatialActorCollector actors = projectedActors(map, grid);
        SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
        solver.solve(actors, projected);
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, new int[]{tileCount / 2}, tileCount + 1);
        planner.addRelations(actors, projected, solver);
        planner.finish(actors);
        return new int[]{planner.unresolvedConstraintCount(), planner.actorLowerBound[0],
                planner.actorUpperBound[0], planner.actorBucket[0]};
    }

    private static int[] tileOrderSignature(SpatialBlocksComponent component, TiledMapLayerData map) {
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(component);
        SpatialTileOrderCache order = new SpatialTileOrderCache();
        order.ensure(1, map, component, compiled);
        int[] ranks = new int[map.mapWidth * map.mapHeight];
        for (int gy = 0; gy < map.mapHeight; gy++) {
            for (int gx = 0; gx < map.mapWidth; gx++) ranks[gy * map.mapWidth + gx] = order.rank(gx, gy);
        }
        Assert.assertEquals(0, order.tileOrderCycleCount);
        return ranks;
    }

    private static SpatialActorCollector projectedActors(TiledMapLayerData map, float[] grid) {
        int actorCount = grid.length / 2;
        SpatialActorCollector actors = new SpatialActorCollector();
        actors.actorCount = actorCount;
        actors.actorSlot = new int[actorCount];
        actors.actorEntityId = new int[actorCount];
        actors.actorStableOrder = new int[actorCount];
        actors.actorDrawIndex = new int[actorCount];
        actors.actorCircleX = new float[actorCount];
        actors.actorCircleY = new float[actorCount];
        actors.actorAltitude = new float[actorCount];
        actors.actorHeight = new float[actorCount];
        float[] point = new float[2];
        for (int actor = 0; actor < actorCount; actor++) {
            map.projectSpatialPoint(grid[actor * 2], grid[actor * 2 + 1], 0f, point, 0);
            actors.actorSlot[actor] = actor;
            actors.actorEntityId[actor] = actor;
            actors.actorStableOrder[actor] = actor;
            actors.actorDrawIndex[actor] = actor;
            actors.actorCircleX[actor] = point[0];
            actors.actorCircleY[actor] = point[1];
            actors.actorHeight[actor] = 64f;
        }
        return actors;
    }

    private static SpatialBlockData[] ring(int structure, int firstId, int x, int y, int width, int height) {
        return new SpatialBlockData[]{
                wall(firstId, structure, x, y, width, 1f, cells(x, y, width, 1)),
                wall(firstId + 1, structure, x, y + height - 1, width, 1f, cells(x, y + height - 1, width, 1)),
                wall(firstId + 2, structure, x, y, 1f, height, cells(x, y, 1, height)),
                wall(firstId + 3, structure, x + width - 1, y, 1f, height, cells(x + width - 1, y, 1, height))
        };
    }

    private static SpatialBlockData[] join(SpatialBlockData[] first, SpatialBlockData[] second) {
        SpatialBlockData[] result = new SpatialBlockData[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static SpatialBlockData wall(int id, int structure, float x, float y, float width, float depth, int[] cells) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id; wall.structureId = structure; wall.x = x; wall.y = y; wall.width = width; wall.depth = depth;
        wall.height = 64f; wall.actorOccluder = true; wall.beginAuthoredLinkedTileRefs();
        for (int i = 0; i < cells.length; i += 2) wall.addLinkedTileRef(cells[i], cells[i + 1], 99);
        return wall;
    }

    private static int[] cells(int minX, int minY, int width, int height) {
        int[] out = new int[width * height * 2]; int at = 0;
        for (int y = minY; y < minY + height; y++) for (int x = minX; x < minX + width; x++) { out[at++] = x; out[at++] = y; }
        return out;
    }

    private static int face(CompiledSpatialStructure.FaceSet faces, byte orientation) {
        for (int i = 0; i < faces.faceCount(); i++) if (faces.orientation(i) == orientation) return i;
        throw new AssertionError("missing face " + orientation);
    }

    private static void assertCells(CompiledSpatialStructure.FaceSet faces, int face, int... expected) {
        Assert.assertEquals(expected.length / 2, faces.anchorCellCount(face));
        int start = faces.anchorCellStart(face);
        for (int i = 0; i < expected.length; i += 2) {
            Assert.assertEquals(expected[i], faces.anchorGx(start + i / 2));
            Assert.assertEquals(expected[i + 1], faces.anchorGy(start + i / 2));
        }
    }

    private static void assertEquivalent(CompiledSpatialStructure.FaceSet a, CompiledSpatialStructure.FaceSet b) {
        Assert.assertEquals(a.faceCount(), b.faceCount());
        Assert.assertEquals(a.anchorCellTotal(), b.anchorCellTotal());
        for (int i = 0; i < a.faceCount(); i++) {
            Assert.assertEquals(a.orientation(i), b.orientation(i));
            Assert.assertEquals(a.constantCoordinate(i), b.constantCoordinate(i), 0f);
            Assert.assertEquals(a.startCoordinate(i), b.startCoordinate(i), 0f);
            Assert.assertEquals(a.endCoordinate(i), b.endCoordinate(i), 0f);
            Assert.assertEquals(a.anchorCellStart(i), b.anchorCellStart(i));
            Assert.assertEquals(a.anchorCellCount(i), b.anchorCellCount(i));
        }
        for (int i = 0; i < a.anchorCellTotal(); i++) { Assert.assertEquals(a.anchorGx(i), b.anchorGx(i)); Assert.assertEquals(a.anchorGy(i), b.anchorGy(i)); }
    }
}
