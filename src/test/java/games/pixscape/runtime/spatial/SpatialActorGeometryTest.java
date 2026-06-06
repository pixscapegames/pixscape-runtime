package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TransformComponent;
import org.junit.Assert;
import org.junit.Test;

public class SpatialActorGeometryTest {
    @Test
    public void spatialHeightFootprintDefaultsAreStable() {
        SpatialHeightComponent height = new SpatialHeightComponent();

        Assert.assertEquals(0f, height.footprintOffsetX, 0.0001f);
        Assert.assertEquals(0f, height.footprintOffsetY, 0.0001f);
        Assert.assertEquals(32f, height.footprintWidth, 0.0001f);
        Assert.assertEquals(16f, height.footprintDepth, 0.0001f);
    }

    @Test
    public void computesFootAndAxisAlignedFootprintFromFootPoint() {
        TransformComponent transform = new TransformComponent();
        transform.x = 100f;
        transform.y = 80f;
        transform.originX = 8f;
        transform.originY = 12f;
        transform.scaleX = 2f;
        transform.scaleY = 0.5f;

        SpatialHeightComponent height = new SpatialHeightComponent();
        height.altitude = 3f;
        height.height = 7f;
        height.footprintOffsetX = 4f;
        height.footprintOffsetY = -2f;
        height.footprintWidth = 20f;
        height.footprintDepth = 10f;

        SpatialActorGeometry.Footprint out = new SpatialActorGeometry.Footprint();
        Assert.assertTrue(SpatialActorGeometry.writeFootprint(transform, height, out));

        Assert.assertEquals(84f, out.footX, 0.0001f);
        Assert.assertEquals(74f, out.footY, 0.0001f);
        Assert.assertEquals(78f, out.minX, 0.0001f);
        Assert.assertEquals(98f, out.maxX, 0.0001f);
        Assert.assertEquals(67f, out.minY, 0.0001f);
        Assert.assertEquals(77f, out.maxY, 0.0001f);
        Assert.assertEquals(3f, out.bottom, 0.0001f);
        Assert.assertEquals(10f, out.top, 0.0001f);
    }

    @Test
    public void nonPositiveFootprintFallsBackToFootPointSafely() {
        TransformComponent transform = new TransformComponent();
        transform.x = 10f;
        transform.y = 20f;

        SpatialHeightComponent height = new SpatialHeightComponent();
        height.footprintWidth = 0f;
        height.footprintDepth = -1f;

        SpatialActorGeometry.Footprint out = new SpatialActorGeometry.Footprint();
        Assert.assertFalse(SpatialActorGeometry.writeFootprint(transform, height, out));
        Assert.assertTrue(out.pointOnly);
        Assert.assertEquals(10f, out.minX, 0.0001f);
        Assert.assertEquals(10f, out.maxX, 0.0001f);
        Assert.assertEquals(20f, out.minY, 0.0001f);
        Assert.assertEquals(20f, out.maxY, 0.0001f);
    }

    @Test
    public void negativeScaleKeepsFootPointFormulaAndSensibleFootprint() {
        TransformComponent transform = new TransformComponent();
        transform.x = 30f;
        transform.y = 40f;
        transform.originX = 5f;
        transform.originY = 6f;
        transform.scaleX = -2f;
        transform.scaleY = -3f;

        SpatialHeightComponent height = new SpatialHeightComponent();
        SpatialActorGeometry.Footprint out = new SpatialActorGeometry.Footprint();

        Assert.assertTrue(SpatialActorGeometry.writeFootprint(transform, height, out));
        Assert.assertEquals(40f, out.footX, 0.0001f);
        Assert.assertEquals(58f, out.footY, 0.0001f);
        Assert.assertEquals(24f, out.minX, 0.0001f);
        Assert.assertEquals(56f, out.maxX, 0.0001f);
        Assert.assertEquals(50f, out.minY, 0.0001f);
        Assert.assertEquals(66f, out.maxY, 0.0001f);
    }
}
