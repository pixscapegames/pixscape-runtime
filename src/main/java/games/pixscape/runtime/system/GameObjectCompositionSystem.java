package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.hierarchy.GameObjectCompositionState;
import games.pixscape.runtime.hierarchy.GameObjectTopologyState;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.SortKey64;

import java.util.Arrays;

/** Resolves effective layer, inherited visibility, local child order, and derived root bounds. */
public final class GameObjectCompositionSystem extends BaseSystem {
    private final DynamicEntityRenderState renderState;
    private final GameObjectCompositionState state;
    private GameObjectHierarchySystem hierarchy;

    private ComponentMapper<EntityIndexComponent> entityIndices;
    private ComponentMapper<PixscapeIdentityComponent> identities;
    private ComponentMapper<VisibilityComponent> visibility;
    private ComponentMapper<GameObjectComponent> gameObjects;
    private ComponentMapper<AABBComponent> bounds;

    private int[] orderA = new int[16];
    private int[] orderB = new int[16];
    private int[] observedParent = new int[0], observedFirst = new int[0], observedNext = new int[0];
    private int[] observedZ = new int[0], observedStableId = new int[0];
    private boolean[] orderPending = new boolean[0];
    private final com.badlogic.gdx.utils.IntArray pendingParents = new com.badlogic.gdx.utils.IntArray(false, 16);
    private transient Runnable keyVisitObserver;

    void setKeyVisitObserver(Runnable observer) { keyVisitObserver = observer; }

    public GameObjectCompositionSystem(DynamicEntityRenderState renderState) {
        this(renderState, 16);
    }

    public GameObjectCompositionSystem(DynamicEntityRenderState renderState, int initialCapacity) {
        this.renderState = renderState;
        this.state = new GameObjectCompositionState(initialCapacity);
    }

    @Override
    protected void initialize() {
        hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        if (hierarchy == null) {
            throw new IllegalStateException("GameObjectCompositionSystem requires GameObjectHierarchySystem.");
        }
    }

    @Override
    protected void processSystem() {
        GameObjectTopologyState topology = hierarchy.topology();
        for (int entityId = topology.firstTraversalEntityId; entityId >= 0;
             entityId = topology.nextTraversalEntityId[entityId]) {
            state.ensureEntityCapacity(entityId);
            state.effectiveLayer[entityId] = -1;
            state.hierarchyVisible[entityId] = true;
            observeOrderInputs(entityId, topology);
            state.boundsResolved[entityId] = false;
        }

        resolveLayerAndVisibility(topology);
        rebuildLocalOrder(topology);
        rebuildDerivedBounds(topology);
    }

    public void prepareRuntimeAvailability() {
        processSystem();
    }

    public GameObjectCompositionState state() {
        return state;
    }

    /** Returns whether the entity currently contributes drawable bounds to its Game Object. */
    public boolean contributesDrawableBounds(int entityId) {
        if (entityId < 0 || entityId >= state.hierarchyVisible.length
                || gameObjects.has(entityId) || !state.hierarchyVisible[entityId]) {
            return false;
        }
        int slot = renderState != null
                ? renderState.renderSlotForEntity(entityId) : DynamicEntityRenderState.NO_SLOT;
        return slot != DynamicEntityRenderState.NO_SLOT
                && renderState.enabled[slot]
                && bounds.getSafe(entityId, null) != null;
    }

    private void resolveLayerAndVisibility(GameObjectTopologyState topology) {
        for (int entityId = topology.firstTraversalEntityId; entityId >= 0;
             entityId = topology.nextTraversalEntityId[entityId]) {
            EntityIndexComponent ownIndex = entityIndices.getSafe(entityId, null);
            int parentEntityId = topology.parentEntityId[entityId];
            if (parentEntityId >= 0) {
                int rootEntityId = topology.rootEntityId[entityId];
                EntityIndexComponent rootIndex = entityIndices.getSafe(rootEntityId, null);
                state.effectiveLayer[entityId] = rootIndex != null ? rootIndex.layerIndex : -1;
                state.hierarchyVisible[entityId] = state.hierarchyVisible[parentEntityId]
                        && authoredVisible(entityId);
            } else {
                state.effectiveLayer[entityId] = ownIndex != null ? ownIndex.layerIndex : -1;
                state.hierarchyVisible[entityId] = authoredVisible(entityId);
            }

            if (renderState != null) {
                int slot = renderState.renderSlotForEntity(entityId);
                if (slot != DynamicEntityRenderState.NO_SLOT) {
                    renderState.layerIndex[slot] = state.effectiveLayer[entityId];
                }
            }
        }
    }

    // Observe direct authored mutations in the traversal already needed for frame composition.
    // No second source of authored order and no additional World traversal are introduced.
    private void observeOrderInputs(int entity, GameObjectTopologyState topology) {
        ensureObservedCapacity(entity + 1);
        int parent = topology.parentEntityId[entity];
        int first = topology.firstChildEntityId[entity];
        int next = topology.nextSiblingEntityId[entity];
        EntityIndexComponent index = entityIndices.getSafe(entity, null);
        PixscapeIdentityComponent identity = identities.getSafe(entity, null);
        int z = index != null ? index.zIndex : 0;
        int stable = identity != null ? identity.stableId : 0;
        if (observedFirst[entity] != first) markParent(entity);
        if (observedParent[entity] != parent || observedNext[entity] != next
                || observedZ[entity] != z || observedStableId[entity] != stable) {
            markParent(observedParent[entity]);
            markParent(parent);
        }
        observedParent[entity] = parent;
        observedFirst[entity] = first;
        observedNext[entity] = next;
        observedZ[entity] = z;
        observedStableId[entity] = stable;
    }

