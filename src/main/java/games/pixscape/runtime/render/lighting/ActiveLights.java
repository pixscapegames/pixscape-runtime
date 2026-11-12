package games.pixscape.runtime.render.lighting;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;

/**
 * Authored light admission, independent of camera culling and light intensity.
 */
public final class ActiveLights {
    private ActiveLights() {
    }

    public static boolean isActive(boolean visible, boolean point, boolean pointEnabled,
                                   boolean cone, boolean coneEnabled) {
        return visible && (point || cone) && (!point || pointEnabled) && (!cone || coneEnabled);
    }

    public static boolean isActive(World world, int entity) {
        VisibilityComponent v = world.getMapper(VisibilityComponent.class).getSafe(entity, null);
        PointLightComponent p = world.getMapper(PointLightComponent.class).getSafe(entity, null);
        ConeLightComponent c = world.getMapper(ConeLightComponent.class).getSafe(entity, null);
        games.pixscape.runtime.system.GameObjectCompositionSystem composition = world.getSystem(games.pixscape.runtime.system.GameObjectCompositionSystem.class);
        games.pixscape.runtime.component.EntityIndexComponent index = world.getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).getSafe(entity, null);
        int layer = index == null ? -1 : index.layerIndex;
        if (composition != null && entity < composition.state().hierarchyVisible.length) {
            if (!composition.state().hierarchyVisible[entity]) return false;
            layer = composition.state().effectiveLayer[entity];
        }
        games.pixscape.runtime.system.LayerStateBuildSystem layers = world.getSystem(games.pixscape.runtime.system.LayerStateBuildSystem.class);
        if (layers != null && (layer < 0 || layer >= layers.state().capacity() || !layers.state().enabled[layer]))
            return false;
        return isActive(v == null || v.visible, p != null, p != null && p.enabled, c != null, c != null && c.enabled);
    }

    /**
     * Cold admission/command boundary only. Never called from per-frame submission or UI act.
     */
    public static int count(World world) {
        IntBag lights = world.getAspectSubscriptionManager().get(Aspect.one(PointLightComponent.class, ConeLightComponent.class)).getEntities();
        int count = 0;
        for (int i = 0; i < lights.size(); i++) if (isActive(world, lights.get(i))) count++;
        return count;
    }

    public static void validateForward(World world) {
        if (count(world) > 1) throw new LightingAdmissionException(
                "Forward rendering supports at most one active light. Switch to Legacy to activate another light.");
    }

    /**
     * Cold admission only; map projection is owned by each map, not by the scene or renderer.
     */
    public static boolean hasOrthogonalMaps(World world) {
        IntBag maps = world.getAspectSubscriptionManager().get(Aspect.all(
                games.pixscape.runtime.component.TiledLayerComponent.class)).getEntities();
        com.artemis.ComponentMapper<games.pixscape.runtime.component.TiledLayerComponent> mapper =
                world.getMapper(games.pixscape.runtime.component.TiledLayerComponent.class);
        for (int i = 0; i < maps.size(); i++) {
            games.pixscape.runtime.component.TiledLayerComponent map = mapper.get(maps.get(i));
            if ((map.data == null ? map.projection : map.data.projection) == games.pixscape.runtime.tiled.TiledProjection.ORTHO)
                return true;
        }
        return false;
    }

}
