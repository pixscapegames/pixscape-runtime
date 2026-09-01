package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.GameObjectHierarchyValidator;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.hierarchy.GameObjectTopologyState;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.IdentityRegistry;

import java.util.Arrays;

/** Owns the single Runtime topology and derived world-transform authority. */
public final class GameObjectHierarchySystem extends BaseSystem {
    private static final int MIN_CAPACITY = 16;

    private final GameObjectTopologyState topology;
    private final WorldTransformState worldTransforms;
    private final IntArray stack = new IntArray(false, 32);
    private final IntArray children = new IntArray(false, 16);

    private ComponentMapper<TransformComponent> transforms;
    private ComponentMapper<GameObjectComponent> gameObjects;
    private ComponentMapper<GameObjectMemberComponent> members;
    private ComponentMapper<PixscapeIdentityComponent> identities;
    private DirtyTrackerSystem dirty;
    private EntitySubscription transformSubscription;
    private EntitySubscription memberSubscription;
    private EntitySubscription gameObjectSubscription;
    private EntitySubscription identitySubscription;

    private int[] observedParentStableId = new int[MIN_CAPACITY];
    private int[] observedStableId = new int[MIN_CAPACITY];
    private long[] sortKeys = new long[MIN_CAPACITY];
    private boolean topologyDirty = true;
    private int rebuildCount;

    public GameObjectHierarchySystem() {
        this(MIN_CAPACITY);
    }

    public GameObjectHierarchySystem(int initialCapacity) {
        int capacity = Math.max(MIN_CAPACITY, initialCapacity);
        topology = new GameObjectTopologyState(capacity);
        worldTransforms = new WorldTransformState(capacity);
        observedParentStableId = new int[capacity];
        observedStableId = new int[capacity];
        sortKeys = new long[capacity];
        Arrays.fill(observedParentStableId, -1);
        Arrays.fill(observedStableId, -1);
    }

