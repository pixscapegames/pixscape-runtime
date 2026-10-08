package games.pixscape.runtime.spatial;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.system.RenderExtractFrameQueueSystem;
import games.pixscape.runtime.system.SpatialRenderOrderSystem;
import games.pixscape.runtime.tiled.*;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

/** Exercises the installed composition system and extracted queue, with two authored wall portions. */
public class SpatialWallContinuationTest {
    @Test public void behindBothSpriteSizesStayBehindAtTheStrictUpperBaseThreshold() {
        for (boolean large : new boolean[]{false, true}) try (Fixture f = new Fixture(large)) {
            for (float height : new float[]{127, 128, 129}) {
                f.pose(true, 0, height);
                f.process();
                f.assertOrder(0);
                Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
                Assert.assertEquals(0, f.spatial.actorOrderingFallbackCount());
                Assert.assertEquals(height, f.height.height, 0);
                Assert.assertEquals(height > 128 ? 2 : 0, f.upperRuntime().relations.relationCount);
                for (int i = 0; i < f.upperRuntime().relations.relationCount; i++)
                    Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE, f.upperRuntime().relations.relationType[i]);
            }
        }
    }

    @Test public void frontBothSpriteSizesStayFrontWithoutFallbackAcrossTheThreshold() {
        for (boolean large : new boolean[]{false, true}) try (Fixture f = new Fixture(large)) {
            for (float height : new float[]{127, 128, 129}) {
                f.pose(false, 0, height);
                f.process();
                f.assertOrder(2);
                Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
                Assert.assertEquals(0, f.spatial.actorOrderingFallbackCount());
            }
        }
    }

    @Test public void movementAndAltitudeChangesRefreshWitnessesWithoutChangingAuthoredHeight() {
        try (Fixture f = new Fixture(true)) {
            for (int cycle = 0; cycle < 3; cycle++) for (float z : new float[]{0, 128}) {
                f.pose(true, z, 129); f.process(); f.assertOrder(z == 0 ? 0 : 1);
                Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
                f.pose(false, z, 129); f.process(); f.assertOrder(2);
                Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
                Assert.assertEquals(129, f.height.height, 0);
                Assert.assertEquals(z + 544, f.transform.y, 0);
            }
            Assert.assertEquals(1, f.cache().rebuildCount());
        }
    }

    @Test public void actorPlaneReferenceKeepsBothSidesWithoutFallbackAtEachAltitude() {
        for (boolean large : new boolean[]{false, true}) try (Fixture f = new Fixture(large)) {
            for (float z : new float[]{0, 32, 127, 128, 129, 160})
                for (float authoredHeight : new float[]{97, 128, 129, 250}) {
                    f.pose(false, z, authoredHeight); f.process(); f.assertOrder(2);
                    Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
                    Assert.assertEquals(0, f.spatial.actorOrderingFallbackCount());
                    f.pose(true, z, authoredHeight); f.process();
                    // The H250 square also reaches the lower diamond's flank at Z127;
                    // the former diameter-wide envelope missed it. At Z128 it no longer overlaps.
                    f.assertOrder(z < 127 || (z == 127 && authoredHeight == 250) ? 0 : 1);
                    Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
                    Assert.assertEquals(0, f.spatial.actorOrderingFallbackCount());
                    Assert.assertEquals(authoredHeight, f.height.height, 0);
                    Assert.assertEquals(z + 624, f.transform.y, 0);
                }
        }
    }

    @Test public void independentCapsAndLongitudinalFacesUseTheActorPlaneBeforePropagation() {
        try (Fixture f = new Fixture(true)) {
            for (boolean orthogonal : new boolean[]{false, true}) {
                f.upperBlock.width = orthogonal ? .25f : 2;
                f.upperBlock.depth = orthogonal ? 2 : .25f;
                f.upperBlocks.revision++;
                for (boolean behind : new boolean[]{false, true}) for (float x : new float[]{0, 240}) {
                    f.pose(behind, 0, 250); f.transform.x = x; f.quad(); f.process();
                    SpatialFaceRelationSolver baseline = f.nativeUpper(new HistoricalVisualAnchorSelector());
                    Assert.assertTrue(baseline.relationCount > 0);
                    for (float z : new float[]{32, 127, 128, 160}) {
                        f.pose(behind, z, 250); f.transform.x = x; f.quad(); f.process();
                        SpatialFaceRelationSolver atAltitude = f.nativeUpper(new HistoricalVisualAnchorSelector());
                        Assert.assertEquals(baseline.relationCount, atAltitude.relationCount);
                        for (int i = 0; i < baseline.relationCount; i++) {
                            Assert.assertEquals(baseline.relationFaceIndex[i], atAltitude.relationFaceIndex[i]);
                            Assert.assertEquals(baseline.relationType[i], atAltitude.relationType[i]);
                        }
                    }
                }
            }
        }
    }

    @Test public void isolatedUpperWallKeepsItsIndependentRelations() {
        try (Fixture f = new Fixture(true)) {
            f.upperBlock.y = 4.5f; f.upperBlocks.revision++;
            f.pose(true, 0, 129); f.process();
            f.assertNativeUpperRetained();
            Assert.assertEquals(0, f.cache().referenceCount(1, 2));
            Assert.assertEquals(1, f.spatial.unresolvedConstraintCount());
        }
    }

    @Test public void exactLateralContactQualifiesButAOneUlpGapDoesNot() {
        try (Fixture f = new Fixture(true)) {
            f.upperBlock.y = 4.25f; f.upperBlocks.revision++;
            f.pose(true, 0, 129); f.process(); f.assertOrder(0);
            Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
            Assert.assertTrue(f.cache().referenceCount(1, 2) > 0);
            f.upperBlock.y = Math.nextUp(4.25f); f.upperBlocks.revision++;
            f.process(); f.assertNativeUpperRetained();
            Assert.assertEquals(0, f.cache().referenceCount(1, 2));
            Assert.assertEquals(1, f.spatial.unresolvedConstraintCount());
        }
    }

    @Test public void verticalGapDoesNotQualifyAsAWallContinuation() {
        try (Fixture f = new Fixture(true)) {
            f.upperBlock.altitude = 129; f.upperBlocks.revision++;
            f.maps[1].originY = f.maps[1].defaultTileAltitude = 129;
            f.pose(true, 0, 130); f.process(); f.assertNativeUpperRetained();
            Assert.assertEquals(0, f.cache().referenceCount(1, 2));
        }
    }

    @Test public void differentDisplayLayerNumbersWithTheSameOffsetsAllowContinuation() {
        try (Fixture f = new Fixture(true)) {
            f.world.getMapper(EntityIndexComponent.class).get(f.owners[1]).layerIndex = 1;
            f.pose(true, 0, 129); f.process(); f.assertOrder(0);
            Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
            Assert.assertTrue(f.cache().referenceCount(1, 2) > 0);
        }
    }

    @Test public void changingEffectiveDisplayOffsetsKeepsNativeDecisionsUntilTheyMatchAgain() {
        try (Fixture f = new Fixture(true)) {
            f.world.getMapper(EntityIndexComponent.class).get(f.owners[1]).layerIndex = 1;
            f.pose(true, 0, 129); f.process(); f.assertOrder(0);
            for (float[] offset : new float[][]{{1, 0}, {0, 1}}) {
                f.upperOffsetX = offset[0]; f.upperOffsetY = offset[1];
                f.process(); f.assertNativeUpperRetained();
                Assert.assertEquals(1, f.cache().rebuildCount());
            }
            f.upperOffsetX = f.upperOffsetY = 0;
            f.process(); f.assertOrder(0);
            Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
            Assert.assertEquals(1, f.cache().rebuildCount());
        }
    }

    @Test public void differentCellDimensionsPreventPropagation() {
        try (Fixture f = new Fixture(true)) {
            f.maps[1].tileWidth = 128;
            f.pose(true, 0, 129); f.process(); f.assertNativeUpperRetained();
            Assert.assertEquals(0, f.cache().referenceCount(1, 2));
        }
    }

    @Test public void commonNonzeroMapOriginPreservesTheLocalContinuation() {
        try (Fixture f = new Fixture(true)) {
            for (int i = 0; i < 2; i++) {
                f.maps[i].originX += 256; f.maps[i].originY += 128;
                int ref = f.refs[i];
                f.tiled.x1[ref] += 256; f.tiled.x2[ref] += 256; f.tiled.x3[ref] += 256; f.tiled.x4[ref] += 256;
                f.tiled.y1[ref] += 128; f.tiled.y2[ref] += 128; f.tiled.y3[ref] += 128; f.tiled.y4[ref] += 128;
            }
            f.pose(true, 0, 129);
            f.transform.x += 256; f.transform.y += 128;
            f.quad(); f.process(); f.assertOrder(0);
            Assert.assertEquals(0, f.spatial.unresolvedConstraintCount());
        }
    }

    @Test public void orthogonalUpperWallDoesNotInheritAParallelWallWitness() {
        try (Fixture f = new Fixture(true)) {
            f.upperBlock.width = .25f; f.upperBlock.depth = 2; f.upperBlocks.revision++;
            f.pose(true, 0, 129); f.process(); f.assertNativeUpperRetained();
            for (int face = 0; face < f.upperRuntime().projected.faceCount; face++)
                Assert.assertEquals(0, f.cache().referenceCount(1, face));
        }
    }

    @Test public void conflictingLocalReferencePortionsKeepTheNativeUpperDecision() {
        try (Fixture f = new Fixture(true)) {
            SpatialBlocksComponent lower = f.world.getMapper(SpatialBlocksComponent.class).get(f.owners[0]);
            SpatialBlockData second = lower.blocks.first().copy();
            second.id = second.structureId = 2; second.y = 4.25f;
            lower.blocks.add(second); lower.revision++;
            f.pose(true, 0, 129); f.process();
            Assert.assertTrue(f.cache().referenceCount(1, 3) > 1);
            SpatialFaceRelationSolver actual = f.upperRuntime().relations;
            boolean found = false;
            for (int i = 0; i < actual.relationCount; i++) if (actual.relationFaceIndex[i] == 3) {
                found = true;
                // The native upper decision now compares the unchanged ground footprint to its own wall.
                Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE, actual.relationType[i]);
            }
            Assert.assertTrue(found);
            // No FRONT/BEHIND priority is invented to repair these independent contradictory references.
            Assert.assertEquals(1, f.spatial.unresolvedConstraintCount());
        }
    }

    @Test public void propagationIsRestrictedToTheFiniteCommonLongitudinalPortion() {
        try (Fixture f = new Fixture(true)) {
            f.upperBlock.width = .5f; f.upperBlocks.revision++;
            f.pose(true, 0, 129); f.process();
            SpatialWallContinuationCache cache = f.cache();
            Assert.assertTrue(cache.referenceCount(1, 2) > 0);
            Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,
                    cache.resolve(1, 2, 0, 150, 8, 0, SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
            Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                    cache.resolve(1, 2, 0, 300, 8, 0, SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE));
        }
    }

    @Test public void invisibleLowerTileCannotSupplyAnInventedWitness() {
        try (Fixture f = new Fixture(true)) {
            f.pose(true, 0, 129); f.process(); f.assertOrder(0);
            f.tiled.visible[f.refs[0]] = false;
            f.process(); f.assertNativeUpperRetained();
            Assert.assertEquals(2, f.queue.size);
            // With no lower witness, the upper native decision still places this footprint behind.
            Assert.assertEquals(RenderSourceDomain.SOURCE_ECS, f.queue.sourceDomain[0]);
            Assert.assertEquals(RenderSourceDomain.SOURCE_TILED, f.queue.sourceDomain[1]);
            f.tiled.visible[f.refs[0]] = true;
            f.process(); f.assertOrder(0);
            Assert.assertEquals(1, f.cache().rebuildCount());
        }
    }

    @Test public void geometryChangesInvalidatePreparedLinksButActorMotionDoesNot() {
        try (Fixture f = new Fixture(true)) {
            f.pose(true, 0, 129); f.process();
            SpatialWallContinuationCache cache = f.cache();
            Assert.assertEquals(1, cache.rebuildCount());
            f.pose(false, 0, 129); f.process();
            Assert.assertEquals(1, cache.rebuildCount());
            f.upperBlock.y += .01f; f.upperBlocks.revision++;
            f.pose(true, 0, 129); f.process(); f.assertNativeUpperRetained();
            Assert.assertEquals(2, cache.rebuildCount());
        }
    }

    @Test public void warmedCompositionHasNoPerFrameAllocations() {
        java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(base instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) base;
        Assume.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        try (Fixture f = new Fixture(true)) {
            f.pose(true, 0, 129);
            for (int i = 0; i < 20000; i++) f.process();
            long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 5000; i++) f.process();
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            Assert.assertEquals(0, allocated);
            Assert.assertEquals(1, f.cache().rebuildCount());
        }
    }

    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static final class Fixture implements AutoCloseable {
        final DynamicEntityRenderState ecs = new DynamicEntityRenderState(1);
        final TiledMapRenderState tiled = new TiledMapRenderState(1024);
        final DrawList draw = new DrawList(8);
        final FrameRenderQueue queue = new FrameRenderQueue(8);
        float upperOffsetX, upperOffsetY;
        final SpatialRenderOrderSystem spatial = new SpatialRenderOrderSystem(ecs, tiled, draw, null,
                new LayerDisplayOffsetResolver() {
                    @Override public void resolveLayer(int layer, Vector2 out) {
                        out.set(layer == 1 ? upperOffsetX : 0, layer == 1 ? upperOffsetY : 0);
                    }
                    @Override public void resolvePhysics(Vector2 out) { out.setZero(); }
                });
        final int[] refs = new int[2], owners = new int[2];
        final TiledMapLayerData[] maps = new TiledMapLayerData[2];
        final World world;
        final int actor, slot;
        final boolean large;
        final TransformComponent transform;
        final SpatialHeightComponent height;
        final SpatialBlockData upperBlock;
        final SpatialBlocksComponent upperBlocks;

        Fixture(boolean large) {
            this.large = large;
            world = new World(new WorldConfigurationBuilder().with(new Seed(), spatial,
                    new RenderExtractFrameQueueSystem(ecs, tiled, draw, queue, new RenderStats(), 1, 1, 1)).build());
            int layerEntity = world.create(); LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
            layer.layerIndex = 0; layer.spatialEnabled = true;
            actor = world.create(); transform = world.getMapper(TransformComponent.class).create(actor);
            world.getMapper(EntityIndexComponent.class).create(actor).layerIndex = 0;
            height = world.getMapper(SpatialHeightComponent.class).create(actor);
            SpatialPhysicsFootprintComponent footprint = world.getMapper(SpatialPhysicsFootprintComponent.class).create(actor);
            footprint.valid = true; footprint.radiusPx = 8;
            slot = ecs.acquireSlotForEntity(actor); ecs.enabled[slot] = ecs.visible[slot] = true;
            ecs.kind[slot] = RenderKind.SPRITE; ecs.textureHandle[slot] = 1; ecs.layerIndex[slot] = 0;
            for (int part = 0; part < 2; part++) {
                TiledMapLayerData map = maps[part] = new TiledMapLayerData(16, 16, 256, 128, 16, TiledProjection.ISO);
                map.spatialEnabled = true; map.defaultTileAltitude = map.originY = part * 128;
                map.visualPaddingTop = 1024; map.visualPaddingLeft = map.visualPaddingRight = 2048; map.setTile(4, 4, 1);
                owners[part] = world.create(); TiledLayerComponent component = world.getMapper(TiledLayerComponent.class).create(owners[part]);
                component.data = map; component.spatialEnabled = true;
                world.getMapper(EntityIndexComponent.class).create(owners[part]).layerIndex = 0;
                SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(owners[part]);
                SpatialBlockData block = new SpatialBlockData(); block.id = block.structureId = 1;
                block.x = block.y = 4; block.width = 2; block.depth = .25f;
                block.altitude = part * 128; block.height = 128;
                block.beginAuthoredLinkedTileRefs(); block.addLinkedTileRef(4, 4, 1); blocks.blocks.add(block);
                TileChunk chunk = map.getChunk(0, 0); chunk.renderRefStartIndex = tiled.registerRefs(chunk.cellCount()); chunk.renderRefCount = chunk.cellCount();
                int ref = refs[part] = map.tiledRenderRefForTile(4, 4);
                tiled.enabled[ref] = tiled.visible[ref] = true; tiled.textureHandle[ref] = 1;
                tiled.x1[ref] = tiled.x4[ref] = 128; tiled.x2[ref] = tiled.x3[ref] = 384;
                tiled.y1[ref] = 512 + part * 128; tiled.y2[ref] = 640 + part * 128;
                tiled.y3[ref] = 768 + part * 128; tiled.y4[ref] = 640 + part * 128;
            }
            upperBlocks = world.getMapper(SpatialBlocksComponent.class).get(owners[1]); upperBlock = upperBlocks.blocks.first();
            pose(true, 0, 129);
        }

        void pose(boolean behind, float altitude, float authoredHeight) {
            transform.x = 240; transform.y = (behind ? 624 : 544) + altitude;
            height.altitude = altitude; height.height = authoredHeight; quad();
        }
        void quad() {
            float halfWidth = large ? 200 : 45, spriteHeight = large ? 440 : 80;
            ecs.x1[slot] = ecs.x4[slot] = transform.x - halfWidth; ecs.x2[slot] = ecs.x3[slot] = transform.x + halfWidth;
            ecs.y1[slot] = ecs.y2[slot] = transform.y - 8; ecs.y3[slot] = ecs.y4[slot] = transform.y + spriteHeight;
        }
        void process() { world.process(); }
        SpatialWallContinuationCache cache() { return (SpatialWallContinuationCache) field(spatial, "wallContinuations"); }
        SpatialLayerFaceRuntime upperRuntime() {
            return ((SpatialLayerRuntimeRegistry) field(spatial, "spatialRuntimeRegistry")).forLayer(owners[1], maps[1]);
        }
        SpatialFaceRelationSolver nativeUpper() {
            return nativeUpper(new SpatialVisualAnchorSelector());
        }
        SpatialFaceRelationSolver nativeUpper(SpatialVisualAnchorSelector selector) {
            SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
            solver.solveVisual((SpatialActorCollector) field(spatial, "actorCollector"), upperRuntime().projected,
                    selector, ecs, tiled, maps[1], upperOffsetX, upperOffsetY);
            return solver;
        }
        void assertNativeUpperRetained() {
            SpatialFaceRelationSolver actual = upperRuntime().relations, nativeSolver = nativeUpper();
            Assert.assertEquals(nativeSolver.relationCount, actual.relationCount);
            for (int i = 0; i < actual.relationCount; i++) {
                Assert.assertEquals(nativeSolver.relationFaceIndex[i], actual.relationFaceIndex[i]);
                Assert.assertEquals(nativeSolver.relationType[i], actual.relationType[i]);
            }
        }
        void assertOrder(int actorPosition) {
            Assert.assertEquals(3, queue.size);
            Assert.assertEquals(RenderSourceDomain.SOURCE_ECS, queue.sourceDomain[actorPosition]);
            Assert.assertEquals(actor, queue.sourceEntity[actorPosition]);
            int tile = 0;
            for (int i = 0; i < queue.size; i++) if (queue.sourceDomain[i] == RenderSourceDomain.SOURCE_TILED)
                Assert.assertEquals(refs[tile++], queue.sourceSlot[i]);
            Assert.assertEquals(2, tile);
        }
        @Override public void close() { world.dispose(); }
        private final class Seed extends BaseSystem {
            @Override protected void processSystem() {
                draw.clearEntries();
                for (int i = 0; i < 2; i++) if (tiled.visible[refs[i]]) draw.addTiledSlot(refs[i]);
                draw.addEcsSlot(slot);
            }
        }
    }
}
