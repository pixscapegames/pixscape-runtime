package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.github.tommyettinger.textra.Font;

/** Creates standard TextraTypist fonts without mutating or owning their BitmapFont sources. */
public final class HudTextraFontFactory {
    private static final char SOLID_BLOCK = '\u2588';

    private HudTextraFontFactory() { }

    /**
     * Builds a resource-owned base Font that borrows source glyph textures and the supplied white
     * region. The isolated BitmapFont projection exists only during this cold-path conversion.
     */
    public static Font prepare(BitmapFont source, TextureRegion whiteRegion) {
        if (source == null) throw new IllegalArgumentException("BitmapFont is required.");
        if (whiteRegion == null || whiteRegion.getTexture() == null) {
            throw new IllegalArgumentException("Registered HUD white region is required.");
        }

        BitmapFont.BitmapFontData sourceData = source.getData();
        BitmapFont.Glyph existingBlock = sourceData.getGlyph(SOLID_BLOCK);
        float unscaledPadLeft = sourceData.padLeft / sourceData.scaleX;
        float unscaledPadTop = sourceData.padTop / sourceData.scaleY;

        BitmapFont.BitmapFontData isolatedData = copyData(sourceData);
        Array<TextureRegion> isolatedRegions = new Array<TextureRegion>(source.getRegions());
        if (existingBlock == null
                || Math.round(existingBlock.width - unscaledPadLeft) != 1) {
            int whitePage = isolatedRegions.size;
            isolatedRegions.add(whiteRegion);
            BitmapFont.Glyph block = new BitmapFont.Glyph();
            block.id = SOLID_BLOCK;
            block.page = whitePage;
            block.srcX = -Math.round(unscaledPadLeft);
            block.srcY = -Math.round(unscaledPadTop);
            block.width = Math.round(1f + unscaledPadLeft);
            block.height = Math.round(1f + unscaledPadTop);
            block.xadvance = 1;
            block.yoffset = -1;
            isolatedData.setGlyph(SOLID_BLOCK, block);
        }

        BitmapFont projection = new BitmapFont(isolatedData, isolatedRegions, false);
        projection.setOwnsTexture(false);
        projection.setUseIntegerPositions(source.usesIntegerPositions());
        try {
            Font prepared = new Font(projection);
            if (prepared.whiteBlock != null) {
                prepared.dispose();
                throw new IllegalStateException(
                        "Prepared TextraTypist Font unexpectedly allocated a private white texture.");
            }
            return prepared;
        } finally {
            projection.dispose();
        }
    }

    private static BitmapFont.BitmapFontData copyData(BitmapFont.BitmapFontData source) {
        BitmapFont.BitmapFontData copy = new BitmapFont.BitmapFontData();
        copy.name = source.name;
        copy.imagePaths = copy(source.imagePaths);
        copy.fontFile = source.fontFile;
        copy.flipped = source.flipped;
        copy.padTop = source.padTop;
        copy.padRight = source.padRight;
        copy.padBottom = source.padBottom;
        copy.padLeft = source.padLeft;
        copy.lineHeight = source.lineHeight;
        copy.capHeight = source.capHeight;
        copy.ascent = source.ascent;
        copy.descent = source.descent;
        copy.down = source.down;
        copy.blankLineScale = source.blankLineScale;
        copy.scaleX = source.scaleX;
        copy.scaleY = source.scaleY;
        copy.markupEnabled = source.markupEnabled;
        copy.cursorX = source.cursorX;
        copy.spaceXadvance = source.spaceXadvance;
        copy.xHeight = source.xHeight;
        copy.breakChars = copy(source.breakChars);
        copy.xChars = copy(source.xChars);
        copy.capChars = copy(source.capChars);

        for (BitmapFont.Glyph[] page : source.glyphs) {
            if (page == null) continue;
            for (BitmapFont.Glyph glyph : page) {
                if (glyph != null) copy.setGlyph(glyph.id, copyGlyph(glyph));
            }
        }
        if (source.missingGlyph != null) {
            copy.missingGlyph = copy.getGlyph((char) source.missingGlyph.id);
        }
        return copy;
    }

    private static String[] copy(String[] source) {
        if (source == null) return null;
        String[] copy = new String[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i];
        return copy;
    }

    private static char[] copy(char[] source) {
        if (source == null) return null;
        char[] copy = new char[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i];
        return copy;
    }

    private static BitmapFont.Glyph copyGlyph(BitmapFont.Glyph source) {
        BitmapFont.Glyph copy = new BitmapFont.Glyph();
        copy.id = source.id;
        copy.srcX = source.srcX;
        copy.srcY = source.srcY;
        copy.width = source.width;
        copy.height = source.height;
        copy.u = source.u;
        copy.v = source.v;
        copy.u2 = source.u2;
        copy.v2 = source.v2;
        copy.xoffset = source.xoffset;
        copy.yoffset = source.yoffset;
        copy.xadvance = source.xadvance;
        copy.kerning = source.kerning;
        copy.fixedWidth = source.fixedWidth;
        copy.page = source.page;
        return copy;
    }
}
