package games.pixscape.runtime.tiled;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation;
import games.pixscape.runtime.hierarchy.GameObjectHierarchyValidator;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import org.junit.Test;

import static org.junit.Assert.*;

public class TiledEntityRoleTest {
    @Test
    public void apiRejectsStructuralTransformsAndFootprintsWithoutMutation() throws Exception {
        try (Fixture f = new Fixture()) {
            PixscapeEngine engine = new PixscapeEngine();
            java.lang.reflect.Field field = PixscapeEngine.class.getDeclaredField("world");
            field.setAccessible(true);
            field.set(engine, f.world);
            for (int entity : new int[]{f.map, f.tiles, f.objects, f.image}) {
                assertThrows(IllegalArgumentException.class,
                        () -> engine.api().entities().requireEntityId(entity).spatial().addFootprint());
                assertFalse(f.world.getMapper(SpatialFootprintComponent.class).has(entity));
            }
            for (int entity : new int[]{f.map, f.tiles}) {
                assertThrows(IllegalArgumentException.class,
                        () -> engine.api().entities().requireEntityId(entity).transform().setScale(2f));
                assertThrows(IllegalArgumentException.class,
                        () -> engine.api().entities().requireEntityId(entity).transform().setRotationRad(0.5f));
                assertThrows(IllegalArgumentException.class,
                        () -> engine.api().entities().requireEntityId(entity).transform().rotateByRad(0.5f));
                assertThrows(IllegalArgumentException.class,
                        () -> engine.api().entities().requireEntityId(entity).transform().setOrigin(2f, 3f));
                assertEquals(1f, f.pose(entity).scaleX, 0f);
                assertEquals(0f, f.pose(entity).rotationRad, 0f);
                assertEquals(0f, f.pose(entity).originX, 0f);
            }
            engine.api().entities().requireEntityId(f.map).transform().setPosition(13.5f, -8.25f);
            engine.api().entities().requireEntityId(f.tiles).transform().setPosition(3.5f, 4.25f);
            f.world.process();
            assertEquals(17f, f.grid().data.originX, 0f);
            assertEquals(-4f, f.grid().data.originY, 0f);
            assertEquals(3.5f, f.grid().originX, 0f);
        }
    }