    private void markParent(int parent) {
        if (parent < 0) return;
        ensureObservedCapacity(parent + 1);
        if (orderPending[parent]) return;
        orderPending[parent] = true;
        pendingParents.add(parent);
    }

    private void ensureObservedCapacity(int required) {
        int old = observedParent.length;
        if (required <= old) return;
        int capacity = Math.max(required, Math.max(16, old * 2));
        observedParent = Arrays.copyOf(observedParent, capacity);
        observedFirst = Arrays.copyOf(observedFirst, capacity);
        observedNext = Arrays.copyOf(observedNext, capacity);
        observedZ = Arrays.copyOf(observedZ, capacity);
        observedStableId = Arrays.copyOf(observedStableId, capacity);
        orderPending = Arrays.copyOf(orderPending, capacity);
        Arrays.fill(observedParent, old, capacity, -1);
        Arrays.fill(observedFirst, old, capacity, -1);
        Arrays.fill(observedNext, old, capacity, -1);
    }

    private void rebuildLocalOrder(GameObjectTopologyState topology) {
        while (pendingParents.size > 0) {
            int parent = pendingParents.pop();
            orderPending[parent] = false;
            state.ensureEntityCapacity(parent);
            state.orderedFirstChildEntityId[parent] = -1;
            int count = 0;
            for (int child = topology.firstChildEntityId[parent]; child >= 0;
                 child = topology.nextSiblingEntityId[child]) {
                ensureOrderCapacity(count + 1);
                orderA[count++] = child;
            }
            // Stable bottom-up merge: bounded O(k log k) for this parent's k children,
            // without fixed radix-bucket work for every small sibling group.
            int[] source = orderA, target = orderB;
            for (int width = 1; width < count; width *= 2) {
                for (int start = 0; start < count; start += 2 * width) {
                    int middle = Math.min(start + width, count), end = Math.min(start + 2 * width, count);
                    int left = start, right = middle;
                    for (int out = start; out < end; out++) {
                        target[out] = right >= end || (left < middle && compareOrder(source[left], source[right]) <= 0)
                                ? source[left++] : source[right++];
                    }
                }
                int[] swap = source; source = target; target = swap;
            }
            for (int i = count - 1; i >= 0; i--) {
                int child = source[i];
                state.orderedNextSiblingEntityId[child] = state.orderedFirstChildEntityId[parent];
                state.orderedFirstChildEntityId[parent] = child;
            }
        }
    }

    private int compareOrder(int left, int right) {
        if (keyVisitObserver != null) { keyVisitObserver.run(); keyVisitObserver.run(); }
        // Preserve the former radix keys, including their unsigned 16/32-bit interpretation.
        int lz = (observedZ[left] - SortKey64.MIN_Z) & 0xffff;
        int rz = (observedZ[right] - SortKey64.MIN_Z) & 0xffff;
        if (lz != rz) return lz < rz ? -1 : 1;
        int li = observedStableId[left] ^ Integer.MIN_VALUE;
        int ri = observedStableId[right] ^ Integer.MIN_VALUE;
        return li == ri ? 0 : li < ri ? -1 : 1;
    }

    private void rebuildDerivedBounds(GameObjectTopologyState topology) {
        for (int entityId = topology.lastTraversalEntityId; entityId >= 0;
             entityId = topology.previousTraversalEntityId[entityId]) {
            int parentEntityId = topology.parentEntityId[entityId];
            if (gameObjects.has(entityId)) {
                if (parentEntityId >= 0 && state.hierarchyVisible[entityId]
                        && state.boundsResolved[entityId]) {
                    union(parentEntityId, state.minX[entityId], state.minY[entityId],
                            state.maxX[entityId], state.maxY[entityId]);
                }
                continue;
            }
            if (parentEntityId < 0 || !contributesDrawableBounds(entityId)) continue;
            AABBComponent aabb = bounds.getSafe(entityId, null);
            union(parentEntityId, aabb.minX, aabb.minY, aabb.maxX, aabb.maxY);
        }
    }

    private void union(int rootEntityId, float minX, float minY, float maxX, float maxY) {
        if (!state.boundsResolved[rootEntityId]) {
            state.boundsResolved[rootEntityId] = true;
            state.minX[rootEntityId] = minX;
            state.minY[rootEntityId] = minY;
            state.maxX[rootEntityId] = maxX;
            state.maxY[rootEntityId] = maxY;
            return;
        }
        state.minX[rootEntityId] = Math.min(state.minX[rootEntityId], minX);
        state.minY[rootEntityId] = Math.min(state.minY[rootEntityId], minY);
        state.maxX[rootEntityId] = Math.max(state.maxX[rootEntityId], maxX);
        state.maxY[rootEntityId] = Math.max(state.maxY[rootEntityId], maxY);
    }

    private boolean authoredVisible(int entityId) {
        VisibilityComponent component = visibility.getSafe(entityId, null);
        return component == null || component.visible;
    }

    private void ensureOrderCapacity(int required) {
        if (required <= orderA.length) return;
        int next = orderA.length;
        while (next < required) next <<= 1;
        orderA = Arrays.copyOf(orderA, next);
        orderB = Arrays.copyOf(orderB, next);
    }
}
