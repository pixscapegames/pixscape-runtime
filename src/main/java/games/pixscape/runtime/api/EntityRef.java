package games.pixscape.runtime.api;

import com.badlogic.gdx.utils.Array;

/**
 * Entity handle that bridges stableId identity and runtime ECS access.
 *
 * <p>The handle is bound to the entity incarnation and Runtime World in which
 * it was resolved. If that entity is removed or the World is replaced, the
 * handle becomes inert and never retargets a recycled {@code entityId}.</p>
 *
 * <p>Facades obtained from this reference remain bound to the same captured entity. When stale,
 * their queries return the safe absence/default values documented by each facade and mutations
 * have no effect. {@link #entityId()} still reports the captured short-lived ID for diagnostics;
 * A stable identity is optional; use {@link #exists()} to check reference validity.</p>
 */
public interface EntityRef {
    /**
     * Runtime ECS-oriented entity identifier (may be short-lived/recycled).
     */
    int entityId();

    /**
     * Returns the assigned persistent/public identity, or {@code -1} if no identity
     * has been assigned or this reference is stale. A live entity may have no stable identity.
     * This is a read-only lookup: it does not create an identity component, assign an ID,
     * or advance the allocator. Use {@link EntitiesAPI#ensureStableId(int)} to assign one explicitly.
     *
     * <p>You should use {@link #exists() ref.exists()} to check whether the referenced entity still exists.
     * Do not infer reference validity from the stable ID.</p>
     */
    int stableId();

    /**
     * Returns whether the same captured entity still exists in the same Runtime World.
     */
    boolean exists();

    /**
     * Current published composition parent, or null for a root, stale reference or absent topology.
     * O(1); creates an incarnation-bound reference when present, never an identity/component.
     * Prepared spawn hierarchies are readable immediately; published reparenting is reflected
     * immediately. Scheduled deletion remains visible until normal publication. No synchronization
     * or physics step is performed. Structural Tiled ownership (map to layers) is excluded;
     * ordinary objects may still have their supported Tiled object layer as parent.
     */
    EntityRef parent();

    /** Independent snapshot; see {@link #children(Array)} for traversal and allocation rules. */
    default Array<EntityRef> children() {
        return children(new Array<EntityRef>());
    }

    /**
     * Clears and fills the caller's container with direct composition children, returning out.
     * O(number of children), plus clearing the previous out entries, using published sibling links;
     * no World scan or sort. Sibling order
     * is topology order, not render/z order, and may change on publication/rebuild. The container
     * is not retained. It may grow and each returned EntityRef/handle is newly captured; this is
     * not a zero-allocation API. No intermediate snapshot is allocated. Stale references or
     * absent topology produce an empty result. Tiled map/layer ownership is excluded; an object
     * layer can expose its ordinary objects. Publication rules match {@link #parent()}.
     * @throws IllegalArgumentException if out is null
     */
    Array<EntityRef> children(Array<EntityRef> out);

    /** Independent snapshot; see {@link #descendants(Array)}. */
    default Array<EntityRef> descendants() {
        return descendants(new Array<EntityRef>());
    }

    /**
     * Clears and fills out with composition descendants, excluding this entity. Depth-first,
     * parent-before-child traversal in current sibling-link order; not render order. O(subtree
     * size), plus clearing the previous out entries, without recursion, a temporary collection
     * or visits to unrelated subtrees.
     * Other ownership, publication, absence and allocation rules match {@link #children(Array)}.
     */
    Array<EntityRef> descendants(Array<EntityRef> out);

    TransformFacade transform();

    SpriteFacade sprite();

    /**
     * Authored local-space corner deformation for this entity's sprite capability.
     */
    QuadDeformFacade quadDeform();

    /**
     * Read-only authored local rectangle, polygon or polyline geometry.
     */
    AuthoredGeometryFacade geometry();

    AnimationFacade animation();

    ParticleFacade particles();

    ShaderFacade shader();

    LightFacade light();

    /**
     * Returns a non-null, read-only view of this entity's custom properties.
     *
     * <p>An entity without custom properties, or a stale entity reference, behaves like an empty
     * property set.</p>
     */
    CustomProperties properties();

    /**
     * Spatial render-order settings for this entity.
     */
    SpatialEntityFacade spatial();

    /**
     * Layer placement and local z-order controls for this entity.
     *
     * <p>For example, {@code entity.renderOrder().layerIndex(4)} preserves the
     * current z-index, while chaining {@code .zIndex(10)} changes it.</p>
     */
    RenderOrderFacade renderOrder();

    /**
     * Schedules removal only while this handle still identifies its captured entity.
     * A standalone entity removes itself; a hierarchy node removes its surviving descendant
     * subtree. Physics joints made invalid by removed Bodies or source joints are removed too.
     * Calling this method on a stale handle has no effect.
     */
    void remove();

    /**
     * Expert ECS escape hatch.
     *
     * <p>Use this for low-level operations while keeping regular usage in the high-level API.</p>
     */
    ECSAPI ecs();
}
