package games.pixscape.runtime.service;

import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsSpatialFootprintProjectorTest {
    @Test
    public void multipleCircleBodyUsesIsolatedTemporaryProjectionPolicy() {
        PhysicsShapesComponent sources = new PhysicsShapesComponent();
        PhysicsShapeData sensor = circle(1, 0.25f, 1f, 2f);
        sensor.sensor = true;
        sources.shapes.add(sensor);
        sources.shapes.add(circle(2, 0.75f, 3f, 4f));

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
        box.geometry = new PhysicsGeometryData();
        box.physicsShapeId = 1;
        box.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
        box.geometry.halfWidth = 1f;
        box.geometry.halfHeight = 1f;
        sources.shapes.add(box);

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
        circle.geometry = new PhysicsGeometryData();
        circle.physicsShapeId = physicsShapeId;
        circle.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        circle.geometry.radius = radius;
        circle.geometry.offsetX = offsetX;
        circle.geometry.offsetY = offsetY;
        return circle;
    }

    private static com.badlogic.gdx.utils.Array<games.pixscape.runtime.physics.CompiledFixtureData>
    compiled(PhysicsShapesComponent sources) {
        return PhysicsService.prepareBodyCandidate(sources.shapes)
                .takeCompiledFixtures()
                .takeFixtures();
    }
}
