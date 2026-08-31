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
}
