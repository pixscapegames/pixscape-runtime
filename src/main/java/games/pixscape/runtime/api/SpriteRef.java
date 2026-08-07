package games.pixscape.runtime.api;

/**
 * Sprite handle bound to one entity incarnation and Runtime World.
 * It becomes inert if that entity is removed or the World is replaced.
 */
public interface SpriteRef {
    int entityId();

    EntityRef entity();

    TransformFacade transform();

    SpriteFacade sprite();

    ShaderFacade shader();

    SpriteRef position(float x, float y);

    SpriteRef scale(float scale);

    SpriteRef scale(float sx, float sy);

    SpriteRef rotationRad(float radians);

    SpriteRef tint(float r, float g, float b, float a);

    SpriteRef alpha(float alpha);

    SpriteRef shader(String shaderName);

    void remove();
}
