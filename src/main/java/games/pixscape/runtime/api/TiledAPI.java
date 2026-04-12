package games.pixscape.runtime.api;

public interface TiledAPI {
    TiledLayerRef ofEntityId(int entityId);
    TiledLayerRef ofStableId(long stableId);
    TiledLayerRef requireEntityId(int entityId);
    TiledLayerRef requireStableId(long stableId);

    TiledAnimationsAPI animations();
}
