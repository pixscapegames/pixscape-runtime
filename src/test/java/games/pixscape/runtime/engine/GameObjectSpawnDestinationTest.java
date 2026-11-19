package games.pixscape.runtime.engine;

import com.artemis.Aspect;
import com.artemis.World;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.GameObjectInstance;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.spatial.SpatialRectangleTestData;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.SpawnPhysicsProbe;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static games.pixscape.runtime.engine.PixscapeEngineGameObjectSpawnTest.*;
import static org.junit.Assert.*;

public class GameObjectSpawnDestinationTest {
    @Test
    public void directFactoryAndDeferredUseExportedIndexAndPreserveLocalState() throws Exception {
        GameObjectAsset definition = asset();
        definition.entities.get(1).entityIndex.zIndex = 17;
        try (Harness h = new Harness(definition)) {
            h.ready();
            int layer = layer(h, 23, false);
            h.frame(0);
            assertNotEquals(layer, 23);
            assertNotEquals(h.engine.api().entities().requireEntityId(layer).stableId(), 23);
            var api = h.engine.api().gameObjects();
            var factory = api.prepare("declared");
            List<GameObjectInstance> instances = new ArrayList<>();
            instances.add(api.spawn("declared", 100, 200, 23));
            instances.add(factory.spawn(100, 200, 23));
            api.spawnDeferred("declared", 100, 200, 23, instances::add, error -> fail(error.toString()));
            assertEquals(2, instances.size());
            h.frame(0);
            assertEquals(3, instances.size());
            for (GameObjectInstance instance : instances) {
                assertEquals(100, instance.root().transform().x(), .001f);
                assertEquals(200, instance.root().transform().y(), .001f);
                assertLayer(h, instance.root(), 23);
                for (EntityRef member : instance.members()) {
                    assertLayer(h, member, 23);
                }
                EntityRef player = instance.requireMember("playerAnimation");
                assertEquals(5, player.transform().x(), .001f);
                assertEquals(7, player.transform().y(), .001f);
                assertEquals(17, player.renderOrder().zIndex());
                assertEquals(instance.root().stableId(), h.engine.getWorld().getMapper(GameObjectMemberComponent.class)
                        .get(player.entityId()).parentStableId);
            }
            assertEquals(0, api.spawn("declared", 0, 0).root().renderOrder().layerIndex());
            assertEquals(0, factory.spawn(0, 0).root().renderOrder().layerIndex());
        }
    }

