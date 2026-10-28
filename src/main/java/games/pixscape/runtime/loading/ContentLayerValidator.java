package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;

/** Checks the scene boundary between Layer entities and indexed content. */
public final class ContentLayerValidator {
    private ContentLayerValidator() { }

    public static void validateWorld(World world, String source) {
        IntBag invalid = world.getAspectSubscriptionManager().get(
                Aspect.all(EntityIndexComponent.class, LayerComponent.class)).getEntities();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(source + " entityId=" + invalid.get(0)
                    + " has both EntityIndexComponent and LayerComponent; "
                    + "LayerComponent belongs only to a scene Layer.");
        }
    }

    public static void validateSerializedScene(String serialized, String source) {
        JsonValue root = new JsonReader().parse(serialized);
        JsonValue identifiers = root.get("componentIdentifiers");
        String layerName = componentName(identifiers, LayerComponent.class.getName(), "LayerComponent");
        String indexName = componentName(identifiers, EntityIndexComponent.class.getName(), "EntityIndexComponent");
        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return;
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            JsonValue components = entity.get("components");
            if (components == null || !components.isObject()) continue;
            JsonValue layer = components.get(layerName);
            if (layer != null && layer.isObject() && layer.has("type")) {
                throw new IllegalArgumentException(source + " entity=" + entity.name
                        + " uses an obsolete schema-3 LayerComponent representation: "
                        + "field 'type' is unsupported.");
            }
            if (layer != null && components.has(indexName)) {
                throw new IllegalArgumentException(source + " entity=" + entity.name
                        + " has both EntityIndexComponent and LayerComponent; "
                        + "LayerComponent belongs only to a scene Layer.");
            }
        }
    }

    private static String componentName(JsonValue identifiers, String className, String fallback) {
        JsonValue identifier = identifiers != null ? identifiers.get(className) : null;
        return identifier != null ? identifier.asString() : fallback;
    }
}
