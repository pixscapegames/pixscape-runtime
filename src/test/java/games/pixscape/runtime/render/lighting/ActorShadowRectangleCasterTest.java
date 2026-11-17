package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.TiledRenderState;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialGeometry;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.system.LightingGeometrySystem;
import org.junit.Test;

import static org.junit.Assert.*;

public class ActorShadowRectangleCasterTest {
    private static final float[] Q = {-80, -40, -80, 180, 80, 180, 80, -40};

    private static ActorSpatialGeometry geometry(ActorSpatialRectangleData rectangle, float[] quad) {
        ActorSpatialGeometry geometry = new ActorSpatialGeometry();
        geometry.update(rectangle, quad[0], quad[1], quad[2], quad[3], quad[4], quad[5],
                quad[6], quad[7], false, false);
        return geometry;
    }

    @Test
    public void footprintMovesOnlyShadowPivotAndKeepsProjectedImageAndUvs() {
        ActorSpatialGeometry geometry = geometry(null, Q);
        ActorShadowRectangleCaster caster = new ActorShadowRectangleCaster();
        caster.update(geometry, 45, -17, 10, 3, -2);
        float[] before = caster.vertices.clone();
        caster.update(geometry, -30, 28, 10, 3, -2);
        for (int i = 0; i < caster.vertexCount; i++) {
            int offset = i * 8;
            assertEquals(before[offset + 3], caster.vertices[offset + 3], .00001f);
            assertEquals(before[offset + 4], caster.vertices[offset + 4], .00001f);
            assertEquals(LightingCoordinates.screenX(before[offset], before[offset + 1]),
                    LightingCoordinates.screenX(caster.vertices[offset], caster.vertices[offset + 1]), .0001f);
            assertEquals(LightingCoordinates.screenY(before[offset], before[offset + 1], before[offset + 2]),
                    LightingCoordinates.screenY(caster.vertices[offset], caster.vertices[offset + 1], caster.vertices[offset + 2]), .0001f);
        }
    }

    @Test
    public void lightFacingClippedTrianglesPreserveUvReferenceAndStayInBounds() {
        ActorSpatialRectangleData rectangle = new ActorSpatialRectangleData();
        rectangle.x = .1f;
        rectangle.y = .2f;
        rectangle.width = .7f;
        rectangle.height = .6f;
        float[] deformed = {-80, -40, -110, 180, 50, 220, 80, -20};
        ActorSpatialGeometry geometry = geometry(rectangle, deformed);
        ActorShadowRectangleCaster caster = new ActorShadowRectangleCaster();
        LightingFrame.Caster bounds = new LightingFrame.Caster();
        for (float altitude : new float[]{0, 40}) {
            caster.update(geometry, 0, 0, altitude, 7, -3);
            assertEquals(geometry.vertexCount, caster.vertexCount);
            float[] reference = caster.vertices.clone();
            caster.bounds(bounds);
            int updates = caster.updates;
            for (int angle = 0; angle < 360; angle++) {
                caster.faceLight(200 * (float) Math.cos(Math.toRadians(angle)),
                        200 * (float) Math.sin(Math.toRadians(angle)));
                assertArrayEquals(reference, caster.vertices, 0);
                for (int i = 0; i < caster.vertexCount; i++) {
                    int o = i * 8;
                    assertTrue(caster.lightVertices[o] >= bounds.minX - .0001f);
                    assertTrue(caster.lightVertices[o] <= bounds.maxX + .0001f);
                    assertTrue(caster.lightVertices[o + 1] >= bounds.minY - .0001f);
                    assertTrue(caster.lightVertices[o + 1] <= bounds.maxY + .0001f);
                    assertEquals(reference[o + 3], caster.lightVertices[o + 3], 0);
                    assertEquals(reference[o + 4], caster.lightVertices[o + 4], 0);
                    assertEquals(geometry.vertices[i * 4],
                            LightingCoordinates.screenX(reference[o], reference[o + 1]), .0001f);
                    assertEquals(geometry.vertices[i * 4 + 1],
                            LightingCoordinates.screenY(reference[o], reference[o + 1], reference[o + 2]), .0001f);
                }
                caster.update(geometry, 0, 0, altitude, 7, -3);
                assertEquals(updates, caster.updates);
            }
        }
    }