    @Override
    protected void initialize() {
        transformSubscription = world.getAspectSubscriptionManager()
                .get(Aspect.all(TransformComponent.class));
        memberSubscription = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectMemberComponent.class));
        gameObjectSubscription = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectComponent.class));
        identitySubscription = world.getAspectSubscriptionManager()
                .get(Aspect.all(PixscapeIdentityComponent.class));

        EntitySubscription.SubscriptionListener structural =
                new EntitySubscription.SubscriptionListener() {
                    @Override
                    public void inserted(IntBag entities) {
                        topologyDirty = true;
                    }

                    @Override
                    public void removed(IntBag entities) {
                        topologyDirty = true;
                        int[] data = entities.getData();
                        for (int i = 0, n = entities.size(); i < n; i++) {
                            int entityId = data[i];
                            topology.clearEntity(entityId);
                            worldTransforms.clear(entityId);
                        }
                    }
                };
        transformSubscription.addSubscriptionListener(structural);
        memberSubscription.addSubscriptionListener(structural);
        gameObjectSubscription.addSubscriptionListener(structural);
        identitySubscription.addSubscriptionListener(structural);
    }

    @Override
    protected void processSystem() {
        ensureCurrentTopology();
        resolveAllFromCurrentAuthoredTransforms();
    }

    public void prepareRuntimeAvailability() {
        processSystem();
    }

    public GameObjectTopologyState topology() {
        return topology;
    }

    public WorldTransformState worldTransforms() {
        return worldTransforms;
    }

    public int rebuildCount() {
        return rebuildCount;
    }

    /**
     * Refreshes the derived hierarchy topology at the cold structural boundary when needed.
     * Package-private so the post-Physics writeback phase can reuse the single hierarchy authority.
     */
    void ensureCurrentTopology() {
        detectInPlaceStructuralMutation();
        if (topologyDirty) rebuildTopology();
    }

    /** Resolves the complete parent-first traversal from current authored transforms. */
    void resolveAllFromCurrentAuthoredTransforms() {
        for (int i = 0, n = topology.traversal.size; i < n; i++) {
            resolveEntityFromCurrentAuthoredTransform(topology.traversal.get(i));
        }
    }

    private void detectInPlaceStructuralMutation() {
        IntBag memberBag = memberSubscription.getEntities();
        int[] memberData = memberBag.getData();
        for (int i = 0, n = memberBag.size(); i < n; i++) {
            int entityId = memberData[i];
            ensureCapacity(entityId);
            GameObjectMemberComponent member = members.get(entityId);
            PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
            int stableId = identity != null ? identity.stableId : -1;
            if (observedParentStableId[entityId] != member.parentStableId
                    || observedStableId[entityId] != stableId) {
                topologyDirty = true;
                return;
            }
        }
        IntBag rootBag = gameObjectSubscription.getEntities();
        int[] rootData = rootBag.getData();
        for (int i = 0, n = rootBag.size(); i < n; i++) {
            int entityId = rootData[i];
            ensureCapacity(entityId);
            PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
            int stableId = identity != null ? identity.stableId : -1;
            if (observedStableId[entityId] != stableId) {
                topologyDirty = true;
                return;
            }
        }
    }

    private void rebuildTopology() {
        IdentityRegistry registry = IdentityRegistry.boundTo(world);
        if (memberSubscription.getEntities().size() > 0 && registry == null) {
            throw new IllegalStateException(
                    "Game Object topology requires the World-bound IdentityRegistry.");
        }
        if (registry != null) {
            new GameObjectHierarchyValidator(world, registry)
                    .validateEntities(world.getAspectSubscriptionManager().get(Aspect.all()).getEntities());
        }

        for (int i = 0; i < topology.traversal.size; i++) {
            int entityId = topology.traversal.get(i);
            topology.clearEntity(entityId);
            worldTransforms.clear(entityId);
        }
        topology.traversal.clear();

        IntBag transformBag = transformSubscription.getEntities();
        int count = transformBag.size();
        ensureSortCapacity(count);
        int[] transformData = transformBag.getData();
        for (int i = 0; i < count; i++) {
            int entityId = transformData[i];
            ensureCapacity(entityId);
            topology.clearEntity(entityId);
            int stableId = stableId(entityId);
            long order = stableId > 0 ? stableId : (0x7fffffffL + entityId);
            sortKeys[i] = (order << 32) | (entityId & 0xffffffffL);
            observedStableId[entityId] = stableId;
            GameObjectMemberComponent member = members.getSafe(entityId, null);
            observedParentStableId[entityId] = member != null ? member.parentStableId : -1;
        }
        Arrays.sort(sortKeys, 0, count);

        for (int i = count - 1; i >= 0; i--) {
            int entityId = (int) sortKeys[i];
            GameObjectMemberComponent member = members.getSafe(entityId, null);
            if (member == null) continue;
            int parentEntityId = registry.findByStableId(member.parentStableId);
            if (parentEntityId < 0 || !transforms.has(parentEntityId)) {
                throw topologyFailure(entityId, "parent stableId " + member.parentStableId
                        + " does not resolve to an active entity with TransformComponent");
            }
            ensureCapacity(parentEntityId);
            topology.parented[entityId] = true;
            topology.parentEntityId[entityId] = parentEntityId;
            topology.nextSiblingEntityId[entityId] = topology.firstChildEntityId[parentEntityId];
            topology.firstChildEntityId[parentEntityId] = entityId;
        }

        stack.clear();
        for (int i = count - 1; i >= 0; i--) {
            int entityId = (int) sortKeys[i];
            if (!topology.parented[entityId]) stack.add(entityId);
        }
        while (stack.size > 0) {
            int entityId = stack.pop();
            topology.traversal.add(entityId);
            int parentEntityId = topology.parentEntityId[entityId];
            if (parentEntityId >= 0) {
                topology.depth[entityId] = topology.depth[parentEntityId] + 1;
                int parentRoot = topology.rootEntityId[parentEntityId];
                topology.rootEntityId[entityId] = parentRoot >= 0 ? parentRoot : parentEntityId;
            } else if (gameObjects.has(entityId)) {
                topology.rootEntityId[entityId] = entityId;
            }

            children.clear();
            for (int child = topology.firstChildEntityId[entityId]; child >= 0;
                 child = topology.nextSiblingEntityId[child]) {
                children.add(child);
            }
            for (int i = children.size - 1; i >= 0; i--) stack.add(children.get(i));
        }
        if (topology.traversal.size != count) {
            throw new IllegalStateException("Game Object topology traversal did not include every transform; "
                    + "the hierarchy is cyclic or structurally invalid.");
        }
        topologyDirty = false;
        rebuildCount++;
    }

    /**
     * Publishes one entity's resolved frame. Callers must invoke this in {@link
     * GameObjectTopologyState#traversal} order so a parent frame is already current.
     */
    void resolveEntityFromCurrentAuthoredTransform(int entityId) {
        resolveEntityFromCurrentAuthoredTransform(entityId, true);
    }

    /**
     * Publishes one entity's resolved frame, optionally leaving geometry dirtiness to a caller
     * that has already marked this entity with a more precise authored transform mask.
     */
    void resolveEntityFromCurrentAuthoredTransform(int entityId, boolean publishGeometryDirty) {
        TransformComponent authored = transforms.getSafe(entityId, null);
        if (authored == null) {
            worldTransforms.clear(entityId);
            return;
        }
        if (gameObjects.has(entityId)
                && !GameObjectTransformMath.isPositiveUniformParentScale(authored)) {
            throw topologyFailure(entityId,
                    "Game Object transform must keep a finite positive uniform scale");
        }

        boolean gameObject = gameObjects.has(entityId);
        float rotation = authored.rotationRad;
        float scaleX = authored.scaleX;
        float scaleY = authored.scaleY;
        float cos = MathUtils.cos(rotation);
        float sin = MathUtils.sin(rotation);
        float localM00 = cos * scaleX;
        float localM01 = -sin * scaleY;
        float localM10 = sin * scaleX;
        float localM11 = cos * scaleY;
        float localM02 = authored.x;
        float localM12 = authored.y;
        if (gameObject) {
            localM02 = authored.x + authored.originX
                    - localM00 * authored.originX - localM01 * authored.originY;
            localM12 = authored.y + authored.originY
                    - localM10 * authored.originX - localM11 * authored.originY;
        }

        float worldM00 = localM00;
        float worldM01 = localM01;
        float worldM02 = localM02;
        float worldM10 = localM10;
        float worldM11 = localM11;
        float worldM12 = localM12;
        int parentEntityId = topology.parentEntityId[entityId];
        if (parentEntityId >= 0) {
            if (!worldTransforms.isResolved(parentEntityId)) {
                throw topologyFailure(entityId, "parent world transform is unresolved");
            }
            float p00 = worldTransforms.m00[parentEntityId];
            float p01 = worldTransforms.m01[parentEntityId];
            float p02 = worldTransforms.m02[parentEntityId];
            float p10 = worldTransforms.m10[parentEntityId];
            float p11 = worldTransforms.m11[parentEntityId];
            float p12 = worldTransforms.m12[parentEntityId];
            worldM00 = p00 * localM00 + p01 * localM10;
            worldM01 = p00 * localM01 + p01 * localM11;
            worldM02 = p00 * localM02 + p01 * localM12 + p02;
            worldM10 = p10 * localM00 + p11 * localM10;
            worldM11 = p10 * localM01 + p11 * localM11;
            worldM12 = p10 * localM02 + p11 * localM12 + p12;
            rotation = worldTransforms.rotationRad[parentEntityId] + rotation;
            float parentScale = worldTransforms.scaleX[parentEntityId];
            scaleX = parentScale * scaleX;
            scaleY = parentScale * scaleY;
        }

        float x = gameObject
                ? worldM02 - authored.originX
                + worldM00 * authored.originX + worldM01 * authored.originY
                : worldM02;
        float y = gameObject
                ? worldM12 - authored.originY
                + worldM10 * authored.originX + worldM11 * authored.originY
                : worldM12;

        boolean changed = !worldTransforms.isResolved(entityId)
                || Float.compare(worldTransforms.x[entityId], x) != 0
                || Float.compare(worldTransforms.y[entityId], y) != 0
                || Float.compare(worldTransforms.rotationRad[entityId], rotation) != 0
                || Float.compare(worldTransforms.scaleX[entityId], scaleX) != 0
                || Float.compare(worldTransforms.scaleY[entityId], scaleY) != 0
                || Float.compare(worldTransforms.m00[entityId], worldM00) != 0
                || Float.compare(worldTransforms.m01[entityId], worldM01) != 0
                || Float.compare(worldTransforms.m02[entityId], worldM02) != 0
                || Float.compare(worldTransforms.m10[entityId], worldM10) != 0
                || Float.compare(worldTransforms.m11[entityId], worldM11) != 0
                || Float.compare(worldTransforms.m12[entityId], worldM12) != 0;
        worldTransforms.setResolvedFrame(
                entityId, x, y, rotation, scaleX, scaleY,
                worldM00, worldM01, worldM02, worldM10, worldM11, worldM12);
        if (changed && publishGeometryDirty && dirty != null) {
            dirty.geometry(entityId, GeometryDirty.ALL);
        }
    }

    private int stableId(int entityId) {
        PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
        return identity != null ? identity.stableId : -1;
    }

    private void ensureCapacity(int entityId) {
        topology.ensureEntityCapacity(entityId);
        worldTransforms.ensureEntityCapacity(entityId);
        if (entityId < observedStableId.length) return;
        int old = observedStableId.length;
        int next = old;
        while (next <= entityId) next <<= 1;
        observedStableId = Arrays.copyOf(observedStableId, next);
        observedParentStableId = Arrays.copyOf(observedParentStableId, next);
        Arrays.fill(observedStableId, old, next, -1);
        Arrays.fill(observedParentStableId, old, next, -1);
    }

    private void ensureSortCapacity(int count) {
        if (count <= sortKeys.length) return;
        int next = sortKeys.length;
        while (next < count) next <<= 1;
        sortKeys = Arrays.copyOf(sortKeys, next);
    }

    private IllegalStateException topologyFailure(int entityId, String detail) {
        return new IllegalStateException("Invalid Runtime Game Object topology at entityId "
                + entityId + ", stableId " + stableId(entityId) + ": " + detail + ".");
    }
}
