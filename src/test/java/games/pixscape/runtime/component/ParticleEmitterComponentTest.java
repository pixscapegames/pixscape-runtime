package games.pixscape.runtime.component;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ParticleEmitterComponentTest {
    @Test public void ambientExemptionDefaultsFalseAndResetsWithThePooledInstance() {
        World world = new World(new WorldConfiguration());
        try {
            int e = world.create();
            ParticleEmitterComponent emitter = world.getMapper(ParticleEmitterComponent.class).create(e);
            Assert.assertFalse(emitter.ignoreAmbientLighting);
            emitter.ignoreAmbientLighting = true;
            world.delete(e);world.process();
            ParticleEmitterComponent reused = world.getMapper(ParticleEmitterComponent.class).create(world.create());
            Assert.assertFalse(reused.ignoreAmbientLighting);
        } finally {world.dispose();}
    }
    @Test public void instanceOptionSurvivesSceneSerializationWithoutChangingTheEffectRequest() {
        World source = world(),target = world();
        try {
            int e=source.create();ParticleEmitterComponent emitter=source.getMapper(ParticleEmitterComponent.class).create(e);
            emitter.effectPath="fire.p";emitter.atlasTag="main";emitter.ignoreAmbientLighting=true;source.process();
            SaveFileFormat request=new SaveFileFormat();request.entities.add(e);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();source.getSystem(WorldSerializationManager.class).save(bytes,request);
            SaveFileFormat loaded=target.getSystem(WorldSerializationManager.class).load(new ByteArrayInputStream(bytes.toByteArray()),SaveFileFormat.class);
            ParticleEmitterComponent copy=target.getMapper(ParticleEmitterComponent.class).get(loaded.entities.get(0));
            Assert.assertTrue(copy.ignoreAmbientLighting);Assert.assertEquals("fire.p",copy.effectPath);Assert.assertEquals("main",copy.atlasTag);
        } finally {source.dispose();target.dispose();}
    }
    private static World world(){World w=new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
        w.getSystem(WorldSerializationManager.class).setSerializer(new JsonArtemisSerializer(w));return w;}
}
