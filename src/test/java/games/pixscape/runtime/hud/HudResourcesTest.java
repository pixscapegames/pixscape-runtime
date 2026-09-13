package games.pixscape.runtime.hud;

import com.badlogic.gdx.Gdx;
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
    private int deletedTextures;

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installGl() {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;
        previousGraphics = Gdx.graphics;
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
    }

    @Test
    public void missingReferencesAndUnknownProfileFailClearly() throws Exception {
        FileHandle root = new FileHandle(temporaryFolder.newFolder("missing"));
        HudScreenAsset asset = new HudScreenAsset();

        assertPreparationFailure(asset, root, "skinId");
        asset.skinId = "ui/game.json";
        assertPreparationFailure(asset, root, "atlasId");
        asset.atlasId = "ui/game.atlas";
        asset.textureProfileId = "hud-unknown";
        assertPreparationFailure(asset, root, "Unknown HUD texture profile");
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
        String authoredSkinId = "  ui\\game.json  ";
        String authoredAtlasId = "  ui\\game.atlas  ";
        String authoredProfileId = "  " + HudTextureProfile.DEFAULT_ID + "  ";
        asset.skinId = authoredSkinId;
        asset.atlasId = authoredAtlasId;
        asset.textureProfileId = authoredProfileId;

        HudResources resources = HudResources.prepare(asset, root);

        Assert.assertEquals(authoredSkinId, asset.skinId);
        Assert.assertEquals(authoredAtlasId, asset.atlasId);
        Assert.assertEquals(authoredProfileId, asset.textureProfileId);
        Assert.assertEquals("ui/game.json", resources.skinId());
        Assert.assertEquals("ui/game.atlas", resources.atlasId());
        Assert.assertEquals(HudTextureProfile.DEFAULT_ID, resources.textureProfile().id());
        Assert.assertNull(resources.skin().getAtlas());
        BitmapFont font = resources.skin().get("default-font", BitmapFont.class);
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
        Assert.assertThrows(IllegalStateException.class, resources::skin);
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
        asset.skinId = "ui/game.json";
        asset.atlasId = "ui/game.atlas";

        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class, () -> HudResources.prepare(asset, root));

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
                RuntimeException.class, () -> HudResources.prepare(asset, root));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(diagnostic));
    }

    private static void assertMetadataFailure(
            Array<TextureAtlas.TextureAtlasData.Page> pages,
            HudTextureProfile profile, String diagnostic) {
        RuntimeException failure = Assert.assertThrows(RuntimeException.class,
                () -> HudResources.validateAtlasMetadata(pages, profile));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(diagnostic));
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
