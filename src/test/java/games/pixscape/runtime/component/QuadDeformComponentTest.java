package games.pixscape.runtime.component;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class QuadDeformComponentTest {

    @Test
    public void defaultsToZeroOffsets() {
        QuadDeformComponent deform = new QuadDeformComponent();

        assertOffsets(deform, new float[8]);
    }

    @Test
    public void pooledComponentResetClearsOffsets() {
        World world = serializationWorld();
        int entity = world.create();
        QuadDeformComponent deform = world.getMapper(QuadDeformComponent.class).create(entity);
        deform.blX = deform.blY = 1f;
        deform.brX = deform.brY = 2f;
        deform.trX = deform.trY = 3f;
        deform.tlX = deform.tlY = 4f;
        world.delete(entity);
        world.process();

        int replacement = world.create();
        QuadDeformComponent reset =
                world.getMapper(QuadDeformComponent.class).create(replacement);

        assertOffsets(reset, new float[8]);
        world.dispose();
    }

    @Test
    public void artemisJsonRoundTripPreservesOffsets() {
        World source = serializationWorld();
        int entity = source.create();
        QuadDeformComponent deform = source.getMapper(QuadDeformComponent.class).create(entity);
        deform.blX = 1f;
        deform.blY = 2f;
        deform.brX = 3f;
        deform.brY = 4f;
        deform.trX = 5f;
        deform.trY = 6f;
        deform.tlX = 7f;
        deform.tlY = 8f;
        source.process();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WorldSerializationManager sourceSerialization =
                source.getSystem(WorldSerializationManager.class);
        sourceSerialization.setSerializer(new JsonArtemisSerializer(source));
        SaveFileFormat request = new SaveFileFormat();
        request.entities.add(entity);
        sourceSerialization.save(output, request);

        World target = serializationWorld();
        WorldSerializationManager targetSerialization =
                target.getSystem(WorldSerializationManager.class);
        targetSerialization.setSerializer(new JsonArtemisSerializer(target));
        SaveFileFormat loaded = targetSerialization.load(
                new ByteArrayInputStream(output.toByteArray()), SaveFileFormat.class);

        Assert.assertEquals(1, loaded.entities.size());
        assertOffsets(
                target.getMapper(QuadDeformComponent.class).get(loaded.entities.get(0)),
                new float[]{1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f});
        source.dispose();
        target.dispose();
    }

    private static World serializationWorld() {
        return new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
    }

    private static void assertOffsets(QuadDeformComponent deform, float[] expected) {
        Assert.assertArrayEquals(expected, new float[]{
                deform.blX, deform.blY,
                deform.brX, deform.brY,
                deform.trX, deform.trY,
                deform.tlX, deform.tlY
        }, 0f);
    }
}
