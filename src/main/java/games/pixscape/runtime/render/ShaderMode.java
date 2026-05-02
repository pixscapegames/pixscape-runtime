package games.pixscape.runtime.render;

/** Render mode targeted by custom shader. */
public enum ShaderMode {
    /** Single texture 2D rendering mode (MeshBatch / sampler2D). */
    TEXTURE_2D,

    /** Multi-texture rendering mode (MultiTextureBatch / multiple sampler2D). */
    MULTI_TEXTURE,

    /** Texture array rendering mode (TextureArrayMeshBatch / sampler2DArray). */
    TEXTURE_ARRAY;

    public static String dirNameForMode(ShaderMode mode) {
        switch (mode) {
            case TEXTURE_2D:         return "sprite";
            case MULTI_TEXTURE:  return "mt_sprite";
            case TEXTURE_ARRAY:  return "ta_sprite";
            default:             throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }
}
