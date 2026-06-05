package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialRenderOrderSystemTest {

    @Test
    public void sameLayerSpatialActorsSortByFootYAfterLegacySort() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int back = fixture.createActor(10f, 40f, 0, 0, true);
        int front = fixture.createActor(10f, 20f, 0, 0, true);

        fixture.process();

        Assert.assertArrayEquals(new int[]{back, front}, fixture.drawOrder());
    }

    @Test
    public void movingActorCrossesAnotherActorFlipsDrawOrder() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int mover = fixture.createActor(10f, 10f, 0, 0, true);
        int fixed = fixture.createActor(10f, 20f, 0, 0, true);

        fixture.process();
        Assert.assertArrayEquals(new int[]{fixed, mover}, fixture.drawOrder());

        fixture.setActorPosition(mover, 10f, 30f);
        fixture.process();

        Assert.assertArrayEquals(new int[]{mover, fixed}, fixture.drawOrder());
    }

    @Test
    public void threeSpatialActorsStableSortByFootY() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int sameA = fixture.createActor(10f, 20f, 0, 0, true);
        fixture.setSortOrder(sameA, 0, 0, 30);
        int lower = fixture.createActor(10f, 40f, 0, 0, true);
        fixture.setSortOrder(lower, 0, 0, 10);
        int sameB = fixture.createActor(10f, 20f, 0, 0, true);
        fixture.setSortOrder(sameB, 0, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{lower, sameA, sameB}, fixture.drawOrder());
    }

    @Test
    public void differentLayersRemainLayerOrdered() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(1, true);
        fixture.createLayer(2, true);
        int lowerLayer = fixture.createActor(10f, 40f, 0, 1, true);
        int higherLayer = fixture.createActor(10f, 20f, 0, 2, true);

        fixture.process();

        Assert.assertArrayEquals(new int[]{lowerLayer, higherLayer}, fixture.drawOrder());
    }

    @Test
    public void nonSpatialLayerKeepsLegacyOrder() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, false);
        int first = fixture.createActor(10f, 40f, 0, 0, true);
        int second = fixture.createActor(10f, 20f, 0, 0, true);

        fixture.process();

        Assert.assertArrayEquals(new int[]{first, second}, fixture.drawOrder());
    }

    @Test
    public void spatialEnabledPhysicsLayerSortsActors() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, LayerComponent.TYPE_PHYSICS, true);
        int behind = fixture.createActor(10f, 20f, 0, 2, true);
        int front = fixture.createActor(10f, 40f, 0, 2, true);
        fixture.setSortOrder(behind, 2, 0, 20);
        fixture.setSortOrder(front, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{front, behind}, fixture.drawOrder());
    }

    @Test
    public void nonSpatialActorKeepsLegacyPosition() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int nonSpatial = fixture.createActor(10f, 100f, 0, 0, false);
        int spatial = fixture.createActor(10f, 10f, 0, 0, true);

        fixture.process();

        Assert.assertArrayEquals(new int[]{nonSpatial, spatial}, fixture.drawOrder());
    }

    @Test
    public void renderSlotsDifferentFromEntityIdsStillSortByMappedEntityFootY() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int actorA = fixture.createActorInRenderSlot(10f, 10f, 0, 0, 50);
        int actorB = fixture.createActorInRenderSlot(10f, 20f, 0, 0, 51);

        fixture.process();
        Assert.assertArrayEquals(new int[]{51, 50}, fixture.drawOrder());

        fixture.setActorPosition(actorA, 10f, 30f);
        fixture.process();

        Assert.assertArrayEquals(new int[]{50, 51}, fixture.drawOrder());
        Assert.assertTrue(actorB >= 0);
    }

    @Test
    public void highRenderSlotWithoutEntityMappingIsNotQueriedAsEntity() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int renderSlot = 300;
        fixture.createRenderOnlySlot(renderSlot, 0, 0, renderSlot);
        fixture.state.entityId[renderSlot] = -1;

        fixture.process();

        Assert.assertArrayEquals(new int[]{renderSlot}, fixture.drawOrder());
    }

    @Test
    public void tiledSlotsRemainInLegacyPositionsAroundSortedActors() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int tileA = fixture.createTiledSlot(300, 0, 10);
        int back = fixture.createActor(10f, 40f, 0, 0, true);
        fixture.setSortOrder(back, 0, 0, 20);
        int front = fixture.createActor(10f, 20f, 0, 0, true);
        fixture.setSortOrder(front, 0, 0, 30);
        int tileB = fixture.createTiledSlot(301, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tileA, back, front, tileB}, fixture.drawOrder());
    }

    @Test
    public void spatialTiledLayerDoesNotInterleaveWithSpatialActor() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int actor = fixture.createActor(10f, 100f, 0, 0, true);
        fixture.setSortOrder(actor, 0, 0, 10);
        int tile = fixture.createSpatialTiledSlot(300, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{actor, tile}, fixture.drawOrder());
    }

    @Test
    public void tiledLayerEntitySpatialEnabledDoesNotMakeTiledSlotsActorSorted() {
        Fixture fixture = new Fixture(512);
        fixture.createSpatialTiledLayer(0);
        int first = fixture.createTiledSlot(300, 0, 30);
        int second = fixture.createTiledSlot(301, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{second, first}, fixture.drawOrder());
    }

    @Test
    public void submitReceivesActorSortedDrawList() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(0, true);
        int back = fixture.createActor(10f, 40f, 0, 0, true);
        int front = fixture.createActor(10f, 20f, 0, 0, true);

        fixture.process();

        Assert.assertArrayEquals(new int[]{back, front}, fixture.beforeSpatialOrder);
        Assert.assertArrayEquals(new int[]{back, front}, fixture.beforeSubmitOrder);
    }

    @Test
    public void actorWorkArraysDoNotGrowAfterWarmup() {
        Fixture fixture = new Fixture(256);
        fixture.createLayer(0, true);
        for (int i = 0; i < 20; i++) {
            fixture.createActor(10f, 100f - i, 0, 0, true);
        }

        fixture.process();
        int capacityAfterWarmup = fixture.spatial.getActorWorkArrayCapacity();
        fixture.process();
        fixture.process();

        Assert.assertEquals(capacityAfterWarmup, fixture.spatial.getActorWorkArrayCapacity());
    }

    private static final class Fixture {
        final RenderStateSOA state;
        final LayerStateSOA layerState;
        final DrawList drawList;
        final RenderStats stats;
        final World world;
        final SpatialRenderOrderSystem spatial;

        int[] beforeSpatialOrder;
        int[] beforeSubmitOrder;

        Fixture(int capacity) {
            this(capacity, false);
        }

        Fixture(int capacity, boolean captureOrder) {
            state = new RenderStateSOA(capacity);
            layerState = new LayerStateSOA(16);
            for (int i = 0; i < layerState.enabled.length; i++) {
                layerState.enabled[i] = true;
            }
            drawList = new DrawList(capacity);
            stats = new RenderStats();
            spatial = new SpatialRenderOrderSystem(state, drawList);

            WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
                    .with(
                            new RenderBuildDrawListSystem(state, layerState, drawList, stats, 128, -1, -1),
                            new RenderSortSystem(state, drawList)
                    );
            if (captureOrder) {
                builder.with(new BeforeSpatialCaptureSystem(drawList, order -> beforeSpatialOrder = order));
            }
            builder.with(spatial);
            if (captureOrder) {
                builder.with(new BeforeSubmitCaptureSystem(drawList, order -> beforeSubmitOrder = order));
            }
            world = new World(builder.build());
        }

        void createLayer(int layerIndex, boolean spatialEnabled) {
            createLayer(layerIndex, LayerComponent.TYPE_CLASSIC, spatialEnabled);
        }

        void createLayer(int layerIndex, int type, boolean spatialEnabled) {
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = layerIndex;
            layer.type = type;
            layer.spatialEnabled = spatialEnabled;
        }

        void createSpatialTiledLayer(int layerIndex) {
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = layerIndex;
            layer.type = LayerComponent.TYPE_TILED;
            layer.spatialEnabled = true;

            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.spatialEnabled = true;
            tiled.defaultTileAltitude = 0f;
            tiled.defaultTileHeight = 10f;
            tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
            tiled.data.spatialEnabled = true;
            tiled.data.defaultTileAltitude = 0f;
            tiled.data.defaultTileHeight = 10f;
            tiled.data.initSlotRange(300, 301);
        }

        int createSpatialTiledSlot(int slot, int layerIndex, int runtimeOrder) {
            createSpatialTiledLayer(layerIndex);
            return createTiledSlot(slot, layerIndex, runtimeOrder);
        }

        int createActor(float x, float y, int z, int layerIndex, boolean spatial) {
            int entity = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
            transform.x = x;
            transform.y = y;

            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = layerIndex;
            index.zIndex = z;

            if (spatial) {
                SpatialHeightComponent height = world.getMapper(SpatialHeightComponent.class).create(entity);
                height.height = 2f;
            }

            enableSlot(entity, layerIndex, z, entity);
            state.entityId[entity] = entity;
            return entity;
        }

        int createActorInRenderSlot(float x, float y, int z, int layerIndex, int slot) {
            int entity = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
            transform.x = x;
            transform.y = y;

            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = layerIndex;
            index.zIndex = z;

            SpatialHeightComponent height = world.getMapper(SpatialHeightComponent.class).create(entity);
            height.height = 2f;

            enableSlot(slot, layerIndex, z, slot);
            state.entityId[slot] = entity;
            return entity;
        }

        void setActorPosition(int actor, float x, float y) {
            TransformComponent transform = world.getMapper(TransformComponent.class).get(actor);
            transform.x = x;
            transform.y = y;
        }

        int createTiledSlot(int slot, int layerIndex, int runtimeOrder) {
            enableSlot(slot, layerIndex, 0, runtimeOrder);
            state.entityId[slot] = -1;
            state.appendTiledVisibleRange(slot, 1);
            return slot;
        }

        int createRenderOnlySlot(int slot, int layerIndex, int z, int runtimeOrder) {
            enableSlot(slot, layerIndex, z, runtimeOrder);
            state.entityId[slot] = -1;
            state.appendTiledVisibleRange(slot, 1);
            return slot;
        }

        void enableSlot(int slot, int layerIndex, int z, int runtimeOrder) {
            state.kind[slot] = RenderStateSOA.KIND_SPRITE;
            state.enabled[slot] = true;
            state.visible[slot] = true;
            state.textureHandle[slot] = 1;
            state.shader[slot] = 1;
            state.blend[slot] = BlendMode.ALPHA.id;
            state.layerIndex[slot] = layerIndex;
            state.z[slot] = z;
            state.runtimeOrder[slot] = runtimeOrder;
            state.entityId[slot] = -1;
            state.sortKey[slot] = SortKey64.packForBlend(
                    state.shader[slot],
                    state.blend[slot],
                    state.textureHandle[slot],
                    layerIndex,
                    z,
                    runtimeOrder
            );
            state.touch(slot);
        }

        void setSortOrder(int slot, int layerIndex, int z, int runtimeOrder) {
            state.layerIndex[slot] = layerIndex;
            state.z[slot] = z;
            state.runtimeOrder[slot] = runtimeOrder;
            state.sortKey[slot] = SortKey64.packForBlend(
                    state.shader[slot],
                    state.blend[slot],
                    state.textureHandle[slot],
                    layerIndex,
                    z,
                    runtimeOrder
            );
        }

        void process() {
            world.process();
        }

        int[] drawOrder() {
            int[] out = new int[drawList.size];
            System.arraycopy(drawList.data(), 0, out, 0, drawList.size);
            return out;
        }
    }

    private interface OrderSink {
        void accept(int[] order);
    }

    private abstract static class CaptureDrawListSystem extends BaseSystem {
        private final DrawList drawList;
        private final OrderSink sink;

        CaptureDrawListSystem(DrawList drawList, OrderSink sink) {
            this.drawList = drawList;
            this.sink = sink;
        }

        @Override
        protected void processSystem() {
            int[] out = new int[drawList.size];
            System.arraycopy(drawList.data(), 0, out, 0, drawList.size);
            sink.accept(out);
        }
    }

    private static final class BeforeSpatialCaptureSystem extends CaptureDrawListSystem {
        BeforeSpatialCaptureSystem(DrawList drawList, OrderSink sink) {
            super(drawList, sink);
        }
    }

    private static final class BeforeSubmitCaptureSystem extends CaptureDrawListSystem {
        BeforeSubmitCaptureSystem(DrawList drawList, OrderSink sink) {
            super(drawList, sink);
        }
    }
}
