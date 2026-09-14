package games.pixscape.runtime.hud;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.hud.document.HudDocumentLoadCode;
import games.pixscape.runtime.hud.document.HudDocumentLoadException;
import games.pixscape.runtime.hud.document.HudValidationIssue;
import games.pixscape.runtime.hud.document.HudValidationIssueCode;
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

public class HudScreenRuntimeTest {
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
    private Files previousFiles;
    private ShaderProgram shader;
    private int deletedTextures;
    private int drawCalls;

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
        previousFiles = Gdx.files;
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
                    if ("getWidth".equals(method.getName())) return 320;
                    if ("getHeight".equals(method.getName())) return 180;
                    return defaultValue(method.getReturnType());
                });
        Gdx.files = (Files) Proxy.newProxyInstance(
                Files.class.getClassLoader(), new Class<?>[]{Files.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        TextureRegistry.clear();
        InternalTextures.dispose();
        shader = shader();
    }

    @After
    public void restoreLibGdx() {
        if (shader != null) shader.dispose();
        InternalTextures.dispose();
        TextureRegistry.clear();
        Gdx.app = previousApp;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
        Gdx.graphics = previousGraphics;
        Gdx.files = previousFiles;
    }

    @Test
    public void completeCandidateReplacesTransactionallyAndHideOwnsDisposal() throws Exception {
        FileHandle root = project();
        writeScreen(root, "a", "hud/a.json");
        writeScreen(root, "b", "hud/b.json");
        writeScreen(root, "invalid", "hud/invalid.json");
        copyFixture(root.child("hud/a.json"), "materializer-smoke.json");
        copyFixture(root.child("hud/b.json"), "materializer-smoke.json");
        root.child("hud/invalid.json").writeString(missingRegionDocument(), false, "UTF-8");

        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        ActiveHudScreen a = runtime.show("a");
        HudSession aSession = a.session();
        HudResources aResources = a.resources();
        Assert.assertEquals("hud/a", a.screenId());
        Assert.assertNotNull(a.materializedHud().actor("smoke-image"));
        Assert.assertNotNull(a.materializedHud().actor("smoke-label"));
        Assert.assertNotNull(a.materializedHud().actor("smoke-button"));

        ActiveHudScreen b = runtime.show("hud/b");
        Assert.assertSame(b, runtime.activeScreen());
        Assert.assertTrue(a.isDisposed());
        Assert.assertTrue(aSession.isDisposed());
        Assert.assertTrue(aResources.isDisposed());

        int deletionsBefore = deletedTextures;
        HudDocumentValidationException failure = Assert.assertThrows(
                HudDocumentValidationException.class, () -> runtime.show("invalid"));
        Assert.assertEquals(HudDocumentValidationException.Phase.RESOURCE_AWARE,
                failure.phase());
        Assert.assertSame(b, runtime.activeScreen());
        Assert.assertFalse(b.isDisposed());
        Assert.assertTrue("failed candidate resources must be cleaned up",
                deletedTextures > deletionsBefore);

        HudSession bSession = b.session();
        HudResources bResources = b.resources();
        runtime.hide();
        runtime.hide();
        Assert.assertNull(runtime.activeScreen());
        Assert.assertTrue(bSession.isDisposed());
        Assert.assertTrue(bResources.isDisposed());
    }

    @Test
    public void frameAndResizeLifecycleDelegatesWithoutRematerializing() throws Exception {
        FileHandle root = project();
        writeScreen(root, "game", "hud/game.json");
        copyFixture(root.child("hud/game.json"), "materializer-smoke.json");
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen active = runtime.show("game");
            MaterializedHud materialized = active.materializedHud();
            CountingActor counter = new CountingActor();
            ((Group) materialized.root()).addActor(counter);

            runtime.act(0.25f);
            runtime.resize(640, 360);
            runtime.draw();

            Assert.assertEquals(0.25f, counter.elapsed, 0f);
            Assert.assertTrue(drawCalls > 0);
            Assert.assertSame(materialized, active.materializedHud());
            Assert.assertEquals(320f, active.session().viewport().getWorldWidth(), 0f);
            Assert.assertEquals(180f, active.session().viewport().getWorldHeight(), 0f);
            Assert.assertEquals(232f, materialized.actor("smoke-button").getX(), 0.01f);
        } finally {
            runtime.dispose();
        }
    }

    @Test
    public void legacyEmptyScreenRequiresNeitherDocumentNorGlResources() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("empty-project"));
        root.child("hud").mkdirs();
        root.child("hud/empty.hudscreen").writeString(
                "{\"schemaVersion\":1}", false, "UTF-8");
        HudScreenRuntime runtime = new HudScreenRuntime(root, null);
        ActiveHudScreen empty = runtime.show("empty");
        Assert.assertTrue(empty.isEmpty());
        Assert.assertNull(empty.materializedHud());
        runtime.act(0.1f);
        runtime.draw();
        runtime.resize(1, 1);
        runtime.dispose();
        Assert.assertTrue(empty.isDisposed());
    }

    @Test
    public void presentLayoutOnlyScreenRunsWithoutSkinAtlasOrTextureBundle() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("layout-only-project"));
        root.child("hud").mkdirs();
        root.child("hud/layout.hudscreen").writeString(
                "{\"schemaVersion\":1,\"referenceWidth\":320,"
                        + "\"referenceHeight\":180,\"documentId\":\"hud/layout.json\"}",
                false, "UTF-8");
        root.child("hud/layout.json").writeString(
                "{\"schemaVersion\":1,\"root\":{\"id\":\"root\","
                        + "\"kind\":\"GROUP\",\"children\":[{"
                        + "\"placementKind\":\"DIRECT\",\"node\":{"
                        + "\"id\":\"stack\",\"kind\":\"STACK\","
                        + "\"children\":[]}}]}}", false, "UTF-8");

        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        ActiveHudScreen active = runtime.show("layout");
        try {
            Assert.assertFalse(active.isEmpty());
            Assert.assertNotNull(active.materializedHud().actor("root"));
            Assert.assertNotNull(active.materializedHud().actor("stack"));
            Assert.assertNull(active.resources().textureArrayBundle());
            Assert.assertNull(active.session().hudBatch().getTextureArrayBundle());
            int drawsBefore = drawCalls;
            runtime.act(0f);
            runtime.resize(640, 360);
            runtime.draw();
            Assert.assertEquals(drawsBefore, drawCalls);
        } finally {
            runtime.dispose();
        }
        Assert.assertTrue(active.isDisposed());
    }

    @Test
    public void failuresRetainStageSpecificAndTypedDiagnostics() throws Exception {
        FileHandle root = project();
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        RuntimeException missingAsset = Assert.assertThrows(
                RuntimeException.class, () -> runtime.show("absent"));
        Assert.assertTrue(missingAsset.getMessage().contains("hud/absent"));

        writeScreen(root, "missing-doc", "hud/missing.json");
        HudDocumentLoadException missingDocument = Assert.assertThrows(
                HudDocumentLoadException.class, () -> runtime.show("missing-doc"));
        Assert.assertEquals(HudDocumentLoadCode.READ_FAILURE, missingDocument.code());
        Assert.assertEquals(root.child("hud/missing.json").path(), missingDocument.source());

        writeScreen(root, "malformed", "hud/malformed.json");
        root.child("hud/malformed.json").writeString("{", false, "UTF-8");
        HudDocumentLoadException malformed = Assert.assertThrows(
                HudDocumentLoadException.class, () -> runtime.show("malformed"));
        Assert.assertEquals(HudDocumentLoadCode.MALFORMED_JSON, malformed.code());

        writeScreen(root, "invalid", "hud/invalid.json");
        copyFixture(root.child("hud/invalid.json"), "invalid-duplicate-node-id.json");
        HudDocumentValidationException invalid = Assert.assertThrows(
                HudDocumentValidationException.class, () -> runtime.show("invalid"));
        Assert.assertEquals(HudDocumentValidationException.Phase.STRUCTURAL, invalid.phase());
        HudValidationIssue issue = invalid.validationResult().issues().get(0);
        Assert.assertEquals(HudValidationIssueCode.DUPLICATE_NODE_ID, issue.code());
        Assert.assertNotNull(issue.nodeId());
        Assert.assertNotNull(issue.path());
        Assert.assertNotNull(issue.message());

        root.child("hud/no-resources.hudscreen").writeString(
                "{\"schemaVersion\":1,\"documentId\":\"hud/a.json\"}",
                false, "UTF-8");
        copyFixture(root.child("hud/a.json"), "materializer-smoke.json");
        RuntimeException missingResource = Assert.assertThrows(
                RuntimeException.class, () -> runtime.show("no-resources"));
        Assert.assertTrue(missingResource.getMessage().contains("skinId"));
    }

    private FileHandle project() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("project"));
        HudResourcesTest.writeHudFiles(root);
        root.child("hud").mkdirs();
        return root;
    }

    private static void writeScreen(FileHandle root, String name, String documentId) {
        root.child("hud/" + name + HudScreenAsset.EXTENSION).writeString(
                "{\"schemaVersion\":1,\"referenceWidth\":320,\"referenceHeight\":180,"
                        + "\"documentId\":\"" + documentId + "\","
                        + "\"skinId\":\"ui/game.json\","
                        + "\"atlasId\":\"ui/game.atlas\","
                        + "\"textureProfileId\":\"" + HudTextureProfile.DEFAULT_ID + "\"}",
                false, "UTF-8");
    }

    private static void copyFixture(FileHandle target, String fixture) {
        target.writeString(new FileHandle(
                "src/test/resources/games/pixscape/runtime/hud/document/v1/" + fixture)
                .readString("UTF-8"), false, "UTF-8");
    }

    private static String missingRegionDocument() {
        return "{\"schemaVersion\":1,\"root\":{\"id\":\"missing\","
                + "\"kind\":\"IMAGE\",\"image\":{\"source\":\"REGION\","
                + "\"resourceName\":\"not-in-atlas\"},\"children\":[]}}";
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

    private Object glValue(String name, Object[] args, Class<?> returnType, int[] nextHandle) {
        if ("glCreateShader".equals(name) || "glCreateProgram".equals(name)
                || "glGenTexture".equals(name) || "glGenBuffer".equals(name)
                || "glGenVertexArray".equals(name)) return nextHandle[0]++;
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
        if ("glDeleteTextures".equals(name) || "glDeleteTexture".equals(name)) deletedTextures++;
        if ("glDrawElements".equals(name)) drawCalls++;
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
}
