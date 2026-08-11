package games.pixscape.runtime.engine;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class PixscapeEngineRenderIntegrationTest {
    private Application previousApp;
    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;
    private Graphics previousGraphics;
    private com.badlogic.gdx.Files previousFiles;

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
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        int[] nextHandle = {1};
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class<?>[]{GL30.class},
                (proxy, method, args) -> glDefaultValue(
                        method.getName(), args, method.getReturnType(), nextHandle));
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(
                com.badlogic.gdx.Files.class.getClassLoader(),
                new Class<?>[]{com.badlogic.gdx.Files.class},
                (proxy, method, args) -> {
                    if (args != null && args.length == 1
                            && args[0] instanceof String
                            && FileHandle.class.equals(method.getReturnType())) {
                        String path = (String) args[0];
                        if ("internal".equals(method.getName())
                                || "classpath".equals(method.getName())) {
                            return new FileHandle("src/main/resources").child(path);
                        }
                        return new FileHandle(path);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    @After
    public void restoreLibGdx() {
        Gdx.app = previousApp;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
        Gdx.graphics = previousGraphics;
        Gdx.files = previousFiles;
    }

    @Test
    public void engineHooksAndCustomSubmitPreserveRenderPhaseOrdering() {
        PixscapeEngine engine = new PixscapeEngine();
        List<String> events = new ArrayList<>();
        SubmitProbe probe = new SubmitProbe();
        engine.setPreRenderSystemCustomizer(builder ->
                builder.with(new CurrentFrameVfxProducer(engine, events)));
        engine.setPostRenderSystemCustomizer(builder ->
                builder.with(new PostRenderProbe(engine, events, probe)));
        engine.setRenderSubmitSystemSupplier(() -> new QueueSubmitProbe(engine, events, probe));

        try {
            engine.initEmptyRuntime();
            Assert.assertNull(engine.getWorld().getSystem(RenderSubmitSystem.class));

            int layerEntity = engine.getWorld().create();
            LayerComponent layer = engine.getWorld().getMapper(LayerComponent.class).create(layerEntity);
            layer.layerIndex = 0;
            int dirtyEntity = engine.getWorld().create();
            engine.getWorld().getSystem(DirtyTrackerSystem.class).material(dirtyEntity);

            engine.update(1f / 60f);
            engine.render();

            Assert.assertEquals(3, events.size());
            Assert.assertEquals("pre", events.get(0));
            Assert.assertEquals("submit", events.get(1));
            Assert.assertEquals("post", events.get(2));
            Assert.assertTrue(probe.sawExtractedCurrentFrameVfx);
            Assert.assertTrue(probe.sawDirtyBeforeFlush);
            Assert.assertTrue(probe.sawClearedDirtyAfterFlush);
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void legacyCustomizerRetainsPostRenderPlacement() {
        PixscapeEngine engine = new PixscapeEngine();
        SubmitProbe probe = new SubmitProbe();
        engine.setRenderSubmitSystemSupplier(() -> new QueueSubmitProbe(engine, null, probe));
        engine.setConfigurationCustomizer(builder ->
                builder.with(new LegacyPostRenderProbe(engine, probe)));

        try {
            engine.initEmptyRuntime();
            int entity = engine.getWorld().create();
            engine.getWorld().getSystem(DirtyTrackerSystem.class).material(entity);
            engine.render();

            Assert.assertTrue(probe.submitted);
            Assert.assertTrue(probe.legacyRanAfterSubmitAndFlush);
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void submitSupplierCreatesFreshSystemForRebuiltWorld() {
        PixscapeEngine engine = new PixscapeEngine();
        List<QueueSubmitProbe> submitters = new ArrayList<>();
        List<World> publishedWorldsAtSupply = new ArrayList<>();
        SubmitProbe probe = new SubmitProbe();
        engine.setRenderSubmitSystemSupplier(() -> {
            publishedWorldsAtSupply.add(engine.getWorld());
            QueueSubmitProbe submitter = new QueueSubmitProbe(engine, null, probe);
            submitters.add(submitter);
            return submitter;
        });

        try {
            engine.initEmptyRuntime();
            World firstWorld = engine.getWorld();
            Assert.assertEquals(1, submitters.size());
            Assert.assertNull(publishedWorldsAtSupply.get(0));
            Assert.assertSame(firstWorld, submitters.get(0).initializedWorld);

            engine.rebuildWorldOnly(engine.config(), new FileHandle("."));
            World secondWorld = engine.getWorld();

            Assert.assertNotSame(firstWorld, secondWorld);
            Assert.assertEquals(2, submitters.size());
            Assert.assertNotSame(submitters.get(0), submitters.get(1));
            Assert.assertSame(firstWorld, publishedWorldsAtSupply.get(1));
            Assert.assertSame(secondWorld, submitters.get(1).initializedWorld);

            engine.setRenderSubmitSystemSupplier(null);
            engine.rebuildWorldOnly(engine.config(), new FileHandle("."));
            Assert.assertEquals(2, submitters.size());
            Assert.assertNotNull(engine.getWorld().getSystem(RenderSubmitSystem.class));
        } finally {
            engine.dispose();
        }
    }

    private static final class CurrentFrameVfxProducer extends BaseSystem {
        private final PixscapeEngine engine;
        private final List<String> events;

        CurrentFrameVfxProducer(PixscapeEngine engine, List<String> events) {
            this.engine = engine;
            this.events = events;
        }

        @Override
        protected void processSystem() {
            events.add("pre");
            Assert.assertTrue(engine.getLayerState().enabled[0]);
            VfxRenderState vfx = engine.getVfxState();
            vfx.addParticleQuad(
                    1, 0, BlendMode.ALPHA.id, 0, 0, 0, 0, 0L,
                    0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f,
                    0f, 0f, 1f, 1f, 1f,
                    RenderRepeatFlags.NONE, -1);
        }
    }

    private static final class QueueSubmitProbe extends BaseSystem {
        private final PixscapeEngine engine;
        private final List<String> events;
        private final SubmitProbe probe;
        private World initializedWorld;

        QueueSubmitProbe(PixscapeEngine engine, List<String> events, SubmitProbe probe) {
            this.engine = engine;
            this.events = events;
            this.probe = probe;
        }

        @Override
        protected void initialize() {
            initializedWorld = world;
        }

        @Override
        protected void processSystem() {
            if (events != null) events.add("submit");
            probe.submitted = true;
            FrameRenderQueue queue = engine.getFrameQueue();
            probe.sawExtractedCurrentFrameVfx = queue.size == 1
                    && queue.sourceDomain[0] == RenderSourceDomain.SOURCE_VFX;
            DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
            probe.sawDirtyBeforeFlush = dirty.materialEntities().size > 0;
        }
    }

    private static final class PostRenderProbe extends BaseSystem {
        private final PixscapeEngine engine;
        private final List<String> events;
        private final SubmitProbe probe;

        PostRenderProbe(PixscapeEngine engine, List<String> events, SubmitProbe probe) {
            this.engine = engine;
            this.events = events;
            this.probe = probe;
        }

        @Override
        protected void processSystem() {
            events.add("post");
            DirtyTrackerSystem dirty = engine.getWorld().getSystem(DirtyTrackerSystem.class);
            probe.sawClearedDirtyAfterFlush = dirty.materialEntities().size == 0;
        }
    }

    private static final class LegacyPostRenderProbe extends BaseSystem {
        private final PixscapeEngine engine;
        private final SubmitProbe probe;

        LegacyPostRenderProbe(PixscapeEngine engine, SubmitProbe probe) {
            this.engine = engine;
            this.probe = probe;
        }

        @Override
        protected void processSystem() {
            DirtyTrackerSystem dirty = engine.getWorld().getSystem(DirtyTrackerSystem.class);
            probe.legacyRanAfterSubmitAndFlush = probe.submitted
                    && dirty.materialEntities().size == 0;
        }
    }

    private static final class SubmitProbe {
        private boolean submitted;
        private boolean sawExtractedCurrentFrameVfx;
        private boolean sawDirtyBeforeFlush;
        private boolean sawClearedDirtyAfterFlush;
        private boolean legacyRanAfterSubmitAndFlush;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Boolean.TYPE) return false;
        if (type == Byte.TYPE) return (byte) 0;
        if (type == Short.TYPE) return (short) 0;
        if (type == Character.TYPE) return (char) 0;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Float.TYPE) return 0f;
        if (type == Double.TYPE) return 0d;
        return null;
    }

    private static Object glDefaultValue(
            String methodName, Object[] args, Class<?> returnType, int[] nextHandle) {
        if ("glCreateShader".equals(methodName)
                || "glCreateProgram".equals(methodName)
                || "glGenTexture".equals(methodName)
                || "glGenBuffer".equals(methodName)
                || "glGenVertexArray".equals(methodName)) {
            return nextHandle[0]++;
        }
        if ("glGetShaderiv".equals(methodName) && args != null && args.length >= 3) {
            ((java.nio.IntBuffer) args[2]).put(0, 1);
            return null;
        }
        if ("glGetProgramiv".equals(methodName) && args != null && args.length >= 3) {
            int parameter = (Integer) args[1];
            int value = parameter == GL20.GL_LINK_STATUS
                    || parameter == GL20.GL_VALIDATE_STATUS ? 1 : 0;
            ((java.nio.IntBuffer) args[2]).put(0, value);
            return null;
        }
        if (methodName.startsWith("glGen") && args != null && args.length >= 2
                && args[1] instanceof java.nio.IntBuffer) {
            java.nio.IntBuffer handles = (java.nio.IntBuffer) args[1];
            int count = (Integer) args[0];
            for (int i = 0; i < count; i++) handles.put(i, nextHandle[0]++);
            return null;
        }
        if ("glCheckFramebufferStatus".equals(methodName)) {
            return GL20.GL_FRAMEBUFFER_COMPLETE;
        }
        return defaultValue(returnType);
    }
}
