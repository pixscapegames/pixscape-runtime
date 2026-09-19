package games.pixscape.runtime.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.tommyettinger.textra.Font;
import com.github.tommyettinger.textra.TypingLabel;
import com.github.tommyettinger.textra.TypingListener;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.render.batch.HudBatch;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;
import org.junit.Test;

/** Reproducible GL30 prototype; intentionally excluded from the normal headless test task. */
public class TextraHudBatchGlPrototypeTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 480;
    private static final String MARKUP = "[SKY]AB[] [*]AB[*] [/]AB[/] [_]AB[_] [~]AB[~] "
            + "{WAVE=0.8;1.0;0.8}ABAB{ENDWAVE}{EVENT=prototype-end}";

    @Test
    public void rendersNativeTypingLabelsThroughHudBatchAndSpriteBatch() {
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Pixscape TextraTypist HUD prototype");
        configuration.setWindowedMode(WIDTH, HEIGHT);
        configuration.setInitialVisible(false);
        configuration.setOpenGLEmulation(
                Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        configuration.disableAudio(true);

        new Lwjgl3Application(new ApplicationAdapter() {
            private Texture page0;
            private Texture page1;
            private BitmapFont bitmapFont;
            private Font assetFont;
            private Font hudFontA;
            private Font hudFontB;
            private Font spriteFont;
            private AtlasRuntimeService.TextureArrayBundle bundle;
            private ShaderProgram shader;
            private HudBatch hudBatch;
            private SpriteBatch spriteBatch;
            private Stage hudStage;
            private Stage spriteStage;

            @Override
            public void create() {
                try {
                    Assert.assertNotNull("A real GL30 context is required", Gdx.gl30);
                    page0 = page("page-0.png", Color.WHITE, Color.CYAN);
                    page1 = page("page-1.png", Color.WHITE, Color.MAGENTA);
                    bitmapFont = bitmapFontWithoutSolidBlock(page0, page1);
                    Assert.assertNull(bitmapFont.getData().getGlyph('\u2588'));

                    Font unpreparedFont = new Font(bitmapFont);
                    Assert.assertNotNull("Textra allocates a private fallback without U+2588",
                            unpreparedFont.whiteBlock);
                    unpreparedFont.dispose();

                    TextureRegion hudWhite = new TextureRegion(InternalTextures.whiteTexture());
                    assetFont = HudTextraFontFactory.prepare(bitmapFont, hudWhite);
                    Assert.assertNull("Shared BitmapFont data must remain unchanged",
                            bitmapFont.getData().getGlyph('\u2588'));
                    Assert.assertNull("Prepared white glyph must prevent Textra allocation",
                            assetFont.whiteBlock);
                    Assert.assertSame(hudWhite.getTexture(),
                            assetFont.mapping.get(assetFont.solidBlock).getTexture());

                    hudFontA = new Font(assetFont);
                    hudFontB = new Font(assetFont);
                    spriteFont = new Font(assetFont);
                    Assert.assertSame(assetFont.mapping.get('A').getTexture(),
                            hudFontA.mapping.get('A').getTexture());
                    Assert.assertSame(hudFontA.mapping.get('A').getTexture(),
                            hudFontB.mapping.get('A').getTexture());
                    float originalB = hudFontB.getBoldStrength();
                    hudFontA.setBoldStrength(originalB + 0.75f);
                    Assert.assertEquals("Native Font copies isolate mutable configuration",
                            originalB, hudFontB.getBoldStrength(), 0f);

                    TypingLabel sharedA = label("AB", assetFont, 0f, 0f);
                    TypingLabel sharedB = label("AB", assetFont, 0f, 0f);
                    float sharedStrength = assetFont.getObliqueStrength();
                    assetFont.setObliqueStrength(sharedStrength + 0.25f);
                    Assert.assertSame(sharedA.getFont(), sharedB.getFont());
                    Assert.assertEquals(assetFont.getObliqueStrength(),
                            sharedA.getFont().getObliqueStrength(), 0f);
                    assetFont.setObliqueStrength(sharedStrength);

                    Array<Texture> pages = Array.with(page0, page1);
                    bundle = AtlasRuntimeService.buildTextureArrayFromTextures(pages);
                    shader = new ShaderProgram(
                            Gdx.files.internal("shaders/core/desktop-gl30/hud-texture-array.vert"),
                            Gdx.files.internal("shaders/core/desktop-gl30/hud-texture-array.frag"));
                    Assert.assertTrue(shader.getLog(), shader.isCompiled());
                    hudBatch = new HudBatch(shader);
                    hudBatch.setTextureArrayBundle(bundle);
                    spriteBatch = new SpriteBatch();
                    hudStage = new Stage(new ScreenViewport(), hudBatch);
                    spriteStage = new Stage(new ScreenViewport(), spriteBatch);
                    hudStage.getViewport().update(WIDTH, HEIGHT, true);
                    spriteStage.getViewport().update(WIDTH, HEIGHT, true);

                    TypingLabel first = label(MARKUP, hudFontA, 40f, 330f);
                    TypingLabel second = label("{SPEED=0.12}ABABABAB", hudFontB, 40f, 250f);
                    TypingLabel reference = label(MARKUP, spriteFont, 40f, 90f);
                    hudStage.addActor(first);
                    hudStage.addActor(second);
                    spriteStage.addActor(reference);

                    verifyNativeTypingApi(first, second);
                    first.restart(MARKUP);
                    first.skipToTheEnd(false, false);
                    reference.skipToTheEnd(false, false);
                    for (int i = 0; i < 12; i++) {
                        hudStage.act(1f / 30f);
                        spriteStage.act(1f / 30f);
                    }
                    Assert.assertTrue("Native WAVE effect must transform at least one glyph",
                            hasNonZero(first.getOffsets().items, first.getOffsets().size));

                    Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
                    Gdx.gl.glClearColor(0.035f, 0.045f, 0.065f, 1f);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    hudStage.draw();
                    capture("build/reports/textra-prototype/desktop-hud-batch.png");

                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    spriteStage.draw();
                    capture("build/reports/textra-prototype/desktop-sprite-batch.png");

                    Assert.assertSame(page0, assetFont.mapping.get('A').getTexture());
                    Assert.assertSame(page1, assetFont.mapping.get('B').getTexture());
                    Assert.assertNotEquals(TextureRegistry.INVALID_HANDLE,
                            TextureRegistry.findHandle(page0));
                    Assert.assertNotEquals(TextureRegistry.INVALID_HANDLE,
                            TextureRegistry.findHandle(page1));
                    Assert.assertEquals(0, bundle.handle2layer.get(
                            InternalTextures.whiteHandle(), -1));
                    Assert.assertSame("Standard bitmap rendering must retain the HudBatch shader",
                            shader, hudBatch.getShader());
                } catch (Throwable prototypeFailure) {
                    failure[0] = prototypeFailure;
                } finally {
                    Gdx.app.exit();
                }
            }

            @Override
            public void dispose() {
                try {
                    if (hudStage != null) hudStage.dispose();
                    if (spriteStage != null) spriteStage.dispose();
                    if (hudBatch != null) hudBatch.dispose();
                    if (spriteBatch != null) spriteBatch.dispose();
                    if (hudFontA != null) hudFontA.dispose();
                    if (hudFontB != null) hudFontB.dispose();
                    if (spriteFont != null) spriteFont.dispose();
                    if (assetFont != null) assetFont.dispose();
                    if (bitmapFont != null) bitmapFont.dispose();
                    if (bundle != null && bundle.textureArray != null) bundle.textureArray.dispose();
                    if (shader != null) shader.dispose();
                    if (page0 != null) page0.dispose();
                    if (page1 != null) page1.dispose();
                    InternalTextures.dispose();
                    TextureRegistry.clear();
                } catch (Throwable disposalFailure) {
                    if (failure[0] == null) failure[0] = disposalFailure;
                }
            }
        }, configuration);

        if (failure[0] != null) {
            throw new AssertionError("TextraTypist HudBatch prototype failed.", failure[0]);
        }
    }

    private static TypingLabel label(String text, Font font, float x, float y) {
        TypingLabel label = new TypingLabel(text, font, Color.WHITE);
        label.setPosition(x, y);
        label.setSize(700f, 72f);
        label.setTextSpeed(0.08f);
        return label;
    }

    private static void capture(String path) {
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true);
        Pixmap screenshot = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
        BufferUtils.copy(pixels, 0, screenshot.getPixels(), pixels.length);
        try {
            PixmapIO.writePNG(Gdx.files.local(path), screenshot);
        } finally {
            screenshot.dispose();
        }
    }

    private static boolean hasNonZero(float[] values, int size) {
        for (int i = 0; i < size; i++) {
            if (Math.abs(values[i]) > 0.0001f) return true;
        }
        return false;
    }

    private static void verifyNativeTypingApi(TypingLabel first, TypingLabel second) {
        final boolean[] eventReceived = {false};
        first.setTypingListener(new TypingListener() {
            @Override public void event(String event) {
                if ("prototype-end".equals(event)) eventReceived[0] = true;
            }
            @Override public void end() { }
            @Override public void onChar(long ch) { }
            @Override public String replaceVariable(String variable) { return null; }
        });

        first.restart();
        second.restart();
        first.act(0.3f);
        int firstProgress = first.getWorkingLayout().countGlyphs();
        int secondProgress = second.getWorkingLayout().countGlyphs();
        Assert.assertNotEquals("Actors keep independent typing progression",
                firstProgress, secondProgress);

        first.pause();
        Assert.assertTrue(first.isPaused());
        Assert.assertFalse(second.isPaused());
        first.resume();
        Assert.assertFalse(first.isPaused());
        first.skipToTheEnd(false, false);
        Assert.assertTrue(first.hasEnded());
        Assert.assertTrue("Native listener receives markup events", eventReceived[0]);
        first.restart("ABAB");
        Assert.assertFalse(first.hasEnded());
    }

    private static BitmapFont bitmapFontWithoutSolidBlock(Texture first, Texture second) {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.name = "textra-prototype";
        data.lineHeight = 22f;
        data.capHeight = 16f;
        data.xHeight = 12f;
        data.ascent = 0f;
        data.descent = -4f;
        data.down = -22f;
        data.spaceXadvance = 9f;
        data.setGlyph('A', glyph('A', 0, 4, 4, 14, 16, 16));
        data.setGlyph('B', glyph('B', 1, 4, 4, 14, 16, 16));
        data.setGlyph('l', glyph('l', 0, 24, 4, 7, 16, 8));
        BitmapFont.Glyph space = glyph(' ', 0, 0, 0, 0, 0, 9);
        data.setGlyph(' ', space);
        data.missingGlyph = space;
        Array<TextureRegion> regions = Array.with(
                new TextureRegion(first), new TextureRegion(second));
        BitmapFont font = new BitmapFont(data, regions, false);
        font.setOwnsTexture(false);
        return font;
    }

    private static BitmapFont.Glyph glyph(
            char id, int page, int x, int y, int width, int height, int advance) {
        BitmapFont.Glyph glyph = new BitmapFont.Glyph();
        glyph.id = id;
        glyph.page = page;
        glyph.srcX = x;
        glyph.srcY = y;
        glyph.width = width;
        glyph.height = height;
        glyph.xadvance = advance;
        return glyph;
    }

    private static Texture page(String name, Color glyphColor, Color markerColor) {
        Pixmap pixmap = new Pixmap(2048, 2048, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(glyphColor);
        pixmap.fillRectangle(4, 4, 14, 16);
        pixmap.fillRectangle(24, 4, 7, 16);
        pixmap.setColor(markerColor);
        pixmap.drawRectangle(4, 4, 14, 16);
        com.badlogic.gdx.files.FileHandle file =
                Gdx.files.local("build/tmp/textra-prototype/" + name);
        PixmapIO.writePNG(file, pixmap);
        pixmap.dispose();
        Texture texture = new Texture(file);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return texture;
    }
}
