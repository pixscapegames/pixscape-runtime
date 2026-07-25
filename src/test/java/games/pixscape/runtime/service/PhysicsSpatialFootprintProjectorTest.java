package games.pixscape.runtime.service;

import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsSpatialFootprintProjectorTest {
    @Test
    public void multipleCircleBodyUsesIsolatedTemporaryProjectionPolicy() {
        PhysicsShapesComponent sources = new PhysicsShapesComponent();
        PhysicsShapeData sensor = circle(1, 0.25f, 1f, 2f);
        sensor.sensor = true;
        sources.add(sensor);
        sources.add(circle(2, 0.75f, 3f, 4f));

        PhysicsSpatialFootprintProjector projector =
                new PhysicsSpatialFootprintProjector();
        PhysicsSpatialFootprintProjector.Projection projection =
                projector.prepare(compiled(sources), 7, 100f);
        SpatialPhysicsFootprintComponent target =
                new SpatialPhysicsFootprintComponent();

        projector.publish(target, projection);

        Assert.assertTrue(target.valid);
        Assert.assertEquals(75f, target.radiusPx, 0f);
        Assert.assertEquals(300f, target.localOffsetXPx, 0f);
        Assert.assertEquals(400f, target.localOffsetYPx, 0f);
        Assert.assertEquals(7, target.physicsGeneration);
    }

    @Test
    public void bodyWithoutCirclePublishesInvalidSpatialFootprint() {
        PhysicsShapesComponent sources = new PhysicsShapesComponent();
        PhysicsShapeData box = new PhysicsShapeData();
        box.directGeometry = new PhysicsDirectGeometryData();
        box.physicsShapeId = 1;
        box.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_BOX;
        box.directGeometry.halfWidth = 1f;
        box.directGeometry.halfHeight = 1f;
        sources.add(box);

        PhysicsSpatialFootprintProjector projector =
                new PhysicsSpatialFootprintProjector();
        SpatialPhysicsFootprintComponent target =
                new SpatialPhysicsFootprintComponent();

        projector.publish(target, projector.prepare(compiled(sources), 3, 100f));

        Assert.assertFalse(target.valid);
        Assert.assertEquals(0f, target.radiusPx, 0f);
        Assert.assertEquals(3, target.physicsGeneration);
    }

    private static PhysicsShapeData circle(
            int physicsShapeId,
            float radius,
            float offsetX,
            float offsetY) {
        PhysicsShapeData circle = new PhysicsShapeData();
        circle.directGeometry = new PhysicsDirectGeometryData();
        circle.physicsShapeId = physicsShapeId;
        circle.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_CIRCLE;
        circle.directGeometry.radius = radius;
        circle.directGeometry.offsetX = offsetX;
        circle.directGeometry.offsetY = offsetY;
        return circle;
    }

    private static com.badlogic.gdx.utils.Array<games.pixscape.runtime.physics.CompiledFixtureData>
    compiled(PhysicsShapesComponent sources) {
        return PhysicsService.prepareBodyCandidate(sources.shapes)
                .takeCompiledFixtures()
                .takeFixtures();
    }
}
