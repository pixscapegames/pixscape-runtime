package games.pixscape.runtime.api;

public interface AssetsAPI {
    AssetRegionRef region(String name);

    AssetRegionRef region(int assetId);

    boolean contains(String name);

    boolean contains(int assetId);
}
