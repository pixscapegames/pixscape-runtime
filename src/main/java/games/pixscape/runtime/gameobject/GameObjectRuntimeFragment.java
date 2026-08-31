package games.pixscape.runtime.gameobject;

import java.util.ArrayList;
import java.util.List;

/** Runtime-serializable real Game Object hierarchy. */
public final class GameObjectRuntimeFragment {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public String sourceAssetId = "";
    public int rootSourceEntityId = -1;
    public List<GameObjectAsset.GameObjectEntityData> entities =
            new ArrayList<GameObjectAsset.GameObjectEntityData>();

    public static GameObjectRuntimeFragment fromAsset(
            GameObjectAsset asset, String sourceAssetId) {
        if (asset == null) throw new IllegalArgumentException("Game Object asset must not be null.");
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.sourceAssetId = sourceAssetId != null ? sourceAssetId : "";
        fragment.rootSourceEntityId = asset.rootSourceEntityId;
        fragment.entities.addAll(asset.entities);
        return fragment;
    }

    public GameObjectAsset toAsset() {
        requireCurrentSchema(this);
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = rootSourceEntityId;
        asset.entities.addAll(entities);
        return asset;
    }

    public static void requireCurrentSchema(GameObjectRuntimeFragment fragment) {
        if (fragment == null) {
            throw new IllegalArgumentException("Runtime Game Object fragment must not be null.");
        }
        if (fragment.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Runtime Game Object fragment requires schemaVersion "
                            + CURRENT_SCHEMA_VERSION + ", found " + fragment.schemaVersion + ".");
        }
        if (fragment.entities == null) {
            throw new IllegalArgumentException("Runtime Game Object fragment entities must not be null.");
        }
    }
}
