package games.pixscape.runtime.api;

/**
 * Shader assignment and float uniform access for v1.
 *
 * <p>v1 supports float uniforms only: no vectors, matrices, textures, or generic typed uniforms.</p>
 * <p>Uniforms are applied only when supported by the active shader/backend.</p>
 */
public interface ShaderFacade {
    String shader();

    /**
     * Selects a shader by logical name.
     */
    ShaderFacade use(String shaderName);

    ShaderFacade clear();

    ShaderFacade setFloat(String uniform, float value);

    float getFloat(String uniform, float defaultValue);

    boolean hasFloat(String uniform);

    ShaderFacade removeFloat(String uniform);

    ShaderFacade clearFloats();
}
