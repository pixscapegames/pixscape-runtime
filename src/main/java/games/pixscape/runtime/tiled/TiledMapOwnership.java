package games.pixscape.runtime.tiled;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;

/** Transitional host-to-map ownership validation used while Tiled remains a layer type. */
public final class TiledMapOwnership {
    private TiledMapOwnership() {
    }

    /**
     * Validates the Stage 1 invariant: every Tiled host owns exactly one map and every map belongs
     * to one Tiled host. This is intentionally a cold activation-time traversal.
     */
    public static void validateTransitionalWorld(World world) {
        if (world == null) throw new IllegalArgumentException("World is required.");

        ComponentMapper<LayerComponent> layers = world.getMapper(LayerComponent.class);
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        IntIntMap tiledHostsByLayer = new IntIntMap();
        IntIntMap mapCountsByLayer = new IntIntMap();

        IntBag hostEntities = world.getAspectSubscriptionManager().get(
                Aspect.all(LayerComponent.class).exclude(EntityIndexComponent.class)).getEntities();
        int[] hostData = hostEntities.getData();
        for (int i = 0; i < hostEntities.size(); i++) {
            int entityId = hostData[i];
            LayerComponent layer = layers.get(entityId);
            if (layer.type != LayerComponent.TYPE_TILED) continue;
            if (tiledHostsByLayer.containsKey(layer.layerIndex)) {
                throw new IllegalArgumentException(
                        "Multiple TYPE_TILED hosts use layerIndex=" + layer.layerIndex + ".");
            }
            tiledHostsByLayer.put(layer.layerIndex, entityId);
        }

        IntBag mapEntities = world.getAspectSubscriptionManager().get(
                Aspect.all(TiledLayerComponent.class, EntityIndexComponent.class)
                        .exclude(LayerComponent.class)).getEntities();
        int[] mapData = mapEntities.getData();
        for (int i = 0; i < mapEntities.size(); i++) {
            int entityId = mapData[i];
            int layerIndex = indexes.get(entityId).layerIndex;
            if (!tiledHostsByLayer.containsKey(layerIndex)) {
                throw new IllegalArgumentException(
                        "Tiled map entity " + entityId
                                + " does not belong to a TYPE_TILED host layerIndex=" + layerIndex + ".");
            }
            mapCountsByLayer.put(layerIndex, mapCountsByLayer.get(layerIndex, 0) + 1);
        }

        for (IntIntMap.Entry host : tiledHostsByLayer.entries()) {
            int count = mapCountsByLayer.get(host.key, 0);
            if (count != 1) {
                throw new IllegalArgumentException(
                        "TYPE_TILED host entity " + host.value + " at layerIndex=" + host.key
                                + " must own exactly one Tiled map, found " + count + ".");
            }
        }

        IntBag misplacedMaps = world.getAspectSubscriptionManager().get(
                Aspect.all(TiledLayerComponent.class).exclude(EntityIndexComponent.class)).getEntities();
        if (misplacedMaps.size() > 0) {
            throw new IllegalArgumentException(
                    "TiledLayerComponent must be owned by a Tiled map entity with EntityIndexComponent.");
        }
    }
}
