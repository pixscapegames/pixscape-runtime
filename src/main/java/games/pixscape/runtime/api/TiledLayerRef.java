package games.pixscape.runtime.api;

public interface TiledLayerRef {
    int entityId();
    long stableId();
    boolean exists();

    TiledMapFacade map();
    TileEditFacade tiles();
    TileAnimationControlFacade tileAnimations();
}
