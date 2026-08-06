package games.pixscape.runtime.api;

/**
 * Runtime layer placement and local z-order controls for one entity.
 *
 * <p>Layer names refer to loaded scene layers. Unknown or ambiguous names fail;
 * use {@link #layerIndex(int)} when duplicate layer names are intentional. The
 * z-index is optional: changing the layer preserves the current z-index, and
 * changing the z-index preserves the current layer. {@code set(...)} changes
 * both values as one operation.</p>
 *
 * <p>For example:</p>
 * <pre>{@code
 * entity.renderOrder().layer("Effects");
 * entity.renderOrder().layer("Effects").zIndex(10);
 * }</pre>
 *
 * <p>Spatial actors remain subject to the scene's Spatial ordering rules. These
 * controls do not add or change Spatial participation.</p>
 */
public interface RenderOrderFacade {

    /**
     * Returns the current scene layer index of the entity.
     */
    int layerIndex();

    /**
     * Returns the current local z-index of the entity.
     */
    int zIndex();

    /**
     * Moves the entity to an existing scene layer by index.
     */
    RenderOrderFacade layerIndex(int layerIndex);

    /**
     * Moves the entity to an existing scene layer by name.
     */
    RenderOrderFacade layer(String layerName);

    /**
     * Changes the local z-index inside the current layer.
     */
    RenderOrderFacade zIndex(int zIndex);

    /**
     * Changes layer and z-index as one public operation.
     */
    RenderOrderFacade set(int layerIndex, int zIndex);

    /**
     * Changes layer and z-index as one public operation.
     */
    RenderOrderFacade set(String layerName, int zIndex);
}
