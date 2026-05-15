package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.IntBuffer;

public final class GLCaps {

    public final boolean es3;
    public final int maxTextureUnits;
    public final int maxTextureSize;

    private GLCaps(boolean es3, int maxTextureUnits, int maxTextureSize) {
        this.es3 = es3;
        this.maxTextureUnits = maxTextureUnits;
        this.maxTextureSize = maxTextureSize;
    }

    public static GLCaps detect() {
        boolean es3 = Gdx.gl30 != null;

        IntBuffer buf = BufferUtils.newIntBuffer(4);

        int units = readInt(buf, GL20.GL_MAX_TEXTURE_IMAGE_UNITS, 1);
        if (units <= 1) {
            units = readInt(buf, GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, units);
        }

        int size = readInt(buf, GL20.GL_MAX_TEXTURE_SIZE, 64);

        return new GLCaps(es3, Math.max(1, units), Math.max(64, size));
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

    @Override
    public String toString() {
        return "GLCaps{es3=" + es3
                + ", textureArray=" + supportsTextureArray()
                + ", maxTextureUnits=" + maxTextureUnits
                + ", maxTextureSize=" + maxTextureSize
                + "}";
    }
}