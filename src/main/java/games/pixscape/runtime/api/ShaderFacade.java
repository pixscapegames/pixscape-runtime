package games.pixscape.runtime.api;

/**
 * Shader assignment and float uniform access for v1.
 *
 * <p>Operations affect an existing render-material capability only. They do not
 * convert an arbitrary entity into a render capability.</p>
 *
 * <p>v1 supports float uniforms only: no vectors, matrices, textures, or generic typed uniforms.</p>
 * <p>Uniforms are applied only when supported by the active shader/backend.</p>
 */
public interface ShaderFacade {
    String shader();

    /**
     * Selects a shader by logical name.
     *
     * @throws IllegalArgumentException when the name is blank or unknown; the previous material
     * state remains unchanged
     */
    ShaderFacade use(String shaderName);

    ShaderFacade clear();

    /**
     * Sets a float uniform on the existing render capability.
     *
     * @throws IllegalArgumentException when the uniform name is blank, before parameter mutation
     */
    ShaderFacade setFloat(String uniform, float value);

    float getFloat(String uniform, float defaultValue);

    boolean hasFloat(String uniform);

    /**
     * Removes a float uniform from the existing render capability.
     *
     * @throws IllegalArgumentException when the uniform name is blank, before parameter mutation
     */
    ShaderFacade removeFloat(String uniform);

    ShaderFacade clearFloats();
}
