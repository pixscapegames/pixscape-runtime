package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeResolver;
import games.pixscape.runtime.physics.ResolvedPhysicsShape;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

public class SpatialMapPlaneProjectionTest {
    @Test
    public void absoluteAltitudesProjectRelativeToDrawnPlaneForIsoAndOrtho() {
        float[][] cases = {{0, 0, 0}, {128, 128, 0}, {256, 256, 0},
                {128, 160, 32}, {128, 96, -32}};
        for (TiledProjection projection : TiledProjection.values()) {
            TiledMapLayerData map = map(projection);
            for (float[] c : cases) {
                map.defaultTileAltitude = c[0];
                float[] cell = new float[8], base = new float[2], top = new float[2];
                map.tileToCellVertices(27, 18, cell);
                map.projectSpatialPoint(27, 18, c[1], base, 0);
                map.projectSpatialPoint(27, 18, c[1] + 128, top, 0);
                Assert.assertEquals(cell[0], base[0], 0);
                Assert.assertEquals(cell[1] + c[2], base[1], 0);
                Assert.assertEquals(base[1] + 128, top[1], 0);
            }
        }
    }

    @Test
    public void changingOnlyMapPlaneReprojectsFacesAndOrderWithoutRecompilingAbsoluteStructures() {
        TiledMapLayerData map = map(TiledProjection.ISO);
        map.setTile(27, 18, 1);
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1; block.structureId = 1;
        block.x = 27; block.y = 18; block.width = 1; block.depth = 1;
        block.altitude = 160; block.height = 128;
        SpatialBlockData.LinkedTileRef ref = new SpatialBlockData.LinkedTileRef();
        ref.gx = 27; ref.gy = 18; ref.tileAssetId = 1;
        block.linkedTileRefs.add(ref);
        blocks.blocks.add(block); blocks.nextSpatialBlockId = 2;
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        SpatialTileOrderCache order = new SpatialTileOrderCache();
        compiled.ensure(blocks);
        map.defaultTileAltitude = 128;
        Assert.assertTrue(faces.ensure(compiled, map));
        Assert.assertTrue(order.ensure(1, map, blocks, compiled));
        Assert.assertTrue(faces.faceCount > 0);
        float intercept = faces.intercept[0];
        int rank = order.rank(27, 18), revision = compiled.revision();
        Assert.assertFalse(faces.ensure(compiled, map));
        Assert.assertFalse(order.ensure(1, map, blocks, compiled));
        map.defaultTileAltitude = 256;
        Assert.assertFalse(compiled.ensure(blocks));
        Assert.assertEquals(revision, compiled.revision());
        Assert.assertTrue(faces.ensure(compiled, map));
        Assert.assertTrue(order.ensure(1, map, blocks, compiled));
        Assert.assertEquals(intercept - 128, faces.intercept[0], .001f);
        Assert.assertEquals(160, faces.faceAltitude[0], 0);
        Assert.assertEquals(128, faces.faceHeight[0], 0);
        Assert.assertEquals(rank, order.rank(27, 18));
        Assert.assertEquals(160, block.altitude, 0);
    }

    @Test
    public void linkedPhysicsUsesSamePlaneWithoutChangingAbsoluteBlockAltitude() {
        TiledMapLayerData map = map(TiledProjection.ISO);
        map.defaultTileAltitude = 128;
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1; block.x = 27; block.y = 18;
        block.width = 1; block.depth = 2; block.altitude = 128; block.height = 128;
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = 1; shape.spatialBlockId = 1;
        ResolvedPhysicsShape resolved = new PhysicsShapeResolver().resolveLinked(
                shape, block, map, 0, 0, 0, 100, 1);
        Assert.assertEquals((1280 + map.originX) / 100, resolved.polygonVertices[0], .001f);
        Assert.assertEquals((2880 + map.originY) / 100, resolved.polygonVertices[1], .001f);
        Assert.assertEquals(128, block.altitude, 0);
    }

    private static TiledMapLayerData map(TiledProjection projection) {
        TiledMapLayerData map = new TiledMapLayerData(50, 50, 256, 128, 16, projection);
        map.originX = 37; map.originY = -83;
        return map;
    }
}
