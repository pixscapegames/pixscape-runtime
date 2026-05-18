package games.pixscape.runtime.api;

public interface AnimationsAPI {
    AnimationRef spawn(int assetId, float x, float y);

    AnimationRef spawn(String name, float x, float y);

    AnimationFacade get(EntityRef entity);
}
