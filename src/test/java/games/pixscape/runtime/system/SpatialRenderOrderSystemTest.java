package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialRenderOrderSystemTest {

    @Test
    public void sameLayerSpatialActorsSortByFootYAfterLegacySort() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int lower = fixture.createActor(10f, 40f, 0, 0, true);
        int higher = fixture.createActor(10f, 20f, 0, 0, true);

        fixture.process();

        Assert.assertArrayEquals(new int[]{lower, higher}, fixture.drawOrder());
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
    public void sameLayerSpatialActorsSortByCircleCenterNotBottom() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int largeHigherCenter = fixture.createActor(10f, 10f, 0, 0, true);
        fixture.setActorCircleFootprint(largeHigherCenter, 20f);
        int smallLowerCenter = fixture.createActor(10f, 20f, 0, 0, true);
        fixture.setActorCircleFootprint(smallLowerCenter, 2f);

        fixture.process();

        Assert.assertArrayEquals(new int[]{smallLowerCenter, largeHigherCenter}, fixture.drawOrder());
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
        int renderRef = fixture.createRenderOnlySlot(renderSlot, 0, 0, renderSlot);
        fixture.state.entityId[renderSlot] = -1;

        fixture.process();

        Assert.assertArrayEquals(new int[]{renderRef}, fixture.drawOrder());
    }

    @Test
    public void tiledDrawIndexMappingUsesRenderRefNotLegacySlot() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 1f, 1f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int legacySlot = map.slotForTile(0, 0);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertNotEquals(legacySlot, tile);
        Assert.assertEquals(indexOf(fixture.beforeSpatialOrder, tile), fixture.spatial.tiledDrawIndexForRef(tile));
        Assert.assertEquals(-1, fixture.spatial.tiledDrawIndexForRef(legacySlot));
    }

    @Test
    public void tiledSlotsRemainInLegacyPositionsAroundSortedActors() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        int tileA = fixture.createTiledSlot(300, 0, 10);
        int lower = fixture.createActor(10f, 40f, 0, 0, true);
        fixture.setSortOrder(lower, 0, 0, 20);
        int higher = fixture.createActor(10f, 20f, 0, 0, true);
        fixture.setSortOrder(higher, 0, 0, 30);
        int tileB = fixture.createTiledSlot(301, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tileA, lower, higher, tileB}, fixture.drawOrder());
    }

    @Test
    public void spatialActorsSortInsideSameActorOnlySpanBetweenTileAnchors() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        TiledMapLayerData map = fixture.createBlockMap(2, 1, 16, 16, 300);
        fixture.createSpatialTiledLayerWithMap(0, map);
        int tileA = fixture.createLinkedTile(map, 0, 0, 101, 0, 10);
        int higher = fixture.createActor(10f, 10f, 0, 0, true);
        fixture.setSortOrder(higher, 0, 0, 20);
        int lower = fixture.createActor(10f, 30f, 0, 0, true);
        fixture.setSortOrder(lower, 0, 0, 30);
        int tileB = fixture.createLinkedTile(map, 1, 0, 102, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tileA, lower, higher, tileB}, fixture.drawOrder());
        assertSameTiledSubsequence(new int[]{tileA, higher, lower, tileB}, fixture.drawOrder(), tileA, tileB);
    }

    @Test
    public void spatialActorsDoNotSortAcrossTileAnchorWithoutBlockRelation() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(0, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 1, 16, 16, 300);
        fixture.createSpatialTiledLayerWithMap(0, map);
        int tileA = fixture.createLinkedTile(map, 0, 0, 101, 0, 10);
        int higher = fixture.createActor(10f, 10f, 0, 0, true);
        fixture.setSortOrder(higher, 0, 0, 20);
        int tileB = fixture.createLinkedTile(map, 1, 0, 102, 0, 30);
        int lower = fixture.createActor(10f, 30f, 0, 0, true);
        fixture.setSortOrder(lower, 0, 0, 40);
        int tileC = fixture.createLinkedTile(map, 2, 0, 103, 0, 50);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tileA, higher, tileB, lower, tileC}, fixture.drawOrder());
        assertSameTiledSubsequence(new int[]{tileA, higher, tileB, lower, tileC}, fixture.drawOrder(), tileA, tileB, tileC);
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
        int lower = fixture.createActor(10f, 40f, 0, 0, true);
        int higher = fixture.createActor(10f, 20f, 0, 0, true);

        fixture.process();

        Assert.assertArrayEquals(new int[]{lower, higher}, fixture.beforeSpatialOrder);
        Assert.assertArrayEquals(new int[]{lower, higher}, fixture.beforeSubmitOrder);
    }

    @Test
    public void postSpatialPipelineDoesNotResortDrawListBeforeSubmit() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(0, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 1f, 1f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 24f, 0, 0, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 0, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{actor, tile}, fixture.beforeSpatialOrder);
        Assert.assertArrayEquals(new int[]{actor, tile}, fixture.beforeSubmitOrder);
        Assert.assertArrayEquals(fixture.beforeSubmitOrder, fixture.drawOrder());
    }

    @Test
    public void completeNonActorRelativeOrderIsPreserved() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 1f, 1f));
        int renderA = fixture.createRenderOnlySlot(40, 2, 0, 5);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int renderB = fixture.createRenderOnlySlot(41, 2, 0, 30);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        assertSameTiledSubsequence(fixture.beforeSpatialOrder, fixture.beforeSpatialDomains,
                fixture.drawOrder(), fixture.drawDomains(), renderA, tile, renderB);
        Assert.assertEquals(1, fixture.countDrawEntry(RenderSourceDomain.SOURCE_ECS, actor));
    }

    @Test
    public void multipleActorsNearSameWallResolveIntoOneSortedBucket() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 1f, 1f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int a18 = fixture.createActor(8f, 18f, 0, 2, true);
        int a24 = fixture.createActor(8f, 24f, 0, 2, true);
        int a20 = fixture.createActor(8f, 20f, 0, 2, true);
        int a22 = fixture.createActor(8f, 22f, 0, 2, true);
        fixture.setActorCircleFootprint(a18, 2f);
        fixture.setActorCircleFootprint(a24, 2f);
        fixture.setActorCircleFootprint(a20, 2f);
        fixture.setActorCircleFootprint(a22, 2f);

        fixture.process();

        Assert.assertArrayEquals(new int[]{a24, a22, a20, a18, tile}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
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

    @Test
    public void repeatedSpatialRunsProduceIdenticalDrawOrder() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int back = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setSortOrder(back, 2, 0, 40);
        int front = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(front, 2, 0, 20);

        fixture.process();
        int[] first = fixture.drawOrder();
        fixture.process();
        Assert.assertArrayEquals(first, fixture.drawOrder());
        fixture.process();
        Assert.assertArrayEquals(first, fixture.drawOrder());
        Assert.assertTrue(tile >= 0);
    }

    @Test
    public void reversedActorCreationOrderKeepsDeterministicSpatialResult() {
        Fixture forward = new Fixture(512);
        forward.createLayer(2, true);
        TiledMapLayerData forwardMap = forward.createBlockMap(3, 3, 16, 16, 300);
        forward.createBlockTiledLayer(1, forwardMap, block(10, 0f, 0f, 2f, 2f));
        forward.createLinkedTile(forwardMap, 0, 0, 101, 1, 10);
        forward.createActorInRenderSlot(8f, 24f, 0, 2, 50);
        forward.setSortOrder(50, 2, 0, 40);
        forward.createActorInRenderSlot(8f, 8f, 0, 2, 51);
        forward.setSortOrder(51, 2, 0, 20);

        Fixture reversed = new Fixture(512);
        reversed.createLayer(2, true);
        TiledMapLayerData reversedMap = reversed.createBlockMap(3, 3, 16, 16, 300);
        reversed.createBlockTiledLayer(1, reversedMap, block(10, 0f, 0f, 2f, 2f));
        reversed.createLinkedTile(reversedMap, 0, 0, 101, 1, 10);
        reversed.createActorInRenderSlot(8f, 8f, 0, 2, 51);
        reversed.setSortOrder(51, 2, 0, 20);
        reversed.createActorInRenderSlot(8f, 24f, 0, 2, 50);
        reversed.setSortOrder(50, 2, 0, 40);

        forward.process();
        reversed.process();

        Assert.assertArrayEquals(forward.drawOrder(), reversed.drawOrder());
    }

    @Test
    public void actorFootBelowTileCellBlockReferenceRendersBeforeLinkedTile() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorFootBelowTileCellBlockReferenceRendersBeforeLinkedTileFromEarlierBucket() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorOutsideBlockKeepsLegacyPosition() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actor = fixture.createActor(40f, 40f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
    }

    @Test
    public void actorCircleFootprintFindsBlockWhenFootPointMisses() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 1f, 1f, 1f, 1f));
        int tile = fixture.createLinkedTile(map, 1, 1, 101, 1, 20);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 9f, 16f, 0f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorCircleFootprintMissDoesNotCreateBlockIntent() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 2f, 2f, 1f, 1f));
        int tile = fixture.createLinkedTile(map, 2, 2, 101, 1, 10);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 4f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorWithoutCircleFootprintDoesNotCreateBlockIntent() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actor = fixture.createActor(8f, 18f, 0, 2, true);
        fixture.clearActorPhysicsFootprint(actor);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorBelowAuthoredBlockSegmentRendersBeforeBlock() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{actor, tile}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorBelowAuthoredBlockSegmentRendersBeforeBlockFromEarlierBucket() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 18f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{actor, tile}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorLeftOfLowerBaseSegmentKeepsLegacyOrder() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(-1f, 18f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockIgnoresActorOutsideFiniteBottomSegmentInfluence() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(6, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actor = fixture.createActor(64f, 8f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void isoAuthoredBlockUsesLineSideForBottomSegmentRelation() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(map.tileToWorldX(0.25f, 0.25f), map.tileToWorldY(0.25f, 0.25f), 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());

        fixture.setActorPosition(actor, map.tileToWorldX(0.8f, 0.8f), map.tileToWorldY(0.8f, 0.8f));
        fixture.setSortOrder(actor, 2, 0, 10);
        fixture.process();

        Assert.assertArrayEquals(new int[]{actor, tile}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockUsesFirstAndLastLinkedRefsAsDrawListSlice() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 2f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        block.addLinkedTileRef(0, 1, 202);
        fixture.createBlockTiledLayer(1, map, block);
        int firstTile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int lastTile = fixture.createLinkedTile(map, 0, 1, 202, 1, 30);
        int actor = fixture.createActor(8f, 40f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{actor, firstTile, lastTile}, fixture.drawOrder());

        fixture.setActorPosition(actor, 8f, 8f);
        fixture.setSortOrder(actor, 2, 0, 5);
        fixture.process();

        Assert.assertArrayEquals(new int[]{firstTile, lastTile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockOutsideActorQueryBboxStillUsesBottomSegmentRelation() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 2f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 2, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 2, 101, 1, 20);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorBetweenTwoAuthoredWallsRendersBetweenTheirLinkedTiles() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData backWall = block(10, 0f, 0f, 1f, 1f);
        backWall.beginAuthoredLinkedTileRefs();
        backWall.addLinkedTileRef(0, 0, 101);
        SpatialBlockData frontWall = block(20, 0f, 1f, 1f, 1f);
        frontWall.beginAuthoredLinkedTileRefs();
        frontWall.addLinkedTileRef(0, 1, 202);
        fixture.createBlockTiledLayer(1, map, backWall, frontWall);
        int backTile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int frontTile = fixture.createLinkedTile(map, 0, 1, 202, 1, 30);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{backTile, frontTile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockWithVerticalNonOverlapIsNotApplicable() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.altitude = 10f;
        block.height = 5f;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockUsesActorBaseAltitudeInsteadOfVisualHeightOverlap() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.altitude = 10f;
        block.height = 5f;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        SpatialHeightComponent height = fixture.world.getMapper(SpatialHeightComponent.class).get(actor);
        height.altitude = 0f;
        height.height = 20f;
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockCapturesHighestReachedLowerAltitude() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.altitude = 10f;
        block.height = 5f;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        SpatialHeightComponent height = fixture.world.getMapper(SpatialHeightComponent.class).get(actor);
        height.altitude = 12f;
        height.height = 1f;
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockSelectionUsesHighestReachedAltitudeBeforeBlockId() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData ground = block(10, 0f, 0f, 1f, 1f);
        ground.altitude = 0f;
        ground.beginAuthoredLinkedTileRefs();
        ground.addLinkedTileRef(0, 0, 101);
        SpatialBlockData upper = block(20, 0f, 0f, 1f, 1f);
        upper.altitude = 155f;
        upper.beginAuthoredLinkedTileRefs();
        upper.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, ground, upper);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        SpatialHeightComponent height = fixture.world.getMapper(SpatialHeightComponent.class).get(actor);
        height.altitude = 170f;
        height.height = 1f;
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockWithInvalidLinkedRefsIsNotApplicable() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);

        fixture.process();

        Assert.assertArrayEquals(new int[]{actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorBehindSpatialTileRendersBeforeTileAnchorRegardlessOfLayerOrder() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int actor = fixture.createActor(8f, 4f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorInFrontOfSpatialTileRendersAfterTileAnchor() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 20);
        int actor = fixture.createActor(8f, 18f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorOutsideSpatialTileGroundFootprintDoesNotMove() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int actor = fixture.createActor(40f, 40f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void tileWithoutSpatialHeightDoesNotAffectActorDepth() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createSpatialTiledLayerWithMap(1, map);
        map.setTile(0, 0, 101);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int actor = fixture.createActor(8f, 4f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorMovingAroundSpatialTileFlipsLocalDepthStably() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int actor = fixture.createActor(8f, 4f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();
        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());

        fixture.setActorPosition(actor, 8f, 18f);
        fixture.process();
        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredSpatialBlockWinsOverDirectSpatialTileCandidate() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 2f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 1, 202);
        fixture.createBlockTiledLayer(1, map, block);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int directTile = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int authoredTile = fixture.createLinkedTile(map, 0, 1, 202, 1, 30);
        int actor = fixture.createActor(8f, 18f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{directTile, authoredTile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredSpatialBlockRemainsBestWhenLaterSpatialTileIsCloser() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData blockMap = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 2f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 1, 202);
        fixture.createBlockTiledLayer(1, blockMap, block);
        int authoredTile = fixture.createLinkedTile(blockMap, 0, 1, 202, 1, 20);

        TiledMapLayerData directMap = fixture.createBlockMap(3, 3, 16, 16, 400);
        fixture.createSpatialTiledLayerWithMap(3, directMap);
        fixture.setSpatialTile(directMap, 0, 0, 101, 0f, 12f);
        int directTile = fixture.createTiledSlot(directMap.slotForTile(0, 0), 3, 30);

        int actor = fixture.createActor(8f, 18f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{authoredTile, actor, directTile}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void directSpatialTileStillOrdersActorWithoutSpatialBlock() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 20);
        int actor = fixture.createActor(8f, 18f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredSpatialBlocksStillUseDeterministicTieBreak() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData higherId = block(20, 0f, 0f, 1f, 2f);
        higherId.beginAuthoredLinkedTileRefs();
        higherId.addLinkedTileRef(0, 1, 202);
        SpatialBlockData lowerId = block(10, 0f, 0f, 1f, 2f);
        lowerId.beginAuthoredLinkedTileRefs();
        lowerId.addLinkedTileRef(0, 1, 202);
        fixture.createBlockTiledLayer(1, map, higherId, lowerId);
        int authoredTile = fixture.createLinkedTile(map, 0, 1, 202, 1, 20);
        int actor = fixture.createActor(8f, 18f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{authoredTile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void overlappingVisualAnchorsWithSeparatedVolumesDoNotCreatePlannerConflict() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(6, 3, 16, 16, 300);

        SpatialBlockData left = block(10, 0f, 1f, 2.4f, 1f);
        left.beginAuthoredLinkedTileRefs();
        left.addLinkedTileRef(0, 1, 101);
        left.addLinkedTileRef(1, 1, 102);
        left.addLinkedTileRef(2, 1, 103);

        SpatialBlockData right = block(20, 2.7f, 1f, 2.3f, 1f);
        right.beginAuthoredLinkedTileRefs();
        right.addLinkedTileRef(2, 1, 103);
        right.addLinkedTileRef(3, 1, 104);
        right.addLinkedTileRef(4, 1, 105);

        fixture.createBlockTiledLayer(1, map, left, right);
        int tile0 = fixture.createLinkedTile(map, 0, 1, 101, 1, 10);
        int tile1 = fixture.createLinkedTile(map, 1, 1, 102, 1, 11);
        int sharedTile = fixture.createLinkedTile(map, 2, 1, 103, 1, 12);
        int tile3 = fixture.createLinkedTile(map, 3, 1, 104, 1, 13);
        int tile4 = fixture.createLinkedTile(map, 4, 1, 105, 1, 14);
        int actor = fixture.createActor(39.5f, 40f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 3f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile0, tile1, sharedTile, tile3, tile4, actor}, fixture.drawOrder());
        assertSameTiledSubsequence(fixture.beforeSpatialOrder,
                fixture.beforeSpatialDomains,
                fixture.drawOrder(),
                fixture.drawDomains(),
                tile0, tile1, sharedTile, tile3, tile4);
        Assert.assertEquals(1, fixture.countDrawEntry(RenderSourceDomain.SOURCE_ECS, actor));
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void authoredBlockOnHigherLayerCanWinWithoutBestReplacementPath() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);

        TiledMapLayerData lowerMap = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData lowerLayerBlock = block(20, 0f, 0f, 1f, 1f);
        lowerLayerBlock.beginAuthoredLinkedTileRefs();
        lowerLayerBlock.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, lowerMap, lowerLayerBlock);
        int lowerTile = fixture.createLinkedTile(lowerMap, 0, 0, 101, 1, 10);

        TiledMapLayerData upperMap = fixture.createBlockMap(3, 3, 16, 16, 400);
        SpatialBlockData upperLayerBlock = block(10, 0f, 1f, 1f, 1f);
        upperLayerBlock.beginAuthoredLinkedTileRefs();
        upperLayerBlock.addLinkedTileRef(0, 1, 202);
        fixture.createBlockTiledLayer(3, upperMap, upperLayerBlock);
        int upperTile = fixture.createLinkedTile(upperMap, 0, 1, 202, 3, 30);

        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{lowerTile, upperTile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void colonoKeepsOutOfInfluenceAuthoredBlockFromWinningTieBreak() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);

        TiledMapLayerData layer3Map = fixture.createBlockMap(4, 3, 16, 16, 300);
        SpatialBlockData outOfInfluence = block(10, 0f, 0f, 1f, 1f);
        outOfInfluence.beginAuthoredLinkedTileRefs();
        outOfInfluence.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(3, layer3Map, outOfInfluence);
        int layer3Tile = fixture.createLinkedTile(layer3Map, 0, 0, 101, 3, 10);

        TiledMapLayerData layer4Map = fixture.createBlockMap(4, 3, 16, 16, 400);
        SpatialBlockData relevant = block(20, 1f, 0f, 1f, 1f);
        relevant.beginAuthoredLinkedTileRefs();
        relevant.addLinkedTileRef(1, 0, 202);
        fixture.createBlockTiledLayer(4, layer4Map, relevant);
        int layer4Tile = fixture.createLinkedTile(layer4Map, 1, 0, 202, 4, 30);

        int actor = fixture.createActor(24f, 8f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{layer3Tile, layer4Tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void unresolvedAuthoredSpatialBlockRefsDoNotOverrideDirectSpatialTile() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 1, 202);
        fixture.createBlockTiledLayer(1, map, block);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int directTile = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 20);
        int actor = fixture.createActor(8f, 18f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertEquals(1, block.linkedTileRefs.size);
        Assert.assertArrayEquals(new int[]{directTile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void isoSpatialTileUsesGroundCellNotTextureBounds() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int actor = fixture.createActor(map.tileToWorldX(0.25f, 0.25f), map.tileToWorldY(0.25f, 0.25f), 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void isoActorClearlyInFrontOfSpatialTileRendersAboveTile() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 20);
        int actor = fixture.createActor(map.tileToWorldX(0.8f, 0.8f), map.tileToWorldY(0.8f, 0.8f), 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void isoActorClearlyBehindSpatialTileRendersBelowTile() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int actor = fixture.createActor(map.tileToWorldX(0.25f, 0.25f), map.tileToWorldY(0.25f, 0.25f), 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorBaseSegmentIntersectingIsoTileBaseRendersBelowTile() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int actor = fixture.createActor(0f, map.tileToWorldY(1f, 0f) - 2f, 0, 2, true);
        fixture.setActorCircleFootprint(actor, 6f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void isoActorRightOfSpatialTileRendersAboveTile() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 20);
        int actor = fixture.createActor(map.tileToWorldX(1.25f, 0.25f), map.tileToWorldY(1.25f, 0.25f), 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void isoActorBottomLeftOfSpatialTileRendersAboveTile() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 20);
        int actor = fixture.createActor(map.tileToWorldX(0.25f, 1.25f), map.tileToWorldY(0.25f, 1.25f), 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void isoActorCrossingTileBaseFlipsOrder() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createSpatialTiledLayerWithMap(1, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 1, 10);
        int actor = fixture.createActor(map.tileToWorldX(0.25f, 0.25f), map.tileToWorldY(0.25f, 0.25f), 0, 2, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();
        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());

        fixture.setActorPosition(actor, map.tileToWorldX(0.8f, 0.8f), map.tileToWorldY(0.8f, 0.8f));
        fixture.process();

        Assert.assertArrayEquals(new int[]{anchor, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void spatialTileOrderIsIndependentFromRelativeActorAndTiledLayers() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(1, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createSpatialTiledLayerWithMap(3, map);
        fixture.setSpatialTile(map, 0, 0, 101, 0f, 12f);
        int anchor = fixture.createTiledSlot(map.slotForTile(0, 0), 3, 20);
        int actor = fixture.createActor(map.tileToWorldX(0.8f, 0.8f), map.tileToWorldY(0.8f, 0.8f), 0, 1, true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 1, 0, 10);

        fixture.process();

        Assert.assertArrayEquals(new int[]{actor, anchor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void verticalNonOverlapDoesNotCreateBlockIntent() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        SpatialBlockData block = block(10, 0f, 0f, 2f, 2f);
        block.altitude = 10f;
        block.height = 5f;
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
    }

    @Test
    public void spatialActorWithoutCircleFixtureDoesNotCreateBlockIntent() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.clearActorPhysicsFootprint(actor);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void circleFixtureWithoutSpatialHeightDoesNotCreateBlockIntent() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actor = fixture.createActor(8f, 24f, 0, 2, false);
        fixture.addPhysicsCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void blockWithoutLinkedTilesKeepsActorStable() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int actor = fixture.createActor(8f, 8f, 0, 2, true);

        fixture.process();

        Assert.assertArrayEquals(new int[]{actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void blockFootprintLinksNeighborTileWhenOriginCellIsEmpty() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 16, 16, 300);
        SpatialBlockData block = block(10, 0.75f, 0f, 0.75f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(1, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 1, 0, 101, 1, 10);
        int actor = fixture.createActor(20f, 8f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void fullyUnresolvedBlockIsSkippedWithoutDeletingLinkedRefs() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 2, 2, 101, 1, 10);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertEquals(1, block.linkedTileRefs.size);
        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void clearedLinkedTileCellSkipsBlockAndRepaintReactivatesIt() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 5);

        fixture.process();
        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());

        map.setTile(0, 0, 0);
        fixture.tiledState.visible[tile] = false;
        fixture.tiledState.enabled[tile] = false;
        fixture.process();
        Assert.assertEquals(1, block.linkedTileRefs.size);
        Assert.assertArrayEquals(new int[]{actor}, fixture.drawOrder());

        map.setTile(0, 0, 202);
        fixture.tiledState.visible[tile] = true;
        fixture.tiledState.enabled[tile] = true;
        fixture.process();
        Assert.assertEquals(1, block.linkedTileRefs.size);
        Assert.assertEquals(101, block.linkedTileRefs.get(0).tileAssetId);
        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void hiddenLinkedTileLayerSkipsBlockAndShowReactivatesIt() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        SpatialBlockData block = block(10, 0f, 0f, 1f, 1f);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 5);

        fixture.process();
        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());

        fixture.layerState.enabled[1] = false;
        fixture.process();
        Assert.assertEquals(1, block.linkedTileRefs.size);
        Assert.assertArrayEquals(new int[]{actor}, fixture.drawOrder());

        fixture.layerState.enabled[1] = true;
        fixture.process();
        Assert.assertEquals(1, block.linkedTileRefs.size);
        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void multipleActorsAroundSameBlockSplitAroundLinkedTileBase() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int back = fixture.createActor(8f, 12f, 0, 2, true);
        fixture.setSortOrder(back, 2, 0, 20);
        int fartherBack = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(fartherBack, 2, 0, 30);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, back, fartherBack}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void blockOrderingDoesNotMoveActorAcrossSpatialActorWithHigherFootY() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int lower = fixture.createActor(24f, 18f, 0, 2, true);
        fixture.setSortOrder(lower, 2, 0, 10);
        int higher = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(higher, 2, 0, 30);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, lower, higher}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void actorMovesBeforeBlockWhenRelationClampsToFrontBucket() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 1f, 1f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int tileB = fixture.createTiledSlot(301, 1, 30);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 40);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, tileB, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void spatialBlockKeepsInternalLinkedTileDrawOrder() {
        Fixture fixture = new Fixture(512);
        TiledMapLayerData map = fixture.createBlockMap(
                5, 2, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 4f, 1f));
        int tile0 = fixture.createLinkedTile(map, 0, 0, 101, 1, 40);
        int tile1 = fixture.createLinkedTile(map, 1, 0, 101, 1, 30);
        int tile2 = fixture.createLinkedTile(map, 2, 0, 101, 1, 20);
        int tile3 = fixture.createLinkedTile(map, 3, 0, 101, 1, 10);
        int adjacent = fixture.createLinkedTile(map, 4, 0, 101, 1, 0);

        fixture.process();

        Assert.assertArrayEquals(new int[]{adjacent, tile3, tile2, tile1, tile0}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void spatialSortNeverChangesTiledSlotRelativeOrder() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(
                5, 2, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 4f, 1f));
        int tile0 = fixture.createLinkedTile(map, 0, 0, 101, 1, 40);
        int tile1 = fixture.createLinkedTile(map, 1, 0, 101, 1, 30);
        int tile2 = fixture.createLinkedTile(map, 2, 0, 101, 1, 20);
        int tile3 = fixture.createLinkedTile(map, 3, 0, 101, 1, 10);
        int adjacent = fixture.createLinkedTile(map, 4, 0, 101, 1, 0);
        int actor = fixture.createActor(
                map.tileToWorldX(0.25f, 0.25f),
                map.tileToWorldY(0.25f, 0.25f),
                0,
                2,
                true);
        fixture.setActorCircleFootprint(actor, 2f);
        fixture.setSortOrder(actor, 2, 0, 5);

        fixture.process();

        assertSameTiledSubsequence(
                fixture.beforeSpatialOrder,
                fixture.beforeSpatialDomains,
                fixture.drawOrder(),
                fixture.drawDomains(),
                adjacent, tile3, tile2, tile1, tile0);
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void spatialDisabledBaselineKeepsFinalDrawOrderIdenticalToPreSpatialOrder() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(2, false);
        TiledMapLayerData map = fixture.createBlockMap(
                4, 2, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createTiledLayerWithMap(1, map, false);
        int tile0 = fixture.createLinkedTile(map, 0, 0, 101, 1, 30);
        int tile1 = fixture.createLinkedTile(map, 1, 0, 101, 1, 20);
        int tile2 = fixture.createLinkedTile(map, 2, 0, 101, 1, 10);
        int actor = fixture.createActor(8f, 24f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 5);

        fixture.process();

        Assert.assertArrayEquals(fixture.beforeSpatialOrder, fixture.drawOrder());
        assertSameTiledSubsequence(fixture.beforeSpatialOrder, fixture.beforeSpatialDomains,
                fixture.drawOrder(), fixture.drawDomains(), tile2, tile1, tile0);
    }

    @Test
    public void actorCrossingVolumeBaseFlipsOrderAndKeepsTileSubsequence() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(
                4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        SpatialBlockData block = block(10, 0f, 0f, 2f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(0, 0, 101);
        block.addLinkedTileRef(1, 0, 101);
        fixture.createBlockTiledLayer(1, map, block);
        int tile0 = fixture.createLinkedTile(map, 0, 0, 101, 1, 20);
        int tile1 = fixture.createLinkedTile(map, 1, 0, 101, 1, 10);
        int actor = fixture.createActor(
                map.tileToWorldX(0.25f, 0.25f),
                map.tileToWorldY(0.25f, 0.25f),
                0,
                2,
                true);
        fixture.setActorCircleFootprint(actor, 2f);

        fixture.setSortOrder(actor, 2, 0, 30);
        fixture.process();
        int[] first = fixture.drawOrder();
        Assert.assertArrayEquals(new int[]{tile1, tile0, actor}, first);
        assertSameTiledSubsequence(fixture.beforeSpatialOrder, fixture.beforeSpatialDomains,
                first, fixture.beforeSubmitDomains, tile1, tile0);

        fixture.setActorPosition(actor, map.tileToWorldX(1.75f, 1.75f), map.tileToWorldY(1.75f, 1.75f));
        fixture.setSortOrder(actor, 2, 0, 5);
        fixture.process();

        Assert.assertArrayEquals(new int[]{actor, tile1, tile0}, fixture.drawOrder());
        assertSameTiledSubsequence(fixture.beforeSpatialOrder, fixture.beforeSpatialDomains,
                fixture.drawOrder(), fixture.drawDomains(), tile1, tile0);
    }

    @Test
    public void equalTieSpatialInputsStayDeterministicAndKeepTileOrder() {
        Fixture fixture = new Fixture(512, true);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 1f));
        int tile0 = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int tile1 = fixture.createLinkedTile(map, 1, 0, 101, 1, 10);
        int actorA = fixture.createActor(8f, 8f, 0, 2, true);
        int actorB = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setActorCircleFootprint(actorA, 2f);
        fixture.setActorCircleFootprint(actorB, 2f);
        fixture.setSortOrder(actorA, 2, 0, 20);
        fixture.setSortOrder(actorB, 2, 0, 20);

        fixture.process();
        int[] first = fixture.drawOrder();
        fixture.process();
        Assert.assertArrayEquals(first, fixture.drawOrder());
        fixture.process();
        Assert.assertArrayEquals(first, fixture.drawOrder());
        assertSameTiledSubsequence(fixture.beforeSpatialOrder, fixture.beforeSpatialDomains,
                fixture.drawOrder(), fixture.drawDomains(), tile0, tile1);
    }

    @Test
    public void multiLayerSpatialSortKeepsEachTiledLayerDomainOrder() {
        Fixture fixture = new Fixture(1024, true);
        fixture.createLayer(2, true);
        fixture.createLayer(4, true);

        TiledMapLayerData lowerMap = fixture.createBlockMap(
                4, 2, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createBlockTiledLayer(1, lowerMap, block(10, 0f, 0f, 2f, 1f));
        int lower0 = fixture.createLinkedTile(lowerMap, 0, 0, 101, 1, 30);
        int lower1 = fixture.createLinkedTile(lowerMap, 1, 0, 101, 1, 20);
        int lower2 = fixture.createLinkedTile(lowerMap, 2, 0, 101, 1, 10);

        TiledMapLayerData upperMap = fixture.createBlockMap(
                4, 2, 90, 30, 400, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createBlockTiledLayer(3, upperMap, block(20, 0f, 0f, 2f, 1f));
        int upper0 = fixture.createLinkedTile(upperMap, 0, 0, 202, 3, 30);
        int upper1 = fixture.createLinkedTile(upperMap, 1, 0, 202, 3, 20);
        int upper2 = fixture.createLinkedTile(upperMap, 2, 0, 202, 3, 10);

        int actorA = fixture.createActor(8f, 8f, 0, 2, true);
        int actorB = fixture.createActor(8f, 24f, 0, 4, true);
        fixture.setActorCircleFootprint(actorA, 2f);
        fixture.setActorCircleFootprint(actorB, 2f);

        fixture.process();

        assertSameTiledSubsequence(fixture.beforeSpatialOrder, fixture.beforeSpatialDomains,
                fixture.drawOrder(), fixture.drawDomains(), lower2, lower1, lower0);
        assertSameTiledSubsequence(fixture.beforeSpatialOrder, fixture.beforeSpatialDomains,
                fixture.drawOrder(), fixture.drawDomains(), upper2, upper1, upper0);
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void unsupportedBlockOrientationIsSkippedWithoutMovingActor() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        SpatialBlockData block = block(10, 0f, 0f, 2f, 2f);
        block.orientation = SpatialBlockOrientation.TILE_AXIS_X;
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 300);
        fixture.createBlockTiledLayer(1, map, block);
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actor = fixture.createActor(8f, 8f, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 20);

        try {
            fixture.process();
            Assert.fail("Expected unsupported spatial block orientation to fail.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("not valid for relation solving"));
        }
    }

    @Test
    public void isoBlockOrderingUsesConfiguredNonTwoToOneProjection() {
        Fixture fixture = new Fixture(512);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(4, 4, 90, 30, 300, SceneMetaRuntime.TiledProjection.ISO);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 1f, 1f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);

        float actorX = map.tileToWorldX(0.25f, 0.25f);
        float actorY = map.tileToWorldY(0.25f, 0.25f);
        int actor = fixture.createActor(actorX, actorY, 0, 2, true);
        fixture.setSortOrder(actor, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, actor}, fixture.drawOrder());
        fixture.assertDrawListIntegrity();
    }

    @Test
    public void highLinkedTileSlotAndDifferentActorEntityRemainSafe() {
        Fixture fixture = new Fixture(1024);
        fixture.createLayer(2, true);
        TiledMapLayerData map = fixture.createBlockMap(3, 3, 16, 16, 700);
        fixture.createBlockTiledLayer(1, map, block(10, 0f, 0f, 2f, 2f));
        int tile = fixture.createLinkedTile(map, 0, 0, 101, 1, 10);
        int actorEntity = fixture.createActorInRenderSlot(8f, 8f, 0, 2, 50);
        fixture.setSortOrder(50, 2, 0, 20);

        fixture.process();

        Assert.assertArrayEquals(new int[]{tile, 50}, fixture.drawOrder());
        Assert.assertTrue(actorEntity >= 0);
        fixture.assertDrawListIntegrity();
    }

    private static final class Fixture {
        static final float PIXELS_PER_METER = 100f;

        final RenderStateSOA state;
        final TiledMapRenderState tiledState;
        final LayerStateSOA layerState;
        final DrawList drawList;
        final RenderStats stats;
        final World world;
        final SpatialRenderOrderSystem spatial;

        int[] beforeSpatialOrder;
        byte[] beforeSpatialDomains;
        int[] beforeSubmitOrder;
        byte[] beforeSubmitDomains;

        Fixture(int capacity) {
            this(capacity, false);
        }

        Fixture(int capacity, boolean captureOrder) {
            state = new RenderStateSOA(capacity);
            tiledState = new TiledMapRenderState(16);
            layerState = new LayerStateSOA(16);
            for (int i = 0; i < layerState.enabled.length; i++) {
                layerState.enabled[i] = true;
            }
            drawList = new DrawList(capacity);
            stats = new RenderStats();
            spatial = new SpatialRenderOrderSystem(state, tiledState, drawList);

            WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
                    .with(
                            new RenderBuildDrawListSystem(state, tiledState, layerState, drawList, stats, 128, -1, -1),
                            new RenderSortSystem(state, tiledState, drawList)
                    );
            if (captureOrder) {
                builder.with(new BeforeSpatialCaptureSystem(drawList, (order, domains) -> {
                    beforeSpatialOrder = order;
                    beforeSpatialDomains = domains;
                }));
            }
            builder.with(spatial);
            if (captureOrder) {
                builder.with(new BeforeSubmitCaptureSystem(drawList, (order, domains) -> {
                    beforeSubmitOrder = order;
                    beforeSubmitDomains = domains;
                }));
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

        int createBlockTiledLayer(int layerIndex, TiledMapLayerData map, SpatialBlockData... blocks) {
            ensureMapRenderRefs(map);
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = layerIndex;
            layer.type = LayerComponent.TYPE_TILED;
            layer.spatialEnabled = true;

            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.spatialEnabled = true;
            tiled.data = map;
            if (tiled.data != null) {
                tiled.data.spatialEnabled = true;
            }

            SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(entity);
            for (SpatialBlockData block : blocks) {
                ensureV2LinkedRefs(block, map);
                component.blocks.add(block);
            }
            return entity;
        }

        int createSpatialTiledLayerWithMap(int layerIndex, TiledMapLayerData map) {
            ensureMapRenderRefs(map);
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = layerIndex;
            layer.type = LayerComponent.TYPE_TILED;
            layer.spatialEnabled = true;

            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.spatialEnabled = true;
            tiled.data = map;
            if (tiled.data != null) {
                tiled.data.spatialEnabled = true;
            }
            return entity;
        }

        int createTiledLayerWithMap(int layerIndex, TiledMapLayerData map, boolean spatialEnabled) {
            ensureMapRenderRefs(map);
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = layerIndex;
            layer.type = LayerComponent.TYPE_TILED;
            layer.spatialEnabled = spatialEnabled;

            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.spatialEnabled = spatialEnabled;
            tiled.data = map;
            if (tiled.data != null) {
                tiled.data.spatialEnabled = spatialEnabled;
            }
            return entity;
        }

        void setSpatialTile(TiledMapLayerData map, int gx, int gy, int assetId, float altitude, float height) {
            map.setTile(gx, gy, assetId);
            map.setTileSpatialOverride(gx, gy, altitude, height, 0);
        }

        int createLinkedTile(TiledMapLayerData map, int gx, int gy, int assetId, int layerIndex, int runtimeOrder) {
            map.setTile(gx, gy, assetId);
            ensureMapRenderRefs(map);
            int slot = map.slotForTile(gx, gy);
            int tiledRenderRef = map.tiledRenderRefForTile(gx, gy);
            enableSlot(slot, layerIndex, 0, runtimeOrder);
            state.entityId[slot] = -1;
            tiledState.setLegacySlotForRef(tiledRenderRef, slot);
            writeTiledRenderData(tiledRenderRef, slot);
            tiledState.addVisibleRef(tiledRenderRef);
            return tiledRenderRef;
        }

        void setActorCircleFootprint(int actor, float radiusPx) {
            setActorCircleFootprint(actor, radiusPx, 0f, 0f);
        }

        void setActorCircleFootprint(int actor, float radiusPx, float offsetXPx, float offsetYPx) {
            PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).get(actor);
            if (fixtures == null) return;
            fixtures.fixtures.clear();
            FixtureDefData fixture = new FixtureDefData();
            fixture.shapeType = FixtureDefData.SHAPE_CIRCLE;
            fixture.radius = radiusPx / SpatialRenderOrderSystemTest.Fixture.PIXELS_PER_METER;
            fixture.offsetX = offsetXPx / SpatialRenderOrderSystemTest.Fixture.PIXELS_PER_METER;
            fixture.offsetY = offsetYPx / SpatialRenderOrderSystemTest.Fixture.PIXELS_PER_METER;
            fixtures.fixtures.add(fixture);
        }

        void addPhysicsCircleFootprint(int actor, float radiusPx) {
            world.getMapper(PhysicsBodyComponent.class).create(actor);
            world.getMapper(PhysicsFixturesComponent.class).create(actor);
            setActorCircleFootprint(actor, radiusPx);
        }

        void clearActorPhysicsFootprint(int actor) {
            PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).get(actor);
            if (fixtures != null) {
                fixtures.fixtures.clear();
            }
        }

        TiledMapLayerData createBlockMap(int width, int height, int tileWidth, int tileHeight, int startSlot) {
            return createBlockMap(width, height, tileWidth, tileHeight, startSlot, SceneMetaRuntime.TiledProjection.ORTHO);
        }

        TiledMapLayerData createBlockMap(int width,
                                         int height,
                                         int tileWidth,
                                         int tileHeight,
                                         int startSlot,
                                         SceneMetaRuntime.TiledProjection projection) {
            TiledMapLayerData map = new TiledMapLayerData(width, height, tileWidth, tileHeight, Math.max(width, height), projection);
            map.initSlotRange(startSlot, startSlot + width * height);
            ensureMapRenderRefs(map);
            return map;
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
                addPhysicsCircleFootprint(entity, 1f);
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
            addPhysicsCircleFootprint(entity, 1f);

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
            int tiledRenderRef = tiledRefForLegacySlot(slot);
            if (tiledRenderRef < 0) {
                tiledRenderRef = tiledState.registerLegacySlot(slot);
            }
            writeTiledRenderData(tiledRenderRef, slot);
            tiledState.addVisibleRef(tiledRenderRef);
            return tiledRenderRef;
        }

        int createRenderOnlySlot(int slot, int layerIndex, int z, int runtimeOrder) {
            enableSlot(slot, layerIndex, z, runtimeOrder);
            state.entityId[slot] = -1;
            int tiledRenderRef = tiledRefForLegacySlot(slot);
            if (tiledRenderRef < 0) {
                tiledRenderRef = tiledState.registerLegacySlot(slot);
            }
            writeTiledRenderData(tiledRenderRef, slot);
            tiledState.addVisibleRef(tiledRenderRef);
            return tiledRenderRef;
        }

        int legacySlotForRef(int tiledRenderRef) {
            return tiledState.legacySlotForRef(tiledRenderRef);
        }

        int tiledRefForLegacySlot(int legacySlot) {
            for (int ref = 0; ref < tiledState.getRefCount(); ref++) {
                if (tiledState.legacySlotForRef(ref) == legacySlot) {
                    return ref;
                }
            }
            return -1;
        }

        void ensureMapRenderRefs(TiledMapLayerData map) {
            if (map == null) return;
            for (int cy = 0; cy < map.getChunksY(); cy++) {
                for (int cx = 0; cx < map.getChunksX(); cx++) {
                    TileChunk chunk = map.getChunk(cx, cy);
                    if (chunk == null) continue;
                    if (chunk.renderRefStartIndex < 0 || chunk.renderRefCount != chunk.soaCount) {
                        chunk.renderRefStartIndex = tiledState.registerLegacyRange(chunk.soaStartIndex, chunk.soaCount);
                        chunk.renderRefCount = chunk.soaCount;
                    } else {
                        for (int i = 0; i < chunk.soaCount; i++) {
                            tiledState.setLegacySlotForRef(chunk.renderRefStartIndex + i, chunk.soaStartIndex + i);
                        }
                    }
                }
            }
        }

        void writeTiledRenderData(int tiledRenderRef, int legacySlot) {
            tiledState.setRenderDataForRef(
                    tiledRenderRef,
                    state.textureHandle[legacySlot],
                    state.shader[legacySlot],
                    state.blend[legacySlot],
                    state.layerIndex[legacySlot],
                    state.paramsId[legacySlot],
                    state.customParamsId[legacySlot],
                    state.sortKey[legacySlot],
                    state.x1[legacySlot],
                    state.y1[legacySlot],
                    state.x2[legacySlot],
                    state.y2[legacySlot],
                    state.x3[legacySlot],
                    state.y3[legacySlot],
                    state.x4[legacySlot],
                    state.y4[legacySlot],
                    state.u1[legacySlot],
                    state.v1[legacySlot],
                    state.u2[legacySlot],
                    state.v2[legacySlot],
                    state.colorPacked[legacySlot],
                    state.a[legacySlot],
                    state.repeatFlags[legacySlot]
            );
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
            state.x2[slot] = 1f;
            state.x3[slot] = 1f;
            state.y3[slot] = 1f;
            state.y4[slot] = 1f;
            state.u2[slot] = 1f;
            state.v2[slot] = 1f;
            state.colorPacked[slot] = 1f;
            state.a[slot] = 1f;
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

        byte[] drawDomains() {
            byte[] out = new byte[drawList.size];
            System.arraycopy(drawList.domainData(), 0, out, 0, drawList.size);
            return out;
        }

        int countDrawEntry(byte domain, int slot) {
            int count = 0;
            for (int i = 0; i < drawList.size; i++) {
                if (drawList.getDomain(i) == domain && drawList.get(i) == slot) {
                    count++;
                }
            }
            return count;
        }

        void assertDrawListIntegrity() {
            for (int i = 0; i < drawList.size; i++) {
                for (int j = i + 1; j < drawList.size; j++) {
                    if (drawList.getDomain(i) == drawList.getDomain(j)) {
                        Assert.assertNotEquals("Duplicate draw entry", drawList.get(i), drawList.get(j));
                    }
                }
            }
        }
    }

    private static SpatialBlockData block(int id, float x, float y, float width, float depth) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.height = 10f;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef((int) Math.floor(x), (int) Math.floor(y), 1);
        return block;
    }

    private static void ensureV2LinkedRefs(SpatialBlockData block, TiledMapLayerData map) {
        if (block == null || map == null || block.linkedTileRefs.size > 0) return;

        int minGx = (int) Math.floor(block.x);
        int minGy = (int) Math.floor(block.y);
        int maxGxExclusive = (int) Math.ceil(block.x + block.width);
        int maxGyExclusive = (int) Math.ceil(block.y + block.depth);
        if (maxGxExclusive <= minGx || maxGyExclusive <= minGy) return;

        block.beginAuthoredLinkedTileRefs();
        for (int gy = minGy; gy < maxGyExclusive; gy++) {
            for (int gx = minGx; gx < maxGxExclusive; gx++) {
                int tileAssetId = map.getTile(gx, gy);
                if (tileAssetId > 0) {
                    block.addLinkedTileRef(gx, gy, tileAssetId);
                }
            }
        }
    }

    private static void assertSameTiledSubsequence(int[] expectedOrder, int[] actualOrder, int... tiledSlots) {
        Assert.assertArrayEquals(
                filterSlots(expectedOrder, tiledSlots),
                filterSlots(actualOrder, tiledSlots));
    }

    private static void assertSameTiledSubsequence(int[] expectedOrder,
                                                   byte[] expectedDomains,
                                                   int[] actualOrder,
                                                   byte[] actualDomains,
                                                   int... tiledSlots) {
        Assert.assertArrayEquals(
                filterSlots(expectedOrder, expectedDomains, tiledSlots),
                filterSlots(actualOrder, actualDomains, tiledSlots));
    }

    private static int[] filterSlots(int[] order, int[] slots) {
        int count = 0;
        for (int slot : order) {
            if (containsSlot(slots, slot)) count++;
        }

        int[] filtered = new int[count];
        int out = 0;
        for (int slot : order) {
            if (containsSlot(slots, slot)) {
                filtered[out++] = slot;
            }
        }
        return filtered;
    }

    private static int[] filterSlots(int[] order, byte[] domains, int[] slots) {
        int count = 0;
        for (int i = 0; i < order.length; i++) {
            if (domains[i] == RenderSourceDomain.SOURCE_TILED && containsSlot(slots, order[i])) count++;
        }

        int[] filtered = new int[count];
        int out = 0;
        for (int i = 0; i < order.length; i++) {
            if (domains[i] == RenderSourceDomain.SOURCE_TILED && containsSlot(slots, order[i])) {
                filtered[out++] = order[i];
            }
        }
        return filtered;
    }

    private static boolean containsSlot(int[] slots, int slot) {
        for (int candidate : slots) {
            if (candidate == slot) return true;
        }
        return false;
    }

    private static int countSlot(int[] order, int slot) {
        int count = 0;
        for (int candidate : order) {
            if (candidate == slot) count++;
        }
        return count;
    }

    private static int indexOf(int[] order, int slot) {
        if (order == null) return -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == slot) return i;
        }
        return -1;
    }

    private static boolean arraysEqual(int[] left, int[] right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) return false;
        }
        return true;
    }

    private interface OrderSink {
        void accept(int[] order, byte[] domains);
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
            byte[] domains = new byte[drawList.size];
            System.arraycopy(drawList.data(), 0, out, 0, drawList.size);
            System.arraycopy(drawList.domainData(), 0, domains, 0, drawList.size);
            sink.accept(out, domains);
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
