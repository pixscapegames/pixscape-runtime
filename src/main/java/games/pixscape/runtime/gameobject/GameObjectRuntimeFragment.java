package games.pixscape.runtime.gameobject;

import com.artemis.io.SaveFileFormat;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.JsonValue;

public final class GameObjectRuntimeFragment extends SaveFileFormat {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;

    public GameObjectRuntimeFragment() {
        super();
    }

    public GameObjectRuntimeFragment(IntBag entities) {
        super(entities);
    }

    public static void requireCurrentSchema(GameObjectRuntimeFragment fragment) {
        if (fragment == null) {
            throw new IllegalArgumentException(
                    "Runtime Game Object fragment requires schemaVersion "
                            + CURRENT_SCHEMA_VERSION + ".");
        }
        validateSchemaVersion(fragment.schemaVersion);
    }

    public static void requireCurrentSchema(JsonValue root) {
        JsonValue value = root != null && root.isObject()
                ? root.get("schemaVersion")
                : null;
        if (value == null || !value.isLong()) {
            throw new IllegalArgumentException(
                    "Runtime Game Object fragment requires numeric schemaVersion "
                            + CURRENT_SCHEMA_VERSION + ".");
        }
        validateSchemaVersion(value.asInt());
    }

    private static void validateSchemaVersion(int schemaVersion) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Runtime Game Object fragment requires schemaVersion "
                            + CURRENT_SCHEMA_VERSION + ", found "
                            + schemaVersion + ".");
        }
    }
}
