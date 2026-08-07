package games.pixscape.runtime.api;

/**
 * Runtime layer placement and local z-order controls for one entity.
 *
 * <p>Operations require existing layer and entity-index components and never
 * create render-order capability.</p>
 *
 * <p>Layer indices refer to exported scene layers. Changing the layer preserves
 * the current z-index, changing the z-index preserves the current layer, and
 * {@link #set(int, int)} changes both values as one operation. The supported
 * z-index range is {@code -32768} through {@code 32767}, inclusive.</p>
 *
 * <p>For example:</p>
 * <pre>{@code
 * entity.renderOrder().layerIndex(4);
 * entity.renderOrder().layerIndex(4).zIndex(10);
 * entity.renderOrder().set(4, 10);
 * }</pre>
 *
 * <p>Spatial actors remain subject to the scene's Spatial ordering rules. These
 * controls do not add or change Spatial participation.</p>
 */
public interface RenderOrderFacade {

    /**
     * Returns whether both authored layer and entity-index ordering components exist.
     * This reports capability presence, not current render submission or visibility.
     */
    boolean exists();

    /**
     * Returns the current scene layer index of the entity.
     *
     * @throws IllegalStateException when {@link #exists()} is {@code false}
     */
    int layerIndex();

    /**
     * Returns the current local z-index of the entity.
     *
     * @throws IllegalStateException when {@link #exists()} is {@code false}
     */
    int zIndex();

    /**
     * Moves the entity to an existing exported scene layer by index.
     */
    RenderOrderFacade layerIndex(int layerIndex);

    /**
     * Changes the local z-index inside the current layer. Valid values are
     * {@code -32768} through {@code 32767}, inclusive.
     */
    RenderOrderFacade zIndex(int zIndex);

    /**
     * Changes layer and z-index as one operation. Valid z-index values are
     * {@code -32768} through {@code 32767}, inclusive.
     */
    RenderOrderFacade set(int layerIndex, int zIndex);
}
