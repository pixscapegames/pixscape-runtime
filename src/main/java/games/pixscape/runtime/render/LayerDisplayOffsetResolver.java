package games.pixscape.runtime.render;

import com.badlogic.gdx.math.Vector2;

/**
 * Derived display-space offsets shared by rendering and display-space culling.
 * Implementations are selected when a World is assembled; consumers do not
 * infer whether they run in Runtime or authoring.
 */
public interface LayerDisplayOffsetResolver {

    void resolveLayer(int layerIndex, Vector2 out);

    void resolvePhysics(Vector2 out);

    default void resolveEntity(com.artemis.World world, int entityId, int layerIndex, Vector2 out) {
        games.pixscape.runtime.system.GameObjectCompositionSystem composition =
                world.getSystem(games.pixscape.runtime.system.GameObjectCompositionSystem.class);
        if (composition != null && entityId >= 0 && entityId < composition.state().getEntityCapacity()) {
            int owner = composition.state().tiledLayerOwner[entityId];
            games.pixscape.runtime.component.LayerParallaxComponent parallax = owner >= 0
                    ? world.getMapper(games.pixscape.runtime.component.LayerParallaxComponent.class)
                            .getSafe(owner, null) : null;
            if (parallax != null) {
                resolveLocalParallax(layerIndex, parallax.factorX, parallax.factorY, out);
                return;
            }
        }
        resolveLayer(layerIndex, out);
    }

    default void resolveLocalParallax(int layerIndex, float factorX, float factorY, Vector2 out) {
        resolveLayer(layerIndex, out);
    }
}
