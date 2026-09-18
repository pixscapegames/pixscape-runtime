package games.pixscape.runtime.hud;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Disposable;

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
    private int generatedTextures;
    private int deletedBuffers;
    private boolean failViewport;
    private final List<String> disposalOrder = new ArrayList<>();

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
        Assert.assertEquals(ActiveHudScreen.ResourceOwnership.OWNED, a.resourceOwnership());
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
    public void twoSkinsWithSameDefaultNamesStayIndependentWhenBorrowedAndReplaced() throws Exception {
        FileHandle root = twoSkinProject();
        HudResources environment = shared(root);
        HudSelectedResources aView = environment.select("ui/a.json");
        HudSelectedResources bView = environment.select("ui/b.json");
        int allocations = generatedTextures;
        Object bundle = environment.textureArrayBundle();
        // Borrowed loading must not read Skin JSON/atlas descriptors again.
        root.child("ui/game.atlas").delete();
        root.child("ui/a.json").delete();
        root.child("ui/b.json").delete();
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen a = runtime.showBorrowing("a", environment);
            Assert.assertEquals(ActiveHudScreen.ResourceOwnership.BORROWED, a.resourceOwnership());
            Assert.assertSame(aView.labelStyle("default"), ((Label) a.materializedHud().actor("label")).getStyle());
            Assert.assertEquals(Color.RED, ((Label) a.materializedHud().actor("label")).getStyle().fontColor);
            Assert.assertSame(aView.drawable("default"), ((Image) a.materializedHud().actor("drawable")).getDrawable());
            Assert.assertSame(aView.region("crosshair"), bView.region("crosshair"));
            Assert.assertNotSame(aView.drawable("default"), bView.drawable("default"));
            Assert.assertNotSame(aView.textButtonStyle("default"), bView.textButtonStyle("default"));
            ActiveHudScreen b = runtime.showBorrowing("b", environment);
            Assert.assertTrue(a.session().isDisposed());
            Assert.assertFalse(environment.isDisposed());
            Assert.assertSame(bView.labelStyle("default"), ((Label) b.materializedHud().actor("label")).getStyle());
            Assert.assertEquals(Color.BLUE, ((Label) b.materializedHud().actor("label")).getStyle().fontColor);
            Assert.assertSame(bView.drawable("default"), ((Image) b.materializedHud().actor("drawable")).getDrawable());
            Assert.assertSame(bundle, b.session().hudBatch().getTextureArrayBundle());
            Assert.assertEquals(allocations, generatedTextures);
            runtime.draw();
            runtime.hide();
            b.dispose();
            Assert.assertTrue(b.session().isDisposed());
            Assert.assertFalse(environment.isDisposed());
            Assert.assertNotNull(aView.labelStyle("default"));
            Assert.assertNotNull(bView.labelStyle("default"));
        } finally { runtime.dispose(); environment.dispose(); }
    }

    @Test public void borrowedDifferentEnvironmentsRemainOpenDuringReplacementAndShutdown() throws Exception {
        FileHandle root = twoSkinProject();
        HudResources first = shared(root), second = shared(root);
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen a = runtime.showBorrowing("a", first);
            ActiveHudScreen b = runtime.showBorrowing("b", second);
            Assert.assertTrue(a.session().isDisposed());
            Assert.assertSame(b, runtime.activeScreen());
            runtime.dispose(); runtime.dispose();
            Assert.assertTrue(b.session().isDisposed());
            Assert.assertFalse(first.isDisposed()); Assert.assertFalse(second.isDisposed());
        } finally { runtime.dispose(); first.dispose(); second.dispose(); }
    }

    @Test public void ownedToBorrowedDisposesOnlyOldOwner() throws Exception {
        FileHandle root = twoSkinProject();
        HudResources environment = shared(root);
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen owned = runtime.show("a");
            ActiveHudScreen borrowed = runtime.showBorrowing("b", environment);
            Assert.assertTrue(owned.resources().isDisposed());
            Assert.assertTrue(owned.session().isDisposed());
            Assert.assertSame(borrowed, runtime.activeScreen());
            Assert.assertFalse(environment.isDisposed());
            runtime.dispose();
            Assert.assertFalse(environment.isDisposed());
        } finally { runtime.dispose(); environment.dispose(); }
    }

    @Test public void borrowingFromRetiringOwnedScreenIsRejectedBeforeReplacingIt() throws Exception {
        FileHandle root = twoSkinProject();
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen owned = runtime.show("a");
            int allocations = generatedTextures;
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> runtime.showBorrowing("b", owned.resources()));
            Assert.assertSame(owned, runtime.activeScreen());
            Assert.assertFalse(owned.resources().isDisposed());
            Assert.assertFalse(owned.session().isDisposed());
            Assert.assertEquals(allocations, generatedTextures);
        } finally { runtime.dispose(); }
    }

    @Test public void borrowedToOwnedLeavesOuterOwnerOpenAndShutdownClosesPrivateOwner() throws Exception {
        FileHandle root = twoSkinProject();
        HudResources environment = shared(root);
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen borrowed = runtime.showBorrowing("a", environment);
            ActiveHudScreen owned = runtime.show("b");
            Assert.assertTrue(borrowed.session().isDisposed());
            Assert.assertFalse(environment.isDisposed());
            Assert.assertFalse(owned.resources().isDisposed());
            runtime.dispose(); runtime.dispose();
            Assert.assertTrue(owned.resources().isDisposed());
            Assert.assertFalse(environment.isDisposed());
        } finally { runtime.dispose(); environment.dispose(); }
    }

    @Test public void sessionsPrecedeSkinCatalogDisposalAndOwnersDisposeExactlyOnce() throws Exception {
        FileHandle root = twoSkinProject();
        ActiveHudScreen active = new HudScreenLoader(root, shader).load("a");
        int[] skinDisposals = {0};
        active.resources().select("ui/a.json").skin().add("probe", (Disposable) () -> {
            Assert.assertTrue(active.session().isDisposed()); skinDisposals[0]++;
        }, Disposable.class);
        disposalOrder.clear();
        active.dispose();
        int deleted = deletedTextures;
        active.dispose();
        Assert.assertEquals(1, skinDisposals[0]);
        Assert.assertEquals(deleted, deletedTextures);
        Assert.assertTrue(disposalOrder.indexOf("buffer") >= 0);
        Assert.assertTrue(disposalOrder.lastIndexOf("buffer") < disposalOrder.indexOf("texture"));

        HudResources environment = shared(root);
        ActiveHudScreen a = new HudScreenLoader(root, shader).loadBorrowing("a", environment);
        ActiveHudScreen b = new HudScreenLoader(root, shader).loadBorrowing("b", environment);
        int[] counts = {0, 0};
        environment.select("ui/a.json").skin().add("probe", (Disposable) () -> {
            Assert.assertTrue(a.session().isDisposed()); Assert.assertTrue(b.session().isDisposed()); counts[0]++;
        }, Disposable.class);
        environment.select("ui/b.json").skin().add("probe", (Disposable) () -> counts[1]++, Disposable.class);
        a.dispose(); b.dispose();
        Assert.assertArrayEquals(new int[]{0, 0}, counts);
        environment.dispose(); environment.dispose();
        Assert.assertArrayEquals(new int[]{1, 1}, counts);
    }

    @Test public void skinBStyleCannotSatisfySkinAAndFailedReplacementPreservesActive() throws Exception {
        FileHandle root = twoSkinProject();
        root.child("ui/a.json").writeString("{}", false);
        HudResources environment = shared(root);
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen b = runtime.showBorrowing("b", environment);
            Assert.assertNotNull(environment.select("ui/b.json").labelStyle("default"));
            Assert.assertNull(environment.select("ui/a.json").labelStyle("default"));
            HudDocumentValidationException failure = Assert.assertThrows(HudDocumentValidationException.class,
                    () -> runtime.showBorrowing("a", environment));
            Assert.assertEquals(HudDocumentValidationException.Phase.RESOURCE_AWARE, failure.phase());
            Assert.assertSame(b, runtime.activeScreen());
            Assert.assertFalse(environment.isDisposed());
            Assert.assertFalse(b.session().isDisposed());
        } finally { runtime.dispose(); environment.dispose(); }
    }

    @Test public void borrowedAssetDocumentSelectionAndRegionFailuresNeverDisposeEnvironment() throws Exception {
        FileHandle root = twoSkinProject();
        HudResources environment = shared(root);
        HudScreenLoader loader = new HudScreenLoader(root, shader);
        try {
            Assert.assertThrows(RuntimeException.class, () -> loader.loadBorrowing("absent", environment));
            root.child("hud/a.json").delete();
            Assert.assertThrows(HudDocumentLoadException.class, () -> loader.loadBorrowing("a", environment));
            root.child("hud/a.json").writeString("{", false);
            Assert.assertThrows(HudDocumentLoadException.class, () -> loader.loadBorrowing("a", environment));
            root.child("hud/a.json").writeString(missingRegionDocument(), false);
            Assert.assertThrows(HudDocumentValidationException.class, () -> loader.loadBorrowing("a", environment));
            root.child("hud/a.json").writeString(twoSkinDocument(), false);
            writeSelectedScreen(root, "a", "ui/unknown.json");
            IllegalArgumentException unknown = Assert.assertThrows(IllegalArgumentException.class,
                    () -> loader.loadBorrowing("a", environment));
            Assert.assertTrue(unknown.getMessage().contains("ui/unknown.json"));
            writeSelectedScreen(root, "a", "");
            Assert.assertThrows(IllegalArgumentException.class, () -> loader.loadBorrowing("a", environment));
            Assert.assertFalse(environment.isDisposed());
            Assert.assertNotNull(environment.select("ui/b.json").labelStyle("default"));
        } finally { environment.dispose(); }
    }

    @Test public void materializationFailuresRespectOwnedAndBorrowedLifetime() throws Exception {
        FileHandle root = twoSkinProject();
        root.child("ui/a.json").writeString(
                "{\"com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle\":{\"broken\":{}}}", false);
        root.child("hud/a.json").writeString(
                "{\"schemaVersion\":1,\"root\":{\"id\":\"label\",\"kind\":\"LABEL\","
                        + "\"label\":{\"text\":\"test\",\"styleName\":\"broken\"},\"children\":[]}}", false);
        HudResources environment = shared(root);
        try {
            int before = deletedTextures;
            Assert.assertThrows(RuntimeException.class, () -> new HudScreenLoader(root, shader).loadBorrowing("a", environment));
            Assert.assertEquals(before, deletedTextures);
            Assert.assertFalse(environment.isDisposed());
            Assert.assertThrows(RuntimeException.class, () -> new HudScreenLoader(root, shader).load("a"));
            Assert.assertEquals("private two-page atlas + bundle cleaned", 3, deletedTextures - before);
        } finally { environment.dispose(); }
    }

    @Test public void sessionFailuresRespectOwnershipAndCleanPartialSessionState() throws Exception {
        FileHandle root = twoSkinProject();
        HudResources environment = shared(root);
        try {
            Assert.assertThrows(IllegalArgumentException.class, () -> new HudScreenLoader(root, null).loadBorrowing("a", environment));
            Assert.assertFalse(environment.isDisposed());
            int texturesBefore = deletedTextures;
            Assert.assertThrows(IllegalArgumentException.class, () -> new HudScreenLoader(root, null).load("a"));
            Assert.assertEquals(3, deletedTextures - texturesBefore);
            texturesBefore = deletedTextures;
            int buffersBefore = deletedBuffers;
            failViewport = true;
            Assert.assertThrows(IllegalStateException.class, () -> new HudScreenLoader(root, shader).loadBorrowing("a", environment));
            Assert.assertFalse(environment.isDisposed());
            Assert.assertEquals(texturesBefore, deletedTextures);
            Assert.assertTrue(deletedBuffers > buffersBefore);
            buffersBefore = deletedBuffers;
            failViewport = true;
            Assert.assertThrows(IllegalStateException.class, () -> new HudScreenLoader(root, shader).load("a"));
            Assert.assertTrue(deletedBuffers > buffersBefore);
            Assert.assertEquals(3, deletedTextures - texturesBefore);
        } finally { environment.dispose(); }
    }

    @Test public void skinlessBorrowedLayoutLeavesEnvironmentOpen() throws Exception {
        FileHandle root = twoSkinProject();
        root.child("hud/layout.hudscreen").writeString("{\"schemaVersion\":1,\"skinId\":\"ui/absent.json\",\"documentId\":\"hud/layout.json\"}", false);
        root.child("hud/layout.json").writeString("{\"schemaVersion\":1,\"root\":{\"id\":\"root\",\"kind\":\"GROUP\",\"children\":[]}}", false);
        HudResources environment = HudResources.prepareEnvironment(root, null, null, java.util.Collections.emptyList());
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen layout = runtime.showBorrowing("layout", environment);
            Assert.assertNotNull(layout.session());
            Assert.assertNull(layout.session().hudBatch().getTextureArrayBundle());
            runtime.dispose();
            Assert.assertTrue(layout.session().isDisposed());
            Assert.assertFalse(environment.isDisposed());
        } finally { runtime.dispose(); environment.dispose(); }
    }

    private FileHandle twoSkinProject() throws Exception {
        FileHandle root = project();
        String original = root.child("ui/game.json").readString();
        for (String id : new String[]{"a", "b"}) {
            String color = "a".equals(id) ? "ff0000ff" : "0000ffff";
            String skin = original.replace("\"hud-title\":{\"font\":\"default-font\"}",
                    "\"default\":{\"font\":\"default-font\",\"fontColor\":{\"hex\":\"" + color + "\"}}")
                    .replace("\"hud-primary\"", "\"default\"");
            skin = skin.substring(0, skin.length() - 1)
                    + ",\"com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable\":{"
                    + "\"default\":{\"region\":\"inventory-panel\"}}}";
            root.child("ui/" + id + ".json").writeString(skin, false);
            writeSelectedScreen(root, id, "ui/" + id + ".json");
            root.child("hud/" + id + ".json").writeString(twoSkinDocument(), false);
        }
        return root;
    }

    @Test
    public void sceneExportMultiSkinUsesOnlyScenePagesAndRelativeFontDescriptors() throws Exception {
        sceneExportMultiSkin(false);
    }

    @Test
    public void sceneExportRelativeFontsLoadThroughCanonicalExactKeys() throws Exception {
        sceneExportMultiSkin(true);
    }

    private void sceneExportMultiSkin(boolean exactKeys) throws Exception {
        FileHandle root = twoSkinProject();
        String atlasId = "atlases/hud/scene/hud.atlas";
        FileHandle scene = root.child("atlases/hud/scene");
        scene.mkdirs();
        root.child("ui/game.atlas").copyTo(scene.child("hud.atlas"));
        root.child("ui/page-a.png").copyTo(scene.child("page-a.png"));
        root.child("ui/page-b.png").copyTo(scene.child("page-b.png"));
        root.child("fonts").mkdirs();
        root.child("ui/default-font.fnt").copyTo(root.child("fonts/default-font.fnt"));
        for (String id : new String[]{"a", "b"}) {
            FileHandle skin = root.child("ui/" + id + ".json");
            skin.writeString(skin.readString().replace("default-font.fnt", "../fonts/default-font.fnt"), false);
        }
        root.child("ui/game.atlas").delete();
        root.child("ui/page-a.png").delete(); root.child("ui/page-b.png").delete();
        root.child("ui/default-font.fnt").delete(); root.child("ui/game.json").delete();
        List<String> reads = new ArrayList<>();
        if (exactKeys) {
            Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(), new Class<?>[]{Files.class},
                    (proxy, method, args) -> "internal".equals(method.getName())
                            ? new ExactKeyFile((String) args[0], reads) : defaultValue(method.getReturnType()));
        }
        FileHandle loadRoot = exactKeys ? new ExactKeyFile(root.path(), reads) : root;
        com.badlogic.gdx.assets.loaders.FileHandleResolver resolver =
                path -> exactKeys ? new ExactKeyFile(path, reads) : new FileHandle(path);
        com.badlogic.gdx.assets.AssetManager manager = new com.badlogic.gdx.assets.AssetManager(resolver);
        manager.setLoader(com.badlogic.gdx.graphics.g2d.TextureAtlas.class,
                new com.badlogic.gdx.assets.loaders.SynchronousAssetLoader<com.badlogic.gdx.graphics.g2d.TextureAtlas,
                        com.badlogic.gdx.assets.loaders.TextureAtlasLoader.TextureAtlasParameter>(resolver) {
                    public com.badlogic.gdx.graphics.g2d.TextureAtlas load(com.badlogic.gdx.assets.AssetManager owner,
                            String path, FileHandle file, com.badlogic.gdx.assets.loaders.TextureAtlasLoader.TextureAtlasParameter parameters) {
                        return new com.badlogic.gdx.graphics.g2d.TextureAtlas();
                    }
                    public com.badlogic.gdx.utils.Array<com.badlogic.gdx.assets.AssetDescriptor> getDependencies(String path,
                            FileHandle file, com.badlogic.gdx.assets.loaders.TextureAtlasLoader.TextureAtlasParameter parameters) { return null; }
                });
        games.pixscape.runtime.loading.FileAvailabilityService availability =
                new games.pixscape.runtime.loading.FileAvailabilityService(manager, false);
        root.child("scenes").mkdirs(); root.child("scenes/scene.json").writeString("{}", false);
        root.child("atlases/scene.atlas").writeString("", false);
        games.pixscape.runtime.configuration.RuntimeConfig config = new games.pixscape.runtime.configuration.RuntimeConfig();
        games.pixscape.runtime.loading.SceneMetaRuntime meta = new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.name = "scene"; meta.file = "scene.json"; meta.defaultHudScreenId = "a";
        config.scenes.put("scene", meta);
        java.lang.reflect.Constructor<games.pixscape.runtime.loading.SceneAvailabilityPlan> constructor =
                games.pixscape.runtime.loading.SceneAvailabilityPlan.class.getDeclaredConstructor(
                        games.pixscape.runtime.loading.FileAvailabilityService.class,
                        games.pixscape.runtime.configuration.RuntimeConfig.class, FileHandle.class, String.class, List.class);
        constructor.setAccessible(true);
        games.pixscape.runtime.loading.SceneAvailabilityPlan plan = constructor.newInstance(
                availability, config, loadRoot, "scene", Arrays.asList("b", "a", "hud/a"));
        HudResources environment = null;
        HudScreenRuntime runtime = new HudScreenRuntime(loadRoot, shader);
        try {
            plan.finishOnNative(); plan.prepareHudResources();
            environment = plan.hudResources();
            Assert.assertEquals(Arrays.asList("ui/a.json", "ui/b.json"), new ArrayList<String>(environment.skinIds()));
            Assert.assertEquals(atlasId, environment.atlasId());
            int allocations = generatedTextures;
            ActiveHudScreen a = runtime.showBorrowing("a", environment);
            Assert.assertEquals(Color.RED, ((Label) a.materializedHud().actor("label")).getStyle().fontColor);
            ActiveHudScreen b = runtime.showBorrowing("b", environment);
            Assert.assertEquals(Color.BLUE, ((Label) b.materializedHud().actor("label")).getStyle().fontColor);
            Assert.assertNotSame(environment.select("ui/a.json").labelStyle("default"),
                    environment.select("ui/b.json").labelStyle("default"));
            Assert.assertSame(environment.select("ui/a.json").region("crosshair"),
                    environment.select("ui/b.json").region("crosshair"));
            Assert.assertEquals(allocations, generatedTextures);
            Assert.assertEquals(2, environment.select("ui/a.json").skin()
                    .getFont("default-font").getRegions().size);
            Assert.assertFalse(root.child("fonts/page-a.png").exists());
            Assert.assertFalse(root.child("fonts/page-b.png").exists());
            Assert.assertFalse(root.child("ui/page-a.png").exists());
            if (exactKeys) {
                Assert.assertFalse(loadRoot.child("ui/../fonts/default-font.fnt").exists());
                Assert.assertTrue(reads.contains(root.child("fonts/default-font.fnt").path()));
                for (String read : reads) Assert.assertFalse(read.contains("/../"));
            }
            runtime.hide(); Assert.assertFalse(environment.isDisposed());
        } finally {
            runtime.dispose(); plan.release(); plan.release();
            if (environment != null) Assert.assertTrue(environment.isDisposed());
            availability.dispose(); manager.dispose();
        }
    }

    /** Preserves raw child keys like GWT and rejects dot segments instead of trusting the OS. */
    private static final class ExactKeyFile extends FileHandle {
        private final List<String> reads;
        ExactKeyFile(String path, List<String> reads) { super(path); this.reads = reads; }
        @Override public FileHandle child(String name) { return new ExactKeyFile(path() + "/" + name, reads); }
        @Override public FileHandle parent() {
            return new ExactKeyFile(path().substring(0, path().lastIndexOf('/')), reads);
        }
        @Override public boolean exists() { return canonical() && super.exists(); }
        @Override public java.io.InputStream read() {
            Assert.assertTrue("Noncanonical exact-key read: " + path(), canonical());
            reads.add(path());
            return super.read();
        }
        private boolean canonical() {
            for (String segment : path().replace('\\', '/').split("/")) {
                if ("..".equals(segment) || ".".equals(segment)) return false;
            }
            return true;
        }
    }

    @Test public void sceneBorrowedProfileIsAuthoritativeButExplicitShowRetainsOwnedValidation() throws Exception {
        FileHandle root = project();
        writeScreen(root, "game", "hud/game.json");
        copyFixture(root.child("hud/game.json"), "materializer-smoke.json");
        FileHandle assetFile = root.child("hud/game.hudscreen");
        String authored = assetFile.readString().replace(HudTextureProfile.DEFAULT_ID, "legacy-other-profile");
        assetFile.writeString(authored, false);
        HudResources environment = HudResources.prepareEnvironment(root, "ui/game.atlas",
                HudTextureProfile.DEFAULT_ID, Arrays.asList("ui/game.json"));
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen borrowed = runtime.showBorrowing("game", environment);
            Assert.assertSame(environment, borrowed.resources());
            Assert.assertEquals(ActiveHudScreen.ResourceOwnership.BORROWED, borrowed.resourceOwnership());
            Assert.assertEquals("legacy-other-profile", borrowed.asset().textureProfileId);
            IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
                    () -> runtime.show("game"));
            Assert.assertTrue(failure.getMessage().contains("legacy-other-profile"));
            Assert.assertSame(borrowed, runtime.activeScreen());
            Assert.assertEquals(authored, assetFile.readString());
            Assert.assertFalse(environment.isDisposed());
        } finally { runtime.dispose(); environment.dispose(); }
    }

    private static HudResources shared(FileHandle root) {
        return HudResources.prepareEnvironment(root, "ui/game.atlas", null, Arrays.asList("ui/a.json", "ui/b.json"));
    }

    private static void writeSelectedScreen(FileHandle root, String id, String skinId) {
        root.child("hud/" + id + ".hudscreen").writeString("{\"schemaVersion\":1,\"documentId\":\"hud/" + id
                + ".json\",\"skinId\":\"" + skinId + "\",\"atlasId\":\"ui/game.atlas\"}", false);
    }

    private static String twoSkinDocument() {
        return "{\"schemaVersion\":1,\"root\":{\"id\":\"root\",\"kind\":\"GROUP\",\"children\":["
                + "{\"placementKind\":\"DIRECT\",\"node\":{\"id\":\"label\",\"kind\":\"LABEL\",\"label\":{\"text\":\"A\",\"styleName\":\"default\"},\"children\":[]}},"
                + "{\"placementKind\":\"DIRECT\",\"node\":{\"id\":\"drawable\",\"kind\":\"IMAGE\",\"image\":{\"source\":\"DRAWABLE\",\"resourceName\":\"default\"},\"children\":[]}},"
                + "{\"placementKind\":\"DIRECT\",\"node\":{\"id\":\"region\",\"kind\":\"IMAGE\",\"image\":{\"source\":\"REGION\",\"resourceName\":\"crosshair\"},\"children\":[]}}]}}";
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
    public void activeHudInputConsumesButtonGestureBeforeWorldInput() throws Exception {
        FileHandle root = project();
        writeScreen(root, "game", "hud/game.json");
        copyFixture(root.child("hud/game.json"), "materializer-smoke.json");
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen active = runtime.show("game");
            runtime.resize(320, 180);
            TextButton button = (TextButton) active.materializedHud().actor("smoke-button");
            int[] worldTouches = {0};
            InputMultiplexer input = new InputMultiplexer(runtime.inputProcessor(), new InputAdapter() {
                @Override public boolean touchDown(int screenX, int screenY, int pointer, int mouseButton) {
                    worldTouches[0]++;
                    return true;
                }
            });

            Assert.assertTrue(input.touchDown(272, 156, 0, Input.Buttons.LEFT));
            Assert.assertTrue(button.getClickListener().isPressed());
            Assert.assertTrue(runtime.isPointerCaptured());
            Assert.assertEquals(0, worldTouches[0]);
            input.touchDragged(10, 10, 0);
            input.touchUp(10, 10, 0, Input.Buttons.LEFT);
            Assert.assertFalse(button.getClickListener().isPressed());
            Assert.assertFalse(runtime.isPointerCaptured());

            Assert.assertTrue(input.touchDown(272, 156, 0, Input.Buttons.LEFT));
            Assert.assertTrue(runtime.isPointerCaptured());
            input.touchCancelled(272, 156, 0, Input.Buttons.LEFT);
            Assert.assertFalse(button.getClickListener().isPressed());
            Assert.assertFalse(runtime.isPointerCaptured());

            Assert.assertTrue(input.touchDown(272, 156, 0, Input.Buttons.LEFT));
            runtime.hide();
            Assert.assertFalse(runtime.isPointerCaptured());
            Assert.assertFalse(runtime.inputProcessor().touchDown(
                    272, 156, 0, Input.Buttons.LEFT));
        } finally {
            runtime.dispose();
        }
    }

    @Test
    public void pointerCaptureReflectsNativeButtonAndMultiPointerTouchFocus() throws Exception {
        FileHandle root = project();
        writeScreen(root, "game", "hud/game.json");
        copyFixture(root.child("hud/game.json"), "materializer-smoke.json");
        HudScreenRuntime runtime = new HudScreenRuntime(root, shader);
        try {
            ActiveHudScreen active = runtime.show("game");
            runtime.resize(320, 180);
            TextButton first = (TextButton) active.materializedHud().actor("smoke-button");
            TextButton second = new TextButton("Second", first.getStyle());
            second.setPosition(16f, 16f);
            ((Group) active.materializedHud().root()).addActor(second);
            Stage stage = active.session().stage();
            int[] firstPoint = screenCenter(stage, first);
            int[] secondPoint = screenCenter(stage, second);

            Assert.assertTrue(runtime.inputProcessor().touchDown(
                    firstPoint[0], firstPoint[1], 0, Input.Buttons.LEFT));
            Assert.assertTrue(runtime.isPointerCaptured());
            Assert.assertFalse(runtime.inputProcessor().touchUp(
                    firstPoint[0], firstPoint[1], 0, Input.Buttons.RIGHT));
            Assert.assertTrue(runtime.isPointerCaptured());

            Assert.assertTrue(runtime.inputProcessor().touchDown(
                    secondPoint[0], secondPoint[1], 1, Input.Buttons.LEFT));
            Assert.assertTrue(runtime.inputProcessor().touchUp(
                    secondPoint[0], secondPoint[1], 1, Input.Buttons.LEFT));
            Assert.assertTrue(runtime.isPointerCaptured());
            Assert.assertTrue(runtime.inputProcessor().touchUp(
                    0, 0, 0, Input.Buttons.LEFT));
            Assert.assertFalse(runtime.isPointerCaptured());

            Assert.assertTrue(runtime.inputProcessor().touchDown(
                    firstPoint[0], firstPoint[1], 0, Input.Buttons.LEFT));
            runtime.show("game");
            Assert.assertFalse(runtime.isPointerCaptured());
        } finally {
            runtime.dispose();
        }
    }

    @Test
    public void screenWithoutDocumentIsRejectedBeforeSessionCreation() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("empty-project"));
        root.child("hud").mkdirs();
        root.child("hud/empty.hudscreen").writeString(
                "{\"schemaVersion\":1}", false, "UTF-8");
        HudScreenRuntime runtime = new HudScreenRuntime(root, null);
        IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
                () -> runtime.show("empty"));
        Assert.assertTrue(failure.getMessage().contains("documentId is required"));
        runtime.dispose();
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

    private static int[] screenCenter(Stage stage, Actor actor) {
        Vector2 point = actor.localToStageCoordinates(
                new Vector2(actor.getWidth() * 0.5f, actor.getHeight() * 0.5f));
        stage.stageToScreenCoordinates(point);
        return new int[] {Math.round(point.x), Math.round(point.y)};
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
        if ("glViewport".equals(name) && failViewport) {
            failViewport = false;
            throw new IllegalStateException("Injected session viewport failure");
        }
        if ("glGenTexture".equals(name)) generatedTextures++;
        if ("glDeleteBuffer".equals(name) || "glDeleteBuffers".equals(name)) {
            deletedBuffers++; disposalOrder.add("buffer");
        }
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
        if ("glDeleteTextures".equals(name) || "glDeleteTexture".equals(name)) {
            deletedTextures++; disposalOrder.add("texture");
        }
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
