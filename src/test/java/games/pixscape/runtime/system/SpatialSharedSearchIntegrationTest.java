package games.pixscape.runtime.system;

import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialLayerFaceRuntime;
import games.pixscape.runtime.spatial.SpatialSharedAnchorIndex;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpatialSharedSearchIntegrationTest {
    @Test
    public void candidatesConstraintsDiagnosticsAndFinalOrderMatchNativeAcrossPublishedChanges() throws Exception {
        for (int count : new int[]{1, 2, 8}) {
            try (SpatialSharedSearchFixture nativePath = new SpatialSharedSearchFixture(count, 32, true);
                 SpatialSharedSearchFixture shared = new SpatialSharedSearchFixture(count, 32, false)) {
                nativePath.spatial.setDiagnosticsEnabled(true);
                shared.spatial.setDiagnosticsEnabled(true);
                for (int frame = 0; frame < 128; frame++) {
                    nativePath.move(frame);
                    shared.move(frame);
                    same(nativePath, shared);
                }
                nativePath.move(0);
                shared.move(0);
                same(nativePath, shared);
                assertTrue(shared.spatial.visualCandidateCount() > 0);
                assertEquals(count == 1 ? 0 : count, shared.search.sharedContextCount());
                long before = reindexes(shared.search);
                for (SpatialSharedSearchFixture f : new SpatialSharedSearchFixture[]{nativePath, shared}) {
                    SpatialBlocksComponent b = f.world.getMapper(SpatialBlocksComponent.class).get(f.owners[0]);
                    b.blocks.first().height = 40;
                    b.revision++;
                }
                same(nativePath, shared);
                assertEquals(count == 1 ? 0 : 1, reindexes(shared.search) - before);
                for (SpatialSharedSearchFixture f : new SpatialSharedSearchFixture[]{nativePath, shared}) {
                    f.maps[0].setTile(40, 40, 1);
                }
                same(nativePath, shared);
                for (SpatialSharedSearchFixture f : new SpatialSharedSearchFixture[]{nativePath, shared}) {
                    SpatialBlocksComponent blocks = f.world.getMapper(SpatialBlocksComponent.class).get(f.owners[0]);
                    SpatialBlockData added = new SpatialBlockData();
                    added.id = blocks.allocateNextSpatialBlockId();
                    added.structureId = added.id;
                    added.x = added.y = 40;
                    added.width = added.depth = 1;
                    added.height = 32;
                    added.addLinkedTileRef(40, 40, 1);
                    blocks.blocks.add(added);
                    blocks.revision++;
                    TransformComponent actor = f.world.getMapper(TransformComponent.class).get(f.actors[0]);
                    actor.x = 32;
                    actor.y = 1260;
                }
                same(nativePath, shared);
                for (SpatialSharedSearchFixture f : new SpatialSharedSearchFixture[]{nativePath, shared}) {
                    SpatialBlocksComponent blocks = f.world.getMapper(SpatialBlocksComponent.class).get(f.owners[0]);
                    blocks.blocks.pop();
                    blocks.revision++;
                    f.maps[0].setTile(40, 40, 0);
                    f.move(0);
                }
                same(nativePath, shared);
                for (byte flags = 0; flags < 8; flags++) {
                    nativePath.maps[0].setTile(4, 4, 2, flags);
                    shared.maps[0].setTile(4, 4, 2, flags);
                    same(nativePath, shared);
                }
                // Published visual padding changes split only the outlier's cohort.
                nativePath.maps[0].setTile(4, 4, 3);
                shared.maps[0].setTile(4, 4, 3);
                same(nativePath, shared);
                assertEquals(count == 8 ? 7 : 0, shared.search.sharedContextCount());
                nativePath.maps[0].setTile(4, 4, 1);
                shared.maps[0].setTile(4, 4, 1);
                same(nativePath, shared);
                for (SpatialSharedSearchFixture f : new SpatialSharedSearchFixture[]{nativePath, shared}) {
                    f.maps[0].originX = 17;
                    f.maps[0].markAllChunksContentDirty();
                }
                same(nativePath, shared);
                for (SpatialSharedSearchFixture f : new SpatialSharedSearchFixture[]{nativePath, shared}) {
                    f.maps[0].originX = 0;
                    f.maps[0].defaultTileAltitude += 16;
                    f.maps[0].markAllChunksContentDirty();
                    f.offsetX[0] = 13;
                }
                same(nativePath, shared);
                nativePath.offsetX[0] = shared.offsetX[0] = 0;
                same(nativePath, shared);
                // Culling and fully off-map actors must not preserve positive-only candidates.
                nativePath.camera.position.set(100000, 100000, 0);
                shared.camera.position.set(100000, 100000, 0);
                nativePath.camera.update();
                shared.camera.update();
                same(nativePath, shared);
                assertEquals(0, shared.spatial.visualCandidateCount());
                nativePath.camera.position.set(0, 0, 0);
                shared.camera.position.set(0, 0, 0);
                nativePath.camera.update();
                shared.camera.update();
                same(nativePath, shared);
            }
        }
    }

    @Test
    public void emptyFramesComponentRemovalEntityReuseAndSceneDisposalReleaseSources() {
        SpatialSharedSearchFixture f = new SpatialSharedSearchFixture(2, 32, false);
        assertEquals(2, f.search.sharedContextCount());
        SpatialLayerFaceRuntime old = f.registry.forLayer(f.owners[0], f.maps[0]);
        f.world.delete(f.owners[0]);
        f.world.process();
        assertEquals(1, f.search.contextCount());
        assertEquals(0, f.search.sharedContextCount());
        f.owners[0] = f.addMap(0);
        f.world.process();
        assertNotSame(old, f.registry.forLayer(f.owners[0], f.maps[0]));
        assertEquals(2, f.search.sharedContextCount());
        for (int slot = 0; slot < f.ecs.activeCount; slot++) {
            f.ecs.enabled[slot] = false;
        }
        for (int owner : f.owners) {
            TiledLayerComponent tiled = f.world.getMapper(TiledLayerComponent.class).get(owner);
            tiled.spatialEnabled = tiled.data.spatialEnabled = false;
        }
        f.world.process();
        assertEquals(0, f.search.contextCount());
        for (int owner : f.owners) {
            f.world.getMapper(TiledLayerComponent.class).remove(owner);
        }
        f.world.process();
        f.close();
        assertEquals(0, f.search.contextCount());
        assertEquals(0, f.search.sharedContextCount());
    }

    @Test
    public void nullSourcePublicationDropsLastPublisherReferences() throws Exception {
        try (SpatialSharedSearchFixture f = new SpatialSharedSearchFixture(1, 32, false)) {
            f.world.getMapper(TiledLayerComponent.class).get(f.owners[0]).data = null;
            f.world.process();
            assertEquals(0, f.search.contextCount());
            RenderTiledSyncSystem publisher = f.world.getSystem(RenderTiledSyncSystem.class);
            for (String name : new String[]{"currentRenderOrder", "currentFaces", "currentTileOrder"}) {
                java.lang.reflect.Field field = RenderTiledSyncSystem.class.getDeclaredField(name);
                field.setAccessible(true);
                assertNull(name, field.get(publisher));
            }
        }
    }

    @Test
    public void replacedSourceAndProjectionChangesCannotReuseOldMemberships() {
        try (SpatialSharedSearchFixture a = new SpatialSharedSearchFixture(2, 32, true);
             SpatialSharedSearchFixture b = new SpatialSharedSearchFixture(2, 32, false)) {
            a.spatial.setDiagnosticsEnabled(true);
            b.spatial.setDiagnosticsEnabled(true);
            for (SpatialSharedSearchFixture f : new SpatialSharedSearchFixture[]{a, b}) {
                f.world.delete(f.owners[1]);
                f.world.process();
                f.owners[1] = f.addMap(1);
                f.maps[1].projection = TiledProjection.ORTHO;
                f.maps[1].markAllChunksContentDirty();
            }
            same(a, b);
            assertEquals(0, b.search.sharedContextCount());
            a.maps[1].projection = b.maps[1].projection = TiledProjection.ISO;
            a.maps[1].markAllChunksContentDirty();
            b.maps[1].markAllChunksContentDirty();
            same(a, b);
            assertEquals(2, b.search.sharedContextCount());
            for (SpatialSharedSearchFixture f : new SpatialSharedSearchFixture[]{a, b}) {
                TiledMapLayerData replacement = new TiledMapLayerData(64, 64, 64, 32, 64, TiledProjection.ISO);
                replacement.defaultTileAltitude = 32;
                replacement.spatialEnabled = true;
                for (int x = 0; x < 8; x++) {
                    replacement.setTile(5 + x, 5, 1);
                }
                f.world.getMapper(TiledLayerComponent.class).get(f.owners[1]).data = replacement;
                f.maps[1] = replacement;
            }
            same(a, b);
            assertEquals(2, b.search.sharedContextCount());
        }
    }

    private static void same(SpatialSharedSearchFixture nativePath, SpatialSharedSearchFixture shared) {
        nativePath.world.process();
        shared.world.process();
        assertEquals(nativePath.evidence(), shared.evidence());
    }

    private static long reindexes(SpatialSharedAnchorIndex index) throws Exception {
        java.lang.reflect.Field field = SpatialSharedAnchorIndex.class.getDeclaredField("reindexCount");
        field.setAccessible(true);
        return field.getLong(index);
    }
}