    @Test
    public void imageExtentChangesMoveNormalizedZoneWithoutChangingUv() {
        ActorSpatialRectangleData rectangle = new ActorSpatialRectangleData();
        rectangle.x = .25f;
        rectangle.width = .5f;
        ActorSpatialGeometry geometry = geometry(rectangle, Q);
        ActorShadowRectangleCaster caster = new ActorShadowRectangleCaster();
        caster.update(geometry, 0, 0, 0, 0, 0);
        float[] before = caster.vertices.clone();
        int updates = caster.updates;
        geometry.update(rectangle, -90, -40, -90, 180, 90, 180, 90, -40, false, false);
        caster.update(geometry, 0, 0, 0, 0, 0);
        assertEquals(updates + 1, caster.updates);
        assertNotEquals(before[0], caster.vertices[0], 0);
        for (int i = 0; i < caster.vertexCount; i++) {
            assertEquals(before[i * 8 + 3], caster.vertices[i * 8 + 3], 0);
            assertEquals(before[i * 8 + 4], caster.vertices[i * 8 + 4], 0);
        }
    }

    @Test
    public void translatedActorAndLightKeepTheSameLocalSilhouette() {
        ActorShadowRectangleCaster caster = new ActorShadowRectangleCaster();
        ActorSpatialGeometry geometry = geometry(null, Q);
        caster.update(geometry, 0, 0, 0, 3, -4);
        caster.faceLight(100, 70);
        float[] before = caster.lightVertices.clone(), moved = Q.clone();
        for (int i = 0; i < 8; i += 2) {
            moved[i] += 20;
            moved[i + 1] += 20;
        }
        caster.update(geometry(null, moved), 20, 20, 0, 3, -4);
        caster.faceLight(130, 80);
        for (int i = 0; i < caster.vertexCount; i++) {
            int o = i * 8;
            assertEquals(before[o] + 30, caster.lightVertices[o], .0001);
            assertEquals(before[o + 1] + 10, caster.lightVertices[o + 1], .0001);
            for (int k = 2; k < 8; k++) {
                assertEquals(before[o + k], caster.lightVertices[o + k], .0001);
            }
        }
    }

