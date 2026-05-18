package games.pixscape.runtime.api;

public interface SpritesAPI {
    SpriteRef spawn(int assetId, float x, float y);

    SpriteRef spawn(String name, float x, float y);
}
