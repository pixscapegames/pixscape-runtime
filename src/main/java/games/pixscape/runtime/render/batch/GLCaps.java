package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.IntBuffer;

public final class GLCaps {
    public final boolean hasES3;
    public final boolean hasTextureArray;   // simplifié: = ES3
    public final int maxTextureUnits;
    public final int maxTextureSize;

    private GLCaps(boolean es3, boolean texArray, int units, int size) {
        this.hasES3 = es3;
        this.hasTextureArray = texArray;
        this.maxTextureUnits = units;
        this.maxTextureSize = size;
    }

    public static GLCaps detect() {
        final boolean es3 = (Gdx.gl30 != null);

        IntBuffer buf = BufferUtils.newIntBuffer(16);

        // --- Max fragment texture units ---
        buf.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_IMAGE_UNITS, buf);
        int units = buf.get(0);

        // Fallback: certains drivers/backends peuvent renvoyer 0 ici
        if (units <= 0) {
            buf.clear();
            Gdx.gl.glGetIntegerv(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, buf);
            units = buf.get(0);
        }

        // Clamp sécurité
        if (units <= 0) units = 1;

        // --- Max texture size ---
        buf.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, buf);
        int size = buf.get(0);
        if (size <= 0) size = 64;

        return new GLCaps(es3, es3, units, size);
    }


    // Petits helpers lisibles
    public boolean supportsTextureArray() { return hasTextureArray; }
    public boolean supportsES3() { return hasES3; }
    public int maxTextureUnits()         { return maxTextureUnits; }
    public int maxTextureSize()          { return maxTextureSize; }

    @Override public String toString() {
        return "GLCaps{ES3=" + hasES3 +
            ", texArray=" + hasTextureArray +
            ", maxUnits=" + maxTextureUnits +
            ", maxTexSize=" + maxTextureSize + "}";
    }
}
