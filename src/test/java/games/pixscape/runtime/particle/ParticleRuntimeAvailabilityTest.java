package games.pixscape.runtime.particle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.service.AtlasRuntimeService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;

public class ParticleRuntimeAvailabilityTest {
    private GL20 previousGl;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void installGlProxy() {
        previousGl = Gdx.gl;
        Gdx.gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class<?>[]{GL20.class},
                (proxy, method, args) -> null);
    }

    @After
    public void restoreGlProxy() {
        Gdx.gl = previousGl;
    }

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
    public void unpreparedGameplayObtainFailsWithoutPreparingResource() throws Exception {
        File root = temporaryFolder.newFolder("dynamic-effects");
        writeEffect(new FileHandle(new File(root, "dynamic.p")));

        AtlasRuntimeService atlases = new AtlasRuntimeService();
        atlases.loadBorrowed("main", new TextureAtlas());
        ParticleRuntimeAvailability availability =
                new ParticleRuntimeAvailability(atlases, new FileHandle(root));

        Assert.assertFalse(availability.isPrepared("main", "dynamic.p"));
        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class,
                () -> availability.obtain("main", "dynamic.p"));

        Assert.assertTrue(failure.getMessage().contains("Runtime Availability"));
        Assert.assertFalse(availability.isPrepared("main", "dynamic.p"));
        Assert.assertEquals(0, availability.fileParseCount());
        Assert.assertEquals(0, availability.templateConstructionCount());
        Assert.assertEquals(0, availability.poolConstructionCount());
        Assert.assertEquals(0, availability.obtainCount());
    }

    @Test
    public void libgdxFixtureLoadsAtlasImageAndParticipatesInRuntimePool() throws Exception {
        FileHandle fixture = fixture("libgdx-standard.p");
        TextureAtlas atlas = atlasWithRegion("smoke");
        AtlasRuntimeService atlases = new AtlasRuntimeService();
        atlases.loadBorrowed("main", atlas);
        ParticleRuntimeAvailability availability =
                new ParticleRuntimeAvailability(atlases, fixture.parent());

        availability.prepare("main", fixture.name());
        ParticleEffectPool.PooledEffect effect = availability.obtain("main", fixture.name());

        ParticleEmitter emitter = effect.findEmitter("compat-smoke");
        Assert.assertNotNull(emitter);
        Assert.assertEquals(1, emitter.getImagePaths().size);
        Assert.assertEquals("particles/smoke.png", emitter.getImagePaths().first());
        Assert.assertEquals(1, emitter.getSprites().size);
        Assert.assertSame(atlas.findRegion("smoke").getTexture(),
                emitter.getSprites().first().getTexture());
        Assert.assertEquals(4, emitter.getCapacity());
        effect.free();

        ParticleEffectPool.PooledEffect reused = availability.obtain("main", fixture.name());
        Assert.assertSame(effect, reused);
        reused.free();
    }

    @Test
    public void libgdxFixtureSupportsAtlasRegionPrefix() throws Exception {
        FileHandle fixture = fixture("libgdx-standard.p");
        TextureAtlas atlas = atlasWithRegion("fx-smoke");
        ParticleEffect effect = new ParticleEffect();

        effect.load(fixture, atlas, "fx-");

        Assert.assertEquals(1, effect.findEmitter("compat-smoke").getSprites().size);
        Assert.assertSame(atlas.findRegion("fx-smoke").getTexture(),
                effect.findEmitter("compat-smoke").getSprites().first().getTexture());
    }

    @Test
    public void forkInternalsRetainOnlyRequiredVisibility() throws Exception {
        Assert.assertTrue(Modifier.isPrivate(
                ParticleEffect.class.getDeclaredField("bounds").getModifiers()));
        Assert.assertTrue(Modifier.isPrivate(
                ParticleEffect.class.getDeclaredField("ownsTexture").getModifiers()));
        Assert.assertTrue(Modifier.isProtected(
                ParticleEffect.class.getDeclaredField("xSizeScale").getModifiers()));
        Assert.assertTrue(Modifier.isProtected(ParticleEffect.class
                .getDeclaredMethod("loadTexture", FileHandle.class).getModifiers()));
        Assert.assertTrue(Modifier.isPublic(
                ParticleEmitter.class.getDeclaredField("particles").getModifiers()));
        Assert.assertTrue(Modifier.isPublic(
                ParticleEmitter.class.getDeclaredMethod("getParticles").getModifiers()));
    }

    private static FileHandle fixture(String name) throws Exception {
        URL resource = ParticleRuntimeAvailabilityTest.class.getResource(name);
        Assert.assertNotNull("Missing particle fixture: " + name, resource);
        return new FileHandle(new File(resource.toURI()));
    }

    private static TextureAtlas atlasWithRegion(String name) {
        TextureAtlas atlas = new TextureAtlas();
        TextureAtlas.AtlasRegion region =
                new TextureAtlas.AtlasRegion(new TestTexture(), 0, 0, 8, 8);
        region.name = name;
        region.index = -1;
        region.packedWidth = 8;
        region.packedHeight = 8;
        region.originalWidth = 8;
        region.originalHeight = 8;
        atlas.getRegions().add(region);
        return atlas;
    }

    private static void writeEffect(FileHandle file) throws Exception {
        ParticleEffect source = new ParticleEffect();
        source.getEmitters().add(new ParticleEmitter());
        StringWriter writer = new StringWriter();
        source.save(writer);
        file.writeString(writer.toString(), false, "UTF-8");
    }

    private static final class TestTexture extends Texture {
        TestTexture() {
            super();
        }

        @Override
        public int getWidth() {
            return 8;
        }

        @Override
        public int getHeight() {
            return 8;
        }
    }
}
