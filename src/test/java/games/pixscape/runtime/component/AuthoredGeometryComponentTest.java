package games.pixscape.runtime.component;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeJointComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AuthoredGeometryComponentTest {

    @Test
    public void componentsCopyInputAndResetWhenPooled() {
        World world = world();
        try {
            float[] source = {0f, 0f, 2f, 0f, 0f, 1f};
            int entity = world.create();
            PolygonComponent polygon = world.getMapper(PolygonComponent.class).create(entity);
            PolylineComponent polyline = world.getMapper(PolylineComponent.class).create(entity);
            polygon.setVertices(source);
            polyline.setVertices(source);
            source[0] = 99f;

            assertEquals(0f, polygon.vertices[0], 0f);
            assertEquals(0f, polyline.vertices[0], 0f);

            world.delete(entity);
            world.process();
            int replacement = world.create();
            PolygonComponent resetPolygon = world.getMapper(PolygonComponent.class).create(replacement);
            PolylineComponent resetPolyline = world.getMapper(PolylineComponent.class).create(replacement);
            assertEquals(0, resetPolygon.vertices.length);
            assertEquals(0, resetPolyline.vertices.length);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void artemisRoundTripPreservesPolygonAndPolylineVertices() {
        World source = world();
        World target = world();
        try {
            int entity = source.create();
            source.getMapper(PolygonComponent.class).create(entity)
                    .setVertices(new float[]{0f, 0f, 3.5f, -2f, 1f, 4f});
            source.getMapper(PolylineComponent.class).create(entity)
                    .setVertices(new float[]{-1f, 2f, 4f, 2f});
            source.process();

            SaveFileFormat loaded = load(target, save(source, entity));
            int reloaded = loaded.entities.get(0);
            assertArrayEquals(new float[]{0f, 0f, 3.5f, -2f, 1f, 4f},
                    target.getMapper(PolygonComponent.class).get(reloaded).vertices, 0f);
            assertArrayEquals(new float[]{-1f, 2f, 4f, 2f},
                    target.getMapper(PolylineComponent.class).get(reloaded).vertices, 0f);
        } finally {
            source.dispose();
            target.dispose();
        }
    }

    @Test
    public void runtimeAndAuthoredComponentsRemainCreatableInTheSameWorld() {
        World world = world();
        try {
            int entity = world.create();

            assertNotNull(world.getMapper(PolygonComponent.class).create(entity));
            assertNotNull(world.getMapper(PolylineComponent.class).create(entity));
            assertNotNull(world.getMapper(PhysicsRuntimeBodyComponent.class).create(entity));
            assertNotNull(world.getMapper(PhysicsRuntimeJointComponent.class).create(entity));
            assertNotNull(world.getMapper(PhysicsCompiledFixturesComponent.class).create(entity));
            assertNotNull(world.getMapper(SpatialPhysicsFootprintComponent.class).create(entity));
        } finally {
            world.dispose();
        }
    }

    private static World world() {
        return new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
    }

    private static byte[] save(World world, int entity) {
        WorldSerializationManager serialization = world.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(world));
        SaveFileFormat request = new SaveFileFormat();
        request.entities.add(entity);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialization.save(out, request);
        return out.toByteArray();
    }

    private static SaveFileFormat load(World world, byte[] bytes) {
        WorldSerializationManager serialization = world.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(world));
        SaveFileFormat loaded = serialization.load(
                new ByteArrayInputStream(bytes), SaveFileFormat.class);
        world.process();
        return loaded;
    }
}
