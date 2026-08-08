package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;

/**
 * Runtime implementation detail. Public Java visibility does not make this type part of the
 * supported compatibility API. Creates engine-owned batches and selects the default shader.
 */
public final class BatchFactory {

    public static final int DEFAULT_BATCH_CAPACITY = 4096;

    public static final class Result {
        public final MetricsBatch batch;
        /**
         * Default shader assigned to newly created materials.
         */
        public final String defaultShaderName;

        private Result(MetricsBatch batch, String defaultShaderName) {
            this.batch = batch;
            this.defaultShaderName = defaultShaderName;
        }
    }

    private BatchFactory() {
    }

    public static Result create(AtlasRuntimeService atlasRuntimeService) {
        return create(atlasRuntimeService, GLCaps.detect());
    }

    public static Result create(AtlasRuntimeService atlasRuntimeService, GLCaps caps) {
        if (caps == null) {
            caps = GLCaps.detect();
        }

        Gdx.app.debug("BatchFactory", "caps=" + caps);

        if (!caps.supportsES3() || !caps.supportsTextureArray()) {
            throw new IllegalStateException(
                    "Pixscape requires Desktop GL30, Android ES3, or HTML WebGL2 with texture array support. caps=" + caps
            );
        }

        String shaderName = ShaderMode.TEXTURE_ARRAY.defaultShaderName();

        if (ShaderRegistry.get(shaderName) == null) {
            throw new IllegalStateException(
                    "Missing shader '" + shaderName + "'. ShaderRegistry is not initialized correctly."
            );
        }

        MetricsBatch batch = new TextureArrayMeshBatch(DEFAULT_BATCH_CAPACITY);
        return new Result(batch, shaderName);
    }
}
