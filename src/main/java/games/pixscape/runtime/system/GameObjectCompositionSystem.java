package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.VisibilityComponent;
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
    private final int[] radixCounts = new int[256];

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
        IntArray traversal = topology.traversal;
        for (int i = 0, n = traversal.size; i < n; i++) {
            int entityId = traversal.get(i);
            state.ensureEntityCapacity(entityId);
            state.effectiveLayer[entityId] = -1;
            state.hierarchyVisible[entityId] = true;
            state.orderedFirstChildEntityId[entityId] = -1;
            state.orderedNextSiblingEntityId[entityId] = -1;
            state.boundsResolved[entityId] = false;
        }

        resolveLayerAndVisibility(topology, traversal);
        rebuildLocalOrder(topology, traversal);
        rebuildDerivedBounds(topology, traversal);
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

    private void resolveLayerAndVisibility(GameObjectTopologyState topology, IntArray traversal) {
        for (int i = 0, n = traversal.size; i < n; i++) {
            int entityId = traversal.get(i);
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

    private void rebuildLocalOrder(GameObjectTopologyState topology, IntArray traversal) {
        int memberCount = 0;
        ensureOrderCapacity(traversal.size);
        for (int i = 0, n = traversal.size; i < n; i++) {
            int entityId = traversal.get(i);
            if (topology.parented[entityId]) orderA[memberCount++] = entityId;
        }
        if (memberCount == 0) return;

        int[] source = orderA;
        int[] target = orderB;
        for (int pass = 0; pass < 10; pass++) {
            Arrays.fill(radixCounts, 0);
            int shift = pass < 4 ? pass * 8 : pass < 6 ? (pass - 4) * 8 : (pass - 6) * 8;
            for (int i = 0; i < memberCount; i++) {
                radixCounts[(keyByte(source[i], topology, pass, shift))]++;
            }
            int sum = 0;
            for (int bucket = 0; bucket < 256; bucket++) {
                int count = radixCounts[bucket];
                radixCounts[bucket] = sum;
                sum += count;
            }
            for (int i = 0; i < memberCount; i++) {
                int entityId = source[i];
                target[radixCounts[keyByte(entityId, topology, pass, shift)]++] = entityId;
            }
            int[] swap = source;
            source = target;
            target = swap;
        }

        for (int i = memberCount - 1; i >= 0; i--) {
            int entityId = source[i];
            int parentEntityId = topology.parentEntityId[entityId];
            state.orderedNextSiblingEntityId[entityId] =
                    state.orderedFirstChildEntityId[parentEntityId];
            state.orderedFirstChildEntityId[parentEntityId] = entityId;
        }
    }

    private int keyByte(int entityId, GameObjectTopologyState topology, int pass, int shift) {
        int key;
        if (pass < 4) {
            PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
            key = identity != null ? identity.stableId : 0;
        } else if (pass < 6) {
            EntityIndexComponent index = entityIndices.getSafe(entityId, null);
            int z = index != null ? index.zIndex : 0;
            key = z - SortKey64.MIN_Z;
        } else {
            PixscapeIdentityComponent parentIdentity = identities.getSafe(
                    topology.parentEntityId[entityId], null);
            key = parentIdentity != null ? parentIdentity.stableId : 0;
        }
        return (key >>> shift) & 0xff;
    }

    private void rebuildDerivedBounds(GameObjectTopologyState topology, IntArray traversal) {
        for (int i = traversal.size - 1; i >= 0; i--) {
            int entityId = traversal.get(i);
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
