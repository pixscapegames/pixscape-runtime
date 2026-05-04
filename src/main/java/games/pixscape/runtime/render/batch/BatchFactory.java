package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.render.RenderSettings;
import games.pixscape.runtime.render.RenderSettings.RenderMode;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;

/**
 * Creates runtime rendering batches and selects a compatible default shader.
 */
public final class BatchFactory {

    public static final class Result {
        public final MetricsBatch batch;
        /** Default shader assigned to newly created materials. */
        public final String defaultShaderName;

        private Result(MetricsBatch b, String name) {
            this.batch = b;
            this.defaultShaderName = name;
        }
    }

    public static Result create(AtlasRuntimeService atlasRuntimeService, RenderSettings settings) {
        return create(atlasRuntimeService, settings, GLCaps.detect());
    }

    public static Result create(AtlasRuntimeService atlasRuntimeService, RenderSettings settings, GLCaps caps) {
        final boolean hasES3 = caps.hasES3;
        final boolean supportsTA = caps.supportsTextureArray();

        Gdx.app.log("BatchFactory", "mode=" + settings.mode()
                + " requireES3=" + settings.requireES3()
                + " caps=" + caps);

        if (settings.requireES3() && !hasES3) {
            throw new IllegalStateException("ES3 is required but not available on this platform.");
        }

        RenderMode mode = settings.mode();

        switch (mode) {
            case SIMPLE:
                return createSimpleBatch();

            case MULTI:
                return createMultiBatchOrFallback(caps);

            case ARRAY:
                return createArrayBatchOrFallback(atlasRuntimeService, caps, settings.requireES3());

            case AUTO:
            default:
                if (hasES3 && supportsTA) {
                    return createArrayBatchOrFallback(atlasRuntimeService, caps, false);
                }
                if (caps.maxTextureUnits() >= 8) {
                    return createMultiBatchOrFallback(caps);
                }
                return createSimpleBatch();
        }
    }

    private static Result createSimpleBatch() {
        MetricsBatch batch = new MeshBatch(4096);
        String shaderName = ShaderMode.TEXTURE_2D.defaultShaderName();

        if (ShaderRegistry.get(shaderName) == null) {
            throw new IllegalStateException("Missing shader '" + shaderName + "'. ShaderRegistry is not initialized correctly.");
        }

        return new Result(batch, shaderName);
    }

    private static Result createMultiBatchOrFallback(GLCaps caps) {
        if (caps.maxTextureUnits() < 4) {
            Gdx.app.log("BatchFactory", "MULTI fallback -> SIMPLE (texture units < 4)");
            return createSimpleBatch();
        }

        String shaderName = ShaderMode.MULTI_TEXTURE.defaultShaderName();

        if (ShaderRegistry.get(shaderName) == null) {
            Gdx.app.log("BatchFactory", "MULTI fallback -> SIMPLE (" + shaderName + " missing)");
            return createSimpleBatch();
        }

        MetricsBatch batch = new MultiTextureMeshBatch(4096);
        return new Result(batch, shaderName);
    }

    private static Result createArrayBatchOrFallback(AtlasRuntimeService atlasRuntimeService,
                                                     GLCaps caps,
                                                     boolean hardRequireES3) {
        final boolean hasES3 = caps.hasES3;
        final boolean supportsTA = caps.supportsTextureArray();

        if (!hasES3 || !supportsTA) {
            if (hardRequireES3) {
                throw new IllegalStateException("Texture array mode requested but not supported on this platform.");
            }
            if (caps.maxTextureUnits() >= 8) {
                return createMultiBatchOrFallback(caps);
            }
            return createSimpleBatch();
        }

        String shaderName = ShaderMode.TEXTURE_ARRAY.defaultShaderName();

        if (ShaderRegistry.get(shaderName) == null) {
            Gdx.app.log("BatchFactory", "ARRAY fallback -> MULTI/SIMPLE (" + shaderName + " missing)");
            if (caps.maxTextureUnits() >= 8) {
                return createMultiBatchOrFallback(caps);
            }
            return createSimpleBatch();
        }

        MetricsBatch batch = new TextureArrayMeshBatch(4096);
        return new Result(batch, shaderName);
    }
}