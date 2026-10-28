package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.IntBuffer;

public final class GLCaps {

    public final boolean es3;
    public final int maxTextureUnits;
    public final int maxTextureSize;
    public final int maxArrayTextureLayers;

    GLCaps(boolean es3, int maxTextureUnits, int maxTextureSize,
           int maxArrayTextureLayers) {
        this.es3 = es3;
        this.maxTextureUnits = maxTextureUnits;
        this.maxTextureSize = maxTextureSize;
        this.maxArrayTextureLayers = maxArrayTextureLayers;
    }

    public static GLCaps detect() {
        boolean es3 = Gdx.gl30 != null;

        IntBuffer buf = BufferUtils.newIntBuffer(4);

        int units = readInt(buf, GL20.GL_MAX_TEXTURE_IMAGE_UNITS, 1);
        if (units <= 1) {
            units = readInt(buf, GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, units);
        }

        int size = readInt(buf, GL20.GL_MAX_TEXTURE_SIZE, 64);
        int arrayLayers = es3
                ? readInt(buf, GL30.GL_MAX_ARRAY_TEXTURE_LAYERS, 1)
                : 0;

        return new GLCaps(es3, Math.max(1, units), Math.max(64, size),
                es3 ? Math.max(1, arrayLayers) : 0);
    }

    private static int readInt(IntBuffer buf, int pname, int fallback) {
        try {
            buf.clear();
            Gdx.gl.glGetIntegerv(pname, buf);
            int value = buf.get(0);
            return value > 0 ? value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public boolean supportsES3() {
        return es3;
    }

    public boolean supportsTextureArray() {
        return es3;
    }

    /** Validates a complete fixed-size TextureArray before GPU allocation. */
    public void validateTextureArray(int width, int height, int requiredLayers) {
        if (!supportsTextureArray()) {
            throw new IllegalStateException(
                    "TextureArray requires GL30, OpenGL ES 3, or WebGL2 support.");
        }
        if (width > maxTextureSize || height > maxTextureSize) {
            throw new IllegalStateException(
                    "TextureArray dimensions " + width + "x" + height
                            + " exceed GL_MAX_TEXTURE_SIZE " + maxTextureSize + ".");
        }
        if (requiredLayers > maxArrayTextureLayers) {
            throw new IllegalStateException(
                    "TextureArray requires " + requiredLayers
                            + " layers but this device supports "
                            + maxArrayTextureLayers + ".");
        }
    }

    @Override
    public String toString() {
        return "GLCaps{es3=" + es3
                + ", textureArray=" + supportsTextureArray()
                + ", maxTextureUnits=" + maxTextureUnits
                + ", maxTextureSize=" + maxTextureSize
                + ", maxArrayTextureLayers=" + maxArrayTextureLayers
                + "}";
    }
}
