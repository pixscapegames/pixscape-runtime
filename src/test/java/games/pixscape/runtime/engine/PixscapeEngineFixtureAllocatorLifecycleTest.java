package games.pixscape.runtime.engine;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class PixscapeEngineFixtureAllocatorLifecycleTest {
    private Application previousApp;
    private Files previousFiles;
    private Graphics previousGraphics;
    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;

    @BeforeClass
    public static void loadGdxNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installGdxProxies() {
        previousApp = Gdx.app;
        previousFiles = Gdx.files;
        previousGraphics = Gdx.graphics;
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;

        AtomicInteger handles = new AtomicInteger(1);
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class[]{GL30.class},
                (proxy, method, args) -> invokeGl(method.getName(), args, method.getReturnType(), handles));
        Graphics graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Gdx.app = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class[]{Application.class},
                (proxy, method, args) -> {
                    if ("getType".equals(method.getName())) {
                        return Application.ApplicationType.HeadlessDesktop;
                    }
                    if ("getGraphics".equals(method.getName())) return graphics;
                    if ("getFiles".equals(method.getName())) return Gdx.files;
                    return defaultValue(method.getReturnType());
                });
        Gdx.files = new ResourceFiles();
        Gdx.graphics = graphics;
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
    }

    @After
    public void restoreGdxState() {
        ShaderRegistry.disposeAll();
        Gdx.app = previousApp;
        Gdx.files = previousFiles;
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
    }

    @Test
    public void initEmptyRuntimeCreatesUnboundWorldAndRejectsFixtureAllocation() {
        PixscapeEngine engine = new PixscapeEngine();
        try {
            engine.initEmptyRuntime();

            FixtureIdAllocatorSystem allocator =
                    engine.getWorld().getSystem(FixtureIdAllocatorSystem.class);
            Assert.assertNotNull(allocator);
            Assert.assertFalse(allocator.isBound());
            try {
                new PhysicsService(engine.getWorld(), null).createDefaultFixture();
                Assert.fail("Expected fixture allocation without an active scene to fail");
            } catch (IllegalStateException expected) {
                Assert.assertEquals(
                        "Cannot allocate fixture ID: no active scene metadata is bound",
                        expected.getMessage());
            }
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void loadProjectThenSceneBindsExportedMetadataAndKeepsSceneCountersIndependent()
            throws Exception {
        FileHandle userRoot = runtimeProject(10, 100);
        PixscapeEngine engine = new PixscapeEngine();
        try {
            engine.loadProject(userRoot);
            FixtureIdAllocatorSystem allocator =
                    engine.getWorld().getSystem(FixtureIdAllocatorSystem.class);
            Assert.assertFalse(allocator.isBound());

            engine.loadScene("A");
            allocator = engine.getWorld().getSystem(FixtureIdAllocatorSystem.class);
            SceneMetaRuntime sceneA = engine.config().getSceneMeta("A");
            Assert.assertSame(sceneA, allocator.sceneMeta());
            Assert.assertEquals(10,
                    new PhysicsService(engine.getWorld(), null).createDefaultFixture().fixtureId);
            Assert.assertEquals(11, sceneA.nextFixtureId);

            engine.loadScene("B");
            allocator = engine.getWorld().getSystem(FixtureIdAllocatorSystem.class);
            SceneMetaRuntime sceneB = engine.config().getSceneMeta("B");
            Assert.assertSame(sceneB, allocator.sceneMeta());
            Assert.assertEquals(100,
                    new PhysicsService(engine.getWorld(), null).createDefaultFixture().fixtureId);
            Assert.assertEquals(101, sceneB.nextFixtureId);

            engine.loadScene("A");
            allocator = engine.getWorld().getSystem(FixtureIdAllocatorSystem.class);
            Assert.assertSame(sceneA, allocator.sceneMeta());
            Assert.assertEquals(11,
                    new PhysicsService(engine.getWorld(), null).createDefaultFixture().fixtureId);
            Assert.assertEquals(12, sceneA.nextFixtureId);
            Assert.assertEquals(101, sceneB.nextFixtureId);
        } finally {
            engine.dispose();
            userRoot.deleteDirectory();
        }
    }

    private static FileHandle runtimeProject(int nextA, int nextB) throws Exception {
        Path path = java.nio.file.Files.createTempDirectory("pixscape-engine-fixture-lifecycle");
        FileHandle userRoot = new FileHandle(path.toFile());
        FileHandle runtimeRoot = userRoot.child(PixscapeEngine.RUNTIME_DIR_NAME);
        FileHandle scenes = runtimeRoot.child("scenes");
        FileHandle atlases = runtimeRoot.child("atlases");
        scenes.mkdirs();
        atlases.mkdirs();
        runtimeRoot.child("project.json").writeString(
                "{\n"
                        + "  \"projectFileName\": \"fixture-lifecycle\",\n"
                        + "  \"version\": \"1\",\n"
                        + "  \"currentSceneName\": \"A\",\n"
                        + "  \"scenes\": {\n"
                        + "    \"A\": {\"name\": \"A\", \"file\": \"scene-a.json\", \"nextFixtureId\": "
                        + nextA + "},\n"
                        + "    \"B\": {\"name\": \"B\", \"file\": \"scene-b.json\", \"nextFixtureId\": "
                        + nextB + "}\n"
                        + "  }\n"
                        + "}\n",
                false,
                "UTF-8");
        writeEmptyScene(scenes.child("scene-a.json"));
        writeEmptyScene(scenes.child("scene-b.json"));
        writeAtlas(atlases, "scene-a");
        writeAtlas(atlases, "scene-b");
        return userRoot;
    }

    private static void writeEmptyScene(FileHandle file) {
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        try {
            WorldSerializationManager serialization =
                    world.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(world));
            serialization.save(file.write(false), new SaveFileFormat());
        } finally {
            world.dispose();
        }
    }

    private static void writeAtlas(FileHandle atlases, String sceneTag) {
        FileHandle page = atlases.child(sceneTag + ".png");
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(1f, 1f, 1f, 1f);
            pixmap.drawPixel(0, 0);
            PixmapIO.writePNG(page, pixmap);
        } finally {
            pixmap.dispose();
        }
        atlases.child(sceneTag + ".atlas").writeString(
                sceneTag + ".png\n"
                        + "size: 1,1\n"
                        + "format: RGBA8888\n"
                        + "filter: Nearest,Nearest\n"
                        + "repeat: none\n"
                        + "white\n"
                        + "  rotate: false\n"
                        + "  xy: 0,0\n"
                        + "  size: 1,1\n"
                        + "  orig: 1,1\n"
                        + "  offset: 0,0\n"
                        + "  index: -1\n",
                false,
                "UTF-8");
    }

    private static Object invokeGl(String name,
                                   Object[] args,
                                   Class<?> returnType,
                                   AtomicInteger handles) {
        if ("glGetIntegerv".equals(name)) {
            ((IntBuffer) args[1]).put(0, 16);
            return null;
        }
        if ("glGetShaderiv".equals(name) || "glGetProgramiv".equals(name)) {
            int value = 1;
            int parameter = (Integer) args[1];
            if (parameter == GL20.GL_ACTIVE_ATTRIBUTES || parameter == GL20.GL_ACTIVE_UNIFORMS) {
                value = 0;
            }
            ((IntBuffer) args[2]).put(0, value);
            return null;
        }
        if ("glCreateShader".equals(name)
                || "glCreateProgram".equals(name)
                || "glGenBuffer".equals(name)
                || "glGenTexture".equals(name)
                || "glGenVertexArray".equals(name)) {
            return handles.getAndIncrement();
        }
        if ("glGetShaderInfoLog".equals(name) || "glGetProgramInfoLog".equals(name)) {
            return "";
        }
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
        if (type == char.class) return (char) 0;
        return null;
    }

    private static final class ResourceFiles implements Files {
        @Override
        public FileHandle getFileHandle(String path, FileType type) {
            if (type == FileType.Internal || type == FileType.Classpath) {
                return new FileHandle(new File("src/main/resources", path));
            }
            return new FileHandle(path);
        }

        @Override
        public FileHandle classpath(String path) {
            return getFileHandle(path, FileType.Classpath);
        }

        @Override
        public FileHandle internal(String path) {
            return getFileHandle(path, FileType.Internal);
        }

        @Override
        public FileHandle external(String path) {
            return getFileHandle(path, FileType.External);
        }

        @Override
        public FileHandle absolute(String path) {
            return getFileHandle(path, FileType.Absolute);
        }

        @Override
        public FileHandle local(String path) {
            return getFileHandle(path, FileType.Local);
        }

        @Override
        public String getExternalStoragePath() {
            return new File(".").getAbsolutePath();
        }

        @Override
        public boolean isExternalStorageAvailable() {
            return true;
        }

        @Override
        public String getLocalStoragePath() {
            return new File(".").getAbsolutePath();
        }

        @Override
        public boolean isLocalStorageAvailable() {
            return true;
        }
    }
}
