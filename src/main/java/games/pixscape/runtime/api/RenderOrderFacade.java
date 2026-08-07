package games.pixscape.runtime.api;

/**
 * Runtime layer placement and local z-order controls for one entity.
 *
 * <p>Operations require existing layer and entity-index components and never
 * create render-order capability.</p>
 *
 * <p>When the entity is stale or either required component is missing,
 * {@link #exists()} returns {@code false}, getters return their documented safe
 * defaults, and setters have no effect. Invalid input still throws when the
 * complete capability exists.</p>
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
     * Returns {@code true} only when the captured entity is current and both authored layer and
     * entity-index ordering components exist.
     * This reports capability presence, not current render submission or visibility.
     */
    boolean exists();

    /**
     * Returns the current scene layer index of the entity.
     *
     * @return the current layer index, or {@code -1} when {@link #exists()} is false
     */
    int layerIndex();

    /**
     * Returns the current local z-index of the entity.
     *
     * @return the current z-index, or {@code 0} when {@link #exists()} is false
     */
    int zIndex();

    /**
     * Moves the entity to an existing exported scene layer by index.
     * Missing or stale capability is inert.
     *
     * @throws IllegalArgumentException when the complete capability exists and the requested
     * layer is missing or ambiguous, or the preserved z-index is outside the supported range
     */
    RenderOrderFacade layerIndex(int layerIndex);

    /**
     * Changes the local z-index inside the current layer. Valid values are
     * {@code -32768} through {@code 32767}, inclusive.
     * Missing or stale capability is inert.
     *
     * @throws IllegalArgumentException when the complete capability exists and the requested
     * z-index is outside the supported range
     */
    RenderOrderFacade zIndex(int zIndex);

    /**
     * Changes layer and z-index as one operation. Valid z-index values are
     * {@code -32768} through {@code 32767}, inclusive.
     * Missing or stale capability is inert.
     *
     * @throws IllegalArgumentException when the complete capability exists and the requested
     * layer is missing or ambiguous, or the z-index is outside the supported range
     */
    RenderOrderFacade set(int layerIndex, int zIndex);
}
