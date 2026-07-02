package games.pixscape.runtime.spatial;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderKind;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SpatialActorCollectorTest {
    private static final float PIXELS_PER_METER = 100f;

    @Test
    public void collectsEligibleActor() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 2, true);
        fixture.addCircle(actor, 3f, 0f, 0f);
        fixture.addActorDrawSlot(actor);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);

        Assert.assertEquals(1, fixture.collector.actorCount());
        Assert.assertEquals(fixture.renderSlotFor(actor), fixture.collector.actorSlot[0]);
        Assert.assertEquals(actor, fixture.collector.actorEntityId[0]);
        Assert.assertEquals(2, fixture.collector.actorLayerIndex[0]);
        Assert.assertEquals(fixture.renderSlotFor(actor), fixture.collector.actorStableOrder[0]);
    }

    @Test
    public void excludesTiledSlots() {
        Fixture fixture = new Fixture();
        int tiledSlot = 30;
        fixture.drawList.addTiledSlot(tiledSlot);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);

        Assert.assertEquals(0, fixture.collector.actorCount());
    }

    @Test
    public void excludesVfxEntriesEvenWhenIndexMatchesActorSlot() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 2, true);
        fixture.addCircle(actor, 3f, 0f, 0f);
        fixture.drawList.addVfxSlot(fixture.renderSlotFor(actor));

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);

        Assert.assertEquals(0, fixture.collector.actorCount());
    }

    @Test
    public void excludesActorOnNonSpatialLayer() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 4, false);
        fixture.addCircle(actor, 3f, 0f, 0f);
        fixture.addActorDrawSlot(actor);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);

        Assert.assertEquals(0, fixture.collector.actorCount());
    }

    @Test
    public void excludesActorWithMissingOrZeroHeight() {
        Fixture fixture = new Fixture();
        int missing = fixture.createActorWithoutHeight(10f, 20f, 2);
        fixture.addCircle(missing, 3f, 0f, 0f);
        int zero = fixture.createActor(30f, 40f, 2, true);
        fixture.height.get(zero).height = 0f;
        fixture.addCircle(zero, 3f, 0f, 0f);
        fixture.addActorDrawSlot(missing);
        fixture.addActorDrawSlot(zero);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);

        Assert.assertEquals(0, fixture.collector.actorCount());
    }

    @Test
    public void excludesActorWithoutValidPhysicsCircleFootprint() {
        Fixture fixture = new Fixture();
        int missingBody = fixture.createActor(10f, 20f, 2, true);
        int disabledBody = fixture.createActor(30f, 40f, 2, true);
        fixture.addCircle(disabledBody, 3f, 0f, 0f);
        fixture.body.get(disabledBody).enabled = false;
        int noCircle = fixture.createActor(50f, 60f, 2, true);
        fixture.body.create(noCircle);
        fixture.fixtures.create(noCircle).fixtures.add(boxFixture());
        fixture.addActorDrawSlot(missingBody);
        fixture.addActorDrawSlot(disabledBody);
        fixture.addActorDrawSlot(noCircle);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);

        Assert.assertEquals(0, fixture.collector.actorCount());
    }

    @Test
    public void computesActorFootprintFromTransformCircleAndPixelsPerMeter() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 2, true);
        fixture.height.get(actor).altitude = 3f;
        fixture.height.get(actor).height = 7f;
        fixture.transform.get(actor).rotationRad = (float) (Math.PI * 0.5);
        fixture.addCircle(actor, 4f, 5f, 1f);
        fixture.addActorDrawSlot(actor);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);

        Assert.assertEquals(1, fixture.collector.actorCount());
        Assert.assertEquals(9f, fixture.collector.actorFootX[0], 0.0001f);
        Assert.assertEquals(25f, fixture.collector.actorFootY[0], 0.0001f);
        Assert.assertEquals(9f, fixture.collector.actorCircleX[0], 0.0001f);
        Assert.assertEquals(25f, fixture.collector.actorCircleY[0], 0.0001f);
        Assert.assertEquals(4f, fixture.collector.actorCircleRadius[0], 0.0001f);
        Assert.assertEquals(3f, fixture.collector.actorAltitude[0], 0.0001f);
        Assert.assertEquals(7f, fixture.collector.actorHeight[0], 0.0001f);
        Assert.assertEquals(5f, fixture.collector.actorBaseStartX[0], 0.0001f);
        Assert.assertEquals(29f, fixture.collector.actorBaseStartY[0], 0.0001f);
        Assert.assertEquals(13f, fixture.collector.actorBaseEndX[0], 0.0001f);
        Assert.assertEquals(29f, fixture.collector.actorBaseEndY[0], 0.0001f);
    }

    @Test
    public void stableOrderingIsDeterministicAcrossCollects() {
        Fixture fixture = new Fixture();
        int actorA = fixture.createActor(10f, 20f, 2, true);
        int actorB = fixture.createActor(30f, 40f, 2, true);
        fixture.addCircle(actorA, 3f, 0f, 0f);
        fixture.addCircle(actorB, 3f, 0f, 0f);
        fixture.addActorDrawSlot(actorA);
        fixture.addActorDrawSlot(actorB);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);
        int[] first = Arrays.copyOf(fixture.collector.actorStableOrder, fixture.collector.actorCount());

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);
        int[] second = Arrays.copyOf(fixture.collector.actorStableOrder, fixture.collector.actorCount());

        Assert.assertArrayEquals(first, second);
    }

    @Test
    public void collectorDoesNotMutateDrawList() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 2, true);
        fixture.addCircle(actor, 3f, 0f, 0f);
        fixture.addActorDrawSlot(actor);
        int nonActorSlot = fixture.createNonActorRenderSlot(2);
        fixture.drawList.addEcsSlot(nonActorSlot);
        int[] before = Arrays.copyOf(fixture.drawList.data(), fixture.drawList.size);
        byte[] domainsBefore = Arrays.copyOf(fixture.drawList.domainData(), fixture.drawList.size);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.spatialLayers, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.body, fixture.fixtures, PIXELS_PER_METER);

        int[] after = Arrays.copyOf(fixture.drawList.data(), fixture.drawList.size);
        byte[] domainsAfter = Arrays.copyOf(fixture.drawList.domainData(), fixture.drawList.size);
        Assert.assertArrayEquals(before, after);
        Assert.assertArrayEquals(domainsBefore, domainsAfter);
    }

    private static FixtureDefData boxFixture() {
        FixtureDefData fixture = new FixtureDefData();
        fixture.shapeType = FixtureDefData.SHAPE_BOX;
        return fixture;
    }

    private static final class Fixture {
        final World world = new World(new WorldConfigurationBuilder().build());
        final DynamicEntityRenderState state = new DynamicEntityRenderState(128);
        final DrawList drawList = new DrawList(128);
        final SpatialActorCollector collector = new SpatialActorCollector();
        final boolean[] spatialLayers = new boolean[8];

        final ComponentMapper<EntityIndexComponent> entityIndex = world.getMapper(EntityIndexComponent.class);
        final ComponentMapper<TransformComponent> transform = world.getMapper(TransformComponent.class);
        final ComponentMapper<SpatialHeightComponent> height = world.getMapper(SpatialHeightComponent.class);
        final ComponentMapper<PhysicsBodyComponent> body = world.getMapper(PhysicsBodyComponent.class);
        final ComponentMapper<PhysicsFixturesComponent> fixtures = world.getMapper(PhysicsFixturesComponent.class);

        Fixture() {
            spatialLayers[2] = true;
        }

        int createActor(float x, float y, int layerIndex, boolean spatialLayer) {
            int actor = createActorWithoutHeight(x, y, layerIndex);
            SpatialHeightComponent h = height.create(actor);
            h.height = 2f;
            if (spatialLayer) {
                spatialLayers[layerIndex] = true;
            } else {
                spatialLayers[layerIndex] = false;
            }
            return actor;
        }

        int createActorWithoutHeight(float x, float y, int layerIndex) {
            int actor = world.create();
            TransformComponent t = transform.create(actor);
            t.x = x;
            t.y = y;
            EntityIndexComponent index = entityIndex.create(actor);
            index.layerIndex = layerIndex;
            enableEntitySlot(actor, layerIndex);
            return actor;
        }

        void addCircle(int actor, float radiusPx, float offsetXPx, float offsetYPx) {
            body.create(actor);
            PhysicsFixturesComponent f = fixtures.create(actor);
            f.fixtures.clear();
            FixtureDefData fixture = new FixtureDefData();
            fixture.shapeType = FixtureDefData.SHAPE_CIRCLE;
            fixture.radius = radiusPx / PIXELS_PER_METER;
            fixture.offsetX = offsetXPx / PIXELS_PER_METER;
            fixture.offsetY = offsetYPx / PIXELS_PER_METER;
            f.fixtures.add(fixture);
        }

        int renderSlotFor(int actor) {
            return state.renderSlotForEntity(actor);
        }

        void addActorDrawSlot(int actor) {
            drawList.addEcsSlot(renderSlotFor(actor));
        }

        int createNonActorRenderSlot(int layerIndex) {
            int entity = world.create();
            int renderSlot = state.acquireSlotForEntity(entity);
            enableSlot(renderSlot, layerIndex);
            return renderSlot;
        }

        void enableEntitySlot(int entity, int layerIndex) {
            int renderSlot = state.acquireSlotForEntity(entity);
            enableSlot(renderSlot, layerIndex);
        }

        void enableSlot(int renderSlot, int layerIndex) {
            state.kind[renderSlot] = RenderKind.SPRITE;
            state.enabled[renderSlot] = true;
            state.visible[renderSlot] = true;
            state.textureHandle[renderSlot] = 1;
            state.layerIndex[renderSlot] = layerIndex;
        }
    }
}
