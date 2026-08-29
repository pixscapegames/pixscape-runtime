package games.pixscape.runtime.tiled;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;

/** Activation-time validation for Tiled Map ownership. */
public final class TiledMapOwnership {
    private TiledMapOwnership() {
    }

    /**
     * Validates that every Map belongs to exactly one real Pixscape Layer.
     */
    public static void validateWorld(World world) {
        if (world == null) throw new IllegalArgumentException("World is required.");

        ComponentMapper<LayerComponent> layers = world.getMapper(LayerComponent.class);
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        IntIntMap layersByIndex = new IntIntMap();

        IntBag layerEntities = world.getAspectSubscriptionManager().get(
                Aspect.all(LayerComponent.class)).getEntities();
        int[] layerData = layerEntities.getData();
        for (int i = 0; i < layerEntities.size(); i++) {
            int entityId = layerData[i];
            LayerComponent layer = layers.get(entityId);
            if (layersByIndex.containsKey(layer.layerIndex)) {
                throw new IllegalArgumentException(
                        "Multiple Pixscape layers use layerIndex=" + layer.layerIndex + ".");
            }
            layersByIndex.put(layer.layerIndex, entityId);
        }

        IntBag mapEntities = world.getAspectSubscriptionManager().get(
                Aspect.all(TiledLayerComponent.class)).getEntities();
        int[] mapData = mapEntities.getData();
        for (int i = 0; i < mapEntities.size(); i++) {
            int entityId = mapData[i];
            if (layers.has(entityId)) {
                throw new IllegalArgumentException(
                        "Tiled map entity " + entityId + " must not also be a Pixscape layer.");
            }
            EntityIndexComponent index = indexes.getSafe(entityId, null);
            if (index == null) {
                throw new IllegalArgumentException(
                        "Tiled map entity " + entityId + " must have EntityIndexComponent.");
            }
            int layerIndex = index.layerIndex;
            if (!layersByIndex.containsKey(layerIndex)) {
                throw new IllegalArgumentException(
                        "Tiled map entity " + entityId
                                + " does not belong to a Pixscape layerIndex=" + layerIndex + ".");
            }
        }

    }
}
