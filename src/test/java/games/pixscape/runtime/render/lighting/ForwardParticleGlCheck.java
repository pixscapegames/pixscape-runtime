package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.*;
import org.junit.Assert;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs in the existing desktop GL check, using the real prepared particle producer. */
final class ForwardParticleGlCheck {
    static void verify() throws Exception {
        Path directory = Files.createTempDirectory("forward-particle");
        ParticleEmitter template = new ParticleEmitter();
        template.setName("premultiplied-emission");
        template.setMinParticleCount(1);
        template.setMaxParticleCount(1);
        template.getDuration().setLow(1000);
        template.getLife().setHigh(300);
        template.getXScale().setHigh(16);
        template.getTransparency().setHigh(.5f);
        template.setContinuous(true);
        template.setAdditive(true);
        template.setPremultipliedAlpha(true);
        template.setImagePaths(Array.with("emission.png"));
        StringWriter text = new StringWriter();
        template.save(text);
        Path effectFile = directory.resolve("emission.p");
        Files.writeString(effectFile, text.toString());

        // Correct premultiplied texture: straight (1,.5,.25) with texture alpha .5.
        Pixmap pixels = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixels.setColor(.5f, .25f, .125f, .5f);
        pixels.fill();
        Texture texture = new Texture(pixels);
        AtlasRuntimeService.TextureArrayBundle bundle = AtlasRuntimeService.buildTextureArrayFromTextures(
                Array.with(texture), 1, 1, GLCaps.detect());
        pixels.dispose();
        TextureAtlas atlas = new TextureAtlas();
        atlas.addRegion("emission", texture, 0, 0, 1, 1);
        AtlasRuntimeService atlases = new AtlasRuntimeService();
        atlases.loadBorrowed("particles", atlas);
        int shader = ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());
        OrthographicCamera camera = new OrthographicCamera(64, 32);
        camera.position.set(32, 16, 0);
        camera.update();
        DynamicEntityRenderState dynamic = new DynamicEntityRenderState(4);
        TiledMapRenderState tiled = new TiledMapRenderState();
        VfxRenderState vfx = new VfxRenderState(4);
        LayerStateSOA layers = new LayerStateSOA(1);
        layers.enabled[0] = true;
        DrawList list = new DrawList(8);
        FrameRenderQueue queue = new FrameRenderQueue(8);
        RenderStats stats = new RenderStats();
        TextureArrayMeshBatch batch = new TextureArrayMeshBatch(64);
        batch.setTextureArrayBundle(bundle);
        RenderParticleSyncSystem particles = new RenderParticleSyncSystem(
                vfx, camera, shader, atlases, new FileHandle(directory.toFile()));
        RenderSubmitSystem submit = new RenderSubmitSystem(layers, queue, camera, 0, 0, 0,
                batch, stats, new RenderStatsSink(100), true);
        World world = new World(new WorldConfigurationBuilder().with(particles,
                new RenderBuildDrawListSystem(dynamic, tiled, vfx, layers, list, stats, 0, 0, 4),
                new RenderSortSystem(dynamic, tiled, vfx, list, 0, 4),
                new RenderExtractFrameQueueSystem(dynamic, tiled, vfx, list, queue, stats, 0, 0, 4),
                submit).build());
        try {
            particles.setEnabled(false);
            submit.setEnabled(false);
            int entity = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
            transform.x = 32;
            transform.y = 16;
            ParticleEmitterComponent emitter = world.getMapper(ParticleEmitterComponent.class).create(entity);
            emitter.effectPath = "emission.p";
            emitter.atlasTag = "particles";
            emitter.ignoreAmbientLighting = true;
            world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 0;
            world.process();
            particles.prepareRuntimeAvailability("particles", new Array<String>());
            submit.prepareComposition();
            particles.setEnabled(true);
            submit.setEnabled(true);
            frame(world, .1f);
            Assert.assertEquals(1, queue.size);
            Assert.assertEquals(BlendMode.PREMULT_ALPHA.id, queue.blend[0]);
            Assert.assertEquals(0, Float.floatToRawIntBits(queue.colorPacked[0]) >>> 24);
            int visible = pixel();
            Assert.assertTrue("Zero-coverage native emission must remain visible", (visible >>> 24) >= 60);
            Assert.assertEquals("Emission must not invent world coverage", 0, visible & 255);

            emitter.ignoreAmbientLighting = false;
            frame(world, 0);
            Assert.assertEquals("Nonexempt emission follows zero ambient", 0, pixel());
            emitter.ignoreAmbientLighting = true;
            emitter.completionRequested = true;
            for (int i = 0; i < 5; i++) frame(world, .1f);
            Assert.assertEquals(0, vfx.activeCount);
            emitter.restartRequested = true;
            frame(world, .1f);
            Assert.assertEquals(1, vfx.activeCount);
            Assert.assertTrue((pixel() >>> 24) >= 60);
            world.delete(entity);
            frame(world, .1f);
            Assert.assertEquals(0, queue.size);
            Assert.assertEquals(0, pixel());
        } finally {
            particles.invalidateAllEffects();
            world.dispose();
            batch.close();
            bundle.textureArray.dispose();
            texture.dispose();
            Files.deleteIfExists(effectFile);
            Files.deleteIfExists(directory);
        }
    }

    private static void frame(World world, float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        world.setDelta(delta);
        world.process();
    }

    private static int pixel() {
        Pixmap pixels = Pixmap.createFromFrameBuffer(32, 16, 1, 1);
        try { return pixels.getPixel(0, 0); }
        finally { pixels.dispose(); }
    }
}