    @Test
    public void compositionAllowsObjectsInObjectLayerButNeverStructuralReparenting() {
        try (Fixture f = new Fixture()) {
            int object = f.node(false, -1);
            f.world.process();
            for (int structure : new int[]{f.map, f.tiles, f.objects, f.image}) {
                assertThrows(IllegalArgumentException.class,
                        () -> GameObjectHierarchyMutation.reparent(f.world, structure, object));
            }
            for (int parent : new int[]{f.map, f.tiles, f.image}) {
                assertThrows(IllegalArgumentException.class,
                        () -> GameObjectHierarchyMutation.reparent(f.world, object, parent));
            }
            assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(object));
            assertFalse(f.world.getMapper(GameObjectComponent.class).has(object));
            GameObjectHierarchyMutation.reparent(f.world, object, f.objects);
            assertEquals(f.ids.getStableId(f.objects), f.world.getMapper(GameObjectMemberComponent.class).get(object).parentStableId);
            GameObjectHierarchyMutation.reparent(f.world, object, -1);
            assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(object));
        }
    }

    @Test
    public void validationKeepsManagedTilePhysicsAndVolumesAndRejectsActorCapabilities() {
        try (Fixture f = new Fixture()) {
            PhysicsBodyComponent body = f.world.getMapper(PhysicsBodyComponent.class).create(f.tiles);
            SpatialBlocksComponent blocks = f.world.getMapper(SpatialBlocksComponent.class).create(f.tiles);
            f.world.process();
            f.validate();
            assertSame(body, f.world.getMapper(PhysicsBodyComponent.class).get(f.tiles));
            assertSame(blocks, f.world.getMapper(SpatialBlocksComponent.class).get(f.tiles));
            for (int entity : new int[]{f.map, f.objects, f.image}) {
                f.world.getMapper(PhysicsBodyComponent.class).create(entity);
                assertTrue(assertThrows(IllegalArgumentException.class, f::validate).getMessage().contains("Bodies"));
                f.world.getMapper(PhysicsBodyComponent.class).remove(entity);
            }
            for (int entity : new int[]{f.map, f.tiles, f.objects, f.image}) {
                f.world.getMapper(SpatialFootprintComponent.class).create(entity);
                assertTrue(assertThrows(IllegalArgumentException.class, f::validate).getMessage().contains("footprints"));
                f.world.getMapper(SpatialFootprintComponent.class).remove(entity);
            }
        }
    }

    @Test
    public void invalidLoadedOwnershipIsDiagnosedWithoutRepair() {
        try (Fixture f = new Fixture()) {
            int generic = f.node(true, -1);
            f.world.getMapper(GameObjectMemberComponent.class).create(f.map).parentStableId = f.ids.getStableId(generic);
            assertThrows(IllegalArgumentException.class, f::validate);
            assertEquals(f.ids.getStableId(generic), f.world.getMapper(GameObjectMemberComponent.class).get(f.map).parentStableId);
            f.world.getMapper(GameObjectMemberComponent.class).remove(f.map);
            f.world.getMapper(GameObjectComponent.class).create(f.tiles);
            assertTrue(assertThrows(IllegalArgumentException.class, f::validate).getMessage().contains("promoted"));
            assertTrue(f.world.getMapper(GameObjectComponent.class).has(f.tiles));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(32), new GameObjectHierarchySystem(32)).build());
        final IdentityRegistry ids = new IdentityRegistry();
        final int map, tiles, objects, image;

        Fixture() {
            ids.bind(world, new SceneMetaRuntime());
            map = node(true, -1);
            TiledMapComponent data = world.getMapper(TiledMapComponent.class).create(map);
            data.projection = TiledProjection.ISO;
            data.tileWidth = 32;
            data.tileHeight = 16;
            data.width = data.height = 8;
            tiles = layer(TiledMapLayerComponent.Kind.TILE);
            TiledLayerComponent grid = world.getMapper(TiledLayerComponent.class).create(tiles);
            grid.projection = data.projection;
            grid.tileWidth = data.tileWidth;
            grid.tileHeight = data.tileHeight;
            grid.mapWidthCells = grid.mapHeightCells = 8;
            grid.chunkSize = 4;
            grid.data = grid.createLayerData();
            objects = layer(TiledMapLayerComponent.Kind.OBJECT);
            image = layer(TiledMapLayerComponent.Kind.IMAGE);
            world.process();
        }

        int layer(TiledMapLayerComponent.Kind kind) {
            int entity = node(kind == TiledMapLayerComponent.Kind.OBJECT, map);
            world.getMapper(TiledMapLayerComponent.class).create(entity).kind = kind;
            return entity;
        }

        int node(boolean group, int parent) {
            int entity = world.create();
            world.getMapper(TransformComponent.class).create(entity);
            world.getMapper(EntityIndexComponent.class).create(entity);
            ids.ensureStableId(entity);
            if (group) {
                world.getMapper(GameObjectComponent.class).create(entity);
            }
            if (parent >= 0) {
                world.getMapper(GameObjectMemberComponent.class).create(entity).parentStableId = ids.getStableId(parent);
            }
            return entity;
        }

        TransformComponent pose(int entity) {
            return world.getMapper(TransformComponent.class).get(entity);
        }

        TiledLayerComponent grid() {
            return world.getMapper(TiledLayerComponent.class).get(tiles);
        }

        void validate() {
            new GameObjectHierarchyValidator(world, ids).validateEntities(
                    world.getAspectSubscriptionManager().get(Aspect.all()).getEntities());
        }

        @Override
        public void close() {
            ids.bind(null, null);
            world.dispose();
        }
    }
}
