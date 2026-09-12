package games.pixscape.runtime.hud;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.viewport.FitViewport;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

public class HudSessionTest {
    private static final String[] ATTRIBUTES = {
            "a_position", "a_color", "a_texCoord0", "a_layer"
    };
    private static final String[] UNIFORMS = {"u_projTrans", "u_array"};

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Application previousApp;
    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;
    private Graphics previousGraphics;
    private int deletedBuffers;
    private int deletedTextures;
    private int deletedPrograms;

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installLibGdxProxies() {
        previousApp = Gdx.app;
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;
        previousGraphics = Gdx.graphics;

        Gdx.app = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(), new Class<?>[]{Application.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        int[] nextHandle = {1};
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(), new Class<?>[]{GL30.class},
                (proxy, method, args) -> glValue(
                        method.getName(), args, method.getReturnType(), nextHandle));
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
                (proxy, method, args) -> {
                    if ("getWidth".equals(method.getName())) return 1920;
                    if ("getHeight".equals(method.getName())) return 1080;
                    return defaultValue(method.getReturnType());
                });
        TextureRegistry.clear();
        InternalTextures.dispose();
    }

    @After
    public void restoreLibGdx() {
        InternalTextures.dispose();
        TextureRegistry.clear();
        Gdx.app = previousApp;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
        Gdx.graphics = previousGraphics;
    }

    @Test
    public void constructionUsesAuthoredFitViewportHudBatchAndBorrowedBundle() throws Exception {
        Prepared prepared = prepare();
        HudScreenAsset asset = prepared.asset;
        asset.referenceWidth = 1920;
        asset.referenceHeight = 1080;
        HudSession session = HudSession.create(asset, prepared.resources, prepared.shader);
        try {
            Assert.assertTrue(session.viewport() instanceof FitViewport);
            Assert.assertEquals(1920f, session.viewport().getWorldWidth(), 0f);
            Assert.assertEquals(1080f, session.viewport().getWorldHeight(), 0f);
            Assert.assertSame(session.hudBatch(), session.stage().getBatch());
            Assert.assertSame(session.viewport(), session.stage().getViewport());
            Assert.assertSame(prepared.resources.textureArrayBundle(),
                    session.hudBatch().getTextureArrayBundle());
            Assert.assertEquals(0, session.stage().getActors().size);
        } finally {
            session.dispose();
            prepared.dispose();
        }
    }

    @Test
    public void actAdvancesARealScene2dActor() throws Exception {
        Prepared prepared = prepare();
        HudSession session = HudSession.create(prepared.asset, prepared.resources, prepared.shader);
        CountingActor actor = new CountingActor();
        session.stage().addActor(actor);
        try {
            session.act(0.25f);
            Assert.assertEquals(0.25f, actor.elapsed, 0f);
        } finally {
            session.dispose();
            prepared.dispose();
        }
    }

    @Test
    public void resizePreservesLogicalSpaceAndCentersCameraAcrossAspectRatios() throws Exception {
        Prepared prepared = prepare();
        HudSession session = HudSession.create(prepared.asset, prepared.resources, prepared.shader);
        try {
            assertResize(session, 1920, 1080);
            assertResize(session, 1280, 720);
            assertResize(session, 3440, 1440);
            Assert.assertEquals(2560, session.viewport().getScreenWidth());
            Assert.assertEquals(440, session.viewport().getScreenX());
            assertResize(session, 1080, 1920);
            Assert.assertEquals(1080, session.viewport().getScreenWidth());
            Assert.assertTrue(session.viewport().getScreenHeight() == 607
                    || session.viewport().getScreenHeight() == 608);
            Assert.assertThrows(IllegalArgumentException.class, () -> session.resize(0, 1080));
        } finally {
            session.dispose();
            prepared.dispose();
        }
    }

    @Test
    public void disposeOwnsBatchExactlyOnceButLeavesResourcesAndShaderBorrowed() throws Exception {
        Prepared prepared = prepare();
        HudSession session = HudSession.create(prepared.asset, prepared.resources, prepared.shader);
        int texturesBefore = deletedTextures;
        int programsBefore = deletedPrograms;

        session.dispose();
        int buffersAfterFirst = deletedBuffers;
        session.dispose();

        Assert.assertTrue(session.isDisposed());
        Assert.assertTrue("HudBatch mesh buffers must be disposed", buffersAfterFirst > 0);
        Assert.assertEquals(buffersAfterFirst, deletedBuffers);
        Assert.assertEquals(texturesBefore, deletedTextures);
        Assert.assertEquals(programsBefore, deletedPrograms);
        Assert.assertFalse(prepared.resources.isDisposed());
        Assert.assertTrue(prepared.shader.isCompiled());

        prepared.dispose();
    }

    @Test
    public void disposedResourcesAndUseAfterDisposeFailClearly() throws Exception {
        Prepared disposedPrepared = prepare();
        disposedPrepared.resources.dispose();
        IllegalStateException constructionFailure = Assert.assertThrows(
                IllegalStateException.class,
                () -> HudSession.create(
                        disposedPrepared.asset, disposedPrepared.resources, disposedPrepared.shader));
        Assert.assertEquals("HudResources has been disposed.", constructionFailure.getMessage());
        disposedPrepared.shader.dispose();

        Prepared prepared = prepare();
        HudSession session = HudSession.create(prepared.asset, prepared.resources, prepared.shader);
        session.dispose();
        try {
            assertDisposed(() -> session.act(0f));
            assertDisposed(session::draw);
            assertDisposed(() -> session.resize(1920, 1080));
            assertDisposed(session::stage);
        } finally {
            prepared.dispose();
        }
    }

    @Test
    public void externallyDisposedBorrowedResourcesFailFastWhileSessionLives() throws Exception {
        Prepared prepared = prepare();
        HudSession session = HudSession.create(prepared.asset, prepared.resources, prepared.shader);
        prepared.resources.dispose();
        try {
            IllegalStateException failure = Assert.assertThrows(
                    IllegalStateException.class, session::draw);
            Assert.assertEquals(
                    "Borrowed HudResources was disposed while HudSession is still active.",
                    failure.getMessage());
        } finally {
            session.dispose();
            prepared.shader.dispose();
        }
    }

    private Prepared prepare() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder());
        HudResourcesTest.writeHudFiles(root);
        HudScreenAsset asset = new HudScreenAsset();
        asset.skinId = "ui/game.json";
        asset.atlasId = "ui/game.atlas";
        return new Prepared(asset, HudResources.prepare(asset, root), shader());
    }

    private static ShaderProgram shader() {
        return new ShaderProgram(
                "attribute vec2 a_position; attribute vec4 a_color;"
                        + "attribute vec2 a_texCoord0; attribute float a_layer;"
                        + "uniform mat4 u_projTrans; void main(){"
                        + "gl_Position=u_projTrans*vec4(a_position,0.0,1.0);}",
                "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
                        + "uniform sampler2DArray u_array; void main(){gl_FragColor=vec4(1.0);}");
    }

    private static void assertResize(HudSession session, int width, int height) {
        session.resize(width, height);
        Assert.assertEquals(1920f, session.viewport().getWorldWidth(), 0f);
        Assert.assertEquals(1080f, session.viewport().getWorldHeight(), 0f);
        Assert.assertEquals(960f, session.viewport().getCamera().position.x, 0f);
        Assert.assertEquals(540f, session.viewport().getCamera().position.y, 0f);
    }

    private static void assertDisposed(Runnable operation) {
        IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, operation::run);
        Assert.assertEquals("HudSession has been disposed.", failure.getMessage());
    }

    private Object glValue(
            String name, Object[] args, Class<?> returnType, int[] nextHandle) {
        if ("glCreateShader".equals(name) || "glCreateProgram".equals(name)
                || "glGenTexture".equals(name) || "glGenBuffer".equals(name)
                || "glGenVertexArray".equals(name)) {
            return nextHandle[0]++;
        }
        if ("glGetShaderiv".equals(name) && args != null && args.length >= 3) {
            ((IntBuffer) args[2]).put(0, 1);
            return null;
        }
        if ("glGetIntegerv".equals(name) && args != null && args.length >= 2) {
            int parameter = (Integer) args[0];
            int value = parameter == GL30.GL_MAX_ARRAY_TEXTURE_LAYERS ? 16 : 4096;
            ((IntBuffer) args[1]).put(0, value);
            return null;
        }
        if ("glGetProgramiv".equals(name) && args != null && args.length >= 3) {
            int parameter = (Integer) args[1];
            int value = parameter == GL20.GL_LINK_STATUS || parameter == GL20.GL_VALIDATE_STATUS
                    ? 1 : parameter == GL20.GL_ACTIVE_ATTRIBUTES
                    ? ATTRIBUTES.length : parameter == GL20.GL_ACTIVE_UNIFORMS
                    ? UNIFORMS.length : 0;
            ((IntBuffer) args[2]).put(0, value);
            return null;
        }
        if ("glGetActiveAttrib".equals(name)) return ATTRIBUTES[(Integer) args[1]];
        if ("glGetActiveUniform".equals(name)) return UNIFORMS[(Integer) args[1]];
        if ("glGetAttribLocation".equals(name) || "glGetUniformLocation".equals(name)) return 1;
        if (name.startsWith("glGen") && args != null && args.length >= 2
                && args[1] instanceof IntBuffer) {
            IntBuffer handles = (IntBuffer) args[1];
            int count = (Integer) args[0];
            for (int i = 0; i < count; i++) handles.put(i, nextHandle[0]++);
            return null;
        }
        if ("glDeleteBuffer".equals(name)) deletedBuffers++;
        if ("glDeleteTextures".equals(name) || "glDeleteTexture".equals(name)) deletedTextures++;
        if ("glDeleteProgram".equals(name)) deletedPrograms++;
        if ("glCheckFramebufferStatus".equals(name)) return GL20.GL_FRAMEBUFFER_COMPLETE;
        return defaultValue(returnType);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class CountingActor extends Actor {
        private float elapsed;

        @Override
        public void act(float delta) {
            elapsed += delta;
            super.act(delta);
        }
    }

    private static final class Prepared {
        private final HudScreenAsset asset;
        private final HudResources resources;
        private final ShaderProgram shader;

        private Prepared(HudScreenAsset asset, HudResources resources, ShaderProgram shader) {
            this.asset = asset;
            this.resources = resources;
            this.shader = shader;
        }

        private void dispose() {
            resources.dispose();
            shader.dispose();
        }
    }
}
