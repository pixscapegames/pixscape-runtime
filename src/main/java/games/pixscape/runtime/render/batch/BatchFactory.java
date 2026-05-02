package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.render.RenderSettings;
import games.pixscape.runtime.render.RenderSettings.RenderMode;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.AtlasRuntimeService;

/**
 * Creates runtime rendering batches and selects a compatible default shader.
 */
public final class BatchFactory {

    public static final class Result {
        public final MetricsBatch batch;
        /** Default shader assigned to newly created materials. */
        public final String defaultShaderName;
        private Result(MetricsBatch b, String name){
            this.batch = b;
            this.defaultShaderName = name;
        }
    }

    /**
     * Convenience overload that detects {@link GLCaps} from the active context.
     *
     * @param atlasRuntimeService atlas runtime service used by texture-array mode
     * @param settings runtime render mode configuration
     * @return created batch and default shader name for new materials
     */
    public static Result create(AtlasRuntimeService atlasRuntimeService, RenderSettings settings) {
        return create(atlasRuntimeService, settings, GLCaps.detect());
    }

    /**
     * Creates a batch using the requested render mode and GPU capabilities.
     *
     * @param atlasRuntimeService atlas runtime service used when texture arrays are selected
     * @param settings render settings defining the preferred batch strategy
     * @param caps detected GPU capability snapshot
     * @return created batch and default shader name for new materials
     */
    public static Result create(AtlasRuntimeService atlasRuntimeService, RenderSettings settings, GLCaps caps) {
        final boolean hasES3     = caps.hasES3;
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
                // AUTO: choose the best compromise based on GPU capabilities.
                if (hasES3 && supportsTA) {
                    return createArrayBatchOrFallback(atlasRuntimeService, caps, /*requireES3*/ false);
                }
                if (caps.maxTextureUnits() >= 8) {
                    return createMultiBatchOrFallback(caps);
                }
                return createSimpleBatch();
        }
    }

    // ------------------------------------------------------------------------
    // Branch implementation details
    // ------------------------------------------------------------------------

    private static Result createSimpleBatch() {
        MetricsBatch batch = new MeshBatch(4096);

        if (ShaderRegistry.get("default") == null) {
            throw new IllegalStateException("Missing shader 'default'. ShaderRegistry is not initialized correctly.");
        }
        return new Result(batch, "default");
    }

    private static Result createMultiBatchOrFallback(GLCaps caps) {
        if (caps.maxTextureUnits() < 4) {
            Gdx.app.log("BatchFactory", "MULTI fallback -> SIMPLE (units<4)");
            return createSimpleBatch();
        }

        // If mt_default is unavailable, fallback to SIMPLE.
        if (ShaderRegistry.get("mt_default") == null) {
            Gdx.app.log("BatchFactory", "MULTI fallback -> SIMPLE (mt_default missing)");
            return createSimpleBatch();
        }

        MetricsBatch batch = new MultiTextureMeshBatch(4096);
        return new Result(batch, "mt_default");
    }


    private static Result createArrayBatchOrFallback(AtlasRuntimeService atlasRuntimeService,
                                                     GLCaps caps,
                                                     boolean hardRequireES3) {
        final boolean hasES3     = caps.hasES3;
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

        // If the texture-array shader is unavailable, fallback to MULTI or SIMPLE.
        if (ShaderRegistry.get("ta_default") == null) {
            if (caps.maxTextureUnits() >= 8) {
                return createMultiBatchOrFallback(caps);
            }
            return createSimpleBatch();
        }

        MetricsBatch batch = new TextureArrayMeshBatch(4096);
        return new Result(batch, "ta_default");
    }

}
