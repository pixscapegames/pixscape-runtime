package games.pixscape.runtime.api;

/**
 * Runtime layer placement and local z-order controls for one entity.
 *
 * <p>Standalone entities and Game Object roots require existing layer and entity-index
 * components. A Game Object member requires only its own entity-index component: its Layer is
 * inherited from the top-level root and its z-index is local to the member.</p>
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
     * Returns {@code true} only when the captured entity is current and has the ordering
     * components required by its hierarchy role.
     * This reports capability presence, not current render submission or visibility.
     */
    boolean exists();

    /**
     * Returns the current scene layer index of the entity. A Game Object member reports its
     * effective top-level-root Layer.
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
     * @throws IllegalStateException when the entity is a Game Object member, because its
     * top-level root owns global layer placement
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
     * @throws IllegalStateException when the entity is a Game Object member, because its
     * top-level root owns global layer placement
     */
    RenderOrderFacade set(int layerIndex, int zIndex);
}
