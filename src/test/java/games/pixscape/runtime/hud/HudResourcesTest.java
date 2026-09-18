package games.pixscape.runtime.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

public class HudResourcesTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;
    private Graphics previousGraphics;
    private Files previousFiles;
    private int deletedTextures;

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installGl() throws Exception {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;
        previousGraphics = Gdx.graphics;
        previousFiles = Gdx.files;
        int[] nextTexture = {1};
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(), new Class<?>[]{GL30.class},
                (proxy, method, args) -> {
                    if ("glGenTexture".equals(method.getName())) return nextTexture[0]++;
                    if ("glDeleteTexture".equals(method.getName())) deletedTextures++;
                    if ("glGetIntegerv".equals(method.getName())) {
                        int parameter = (Integer) args[0];
                        IntBuffer values = (IntBuffer) args[1];
                        if (parameter == GL20.GL_MAX_TEXTURE_IMAGE_UNITS
                                || parameter == GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) {
                            values.put(0, 16);
                        } else if (parameter == GL20.GL_MAX_TEXTURE_SIZE) {
                            values.put(0, 4096);
                        } else if (parameter == GL30.GL_MAX_ARRAY_TEXTURE_LAYERS) {
                            values.put(0, 16);
                        }
                    }
                    return defaultValue(method.getReturnType());
                });
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        FileHandle builtInDescriptor = new FileHandle(temporaryFolder.newFile("lsans-15.fnt"));
        try (java.io.InputStream source = HudResourcesTest.class.getClassLoader()
                .getResourceAsStream(HudBuiltInLabelStyle.FONT_DESCRIPTOR)) {
            java.nio.file.Files.copy(source, builtInDescriptor.file().toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Gdx.files = (Files) Proxy.newProxyInstance(
                Files.class.getClassLoader(), new Class<?>[]{Files.class},
                (proxy, method, args) -> "classpath".equals(method.getName())
                        ? builtInDescriptor
                        : defaultValue(method.getReturnType()));
        TextureRegistry.clear();
        InternalTextures.dispose();
    }

    @After
    public void restoreGl() {
        InternalTextures.dispose();
        TextureRegistry.clear();
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
        Gdx.graphics = previousGraphics;
        Gdx.files = previousFiles;
    }

    @Test
    public void missingReferencesAndUnknownProfileFailClearly() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("missing"));
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/missing.json";

        assertPreparationFailure(asset, root, "skinId");
        asset.skinId = "ui/game.json";
        assertPreparationFailure(asset, root, "atlasId");
        asset.atlasId = "ui/game.atlas";
        asset.textureProfileId = "hud-unknown";
        assertPreparationFailure(asset, root, "Unknown HUD texture profile");
    }

    @Test
    public void sharedCatalogCanonicalSelectionAndSkinlessViewAreFrozen() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("catalog"));
        writeHudFiles(root);
        root.child("ui/other.json").writeString(root.child("ui/game.json").readString(), false);
        HudResources resources = HudResources.prepareEnvironment(root, "ui/game.atlas", null,
                java.util.Arrays.asList("  ui\\game.json  ", "ui/other.json"));
        HudSelectedResources a = resources.select(" ui\\game.json ");
        HudSelectedResources b = resources.select("ui/other.json");
        try {
            Assert.assertEquals(java.util.Arrays.asList("ui/game.json", "ui/other.json"),
                    new java.util.ArrayList<>(resources.skinIds()));
            Assert.assertEquals("ui/game.json", a.skinId());
            Assert.assertNotSame(a.labelStyle("hud-title"), b.labelStyle("hud-title"));
            Assert.assertSame(a.region("crosshair"), b.region("crosshair"));
            for (int i = 0; i < 10; i++) {
                Assert.assertNull(a.region("missing"));
                Assert.assertNull(a.labelStyle("missing"));
                Assert.assertFalse(a.hasDrawable("missing"));
            }
            Assert.assertThrows(UnsupportedOperationException.class, () -> resources.skinIds().clear());
            Assert.assertThrows(IllegalArgumentException.class, () -> resources.select("ui/missing.json"));
            Assert.assertEquals("ui/game.json", a.skinId());
            Assert.assertNotNull(a.labelStyle("hud-title"));
            Assert.assertTrue(a.satisfies(fullRequirements()));
            HudSelectedResources skinless = resources.select("  ");
            Assert.assertNull(skinless.skinId());
            Assert.assertNull(skinless.labelStyle("hud-title"));
            Assert.assertNotNull(skinless.builtInLabelStyle());
            Assert.assertTrue(skinless.hasBuiltInLabelStyle());
            Assert.assertTrue(skinless.satisfies(requirementsFor(
                    "{\"id\":\"label\",\"kind\":\"LABEL\","
                            + "\"label\":{\"text\":\"Label\"},\"children\":[]}")));
            Assert.assertFalse(skinless.satisfies(fullRequirements()));
            Assert.assertTrue(skinless.hasRegion("crosshair"));
            Assert.assertFalse(com.badlogic.gdx.utils.Disposable.class.isInstance(a));
            Assert.assertFalse(AutoCloseable.class.isInstance(a));
        } finally { resources.dispose(); }
        Assert.assertThrows(IllegalStateException.class, () -> a.region("crosshair"));
        Assert.assertThrows(IllegalStateException.class, () -> b.labelStyle("hud-title"));
        Assert.assertThrows(IllegalStateException.class, () -> resources.select(null));
    }

    @Test
    public void duplicateCanonicalSkinIdsFailBeforeAllocationOrFileResolution() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("duplicates"));
        int deletionsBefore = deletedTextures;
        IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
                () -> HudResources.prepareEnvironment(root, "ui/absent.atlas", null,
                        java.util.Arrays.asList("ui/game.json", " ui\\game.json ")));
        Assert.assertEquals("Duplicate canonical HUD Skin ID: ui/game.json.", failure.getMessage());
        Assert.assertEquals(deletionsBefore, deletedTextures);
        for (String id : new String[]{null, "", "  ", "../escape.json", "ui//bad.json"}) {
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> HudResources.prepareEnvironment(root, null, null,
                            java.util.Collections.singletonList(id)));
        }
    }

    @Test
    public void skinlessEnvironmentSupportsLayoutWithoutAllocatingGraphics() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("skinless-catalog"));
        HudResources resources = HudResources.prepareEnvironment(root, null, null,
                java.util.Collections.emptyList());
        try {
            HudSelectedResources selected = resources.select(null);
            Assert.assertTrue(selected.satisfies(requirementsFor(
                    "{\"id\":\"root\",\"kind\":\"GROUP\",\"children\":[]}")));
            Assert.assertNull(selected.region("crosshair"));
            Assert.assertNull(selected.drawable("default"));
            Assert.assertNull(selected.textButtonStyle("default"));
            Assert.assertTrue(resources.skinIds().isEmpty());
            // Even a shared environment with zero/one Skin must not infer a selection.
            Assert.assertNull(selected.region("crosshair"));
        } finally { resources.dispose(); }
    }

    @Test
    public void failedSecondSkinPreparationCleansFirstSkinAndSharedAtlas() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("failed-second"));
        writeHudFiles(root);
        root.child("ui/broken.json").writeString("{", false);
        int before = deletedTextures;
        Assert.assertThrows(RuntimeException.class, () -> HudResources.prepareEnvironment(root,
                "ui/game.atlas", null, java.util.Arrays.asList("ui/game.json", "ui/broken.json")));
        Assert.assertEquals("Only the two atlas pages were allocated", 2, deletedTextures - before);
    }

    @Test
    public void resourceFreePreparationNeedsNoFilesOrGlResourcesAndDisposesOnce()
            throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("resource-free"));
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/resource-free.json";
        asset.skinId = "ui/not-loaded.json";
        asset.atlasId = "ui/not-loaded.atlas";
        HudResourceRequirements requirements = requirementsFor(
                "{\"id\":\"root\",\"kind\":\"GROUP\",\"children\":[]}");

        HudResources resources = HudResources.prepareStandalone(asset, root, requirements);
        HudSelectedResources selected = resources.select(null);

        Assert.assertTrue(selected.satisfies(requirements));
        Assert.assertNull(selected.skinId());
        Assert.assertNull(resources.atlasId());
        Assert.assertNull(resources.textureArrayBundle());
        Assert.assertFalse(selected.hasRegion("anything"));
        Assert.assertFalse(selected.hasDrawable("anything"));
        Assert.assertFalse(selected.hasLabelStyle("anything"));
        Assert.assertFalse(selected.hasTextButtonStyle("anything"));
        Assert.assertNull(selected.skin());
        Assert.assertThrows(IllegalStateException.class, resources::atlas);

        resources.dispose();
        resources.dispose();
        Assert.assertTrue(resources.isDisposed());
    }

    @Test
    public void atlasOnlyRegionPreparationDoesNotRequireSkin() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("atlas-only"));
        writeHudFiles(root);
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/atlas-only.json";
        asset.atlasId = "ui/game.atlas";
        HudResourceRequirements requirements = requirementsFor(
                "{\"id\":\"image\",\"kind\":\"IMAGE\",\"image\":{"
                        + "\"source\":\"REGION\",\"resourceName\":\"inventory-art\"},"
                        + "\"children\":[]}");

        HudResources resources = HudResources.prepareStandalone(asset, root, requirements);
        try {
            HudSelectedResources selected = resources.select(null);
            Assert.assertTrue(selected.satisfies(requirements));
            Assert.assertNull(selected.skinId());
            Assert.assertEquals("ui/game.atlas", resources.atlasId());
            Assert.assertTrue(selected.hasRegion("inventory-art"));
            Assert.assertFalse(selected.hasDrawable("inventory-panel"));
            Assert.assertNotNull(resources.textureArrayBundle());
        } finally {
            resources.dispose();
        }
    }

    @Test
    public void builtInTextButtonUsesPackedFontAndWhiteRegionWithoutSkin() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("built-in-button"));
        writeHudFiles(root);
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/built-in-button.json";
        asset.atlasId = "ui/game.atlas";
        HudResourceRequirements requirements = requirementsFor(
                "{\"id\":\"button\",\"kind\":\"TEXT_BUTTON\",\"textButton\":{"
                        + "\"text\":\"Button\"},\"children\":[]}");

        HudResources resources = HudResources.prepareStandalone(asset, root, requirements);
        try {
            HudSelectedResources selected = resources.select(null);
            Assert.assertTrue(selected.satisfies(requirements));
            Assert.assertNull(selected.skinId());
            Assert.assertNotNull(selected.builtInTextButtonStyle());
            Assert.assertNotNull(selected.builtInTextButtonStyle().up);
            Assert.assertNotNull(selected.builtInTextButtonStyle().over);
            Assert.assertNotNull(selected.builtInTextButtonStyle().down);
            Assert.assertNotNull(selected.builtInTextButtonStyle().disabled);
        } finally {
            resources.dispose();
        }
    }

    @Test
    public void actualDocumentRequirementsControlMissingReferenceDiagnostics() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("requirements"));
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/complete.json";
        HudResourceRequirements region = requirementsFor(
                "{\"id\":\"image\",\"kind\":\"IMAGE\",\"image\":{"
                        + "\"source\":\"REGION\",\"resourceName\":\"art\"},"
                        + "\"children\":[]}");
        HudResourceRequirements label = requirementsFor(
                "{\"id\":\"label\",\"kind\":\"LABEL\",\"label\":{"
                        + "\"text\":\"Title\",\"styleName\":\"title\"},"
                        + "\"children\":[]}");

        RuntimeException missingAtlas = Assert.assertThrows(RuntimeException.class,
                () -> HudResources.prepareStandalone(asset, root, region));
        Assert.assertTrue(missingAtlas.getMessage(), missingAtlas.getMessage().contains("atlasId"));

        RuntimeException missingSkin = Assert.assertThrows(RuntimeException.class,
                () -> HudResources.prepareStandalone(asset, root, label));
        Assert.assertTrue(missingSkin.getMessage(), missingSkin.getMessage().contains("skinId"));
    }

    @Test
    public void atlasMetadataRejectsWrongDimensionsAndSamplerPolicy() {
        HudTextureProfile profile = HudTextureProfile.forId(null);
        TextureAtlas.TextureAtlasData.Page page = validPage("page.png");
        Array<TextureAtlas.TextureAtlasData.Page> pages = Array.with(page);

        page.width = 1024;
        assertMetadataFailure(pages, profile, "expected 2048x2048, got 1024x2048");
        page.width = 2048;
        page.minFilter = Texture.TextureFilter.Nearest;
        assertMetadataFailure(pages, profile, "Nearest/Linear");
        page.minFilter = Texture.TextureFilter.Linear;
        page.uWrap = Texture.TextureWrap.Repeat;
        assertMetadataFailure(pages, profile, "Repeat/ClampToEdge");
        page.uWrap = Texture.TextureWrap.ClampToEdge;
        page.useMipMaps = true;
        assertMetadataFailure(pages, profile, "mipmaps=true");
    }

    @Test
    public void atlasBackedMultiPageFontsSucceedAndAnyExternalPageFails() {
        Texture first = texture(1, 1);
        Texture second = texture(1, 1);
        Texture external = texture(1, 1);
        Skin skin = new Skin();
        try {
            BitmapFont valid = font(first, second);
            skin.add("valid-font", valid, BitmapFont.class);
            HudResources.validateFonts(skin, Array.with(first, second));

            BitmapFont invalid = font(first, external);
            skin.add("external-font", invalid, BitmapFont.class);
            IllegalStateException failure = Assert.assertThrows(
                    IllegalStateException.class,
                    () -> HudResources.validateFonts(skin, Array.with(first, second)));
            Assert.assertEquals("HUD BitmapFont 'external-font' uses a texture that is not part "
                    + "of the declared HUD atlas.", failure.getMessage());
        } finally {
            skin.dispose();
            first.dispose();
            second.dispose();
            external.dispose();
        }
    }

    @Test
    public void preparationOwnsOrderedAtlasSkinAndBundleAndDisposesOnce() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("complete"));
        writeHudFiles(root);
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/complete.json";
        String authoredSkinId = "  ui\\game.json  ";
        String authoredAtlasId = "  ui\\game.atlas  ";
        String authoredProfileId = "  " + HudTextureProfile.DEFAULT_ID + "  ";
        asset.skinId = authoredSkinId;
        asset.atlasId = authoredAtlasId;
        asset.textureProfileId = authoredProfileId;

        HudResources resources = HudResources.prepareStandalone(asset, root, fullRequirements());
        HudSelectedResources selected = resources.select(authoredSkinId);

        Assert.assertEquals(authoredSkinId, asset.skinId);
        Assert.assertEquals(authoredAtlasId, asset.atlasId);
        Assert.assertEquals(authoredProfileId, asset.textureProfileId);
        Assert.assertEquals("ui/game.json", selected.skinId());
        Assert.assertEquals("ui/game.atlas", resources.atlasId());
        Assert.assertEquals(HudTextureProfile.DEFAULT_ID, resources.textureProfile().id());
        Assert.assertNull(selected.skin().getAtlas());
        BitmapFont font = selected.skin().get("default-font", BitmapFont.class);
        Assert.assertEquals(2, font.getRegions().size);
        Array<Texture> pages = new Array<Texture>();
        pages.add(font.getRegions().get(0).getTexture());
        pages.add(font.getRegions().get(1).getTexture());
        for (int i = 0; i < pages.size; i++) {
            int handle = TextureRegistry.findHandle(pages.get(i));
            Assert.assertNotEquals(TextureRegistry.INVALID_HANDLE, handle);
            Assert.assertEquals(i + 1,
                    resources.textureArrayBundle().handle2layer.get(handle, -1));
        }
        Assert.assertEquals(0, resources.textureArrayBundle().handle2layer.get(
                InternalTextures.whiteHandle(), -1));
        Assert.assertEquals(Texture.TextureFilter.Linear,
                resources.textureArrayBundle().textureArray.getMinFilter());
        Assert.assertEquals(Texture.TextureFilter.Linear,
                resources.textureArrayBundle().textureArray.getMagFilter());
        Assert.assertEquals(Texture.TextureWrap.ClampToEdge,
                resources.textureArrayBundle().textureArray.getUWrap());
        Assert.assertEquals(Texture.TextureWrap.ClampToEdge,
                resources.textureArrayBundle().textureArray.getVWrap());
        Assert.assertFalse(resources.textureArrayBundle().textureArray.isManaged());

        resources.dispose();
        int deletionsAfterFirstDispose = deletedTextures;
        resources.dispose();

        Assert.assertTrue(resources.isDisposed());
        Assert.assertEquals(deletionsAfterFirstDispose, deletedTextures);
        Assert.assertThrows(IllegalStateException.class, selected::skin);
    }

    @Test
    public void failedPreparationDisposesLoadedResourcesBeforeRegistryMutation() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("transactional"));
        FileHandle ui = root.child("ui");
        ui.mkdirs();
        writePage(ui.child("page.png"));
        ui.child("game.atlas").writeString(
                pageDescriptor("page.png", "button", -1), false, "UTF-8");
        writeSmallPage(ui.child("external-font.png"));
        ui.child("external-font.fnt").writeString(
                "info face=\"test\" size=16 bold=0 italic=0 charset=\"\" unicode=0 "
                        + "stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1\n"
                        + "common lineHeight=16 base=12 scaleW=1 scaleH=1 pages=1 packed=0\n"
                        + "page id=0 file=\"external-font.png\"\n"
                        + "chars count=1\n"
                        + "char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 "
                        + "xadvance=1 page=0 chnl=0\n"
                        + "kernings count=0\n",
                false, "UTF-8");
        ui.child("game.json").writeString(
                "{\"com.badlogic.gdx.graphics.g2d.BitmapFont\":{"
                        + "\"external-font\":{\"file\":\"external-font.fnt\"}}}",
                false, "UTF-8");
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/transactional.json";
        asset.skinId = "ui/game.json";
        asset.atlasId = "ui/game.atlas";

        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class,
                () -> HudResources.prepareStandalone(asset, root, fullRequirements()));

        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains("external-font"));
        Assert.assertTrue("Atlas and external font texture must be disposed", deletedTextures >= 2);
        Texture probe = texture(1, 1);
        try {
            Assert.assertEquals(2, TextureRegistry.handleOf(probe));
        } finally {
            probe.dispose();
        }
    }

    public static void writeHudFiles(FileHandle root) {
        FileHandle ui = root.child("ui");
        ui.mkdirs();
        writePage(ui.child("page-a.png"));
        writePage(ui.child("page-b.png"));
        ui.child("game.atlas").writeString(
                pageDescriptor("page-a.png", "default-font", 0)
                        + regionDescriptor(HudBuiltInLabelStyle.ATLAS_REGION, -1)
                        + regionDescriptor(HudBuiltInTextButtonStyle.BACKGROUND_REGION, -1)
                        + regionDescriptor("crosshair", -1)
                        + regionDescriptor("overlay-gradient", -1)
                        + regionDescriptor("inventory-art", -1)
                        + regionDescriptor("inventory-panel", -1)
                        + "\n" + pageDescriptor("page-b.png", "default-font", 1),
                false, "UTF-8");
        ui.child("default-font.fnt").writeString(
                "info face=\"test\" size=16 bold=0 italic=0 charset=\"\" unicode=0 "
                        + "stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1\n"
                        + "common lineHeight=16 base=12 scaleW=2048 scaleH=2048 pages=2 packed=0\n"
                        + "page id=0 file=\"page-a.png\"\n"
                        + "page id=1 file=\"page-b.png\"\n"
                        + "chars count=2\n"
                        + "char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 "
                        + "xadvance=1 page=0 chnl=0\n"
                        + "char id=66 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 "
                        + "xadvance=1 page=1 chnl=0\n"
                        + "kernings count=0\n",
                false, "UTF-8");
        ui.child("game.json").writeString(
                "{\"com.badlogic.gdx.graphics.g2d.BitmapFont\":{"
                        + "\"default-font\":{\"file\":\"default-font.fnt\"}},"
                        + "\"com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle\":{"
                        + "\"hud-title\":{\"font\":\"default-font\"},"
                        + "\"hud-body\":{\"font\":\"default-font\"},"
                        + "\"hud-body-bitmap\":{\"font\":\"default-font\"}},"
                        + "\"com.badlogic.gdx.scenes.scene2d.ui.TextButton$TextButtonStyle\":{"
                        + "\"hud-primary\":{\"font\":\"default-font\","
                        + "\"up\":\"inventory-panel\"}}}",
                false, "UTF-8");
    }

    private static String pageDescriptor(String file, String region, int index) {
        return file + "\n"
                + "size: 2048, 2048\n"
                + "format: RGBA8888\n"
                + "filter: Linear,Linear\n"
                + "repeat: none\n"
                + region + "\n"
                + "  rotate: false\n"
                + "  xy: 0, 0\n"
                + "  size: 1, 1\n"
                + "  orig: 1, 1\n"
                + "  offset: 0, 0\n"
                + "  index: " + index + "\n";
    }

    private static String regionDescriptor(String region, int index) {
        return region + "\n"
                + "  rotate: false\n"
                + "  xy: 0, 0\n"
                + "  size: 1, 1\n"
                + "  orig: 1, 1\n"
                + "  offset: 0, 0\n"
                + "  index: " + index + "\n";
    }

    private static void writePage(FileHandle file) {
        Pixmap pixmap = new Pixmap(2048, 2048, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(1f, 1f, 1f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static void writeSmallPage(FileHandle file) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(1f, 1f, 1f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static TextureAtlas.TextureAtlasData.Page validPage(String name) {
        TextureAtlas.TextureAtlasData.Page page = new TextureAtlas.TextureAtlasData.Page();
        page.name = name;
        page.width = 2048;
        page.height = 2048;
        page.minFilter = Texture.TextureFilter.Linear;
        page.magFilter = Texture.TextureFilter.Linear;
        page.uWrap = Texture.TextureWrap.ClampToEdge;
        page.vWrap = Texture.TextureWrap.ClampToEdge;
        page.useMipMaps = false;
        return page;
    }

    private static BitmapFont font(Texture... textures) {
        Array<TextureRegion> regions = new Array<TextureRegion>(textures.length);
        for (int i = 0; i < textures.length; i++) {
            regions.add(new TextureRegion(textures[i]));
        }
        return new BitmapFont(new BitmapFont.BitmapFontData(), regions, true);
    }

    private static Texture texture(int width, int height) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            return new Texture(pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static void assertPreparationFailure(
            HudScreenAsset asset, FileHandle root, String diagnostic) {
        RuntimeException failure = Assert.assertThrows(
                RuntimeException.class,
                () -> HudResources.prepareStandalone(asset, root, fullRequirements()));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(diagnostic));
    }

    private static void assertMetadataFailure(
            Array<TextureAtlas.TextureAtlasData.Page> pages,
            HudTextureProfile profile, String diagnostic) {
        RuntimeException failure = Assert.assertThrows(RuntimeException.class,
                () -> HudResources.validateAtlasMetadata(pages, profile));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(diagnostic));
    }

    static HudResourceRequirements fullRequirements() {
        FileHandle fixture = new FileHandle(
                "src/test/resources/games/pixscape/runtime/hud/document/v1/"
                        + "materializer-smoke.json");
        HudDocumentValidator validator = new HudDocumentValidator();
        return HudResourceRequirements.from(validator.validate(
                new HudDocumentCodec().read(fixture)).validatedDocument());
    }

    static HudResourceRequirements requirementsFor(String root) {
        HudDocumentValidator validator = new HudDocumentValidator();
        return HudResourceRequirements.from(validator.validate(new HudDocumentCodec().read(
                "{\"schemaVersion\":1,\"root\":" + root + "}")).validatedDocument());
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
}
