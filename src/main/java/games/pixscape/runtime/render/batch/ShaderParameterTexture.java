package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FloatTextureData;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Managed RGBA32F data texture shared by all draws in one standard batch. */
public final class ShaderParameterTexture implements AutoCloseable {
    public static final int TEXTURE_UNIT = 1;
    public static final int TEXELS_PER_ROW = ShaderParameterRows.FLOATS_PER_ROW / 4;

    private final Texture texture;
    private final FloatBuffer uploadBuffer;

    /** Headless tests may install a stub GL30 whose capability queries return zero. */
    public static boolean hasLiveContext() {
        if (Gdx.gl30 == null || Gdx.gl == null || Gdx.graphics == null
                || Gdx.graphics.getGLVersion() == null) return false;
        IntBuffer size = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, size);
        return size.get(0) > 0;
    }

    public ShaderParameterTexture(int rowCapacity) {
        if (Gdx.gl30 == null) {
            throw new IllegalStateException("Shader parameter table requires GL30 / ES3 / WebGL2");
        }
        IntBuffer caps = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, caps);
        int maxSize = caps.get(0);
        if (TEXELS_PER_ROW > maxSize || rowCapacity > maxSize) {
            throw new IllegalStateException("Parameter table " + TEXELS_PER_ROW + "x" + rowCapacity
                    + " exceeds GL_MAX_TEXTURE_SIZE=" + maxSize);
        }
        caps.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_IMAGE_UNITS, caps);
        if (caps.get(0) <= TEXTURE_UNIT) {
            throw new IllegalStateException("Shader parameter table requires texture unit " + TEXTURE_UNIT);
        }
        FloatTextureData data = new FloatTextureData(TEXELS_PER_ROW, rowCapacity,
                GL30.GL_RGBA32F, GL20.GL_RGBA, GL20.GL_FLOAT, false);
        texture = new Texture(data);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        uploadBuffer = BufferUtils.newFloatBuffer(rowCapacity * ShaderParameterRows.FLOATS_PER_ROW);
    }

    public void upload(ShaderParameterRows rows) {
        int count = rows.rowCount() * ShaderParameterRows.FLOATS_PER_ROW;
        uploadBuffer.clear();
        uploadBuffer.put(rows.data(), 0, count);
        uploadBuffer.flip();
        texture.bind(TEXTURE_UNIT);
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, TEXELS_PER_ROW,
                rows.rowCount(), GL20.GL_RGBA, GL20.GL_FLOAT, uploadBuffer);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    public void bind() {
        texture.bind(TEXTURE_UNIT);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    @Override
    public void close() { texture.dispose(); }
}
