package games.pixscape.runtime.gameobject;

import java.util.ArrayList;
import java.util.List;

/** Runtime-serializable real Game Object hierarchy. */
public final class GameObjectRuntimeFragment {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public String sourceAssetId = "";
    public int rootSourceEntityId = -1;
    public List<GameObjectAsset.GameObjectEntityData> entities =
            new ArrayList<GameObjectAsset.GameObjectEntityData>();
    public List<GameObjectAsset.GameObjectJointData> joints =
            new ArrayList<GameObjectAsset.GameObjectJointData>();

    public static GameObjectRuntimeFragment fromAsset(
            GameObjectAsset asset, String sourceAssetId) {
        if (asset == null) throw new IllegalArgumentException("Game Object asset must not be null.");
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.schemaVersion = CURRENT_SCHEMA_VERSION;
        fragment.sourceAssetId = sourceAssetId != null ? sourceAssetId : "";
        fragment.rootSourceEntityId = asset.rootSourceEntityId;
        fragment.entities.addAll(asset.entities);
        fragment.joints.addAll(asset.joints);
        return fragment;
    }

    public GameObjectAsset toAsset() {
        requireCurrentSchema(this);
        GameObjectAsset asset = new GameObjectAsset();
        asset.schemaVersion = GameObjectAsset.SCHEMA_VERSION;
        asset.rootSourceEntityId = rootSourceEntityId;
        asset.entities.addAll(entities);
        asset.joints.addAll(joints);
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
        if (fragment.entities == null || fragment.joints == null) {
            throw new IllegalArgumentException("Runtime Game Object fragment entities and joints must not be null.");
        }
    }
}
