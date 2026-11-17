package games.pixscape.runtime.spatial;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderKind;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SpatialActorCollectorTest {
    @Test
    public void ownerMotionTransportsIndependentFootprintAndRectangleWithoutPhysics() {
        Fixture f = new Fixture();
        try {
            int e = f.createActor(10, 20, 2, true);
            f.addCircle(e, 8, 40, -12);
            f.addActorDrawSlot(e);
            f.world.getMapper(PhysicsBodyComponent.class).remove(e);
            ActorSpatialRectangleData rectangle = f.height.get(e).rectangles.staticRectangle;
            rectangle.x = .1f;
            rectangle.y = .2f;
            rectangle.width = rectangle.height = .5f;
            float[] quad = new float[8];
            for (int step = 0; step < 3; step++) {
                f.publishQuad(e, f.transform.get(e).x, f.transform.get(e).y, 100, 200);
                f.collector.collect(f.drawList, f.state, f.world.getEntityManager(),
                        f.entityIndex, f.transform, f.height, f.shapes);
                f.collector.writeInfluenceQuad(0, 0, 0, quad);
                Assert.assertEquals(f.transform.get(e).x + rectangle.x * 100, quad[0], .0001f);
                Assert.assertEquals(f.transform.get(e).y + rectangle.y * 200, quad[1], .0001f);
                Assert.assertEquals(f.transform.get(e).x + f.footprints.get(e).localOffsetXPx, f.collector.actorFootX[0], .0001f);
                if (step == 0) {
                    f.footprints.get(e).localOffsetXPx = -90;
                    f.footprints.get(e).localOffsetYPx = 35;
                    f.footprints.get(e).radiusPx = 17;
                } else {
                    rectangle.x = .3f;
                    f.transform.get(e).x += 100;
                    f.transform.get(e).y -= 20;
                }
            }
        } finally {
            f.world.dispose();
        }
    }

    @Test
    public void activeRectangleControlsBoundsAndVerticalIntervalWithoutMovingFootprint() {
        Fixture f = new Fixture();
        try {
            int e = f.createActor(10, 20, 2, true);
            f.addCircle(e, 3, 0, 0);
            f.addActorDrawSlot(e);
            SpatialRepresentationComponent height = f.height.get(e);
            f.footprints.get(e).altitude = 5;
            games.pixscape.runtime.component.AnimationComponent animation = f.world.getMapper(
                    games.pixscape.runtime.component.AnimationComponent.class).create(e);
            f.world.getMapper(games.pixscape.runtime.component.AssetRefComponent.class).create(e).assetId = 7;
            height.rectangles = new ActorSpatialRectangles();
            ActorSpatialRectangleData partial = new ActorSpatialRectangleData();
            partial.width = .4f;
            partial.height = .3f;
            partial.x = .1f;
            partial.y = .2f;
            partial.pivotX = 100;
            partial.pivotY = -200;
            height.rectangles.put(7, "partial", partial);
            ActorSpatialRectangleData tall = partial.copy();
            tall.height = .8f;
            tall.y = .1f;
            height.rectangles.put(7, "tall", tall);
            float[] quad = new float[8];
            for (String clip : new String[]{"partial", "tall"}) {
                animation.currentClip = clip;
                for (int frame = 0; frame < 4; frame++) {
                    animation.frame = frame;
                    f.collector.collect(f.drawList, f.state, f.world.getEntityManager(), f.entityIndex, f.transform, f.height, f.shapes);
                    Assert.assertEquals(1, f.collector.actorCount());
                    Assert.assertEquals(5, f.collector.actorAltitude[0], 0);
                    Assert.assertEquals(20, f.collector.actorCircleY[0], 0);
                    ActorSpatialRectangleData r = height.rectangles.get(7, clip);
                    Assert.assertEquals(r.height * 200, f.collector.actorHeight[0], .0001f);
                    Assert.assertEquals(5 + r.y * 200, f.collector.actorBottom[0], .0001f);
                    f.collector.writeInfluenceQuad(0, 0, 0, quad);
                    float bottom = 20 + r.y * 200, top = bottom + r.height * 200;
                    Assert.assertArrayEquals(new float[]{20, bottom, 60, bottom, 60, top, 20, top}, quad, .0001f);
                }
            }
            animation.currentClip = "missing";
            f.collector.collect(f.drawList, f.state, f.world.getEntityManager(), f.entityIndex, f.transform, f.height, f.shapes);
            Assert.assertEquals(1, f.collector.actorCount());
            height.rectangles = null;
            f.collector.collect(f.drawList, f.state, f.world.getEntityManager(), f.entityIndex, f.transform, f.height, f.shapes);
            Assert.assertEquals(1, f.collector.actorCount());
        } finally {
            f.world.dispose();
        }
    }

    @Test
    public void collectsEligibleActor() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 2, true);
        fixture.addCircle(actor, 3f, 0f, 0f);
        fixture.addActorDrawSlot(actor);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);

        Assert.assertEquals(1, fixture.collector.actorCount());
        Assert.assertFalse(fixture.body.has(actor));
        Assert.assertFalse(fixture.world.getMapper(games.pixscape.runtime.component.physics.PhysicsShapesComponent.class).has(actor));
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

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);

        Assert.assertEquals(0, fixture.collector.actorCount());
    }

    @Test
    public void excludesVfxEntriesEvenWhenIndexMatchesActorSlot() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 2, true);
        fixture.addCircle(actor, 3f, 0f, 0f);
        fixture.drawList.addVfxSlot(fixture.renderSlotFor(actor));

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);

        Assert.assertEquals(0, fixture.collector.actorCount());
    }

    @Test
    public void collectsActorOnAnyOrdinaryLayer() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 4, false);
        fixture.addCircle(actor, 3f, 0f, 0f);
        fixture.addActorDrawSlot(actor);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);

        Assert.assertEquals(1, fixture.collector.actorCount());
    }

    @Test
    public void missingRepresentationUsesImageAndEmptyZoneEmitsNothing() {
        Fixture fixture = new Fixture();
        int missing = fixture.createActorWithoutHeight(10f, 20f, 2);
        fixture.addCircle(missing, 3f, 0f, 0f);
        int zero = fixture.createActor(30f, 40f, 2, true);
        games.pixscape.runtime.spatial.SpatialRectangleTestData.setHeight(fixture.height.get(zero), 0f);
        fixture.addCircle(zero, 3f, 0f, 0f);
        fixture.addActorDrawSlot(missing);
        fixture.addActorDrawSlot(zero);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);

        Assert.assertEquals(1, fixture.collector.actorCount());
    }

    @Test
    public void excludesActorWithoutValidAuthoredCircleFootprint() {
        Fixture fixture = new Fixture();
        int missingBody = fixture.createActor(10f, 20f, 2, true);
        int disabledBody = fixture.createActor(30f, 40f, 2, true);
        fixture.addCircle(disabledBody, 3f, 0f, 0f);
        fixture.footprints.get(disabledBody).radiusPx = 0f;
        int noCircle = fixture.createActor(50f, 60f, 2, true);
        fixture.body.create(noCircle);
        fixture.footprints.create(noCircle).radiusPx = 0;
        fixture.addActorDrawSlot(missingBody);
        fixture.addActorDrawSlot(disabledBody);
        fixture.addActorDrawSlot(noCircle);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);

        Assert.assertEquals(0, fixture.collector.actorCount());
    }

    @Test
    public void computesActorFootprintFromTransformAndAuthoredCircle() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(10f, 20f, 2, true);
        fixture.footprints.create(actor).altitude = 3f;
        fixture.height.get(actor).rectangles.staticRectangle.height = .035f;
        fixture.transform.get(actor).rotationRad = (float) (Math.PI * 0.5);
        fixture.addCircle(actor, 4f, 5f, 1f);
        fixture.addActorDrawSlot(actor);

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);

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
        float[] influence = new float[8];
        fixture.collector.writeInfluenceQuad(0, 100f, -50f, influence);
        // The rectangle follows the owner origin; neither footprint offset nor altitude is added.
        Assert.assertArrayEquals(new float[]{110, -30, 210, -30, 210, -23, 110, -23}, influence, .0001f);
    }

    @Test
    public void usesResolvedWorldPoseAndEffectiveRenderLayerForGameObjectMembers() {
        Fixture fixture = new Fixture();
        int actor = fixture.createActor(1f, 2f, 1, false);
        fixture.publishQuad(actor, 100, 200, 100, 200);
        fixture.addCircle(actor, 3f, 5f, 0f);
        fixture.addActorDrawSlot(actor);
        fixture.state.layerIndex[fixture.renderSlotFor(actor)] = 2;
        WorldTransformState worldPose = new WorldTransformState();
        worldPose.setResolved(actor, 100f, 200f, (float) (Math.PI * .5), 1f, 1f);

        fixture.collector.collect(fixture.drawList, fixture.state,
                fixture.world.getEntityManager(), fixture.entityIndex, fixture.transform,
                fixture.height, fixture.shapes, null, worldPose);

        Assert.assertEquals(1, fixture.collector.actorCount());
        Assert.assertEquals(2, fixture.collector.actorLayerIndex[0]);
        Assert.assertEquals(100f, fixture.collector.actorFootX[0], .0001f);
        Assert.assertEquals(205f, fixture.collector.actorFootY[0], .0001f);
        float[] influence = new float[8];
        fixture.collector.writeInfluenceQuad(0, 0, 0, influence);
        Assert.assertEquals(100, influence[0], .0001f);
        Assert.assertEquals(200, influence[2], .0001f);
        Assert.assertEquals(200, influence[1], .0001f);
        Assert.assertEquals(400, influence[5], .0001f);
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

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);
        int[] first = Arrays.copyOf(fixture.collector.actorStableOrder, fixture.collector.actorCount());

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);
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

        fixture.collector.collect(fixture.drawList, fixture.state, fixture.world.getEntityManager(),
                fixture.entityIndex, fixture.transform, fixture.height, fixture.shapes);

        int[] after = Arrays.copyOf(fixture.drawList.data(), fixture.drawList.size);
        byte[] domainsAfter = Arrays.copyOf(fixture.drawList.domainData(), fixture.drawList.size);
        Assert.assertArrayEquals(before, after);
        Assert.assertArrayEquals(domainsBefore, domainsAfter);
    }

    private static final class Fixture {
        final World world = new World(new WorldConfigurationBuilder().build());
        final DynamicEntityRenderState state = new DynamicEntityRenderState(128);
        final DrawList drawList = new DrawList(128);
        final SpatialActorCollector collector = new SpatialActorCollector();

        final ComponentMapper<EntityIndexComponent> entityIndex = world.getMapper(EntityIndexComponent.class);
        final ComponentMapper<TransformComponent> transform = world.getMapper(TransformComponent.class);
        final ComponentMapper<SpatialRepresentationComponent> height = world.getMapper(SpatialRepresentationComponent.class);
        final ComponentMapper<PhysicsBodyComponent> body = world.getMapper(PhysicsBodyComponent.class);
        final ComponentMapper<SpatialFootprintComponent> shapes =
                world.getMapper(SpatialFootprintComponent.class);
        final ComponentMapper<SpatialFootprintComponent> footprints = shapes;

        Fixture() {
            collector.setRepresentationMappers(world.getMapper(games.pixscape.runtime.component.AnimationComponent.class), world.getMapper(games.pixscape.runtime.component.AssetRefComponent.class));
        }

        int createActor(float x, float y, int layerIndex, boolean spatialLayer) {
            int actor = createActorWithoutHeight(x, y, layerIndex);
            SpatialRepresentationComponent h = height.create(actor);
            h.rectangles = new ActorSpatialRectangles();
            h.rectangles.staticRectangle = new ActorSpatialRectangleData();
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
            publishQuad(actor, x, y, 100, 200);
            return actor;
        }

        void publishQuad(int entity, float x, float y, float width, float height) {
            int slot = renderSlotFor(entity);
            state.x1[slot] = state.x2[slot] = x;
            state.x3[slot] = state.x4[slot] = x + width;
            state.y1[slot] = state.y4[slot] = y;
            state.y2[slot] = state.y3[slot] = y + height;
        }

        void addCircle(int actor, float radiusPx, float offsetXPx, float offsetYPx) {
            SpatialFootprintComponent footprint = footprints.create(actor);
            footprint.radiusPx = radiusPx;
            footprint.localOffsetXPx = offsetXPx;
            footprint.localOffsetYPx = offsetYPx;
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
