package games.pixscape.runtime.service;

import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.PhysicsBodyCompiler;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedCompiledFixtures;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsSpatialFootprintProjectorTest {
    @Test
    public void multipleCircleBodyUsesIsolatedTemporaryProjectionPolicy() {
        PhysicsShapesComponent sources = new PhysicsShapesComponent();
        sources.add(circle(1, 0.25f, 1f, 2f));
        sources.add(circle(2, 0.75f, 3f, 4f));

        PreparedCompiledFixtures prepared =
                new PhysicsBodyCompiler().compilePrepared(sources);
        PhysicsSpatialFootprintProjector projector =
                new PhysicsSpatialFootprintProjector();
        PhysicsSpatialFootprintProjector.Projection projection =
                projector.prepare(prepared, 7, 100f);
        SpatialPhysicsFootprintComponent target =
                new SpatialPhysicsFootprintComponent();

        projector.publish(target, projection);

        Assert.assertTrue(target.valid);
        Assert.assertEquals(25f, target.radiusPx, 0f);
        Assert.assertEquals(100f, target.localOffsetXPx, 0f);
        Assert.assertEquals(200f, target.localOffsetYPx, 0f);
        Assert.assertEquals(7, target.physicsGeneration);
    }

    @Test
    public void bodyWithoutCirclePublishesInvalidSpatialFootprint() {
        PhysicsShapesComponent sources = new PhysicsShapesComponent();
        PhysicsShapeData box = new PhysicsShapeData();
        box.physicsShapeId = 1;
        box.shapeType = PhysicsShapeData.SHAPE_BOX;
        box.halfWidth = 1f;
        box.halfHeight = 1f;
        sources.add(box);

        PreparedCompiledFixtures prepared =
                new PhysicsBodyCompiler().compilePrepared(sources);
        PhysicsSpatialFootprintProjector projector =
                new PhysicsSpatialFootprintProjector();
        SpatialPhysicsFootprintComponent target =
                new SpatialPhysicsFootprintComponent();

        projector.publish(target, projector.prepare(prepared, 3, 100f));

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
        circle.physicsShapeId = physicsShapeId;
        circle.shapeType = PhysicsShapeData.SHAPE_CIRCLE;
        circle.radius = radius;
        circle.offsetX = offsetX;
        circle.offsetY = offsetY;
        return circle;
    }
}
