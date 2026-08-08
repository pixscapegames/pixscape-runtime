package games.pixscape.runtime.particle;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.service.AtlasRuntimeService;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.StringWriter;

public class ParticleRuntimeAvailabilityTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void knownFirstObtainReusesPreparedTemplateAndPool() throws Exception {
        File root = temporaryFolder.newFolder("effects");
        writeEffect(new FileHandle(new File(root, "known.p")));

        AtlasRuntimeService atlases = new AtlasRuntimeService();
        atlases.loadBorrowed("main", new TextureAtlas());
        ParticleRuntimeAvailability availability =
                new ParticleRuntimeAvailability(atlases, new FileHandle(root));

        availability.prepare("main", "known.p");
        int parses = availability.fileParseCount();
        int templates = availability.templateConstructionCount();
        int pools = availability.poolConstructionCount();

        ParticleEffectPool.PooledEffect first = availability.obtain("main", "known.p");
        first.free();

        Assert.assertEquals(parses, availability.fileParseCount());
        Assert.assertEquals(templates, availability.templateConstructionCount());
        Assert.assertEquals(pools, availability.poolConstructionCount());
        Assert.assertEquals(1, availability.obtainCount());
    }

    @Test
    public void dynamicPostReadyDependencyStillUsesLazyFallback() throws Exception {
        File root = temporaryFolder.newFolder("dynamic-effects");
        writeEffect(new FileHandle(new File(root, "dynamic.p")));

        AtlasRuntimeService atlases = new AtlasRuntimeService();
        atlases.loadBorrowed("main", new TextureAtlas());
        ParticleRuntimeAvailability availability =
                new ParticleRuntimeAvailability(atlases, new FileHandle(root));

        Assert.assertFalse(availability.isPrepared("main", "dynamic.p"));
        ParticleEffectPool.PooledEffect effect =
                availability.obtain("main", "dynamic.p");
        effect.free();

        Assert.assertTrue(availability.isPrepared("main", "dynamic.p"));
        Assert.assertEquals(1, availability.fileParseCount());
        Assert.assertEquals(1, availability.templateConstructionCount());
        Assert.assertEquals(1, availability.poolConstructionCount());
        Assert.assertEquals(1, availability.obtainCount());
    }

    private static void writeEffect(FileHandle file) throws Exception {
        ParticleEffect source = new ParticleEffect();
        source.getEmitters().add(new ParticleEmitter());
        StringWriter writer = new StringWriter();
        source.save(writer);
        file.writeString(writer.toString(), false, "UTF-8");
    }
}
