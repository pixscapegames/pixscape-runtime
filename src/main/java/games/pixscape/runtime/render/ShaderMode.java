package games.pixscape.runtime.render;

/** Render mode targeted by custom shader. */
public enum ShaderMode {
    /** Single texture 2D rendering mode (MeshBatch / sampler2D). */
    TEXTURE_2D("texture2d", "texture2d-default"),

    /** Multi-texture rendering mode (MultiTextureBatch / multiple sampler2D). */
    MULTI_TEXTURE("multi-texture", "multi-texture-default"),

    /** Texture array rendering mode (TextureArrayMeshBatch / sampler2DArray). */
    TEXTURE_ARRAY("texture-array", "texture-array-default");

    private final String shaderFileBaseName;
    private final String defaultShaderName;

    ShaderMode(String shaderFileBaseName, String defaultShaderName) {
        this.shaderFileBaseName = shaderFileBaseName;
        this.defaultShaderName = defaultShaderName;
    }

    public String shaderFileBaseName() {
        return shaderFileBaseName;
    }

    public String defaultShaderName() {
        return defaultShaderName;
    }
}