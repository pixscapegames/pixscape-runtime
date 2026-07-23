package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.spatial.SpatialShapeData;
import games.pixscape.runtime.component.SpatialShapesComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import com.badlogic.gdx.utils.IntArray;
import org.junit.Assert;
import org.junit.Test;

public class SpatialQueryServiceTest {

    @Test
    public void volumeVerticalOverlapReturnsTrueForSharedHeightRange() {
        SpatialVolume wall = volume(0f, 3f, 0f, 0f, 10f, 10f);
        SpatialVolume actor = volume(0f, 2f, 1f, 1f, 2f, 2f);

        Assert.assertTrue(wall.verticalOverlaps(actor));
        Assert.assertTrue(actor.verticalOverlaps(wall));
    }

    @Test
    public void actorAboveWallDoesNotVerticallyOverlap() {
        SpatialQueryService service = new SpatialQueryService();
        SpatialVolume wall = volume(0f, 3f, 0f, 0f, 10f, 10f);
        SpatialVolume actor = volume(4f, 2f, 1f, 1f, 2f, 2f);

        Assert.assertFalse(actor.verticalOverlaps(wall));
        Assert.assertTrue(service.isAbove(actor, wall));
        Assert.assertEquals(SpatialRelation.ABOVE, service.relation(actor, wall));
    }

    @Test
    public void partialVerticalOverlapIsReportedAsPossiblePartialOcclusion() {
        SpatialQueryService service = new SpatialQueryService();
        SpatialVolume wall = volume(0f, 3f, 0f, 0f, 10f, 10f);
        SpatialVolume actor = volume(2f, 2f, 1f, 1f, 2f, 2f);

        Assert.assertTrue(actor.verticalOverlaps(wall));

        SpatialOcclusionResult result = service.actorOcclusion(actor, wall, true, null);
        Assert.assertTrue(result.occluded);
        Assert.assertTrue(result.partiallyOccluded);
        Assert.assertEquals(0f, result.occluderBottom, 0.0001f);
        Assert.assertEquals(3f, result.occluderTop, 0.0001f);
        Assert.assertEquals(2f, result.actorBottom, 0.0001f);
        Assert.assertEquals(4f, result.actorTop, 0.0001f);
    }

    @Test
    public void footprintIntersectionDistinguishesOverlapAndSeparation() {
        SpatialVolume a = volume(0f, 1f, 0f, 0f, 10f, 10f);
        SpatialVolume overlap = volume(0f, 1f, 5f, 5f, 12f, 12f);
        SpatialVolume separated = volume(0f, 1f, 11f, 11f, 20f, 20f);

        Assert.assertTrue(a.footprintIntersects(overlap));
        Assert.assertFalse(a.footprintIntersects(separated));
    }

    @Test
    public void missingSpatialHeightDefaultsToZeroHeightVolume() {
        SpatialQueryService service = new SpatialQueryService();
        TransformComponent transform = new TransformComponent();
        transform.x = 4f;
        transform.y = 6f;

        SpatialVolume volume = service.buildEntityVolume(transform, null, null);

        Assert.assertEquals(4f, volume.worldX, 0.0001f);
        Assert.assertEquals(6f, volume.worldY, 0.0001f);
        Assert.assertEquals(0f, volume.altitude, 0.0001f);
        Assert.assertEquals(0f, volume.height, 0.0001f);
        Assert.assertFalse(volume.hasHeight());
    }

    @Test
    public void entityVolumeUsesSpatialShapeFootprintWhenAvailable() {
        SpatialQueryService service = new SpatialQueryService();
        TransformComponent transform = new TransformComponent();
        transform.x = 10f;
        transform.y = 20f;

        SpatialHeightComponent height = new SpatialHeightComponent();
        height.altitude = 1f;
        height.height = 5f;

        SpatialShapesComponent shapes = new SpatialShapesComponent();
        SpatialShapeData shape = new SpatialShapeData();
        shape.collisionEnabled = true;
        shape.halfW = 2f;
        shape.halfH = 3f;
        shapes.shapes.add(shape);

        SpatialVolume volume = service.buildEntityVolume(transform, height, shapes);

        Assert.assertEquals(1f, volume.altitude, 0.0001f);
        Assert.assertEquals(5f, volume.height, 0.0001f);
        Assert.assertEquals(8f, volume.footprintMinX, 0.0001f);
        Assert.assertEquals(17f, volume.footprintMinY, 0.0001f);
        Assert.assertEquals(12f, volume.footprintMaxX, 0.0001f);
        Assert.assertEquals(23f, volume.footprintMaxY, 0.0001f);
    }

