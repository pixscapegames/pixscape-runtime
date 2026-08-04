package games.pixscape.runtime.service;

import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsSpatialFootprintProjectorTest {
    @Test
    public void explicitSpatialCircleOverridesEarlierOrdinaryCircle() {
        PhysicsShapesComponent sources = new PhysicsShapesComponent();
        sources.shapes.add(circle(1, 0.25f, 1f, 2f));
        PhysicsShapeData explicit = circle(2, 0.75f, 3f, 4f);
        explicit.spatialFootprint = true;
        sources.shapes.add(explicit);
        sources.shapes.add(circle(3, 1.25f, 5f, 6f));

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
        Assert.assertEquals(2, target.sourcePhysicsShapeId);
        Assert.assertTrue(target.explicitOwnership);
    }

    @Test
    public void legacyBodyWithoutExplicitCircleUsesFirstValidCircle() {
        PhysicsShapesComponent sources = new PhysicsShapesComponent();
        sources.shapes.add(circle(1, 0.25f, 1f, 2f));
        sources.shapes.add(circle(2, 0.75f, 3f, 4f));

        PhysicsSpatialFootprintProjector projector =
                new PhysicsSpatialFootprintProjector();
        SpatialPhysicsFootprintComponent target = new SpatialPhysicsFootprintComponent();
        projector.publish(target, projector.prepare(compiled(sources), 7, 100f));

        Assert.assertTrue(target.valid);
        Assert.assertEquals(25f, target.radiusPx, 0f);
        Assert.assertEquals(1, target.sourcePhysicsShapeId);
        Assert.assertFalse(target.explicitOwnership);
    }

    @Test
    public void multipleExplicitCompiledFixturesPublishInvalidFootprint() {
        com.badlogic.gdx.utils.Array<games.pixscape.runtime.physics.CompiledFixtureData> fixtures =
                new com.badlogic.gdx.utils.Array<>(true, 2,
                        games.pixscape.runtime.physics.CompiledFixtureData.class);
        fixtures.add(compiledCircle(1, 0.5f, true));
        fixtures.add(compiledCircle(2, 0.75f, true));

        PhysicsSpatialFootprintProjector projector =
                new PhysicsSpatialFootprintProjector();
        PhysicsSpatialFootprintProjector.Projection projection =
                projector.prepare(fixtures, 9, 100f);
        SpatialPhysicsFootprintComponent target = new SpatialPhysicsFootprintComponent();
        projector.publish(target, projection);

        Assert.assertFalse(target.valid);
        Assert.assertEquals(0, target.sourcePhysicsShapeId);
        Assert.assertFalse(target.explicitOwnership);
        Assert.assertTrue(target.invalidExplicitOwnership);
        Assert.assertTrue(projection.hasInvalidExplicitOwnership());
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
        Assert.assertEquals(0, target.sourcePhysicsShapeId);
        Assert.assertFalse(target.explicitOwnership);
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

    private static games.pixscape.runtime.physics.CompiledFixtureData compiledCircle(
            int physicsShapeId, float radius, boolean spatialFootprint) {
        games.pixscape.runtime.physics.CompiledFixtureData fixture =
                new games.pixscape.runtime.physics.CompiledFixtureData();
        fixture.physicsShapeId = physicsShapeId;
        fixture.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        fixture.radius = radius;
        fixture.spatialFootprint = spatialFootprint;
        return fixture;
    }
}