    @Test
    public void invalidDestinationsRejectBeforeAllocationIncludingLegacyZeroAndQueuedErrors() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready();
            var api = h.engine.api().gameObjects();
            var factory = api.prepare("declared");
            assertNoAllocation(h, () -> api.spawn("declared", 0, 0, 23));
            assertNoAllocation(h, () -> factory.spawn(0, 0, 23));
            int a = layer(h, 23, false);
            int b = layer(h, 23, false);
            h.barrier();
            assertNoAllocation(h, () -> api.spawn("declared", 0, 0, 23));
            assertNoAllocation(h, () -> factory.spawn(0, 0, 23));
            List<String> callbacks = new ArrayList<>();
            int next = h.fixture.sceneA.nextEntityStableId;
            api.spawnDeferred("declared", 0, 0, 23, ignored -> fail("Ambiguous destination accepted"),
                    error -> callbacks.add("ambiguous"));
            h.engine.getWorld().delete(b);
            h.engine.getWorld().delete(a);
            h.engine.getWorld().delete(h.engine.api().entities().requireStableId(1).entityId());
            h.engine.getWorld().process(); // Test publication/purge, never called by spawn.
            assertNoAllocation(h, () -> api.spawn("declared", 0, 0));
            assertNoAllocation(h, () -> factory.spawn(0, 0));
            api.spawnDeferred("declared", 0, 0, ignored -> fail("Missing zero accepted"),
                    error -> callbacks.add("zero"));
            assertTrue(callbacks.isEmpty());
            h.frame(0);
            assertEquals(List.of("ambiguous", "zero"), callbacks);
            assertEquals(next, h.fixture.sceneA.nextEntityStableId);
            assertEquals(0, h.nativeWorld().getBodyCount());
        }
    }

    @Test
    public void descendantFootprintAdmitsInheritedVisualOnlyInSpatialLayerAfterNormalSystems() throws Exception {
        GameObjectAsset definition = spatialAsset();
        try (Harness h = new Harness(definition)) {
            h.ready();
            int spatialLayer = layer(h, 23, true);
            h.engine.getCamera().setToOrtho(false, 800, 600);
            h.frame(0);
            var api = h.engine.api().gameObjects();
            assertNoAllocation(h, () -> api.spawn("declared", 0, 0));
            assertNoAllocation(h, () -> api.prepare("declared").spawn(0, 0, 0));
            GameObjectInstance instance = api.spawn("declared", 100, 200, 23);
            EntityRef owner = instance.requireMember("playerAnimation");
            EntityRef visual = instance.requireMember("visualOnly");
            addHeadlessVisual(h, visual);
            h.frame(0);
            assertFalse(instance.root().spatial().hasOwnFootprint());
            assertTrue(owner.spatial().hasOwnFootprint());
            assertEquals(owner.entityId(), visual.spatial().resolvedOwner().entityId());
            int slot = h.engine.getDynamicEntityRenderState().entityIdToRenderSlot[visual.entityId()];
            assertTrue(slot >= 0);
            assertEquals(23, h.engine.getDynamicEntityRenderState().layerIndex[slot]);
            var state = h.engine.getDynamicEntityRenderState();
            assertTrue("visible=" + state.visible[slot] + ", enabled=" + state.enabled[slot]
                    + ", texture=" + state.textureHandle[slot] + ", draws=" + h.engine.getDrawList().size,
                    visual.spatial().participatesInRenderOrder());

            List<Throwable> errors = new ArrayList<>();
            api.spawnDeferred("declared", 100, 200, 23, ignored -> fail("Disabled Spatial layer accepted"), errors::add);
            int next = h.fixture.sceneA.nextEntityStableId;
            h.engine.getWorld().getMapper(LayerComponent.class).get(spatialLayer).spatialEnabled = false;
            h.frame(0);
            assertEquals(1, errors.size());
            assertEquals(next, h.fixture.sceneA.nextEntityStableId);
            assertFalse(visual.spatial().participatesInRenderOrder());
            assertTrue(instance.exists());
        }
    }

    @Test
    public void deferredDestinationCannotFollowRemovedOrRecycledLayerIdentity() throws Exception {
        for (boolean replace : new boolean[]{false, true}) {
            try (Harness h = new Harness(asset())) {
                h.ready();
                int original = layer(h, 23, false);
                h.frame(0);
                EntityRef old = h.engine.api().entities().requireEntityId(original);
                int oldStableId = old.stableId();
                List<Throwable> errors = new ArrayList<>();
                h.engine.api().gameObjects().spawnDeferred("declared", 0, 0, 23,
                        ignored -> fail("Retired destination accepted"), errors::add);
                old.remove();
                h.engine.getWorld().process();
                if (replace) {
                    int replacement = layer(h, 23, false);
                    assertEquals("Exercise ECS ID reuse", original, replacement);
                    assertNotEquals(oldStableId, h.engine.api().entities().requireEntityId(replacement).stableId());
                    h.barrier();
                }
                int next = h.fixture.sceneA.nextEntityStableId;
                h.frame(0);
                assertEquals(1, errors.size());
                assertEquals(next, h.fixture.sceneA.nextEntityStableId);
                assertEquals(0, h.nativeWorld().getBodyCount());
                assertFalse(old.exists());
            }
        }
    }

    @Test
    public void physicalFailureInExplicitLayerKeepsPreviousInstanceAndMonotonicIds() throws Exception {
        try (Harness h = new Harness(asset())) {
            h.ready();
            layer(h, 23, false);
            h.frame(0);
            var api = h.engine.api().gameObjects();
            GameObjectInstance existing = api.spawn("declared", 100, 200, 23);
            var body = h.player(existing);
            var joint = body.getJointList().first().joint;
            int next = h.fixture.sceneA.nextEntityStableId;
            int traversal = h.hierarchy().topology().traversalSize;
            SpawnPhysicsProbe probe = new SpawnPhysicsProbe(h.engine.getBox2dSyncSystem());
            probe.failFixture = 2;
            assertThrows(IllegalStateException.class, () -> api.spawn("declared", 0, 0, 23));
            assertTrue(h.fixture.sceneA.nextEntityStableId > next);
            assertEquals(traversal, h.hierarchy().topology().traversalSize);
            assertSame(body, h.player(existing));
            assertSame(joint, body.getJointList().first().joint);
            assertEquals(2, h.nativeWorld().getBodyCount());
            assertEquals(2, h.nativeWorld().getJointCount());
            probe.failFixture = 0;
            h.frame(0);
            assertLayer(h, existing.root(), 23);
            assertSame(body, h.player(existing));
        }
    }

    private static int layer(Harness h, int index, boolean spatial) {
        World world = h.engine.getWorld();
        int id = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(id);
        layer.layerIndex = index;
        layer.spatialEnabled = spatial;
        h.engine.api().entities().ensureStableId(id);
        return id;
    }

    private static void assertLayer(Harness h, EntityRef node, int index) {
        assertEquals(index, node.renderOrder().layerIndex());
        assertEquals(index, h.engine.getWorld().getMapper(EntityIndexComponent.class).get(node.entityId()).layerIndex);
    }

    private static void assertNoAllocation(Harness h, Runnable action) {
        int next = h.fixture.sceneA.nextEntityStableId;
        int traversal = h.hierarchy().topology().traversalSize;
        int entities = h.engine.getWorld().getAspectSubscriptionManager().get(Aspect.all()).getEntities().size();
        int bodies = h.nativeWorld().getBodyCount(), joints = h.nativeWorld().getJointCount();
        assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(next, h.fixture.sceneA.nextEntityStableId);
        assertEquals(traversal, h.hierarchy().topology().traversalSize);
        assertEquals(entities, h.engine.getWorld().getAspectSubscriptionManager().get(Aspect.all()).getEntities().size());
        assertEquals(bodies, h.nativeWorld().getBodyCount());
        assertEquals(joints, h.nativeWorld().getJointCount());
    }

    private static GameObjectAsset spatialAsset() {
        GameObjectAsset definition = asset();
        definition.entities.get(1).spatialFootprint = new SpatialFootprintComponent();
        definition.entities.get(1).gameObject = new GameObjectAsset.GameObjectData();
        definition.entities.get(4).parentSourceEntityId = 2;
        return definition;
    }

    private static void addHeadlessVisual(Harness h, EntityRef visual) {
        // Synthetic texture binding only; geometry, composition, render sync and Spatial run normally.
        World world = h.engine.getWorld();
        int id = visual.entityId();
        world.getMapper(DimensionsComponent.class).create(id).width = 32;
        world.getMapper(DimensionsComponent.class).get(id).height = 64;
        world.getMapper(OrientedBoundsComponent.class).create(id);
        world.getMapper(AABBComponent.class).create(id);
        world.getMapper(VisibilityComponent.class).create(id).visible = true;
        world.getMapper(RenderMaterialComponent.class).create(id).textureHandle = 1;
        world.getMapper(TextureRegionComponent.class).create(id).valid = true;
        SpatialRectangleTestData.setImageZone(world.getMapper(SpatialRepresentationComponent.class).create(id), 32, 32, 64, 16, 0);
        world.getSystem(DirtyTrackerSystem.class).geometry(id, GeometryDirty.ALL);
    }
}
