package games.pixscape.runtime.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.graphics.TextureArrayData;
import com.badlogic.gdx.utils.Array;

/** Upload data for prepared Pixmaps that deliberately cannot be reloaded. */
final class OneShotPixmapTextureArrayData implements TextureArrayData {

    /*
     * TextureArray retains its TextureArrayData, PixmapTextureData retains its
     * Pixmap, and GWT Pixmap.dispose() does not clear the Canvas backing. Avoid
     * FileTextureArrayData here so the retained data becomes metadata-only.
     */
    private Pixmap[] pixmaps;
    private final int width;
    private final int height;
    private final int depth;
    private final int internalFormat;
    private final int glType;
    private final boolean useMipMaps;
    private final boolean disposePixmaps;
    private boolean prepared;

    OneShotPixmapTextureArrayData(Array<Pixmap> layers,
                                  Pixmap.Format expectedFormat,
                                  boolean useMipMaps,
                                  boolean disposePixmaps) {
        if (layers == null || layers.size == 0) {
            throw new IllegalArgumentException("Texture-array layers must not be empty.");
        }
        if (expectedFormat == null) {
            throw new IllegalArgumentException("Texture-array format must not be null.");
        }

        Pixmap first = requireLayer(layers, 0);
        width = first.getWidth();
        height = first.getHeight();
        depth = layers.size;
        internalFormat = Pixmap.Format.toGlFormat(expectedFormat);
        glType = Pixmap.Format.toGlType(expectedFormat);
        this.useMipMaps = useMipMaps;
        this.disposePixmaps = disposePixmaps;

        pixmaps = new Pixmap[depth];
        for (int i = 0; i < depth; i++) {
            Pixmap pixmap = requireLayer(layers, i);
            if (pixmap.getWidth() != width || pixmap.getHeight() != height) {
                throw new IllegalArgumentException(
                        "Texture-array layer " + i + " has dimensions "
                                + pixmap.getWidth() + "x" + pixmap.getHeight()
                                + ", expected " + width + "x" + height + "."
                );
            }
            if (pixmap.getFormat() != expectedFormat) {
                throw new IllegalArgumentException(
                        "Texture-array layer " + i + " has format " + pixmap.getFormat()
                                + ", expected " + expectedFormat + "."
                );
            }
            pixmaps[i] = pixmap;
        }
    }

    private static Pixmap requireLayer(Array<Pixmap> layers, int index) {
        Pixmap pixmap = layers.get(index);
        if (pixmap == null) {
            throw new IllegalArgumentException("Texture-array layer " + index + " must not be null.");
        }
        return pixmap;
    }

    static TextureArray upload(Array<Pixmap> layers, boolean disposeLayersAfterUpload) {
        OneShotPixmapTextureArrayData data = null;
        TextureArray textureArray = null;
        try {
            data = new OneShotPixmapTextureArrayData(
                    layers,
                    Pixmap.Format.RGBA8888,
                    false,
                    disposeLayersAfterUpload
            );
            textureArray = new TextureArray(data);
            textureArray.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            textureArray.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            return textureArray;
        } catch (RuntimeException failure) {
            if (data != null) {
                data.releasePixmaps(failure);
            } else if (disposeLayersAfterUpload) {
                disposeLayers(layers);
            }
            if (textureArray != null) {
                try {
                    textureArray.dispose();
                } catch (RuntimeException ignored) {
                    // Preserve the upload/configuration failure.
                }
            }
            throw failure;
        }
    }

    private static void disposeLayers(Array<Pixmap> layers) {
        if (layers == null) return;
        for (int i = 0; i < layers.size; i++) {
            Pixmap pixmap = layers.get(i);
            if (pixmap != null) pixmap.dispose();
        }
    }

    @Override
    public boolean isPrepared() {
        return prepared;
    }

    @Override
    public void prepare() {
        if (pixmaps == null) {
            throw new IllegalStateException("Texture-array upload data has already been consumed.");
        }
        prepared = true;
    }

    @Override
    public void consumeTextureArrayData() {
        if (!prepared) {
            throw new IllegalStateException("Texture-array upload data must be prepared before consumption.");
        }
        if (pixmaps == null) {
            throw new IllegalStateException("Texture-array upload data has already been consumed.");
        }

        RuntimeException failure = null;
        try {
            for (int i = 0; i < pixmaps.length; i++) {
                Pixmap pixmap = pixmaps[i];
                Gdx.gl30.glTexSubImage3D(
                        GL30.GL_TEXTURE_2D_ARRAY,
                        0,
                        0,
                        0,
                        i,
                        width,
                        height,
                        1,
                        pixmap.getGLInternalFormat(),
                        pixmap.getGLType(),
                        pixmap.getPixels()
                );
            }
            if (useMipMaps) {
                Gdx.gl20.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);
            }
        } catch (RuntimeException uploadFailure) {
            failure = uploadFailure;
        } finally {
            failure = releasePixmaps(failure);
        }
        if (failure != null) throw failure;
    }

    private RuntimeException releasePixmaps(RuntimeException priorFailure) {
        Pixmap[] retained = pixmaps;
        pixmaps = null;
        if (!disposePixmaps || retained == null) return priorFailure;

        RuntimeException failure = priorFailure;
        for (int i = 0; i < retained.length; i++) {
            Pixmap pixmap = retained[i];
            retained[i] = null;
            if (pixmap == null) continue;
            try {
                pixmap.dispose();
            } catch (RuntimeException disposalFailure) {
                if (failure == null) failure = disposalFailure;
            }
        }
        return failure;
    }

    int retainedPixmapCount() {
        return pixmaps != null ? pixmaps.length : 0;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public boolean isManaged() {
        return false;
    }

    @Override
    public int getInternalFormat() {
        return internalFormat;
    }

    @Override
    public int getGLType() {
        return glType;
    }
}
