package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

/**
 * Real GL regression against the general shader, including the existing borrowed-program contract.
 */
final class UnlitForwardGlCheck {
    static void verify() {
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.setWindowedMode(64, 32);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                Texture texture = null;
                AtlasRuntimeService.TextureArrayBundle bundle = null;
                Pixmap pixel = null;
                try {
                    ShaderRegistry.initDefaults();
                    Gdx.gl.glViewport(0, 0, 64, 32);
                    pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                    pixel.setColor(1, .5f, .25f, 1);
                    pixel.fill();
                    texture = new Texture(pixel);
                    bundle = AtlasRuntimeService.buildTextureArrayFromTextures(com.badlogic.gdx.utils.Array.with(texture), 1, 1,
                            games.pixscape.runtime.render.batch.GLCaps.detect());
                    verify(texture, bundle);
                } catch (Throwable t) {
                    failure[0] = t;
                } finally {
                    if (bundle != null) bundle.textureArray.dispose();
                    if (texture != null) texture.dispose();
                    if (pixel != null) pixel.dispose();
                    ShaderRegistry.disposeAll();
                    InternalTextures.dispose();
                    TextureRegistry.clear();
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Unlit transitions and managed restoration", failure[0]);
    }

    private static final class Renderer extends ForwardLightingRenderer {
        ShaderProgram general, selected;
        boolean forceGeneral;

        @Override
        protected void renderEntries(FrameRenderQueue q, LayerStateSOA layers, OrthographicCamera camera,
                                     LightingRenderer shadows, float r, float g, float b) {
            selected = packedReceiverShader();
            if (forceGeneral) useReceiverShader(general, q, camera, shadows, r, g, b);
            super.renderEntries(q, layers, camera, shadows, r, g, b);
        }
    }

    static void verify(Texture texture, AtlasRuntimeService.TextureArrayBundle bundle) {
        World world = new World();
        World nextWorld = new World();
        Renderer renderer = new Renderer();
        LightingRenderer shadows = new LightingRenderer();
        FrameBuffer external = new FrameBuffer(Pixmap.Format.RGBA8888, 80, 40, false);
        PointLightComponent authored = world.getMapper(PointLightComponent.class).create(world.create());
        authored.shadowResolution = 256;
        authored.shadowQuality = 2;
        world.process();
        LayerStateSOA layers = new LayerStateSOA(1);
        layers.enabled[0] = true;
        OrthographicCamera camera = new OrthographicCamera(64, 32);
        camera.position.set(32, 16, 0);
        camera.update();
        FrameRenderQueue q = new FrameRenderQueue(4);
        int material = ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());
        q.addQuad(TextureRegistry.handleOf(texture), material, 0, 0, 0, 0, 0, 0, 0, 0, 32, 64, 32, 64, 0, 0, 0, 1, 1, Color.WHITE.toFloatBits(), (byte) 0, FrameRenderQueue.SOURCE_TILED, 0, 0);
        float[] vertices = new float[48];
        for (int i = 0; i < 6; i++) vertices[i * 8 + 7] = 1;
        LightingSurface surface = new LightingSurface(vertices);
        surface.forwardTopology = ForwardTopology.NATIVE;
        surface.receiveLight = true;
        q.lighting.tiles.put(0, surface);
        LightingFrame.Light light = new LightingFrame.Light();
        light.z = 30;
        light.r = .4f;
        light.g = .3f;
        light.b = .2f;
        light.intensity = 1;
        light.radius = 200;
        light.falloff = 1;
        light.resolution = 256;
        GL20 original = Gdx.gl20;
        GL30 original30 = Gdx.gl30;
        int[] compiles = {0}, links = {0};
        try {
            renderer.prepare(world, bundle);
            renderer.general = renderer.packedReceiverShader();
            shadows.prepareShadows(world);
            GL30 counting = (GL30) Proxy.newProxyInstance(GL30.class.getClassLoader(), new Class[]{GL30.class}, (p, m, a) -> {
                if (m.getName().equals("glCompileShader")) compiles[0]++;
                if (m.getName().equals("glLinkProgram")) links[0]++;
                try {
                    return m.invoke(original30, a);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
            Gdx.gl = Gdx.gl20 = Gdx.gl30 = counting;
            ShaderProgram firstUnlit = null;
            for (int i = 0; i < 24; i++) {
                q.lighting.lights.clear();
                if (i % 3 == 1) q.lighting.lights.add(light);
                light.intensity = i == 10 ? 0 : 1;
                light.quality = i % 2 == 0 ? 2 : 0;
                float r = i % 2 == 0 ? 1 : .25f, g = i % 2 == 0 ? 1 : .12f, b = i % 2 == 0 ? 1 : .4f;
                q.blend[0] = i % 4 == 0 ? BlendMode.PREMULT_ALPHA.id : BlendMode.OPAQUE.id;
                q.ignoreAmbientLighting[0] = i % 5 == 0;
                int[] expected = pixels(renderer, q, layers, camera, shadows, r, g, b, true, 64, 32);
                int[] actual = pixels(renderer, q, layers, camera, shadows, r, g, b, false, 64, 32);
                Assert.assertArrayEquals("Transition frame " + i, expected, actual);
                if (q.lighting.lights.size == 0) {
                    Assert.assertNotSame(renderer.general, renderer.selected);
                    if (firstUnlit == null) firstUnlit = renderer.selected;
                    else Assert.assertSame(firstUnlit, renderer.selected);
                } else
                    Assert.assertSame("Includes zero-intensity published lights", renderer.general, renderer.selected);
            }
            Assert.assertEquals("No gameplay shader compilation", 0, compiles[0]);
            Assert.assertEquals("No gameplay program linkage", 0, links[0]);
            // Resize and a different World reuse programs; the HDR target follows the actual external viewport.
            external.begin();
            renderer.prepare(nextWorld, bundle);
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(4, 3, 60, 24);
            IntBuffer state = BufferUtils.newIntBuffer(4);
            for (int n = 0; n < 2; n++) {
                q.lighting.lights.clear();
                if (n == 1) q.lighting.lights.add(light);
                Assert.assertArrayEquals(pixels(renderer, q, layers, camera, shadows, .25f, .12f, .4f, true, 80, 40),
                        pixels(renderer, q, layers, camera, shadows, .25f, .12f, .4f, false, 80, 40));
                Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, state);
                Assert.assertEquals(external.getFramebufferHandle(), state.get(0));
                Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, state);
                Assert.assertEquals(4, state.get(0));
                Assert.assertEquals(24, state.get(3));
                Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
            }
            Assert.assertEquals(80L * 40 * 8, renderer.textureBytes());
            Assert.assertEquals(0, compiles[0]);
            Assert.assertEquals(0, links[0]);
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            external.end();
            Gdx.gl.glViewport(0, 0, 64, 32);
            q.lighting.lights.clear();
            int[] beforeRestore = pixels(renderer, q, layers, camera, shadows, .25f, .12f, .4f, false, 64, 32);
            // Exercise LibGDX's actual managed-program restoration hook, outside gameplay.
            ShaderProgram.invalidateAllShaderPrograms(Gdx.app);
            renderer.prepare(nextWorld, bundle);
            Assert.assertTrue(compiles[0] > 0);
            Assert.assertTrue(links[0] > 0);
            compiles[0] = links[0] = 0;
            Assert.assertArrayEquals(beforeRestore, pixels(renderer, q, layers, camera, shadows, .25f, .12f, .4f, false, 64, 32));
            q.lighting.lights.add(light);
            Assert.assertArrayEquals(pixels(renderer, q, layers, camera, shadows, .25f, .12f, .4f, true, 64, 32),
                    pixels(renderer, q, layers, camera, shadows, .25f, .12f, .4f, false, 64, 32));
            Assert.assertEquals(0, compiles[0]);
            Assert.assertEquals(0, links[0]);
            Assert.assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            renderer.dispose();
            renderer.prepare(nextWorld, bundle);
            renderer.general = renderer.packedReceiverShader();
            q.lighting.lights.clear();
            pixels(renderer, q, layers, camera, shadows, 1, 1, 1, false, 64, 32);
            Assert.assertNotSame("Recreated resources include both variants", renderer.general, renderer.selected);
            Assert.assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally {
            Gdx.gl = Gdx.gl20 = original;
            Gdx.gl30 = original30;
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
            Gdx.gl.glViewport(0, 0, 64, 32);
            renderer.dispose();
            shadows.dispose();
            external.dispose();
            world.dispose();
            nextWorld.dispose();
        }
    }

    private static int[] pixels(Renderer renderer, FrameRenderQueue q, LayerStateSOA layers, OrthographicCamera camera,
                                LightingRenderer shadows, float r, float g, float b, boolean force, int w, int h) {
        renderer.forceGeneral = force;
        Gdx.gl.glClearColor(0, 0, 0, 0);
        boolean clipped = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (clipped) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        renderer.render(q, layers, camera, shadows, r, g, b, new RenderStats());
        Pixmap image = Pixmap.createFromFrameBuffer(0, 0, w, h);
        int[] result = new int[w * h];
        try {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) result[y * w + x] = image.getPixel(x, y);
        } finally {
            image.dispose();
        }
        return result;
    }
}
