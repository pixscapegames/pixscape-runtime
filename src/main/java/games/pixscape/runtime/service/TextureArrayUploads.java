package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.utils.Array;

/** Internal one-shot texture-array upload boundary for prepared CPU Pixmaps. */
public final class TextureArrayUploads {

    private TextureArrayUploads() {
    }

    /** Uploads the layers without taking ownership of the supplied Pixmaps. */
    public static TextureArray uploadBorrowed(Array<Pixmap> layers) {
        return OneShotPixmapTextureArrayData.upload(layers, false);
    }

    static TextureArray uploadOwned(Array<Pixmap> layers) {
        return OneShotPixmapTextureArrayData.upload(layers, true);
    }
}