    @Test
    public void invalidAutonomousFootprintIsDiagnosedAndValidStateCanRecover() {
        DynamicEntityRenderState dynamic = new DynamicEntityRenderState(4);
        FrameRenderQueue queue = new FrameRenderQueue(4);
        LightingGeometrySystem geometry = new LightingGeometrySystem(dynamic, new TiledRenderState(4),
                queue, new LayerStateSOA(1));
        World world = new World(new WorldConfigurationBuilder().with(geometry).build());
        try {
            int actor = world.create();
            int slot = dynamic.acquireSlotForEntity(actor);
            dynamic.enabled[slot] = true;
            dynamic.x3[slot] = dynamic.x4[slot] = 32;
            dynamic.y2[slot] = dynamic.y3[slot] = 64;
            world.getMapper(TransformComponent.class).create(actor);
            SpatialRepresentationComponent height = world.getMapper(SpatialRepresentationComponent.class).create(actor);
            height.rectangles = new ActorSpatialRectangles();
            height.rectangles.staticRectangle = new ActorSpatialRectangleData();
            world.getMapper(SurfaceLightingComponent.class).create(actor).shadowCaster = true;
            world.process(); // Dormant representation is a normal image.
            assertNull(queue.lighting.entities.get(actor).shadowVertices);

            SpatialFootprintComponent foot = world.getMapper(SpatialFootprintComponent.class).create(actor);
            foot.radiusPx = 8f;
            world.process();
            LightingSurface surface = queue.lighting.casters.first().surface;
            foot.radiusPx = 0f;
            assertThrows(LightingAdmissionException.class, world::process);
            foot.radiusPx = 8f;
            world.process();
            assertSame(surface, queue.lighting.casters.first().surface);
            assertEquals(6, surface.shadowVertexCount);

            foot.radiusPx = 0f;
            assertThrows(LightingAdmissionException.class, world::process);
            world.getMapper(TransformComponent.class).remove(actor);
            assertThrows(LightingAdmissionException.class, world::process);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void ecsPublicationTracksClipsFramesAndAltitudeWithoutChangingTheSprite() {
        DynamicEntityRenderState d = new DynamicEntityRenderState(4);
        FrameRenderQueue q = new FrameRenderQueue(4);
        TiledRenderState tiled = new TiledRenderState(4);
        LightingGeometrySystem geometry = new LightingGeometrySystem(d, tiled, q, new LayerStateSOA(1));
        World world = new World(new WorldConfigurationBuilder().with(geometry).build());
        try {
            int e = world.create(), slot = d.acquireSlotForEntity(e);
            d.enabled[slot] = true;
            d.x1[slot] = Q[0];
            d.y1[slot] = Q[1];
            d.x2[slot] = Q[2];
            d.y2[slot] = Q[3];
            d.x3[slot] = Q[4];
            d.y3[slot] = Q[5];
            d.x4[slot] = Q[6];
            d.y4[slot] = Q[7];
            d.u1[slot] = .1f;
            d.u2[slot] = .4f;
            d.v1[slot] = .2f;
            d.v2[slot] = .8f;
            world.getMapper(TransformComponent.class).create(e);
            SpatialFootprintComponent foot = world.getMapper(SpatialFootprintComponent.class).create(e);
            foot.radiusPx = 8f;
            SpatialRepresentationComponent h = world.getMapper(SpatialRepresentationComponent.class).create(e);
            h.rectangles = new ActorSpatialRectangles();
            h.rectangles.put(7, "walk", new ActorSpatialRectangleData());
            ActorSpatialRectangleData idle = new ActorSpatialRectangleData();
            idle.pivotX = 9;
            idle.height = .5f;
            h.rectangles.put(7, "idle", idle);
            world.getMapper(AssetRefComponent.class).create(e).assetId = 7;
            AnimationComponent animation = world.getMapper(AnimationComponent.class).create(e);
            animation.currentClip = "walk";
            world.getMapper(SurfaceLightingComponent.class).create(e).shadowCaster = true;
            geometry.prepareRuntimeAvailability();
            ActorShadowRectangleCaster storage = q.lighting.entities.get(e).actorShadowRectangle;
            world.process();
            LightingSurface s = q.lighting.entities.get(e);
            assertSame(storage.vertices, s.shadowVertices);
            int identity = s.casterShadowGroup();
            assertTrue(identity > 0);
            assertEquals(identity, s.receiverShadowGroup(0));
            tiled.lightingRevision++;
            world.process();
            assertEquals(identity, s.casterShadowGroup());
            float[] receiver = s.vertices.clone();
            ActorSpatialRectangleData active = h.rectangles.get(7, "walk");
            active.x = .15f;
            active.y = .1f;
            active.pivotX = 9;
            world.process();
            assertArrayEquals("Rectangle and pivot edits must not move the receiver", receiver, s.vertices, 0);
            int updates = storage.updates;
            d.u1[slot] = .4f;
            d.u2[slot] = .1f;
            animation.frame = 4;
            world.process();
            assertEquals(updates + 1, storage.updates);
            assertEquals(.4f, q.lighting.casters.first().u1, 0);
            animation.currentClip = "idle";
            foot.altitude = 40;
            world.process();
            assertEquals(updates + 2, storage.updates);
            assertEquals(0, q.lighting.casters.first().minZ, .001);
            assertEquals(110, q.lighting.casters.first().maxZ, .001);
            assertEquals(Q[0], d.x1[slot], 0);
            assertEquals(Q[5], d.y3[slot], 0);
            assertSame(storage, s.actorShadowRectangle);
            assertTrue(s.twoSided);
            assertTrue(s.actor);
            assertEquals(1, q.lighting.casters.size);
            assertEquals(identity, s.casterShadowGroup());
            assertEquals(identity, s.receiverShadowGroup(24));
            world.getMapper(SpatialRepresentationComponent.class).remove(e);
            world.process();
            assertSame("Absent settings use the full image", storage.vertices, s.shadowVertices);
            world.getMapper(SpatialFootprintComponent.class).remove(e);
            world.process();
            assertNull("Removing the owner stops the actor caster", s.shadowVertices);
            LightingFrame.Caster actual = q.lighting.casters.first();
            LightingFrame.Caster expected = new LightingFrame.Caster();
            expected.surface = new LightingSurface(s.vertices);
            expected.bounds();
            assertEquals(expected.minX, actual.minX, 0);
            assertEquals(expected.maxY, actual.maxY, 0);
            d.enabled[slot] = false;
            world.delete(e);
            world.process();
            int recreated = world.create(), newSlot = d.acquireSlotForEntity(recreated);
            d.enabled[newSlot] = true;
            d.x1[newSlot] = d.x2[newSlot] = -32;
            d.x3[newSlot] = d.x4[newSlot] = 32;
            d.y2[newSlot] = d.y3[newSlot] = 64;
            world.process();
            assertTrue(q.lighting.entities.get(recreated).casterShadowGroup() > identity);
        } finally {
            world.dispose();
        }
    }
}