    @Test
    public void tiledCellVolumeDefaultsAndExplicitSpatialValues() {
        SpatialQueryService service = new SpatialQueryService();
        TiledMapLayerData map = new TiledMapLayerData(4, 4, 16, 16, 2);
        map.setTile(1, 1, 10);

        SpatialVolume defaultVolume = service.buildTiledCellVolume(map, 1, 1);
        Assert.assertEquals(0f, defaultVolume.altitude, 0.0001f);
        Assert.assertEquals(0f, defaultVolume.height, 0.0001f);
        Assert.assertEquals(0, service.getTileSpatialFlags(map, 1, 1));

        map.setTileSpatial(1, 1, 7f, 12f, 3);

        SpatialVolume explicitVolume = service.buildTiledCellVolume(map, 1, 1);
        Assert.assertEquals(7f, explicitVolume.altitude, 0.0001f);
        Assert.assertEquals(12f, explicitVolume.height, 0.0001f);
        Assert.assertEquals(3, service.getTileSpatialFlags(map, 1, 1));
        Assert.assertEquals(16f, explicitVolume.footprintMinX, 0.0001f);
        Assert.assertEquals(16f, explicitVolume.footprintMinY, 0.0001f);
        Assert.assertEquals(32f, explicitVolume.footprintMaxX, 0.0001f);
        Assert.assertEquals(32f, explicitVolume.footprintMaxY, 0.0001f);
    }

    @Test
    public void actorOccludedByRequiresActorOccluderFlagAndSpatialConditions() {
        SpatialQueryService service = new SpatialQueryService();
        SpatialVolume wall = volume(0f, 3f, 0f, 0f, 10f, 10f);
        SpatialVolume actor = volume(0f, 2f, 5f, 5f, 6f, 6f);

        Assert.assertFalse(service.actorOccludedBy(actor, wall, false));
        Assert.assertTrue(service.actorOccludedBy(actor, wall, true));

        SpatialVolume separatedActor = volume(0f, 2f, 20f, 20f, 21f, 21f);
        Assert.assertFalse(service.actorOccludedBy(separatedActor, wall, true));

        SpatialVolume aboveActor = volume(4f, 2f, 5f, 5f, 6f, 6f);
        Assert.assertFalse(service.actorOccludedBy(aboveActor, wall, true));
    }

    @Test
    public void actorOccluderFlagCanBeReadFromSpatialShapes() {
        SpatialQueryService service = new SpatialQueryService();
        SpatialShapesComponent shapes = new SpatialShapesComponent();

        Assert.assertFalse(service.hasActorOccluder(shapes));

        SpatialShapeData shape = new SpatialShapeData();
        shape.actorOccluder = true;
        shapes.shapes.add(shape);

        Assert.assertTrue(service.hasActorOccluder(shapes));
    }

    @Test
    public void broadphaseGridReturnsOnlyLocalCandidates() {
        SpatialBroadphaseGrid grid = new SpatialBroadphaseGrid(16f);
        SpatialVolume actor = volume(0f, 2f, 1f, 1f, 2f, 2f);
        SpatialVolume nearWall = volume(0f, 3f, 0f, 0f, 4f, 4f);
        SpatialVolume farWall = volume(0f, 3f, 128f, 128f, 132f, 132f);

        grid.insert(10, nearWall);
        grid.insert(20, farWall);

        IntArray candidates = grid.query(actor, null);

        Assert.assertEquals(1, candidates.size);
        Assert.assertEquals(10, candidates.get(0));
    }

    @Test
    public void broadphaseGridDeduplicatesMultiCellVolumes() {
        SpatialBroadphaseGrid grid = new SpatialBroadphaseGrid(4f);
        SpatialVolume large = volume(0f, 3f, 0f, 0f, 9f, 9f);
        SpatialVolume query = volume(0f, 2f, 1f, 1f, 2f, 2f);

        grid.insert(7, large);

        IntArray candidates = grid.query(query, null);

        Assert.assertEquals(1, candidates.size);
        Assert.assertEquals(7, candidates.get(0));
    }

    @Test
    public void actorOccludedByAnyUsesProvidedCandidateSetOnly() {
        SpatialQueryService service = new SpatialQueryService();

        SpatialVolume actor = volume(0f, 2f, 1f, 1f, 2f, 2f);
        SpatialVolume[] volumes = new SpatialVolume[3];
        boolean[] actorOccluders = new boolean[3];

        volumes[0] = volume(0f, 3f, 100f, 100f, 104f, 104f);
        actorOccluders[0] = true;
        volumes[1] = volume(0f, 3f, 0f, 0f, 4f, 4f);
        actorOccluders[1] = false;
        volumes[2] = volume(0f, 3f, 0f, 0f, 4f, 4f);
        actorOccluders[2] = true;

        IntArray candidates = new IntArray();
        candidates.add(0);
        candidates.add(1);

        Assert.assertFalse(service.actorOccludedByAny(actor, volumes, actorOccluders, candidates));

        candidates.add(2);

        Assert.assertTrue(service.actorOccludedByAny(actor, volumes, actorOccluders, candidates));
    }

    private static SpatialVolume volume(float altitude,
                                        float height,
                                        float minX,
                                        float minY,
                                        float maxX,
                                        float maxY) {
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        return new SpatialVolume().set(cx, cy, altitude, height, minX, minY, maxX, maxY);
    }
}
