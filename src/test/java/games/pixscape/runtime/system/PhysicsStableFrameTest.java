package games.pixscape.runtime.system;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialActorCollector;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsStableFrameTest {
    @Test
    public void filteredSensorLightFootprintRemainsEligibleForSpatialCollector() {
        World world = new World(new WorldConfigurationBuilder()
                .with(new PhysicsSpatialFootprintSyncSystem(100f)).build());
        int light = world.create();
        world.getMapper(PointLightComponent.class).create(light);
        world.getMapper(TransformComponent.class).create(light);
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(light);
        index.layerIndex = 1;
        SpatialHeightComponent height = world.getMapper(SpatialHeightComponent.class).create(light);
        height.height = 1f;
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(light);
        PhysicsShapeData sensor = new PhysicsShapeData();
        sensor.physicsShapeId = 1;
        sensor.geometry = new PhysicsGeometryData();
        sensor.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        sensor.geometry.radius = 0.05f;
        sensor.spatialFootprint = true;
        sensor.technicalSpatialLight = true;
        sensor.sensor = true;
        sensor.maskBits = 0;
        shapes.shapes.add(sensor);
        PhysicsService.publishPreparedCandidate(shapes,
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(light),
                PhysicsService.prepareBodyCandidate(shapes.shapes));
        world.process();

        DynamicEntityRenderState state = new DynamicEntityRenderState(2);
        int slot = state.acquireSlotForEntity(light);
        state.kind[slot] = RenderKind.SPRITE;
        state.enabled[slot] = true;
        state.visible[slot] = true;
        state.textureHandle[slot] = 1;
        state.layerIndex[slot] = 1;
        DrawList drawList = new DrawList(2);
        drawList.addEcsSlot(slot);
        SpatialActorCollector collector = new SpatialActorCollector();
        collector.collect(drawList, state, new boolean[]{false, true},
                world.getEntityManager(), world.getMapper(EntityIndexComponent.class),
                world.getMapper(TransformComponent.class),
                world.getMapper(SpatialHeightComponent.class),
                world.getMapper(SpatialPhysicsFootprintComponent.class));
        Assert.assertEquals(1, collector.actorCount());
        Assert.assertEquals(5f, world.getMapper(SpatialPhysicsFootprintComponent.class)
                .get(light).radiusPx, 0f);
    }

    @Test
    public void oneThousandStableFramesPerformNoPhysicsCompilationOrRebuild() {
        GdxNativesLoader.load();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2());
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        Counters counters = new Counters();
        sync.setTestObserver(counters);
        World world = new World(new WorldConfigurationBuilder()
                .with(dirty, sync, new PhysicsSpatialFootprintSyncSystem(100f))
                .build());

        int actor = world.create();
        world.getMapper(TransformComponent.class).create(actor);
        world.getMapper(PhysicsBodyComponent.class).create(actor);
        EntityIndexComponent index =
                world.getMapper(EntityIndexComponent.class).create(actor);
        index.layerIndex = 0;
        SpatialHeightComponent height =
                world.getMapper(SpatialHeightComponent.class).create(actor);
        height.height = 2f;
        PhysicsShapesComponent sources =
                world.getMapper(PhysicsShapesComponent.class).create(actor);
        PhysicsShapeData circle = new PhysicsShapeData();
        circle.geometry = new PhysicsGeometryData();
        circle.physicsShapeId = 1;
        circle.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        circle.geometry.radius = 0.25f;
        circle.spatialFootprint = true;
        sources.shapes.add(circle);
        PhysicsShapeData polygon = new PhysicsShapeData();
        polygon.geometry = new PhysicsGeometryData();
        polygon.physicsShapeId = 2;
        polygon.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        polygon.geometry.polygonVertices = new float[]{
                0f, 0f, 2f, 0f, 2f, 2f, 1f, 1f, 0f, 2f
        };
        polygon.geometry.polygonVertexCount = 5;
        sources.shapes.add(polygon);

        DynamicEntityRenderState renderState = new DynamicEntityRenderState(8);
        int slot = renderState.acquireSlotForEntity(actor);
        renderState.kind[slot] = RenderKind.SPRITE;
        renderState.enabled[slot] = true;
        renderState.visible[slot] = true;
        renderState.textureHandle[slot] = 1;
        renderState.layerIndex[slot] = 0;
        DrawList drawList = new DrawList(8);
        drawList.addEcsSlot(slot);
        boolean[] spatialLayers = {true};
        SpatialActorCollector collector = new SpatialActorCollector();

        PhysicsService.publishPreparedCandidate(
                sources,
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(actor),
                PhysicsService.prepareBodyCandidate(sources.shapes));
        world.process();
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).get(actor);
        SpatialPhysicsFootprintComponent footprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(actor);
        Assert.assertTrue(compiled.valid);
        Assert.assertTrue(footprint.valid);
        Assert.assertEquals(circle.geometry.radius * 100f, footprint.radiusPx, 0f);
        Assert.assertEquals(compiled.generation, footprint.physicsGeneration);
        Assert.assertEquals(0, counters.bodyCompiles);
        Assert.assertEquals(0, counters.shapeCompiles);
        Assert.assertEquals(0, counters.polygonDecompositions);
        counters.reset();

        ComponentMapper<EntityIndexComponent> entityIndexes =
                world.getMapper(EntityIndexComponent.class);
        ComponentMapper<TransformComponent> transforms =
                world.getMapper(TransformComponent.class);
        ComponentMapper<SpatialHeightComponent> heights =
                world.getMapper(SpatialHeightComponent.class);
        ComponentMapper<SpatialPhysicsFootprintComponent> spatialFootprints =
                world.getMapper(SpatialPhysicsFootprintComponent.class);
        for (int frame = 0; frame < 1000; frame++) {
            world.process();
            collector.collect(
                    drawList,
                    renderState,
                    spatialLayers,
                    world.getEntityManager(),
                    entityIndexes,
                    transforms,
                    heights,
                    spatialFootprints);
            Assert.assertEquals(1, collector.actorCount());
        }

        Assert.assertEquals(0, counters.bodyCompiles);
        Assert.assertEquals(0, counters.shapeCompiles);
        Assert.assertEquals(0, counters.bodyRebuilds);
        Assert.assertEquals(0, counters.provenanceCreations);
        Assert.assertEquals(0, counters.polygonDecompositions);

        int generationBeforeMutation = compiled.generation;
        PhysicsShapeData liveCircle = sources.shapes.first();
        liveCircle.geometry.radius = 0.5f;
        PhysicsService.publishPreparedCandidate(
                sources,
                compiled,
                PhysicsService.prepareBodyCandidate(sources.shapes));
        dirty.physics(actor, PhysicsDirtyBits.ALL);
        world.process();
        Assert.assertEquals(generationBeforeMutation + 1, compiled.generation);
        Assert.assertEquals(liveCircle.geometry.radius * 100f, footprint.radiusPx, 0f);
        Assert.assertEquals(compiled.generation, footprint.physicsGeneration);

        Body nativeBody = world.getMapper(
                games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent.class)
                .get(actor).body;
        Assert.assertNotNull(nativeBody);
        world.getMapper(PhysicsBodyComponent.class).remove(actor);
        dirty.physics(actor, PhysicsDirtyBits.ALL);
        world.process();
        Assert.assertTrue(compiled.valid);
        Assert.assertTrue(footprint.valid);
        Assert.assertEquals(compiled.generation, footprint.physicsGeneration);
        world.dispose();
        box2d.dispose();
    }

    private static final class Counters
            implements Box2dSyncSystem.TestObserver {
        int bodyCompiles;
        int shapeCompiles;
        int polygonDecompositions;
        int bodyRebuilds;
        int provenanceCreations;

        void reset() {
            bodyCompiles = 0;
            shapeCompiles = 0;
            polygonDecompositions = 0;
            bodyRebuilds = 0;
            provenanceCreations = 0;
        }

        @Override
        public void onBodyCompile() {
            bodyCompiles++;
        }

        @Override
        public void onShapeCompile() {
            shapeCompiles++;
        }

        @Override
        public void onPolygonDecomposition() {
            polygonDecompositions++;
        }

        @Override
        public void onBodyRebuild() {
            bodyRebuilds++;
        }

        @Override
        public void beforeCreateFixture(
                int bodyEntityId, CompiledFixtureData fixture) {
        }

        @Override
        public void onFixtureProvenanceCreated(
                int bodyEntityId, CompiledFixtureData fixture) {
            provenanceCreations++;
        }

        @Override
        public void beforeCreateOrRebuildJoint(int jointEntityId) {
        }
    }
}
