package games.pixscape.runtime.api;

/**
 * Float-only shader parameter facade for v1.
 */
public interface ShaderFacade {
    String shader();

    ShaderFacade use(String shaderName);
    ShaderFacade clear();

    ShaderFacade setFloat(String uniform, float value);
    float getFloat(String uniform, float defaultValue);
    boolean hasFloat(String uniform);
    ShaderFacade removeFloat(String uniform);
    ShaderFacade clearFloats();
}
