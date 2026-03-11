package games.pixscape.runtime.render;

/** Mode de rendu ciblé par le shader custom. */
public enum ShaderMode {
    /** Sprite simple (MeshBatch / 1 texture). */
    SPRITE,

    /** Multi-textures (MultiTextureBatch). */
    MULTI_TEXTURE,

    /** Texture array (TextureArrayMeshBatch). */
    TEXTURE_ARRAY;

    public static String dirNameForMode(ShaderMode mode) {
        switch (mode) {
            case SPRITE:         return "sprite";
            case MULTI_TEXTURE:  return "mt_sprite";
            case TEXTURE_ARRAY:  return "ta_sprite";
            default:             throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }
}
