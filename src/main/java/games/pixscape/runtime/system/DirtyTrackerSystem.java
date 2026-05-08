// ------------------------------------------------------------
// DirtyTrackerSystem.java
// ------------------------------------------------------------
package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.GeometryDirty;

import java.util.Arrays;

/**
 * Dirty tracker outside components (per bit) + GEOMETRY submask packed in bitsByEntity[e].
 * <p>
 * - bitsByEntity[e] : int packed (coarse bits + submask GEOMETRY)
 * - 1 list per coarse bit (IntArray) + idx[] for O(1) swap-pop
 * - purge auto via SubscriptionListener.removed()
 * <p>
 * Convention:
 * - Le submask GEOMETRY = logical granularity (pos/origin/rot/scale/size).
 * - Le bit coarse GEOMETRY = "render pipeline must be recomputed side sprite sync".
 * <p>
 * API dev-friendly:
 * dirty.markDirty().transform(e).position().rotation();
 * dirty.markDirty().material(e);
 * dirty.markDirty().transform(e).all();
 * <p>
 * IMPORTANT: returned builders are reused (zero alloc). Do not store them.
 */
public final class DirtyTrackerSystem extends BaseSystem {

    private final int initialCapacityHint;

    /**
     * Packed bits: coarse + geom submask.
     */
    private int[] bitsByEntity;

    // lists per coarse bit
    private final IntArray geometry = new IntArray(false, 256);
    private final IntArray material = new IntArray(false, 256);
    private final IntArray color = new IntArray(false, 256);
    private final IntArray order = new IntArray(false, 256);
    private final IntArray layer = new IntArray(false, 256);
    private final IntArray camera = new IntArray(false, 256);
    private final IntArray physics = new IntArray(false, 256);
    private final IntArray joints = new IntArray(false, 256);

    // idx per list
    private int[] idxGeometry;
    private int[] idxMaterial;
    private int[] idxColor;
    private int[] idxOrder;
    private int[] idxLayer;
    private int[] idxCamera;
    private int[] idxPhysics;
    private int[] physicsSubByEntity;
    private int[] idxJoints;
    private int[] jointSubByEntity;

    // Fluent facade (reused)
    private final MarkDirty markDirty = new MarkDirty(this);

    public DirtyTrackerSystem(int initialCapacityHint) {
        this.initialCapacityHint = Math.max(1024, initialCapacityHint);
    }

    // ------------------------------------------------------------
    // Artemis lifecycle
    // ------------------------------------------------------------

    @Override
    protected void initialize() {
        ensureCapacity(initialCapacityHint);

        EntitySubscription subAll = world.getAspectSubscriptionManager().get(Aspect.all());
        subAll.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) { /* no-op */ }

            @Override
            public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    clearEntity(data[i]);
                }
            }
        });
    }

    @Override
    protected void processSystem() {
        // no-op (consumed explicitly)
    }

    // ------------------------------------------------------------
    // Public central API for devs
    // ------------------------------------------------------------

    public MarkDirty markDirty() {
        return markDirty;
    }

    // ------------------------------------------------------------
    // Capacity
    // ------------------------------------------------------------

    public void ensureCapacity(int capacity) {
        if (capacity <= 0) capacity = 1024;

        if (bitsByEntity == null) {
            bitsByEntity = new int[capacity];
        } else if (bitsByEntity.length < capacity) {
            bitsByEntity = Arrays.copyOf(bitsByEntity, capacity);
        }

        idxGeometry = ensureIdx(idxGeometry, capacity);
        idxMaterial = ensureIdx(idxMaterial, capacity);
        idxColor = ensureIdx(idxColor, capacity);
        idxOrder = ensureIdx(idxOrder, capacity);
        idxLayer = ensureIdx(idxLayer, capacity);
        idxCamera = ensureIdx(idxCamera, capacity);
        idxPhysics = ensureIdx(idxPhysics, capacity);
        idxJoints = ensureIdx(idxJoints, capacity);

        if (physicsSubByEntity == null) {
            physicsSubByEntity = new int[capacity];
        } else if (physicsSubByEntity.length < capacity) {
            physicsSubByEntity = Arrays.copyOf(physicsSubByEntity, capacity);
        }
        if (jointSubByEntity == null) {
            jointSubByEntity = new int[capacity];
        } else if (jointSubByEntity.length < capacity) {
            jointSubByEntity = Arrays.copyOf(jointSubByEntity, capacity);
        }
    }

    private static int[] ensureIdx(int[] arr, int capacity) {
        if (arr == null) {
            int[] a = new int[capacity];
            Arrays.fill(a, -1);
            return a;
        }
        if (arr.length >= capacity) return arr;

        int oldLen = arr.length;
        int[] a = Arrays.copyOf(arr, capacity);
        Arrays.fill(a, oldLen, capacity, -1);
        return a;
    }

    private void ensureEntityIndex(int e) {
        if (e < 0) return;
        if (bitsByEntity == null || e >= bitsByEntity.length) {
            int next = Math.max(initialCapacityHint, (bitsByEntity != null ? bitsByEntity.length : 1024));
            while (e >= next) next <<= 1;
            ensureCapacity(next);
        }
    }

    private boolean isActive(int e) {
        return world != null && world.getEntityManager().isActive(e);
    }

    // ------------------------------------------------------------
    // Mark API (coarse)
    // ------------------------------------------------------------

    public void mark(int e, int coarseMask) {
        if ((coarseMask & DirtyBits.COARSE_MASK) == DirtyBits.NONE) return;
        if (!isActive(e)) return;

        ensureEntityIndex(e);

        int packed = bitsByEntity[e];
        int prevCoarse = packed & DirtyBits.COARSE_MASK;
        int add = (coarseMask & DirtyBits.COARSE_MASK) & ~prevCoarse;
        if (add == 0) {
            // already present => nothing to do (list already ticketed)
            bitsByEntity[e] = packed | (coarseMask & DirtyBits.COARSE_MASK);
            return;
        }

        bitsByEntity[e] = packed | (coarseMask & DirtyBits.COARSE_MASK);

        if ((add & DirtyBits.GEOMETRY) != 0) addToList(e, DirtyBits.GEOMETRY);
        if ((add & DirtyBits.MATERIAL) != 0) addToList(e, DirtyBits.MATERIAL);
        if ((add & DirtyBits.COLOR) != 0) addToList(e, DirtyBits.COLOR);
        if ((add & DirtyBits.ORDER) != 0) addToList(e, DirtyBits.ORDER);
        if ((add & DirtyBits.LAYER) != 0) addToList(e, DirtyBits.LAYER);
        if ((add & DirtyBits.CAMERA) != 0) addToList(e, DirtyBits.CAMERA);
        if ((add & DirtyBits.PHYSICS) != 0) addToList(e, DirtyBits.PHYSICS);
        if ((add & DirtyBits.JOINTS) != 0) addToList(e, DirtyBits.JOINTS);
    }

    public void material(int e) {
        mark(e, DirtyBits.MATERIAL);
    }

    public void color(int e) {
        mark(e, DirtyBits.COLOR);
    }

    public void order(int e) {
        mark(e, DirtyBits.ORDER);
    }

    public void layer(int e) {
        mark(e, DirtyBits.LAYER);
    }

    public void camera(int e) {
        mark(e, DirtyBits.CAMERA);
    }

    // ------------------------------------------------------------
    // GEOMETRY API (coarse + submask)
    // ------------------------------------------------------------

    /**
     * Marks coarse GEOMETRY and ORs GeometryDirty.* submask into the packed field.
     */
    public void geometry(int e, int geomSubMask) {
        if (!isActive(e)) return;

        ensureEntityIndex(e);

        if ((geomSubMask & GeometryDirty.ALL) != 0) {
            int packed = bitsByEntity[e];
            int prevSub = DirtyBits.geomSubFromPacked(packed);
            int nextSub = prevSub | (geomSubMask & GeometryDirty.ALL);
            if (nextSub != prevSub) {
                bitsByEntity[e] = DirtyBits.geomSubPackInto(packed, nextSub);
            }
        }

        // ensures coarse GEOMETRY (and ticket in the list)
        mark(e, DirtyBits.GEOMETRY);
    }

    public int geomSub(int e) {
        if (e < 0 || bitsByEntity == null || e >= bitsByEntity.length) return GeometryDirty.NONE;
        return DirtyBits.geomSubFromPacked(bitsByEntity[e]);
    }

    public void clearGeomSub(int e, int subMask) {
        if ((subMask & GeometryDirty.ALL) == 0) return;
        if (e < 0 || bitsByEntity == null || e >= bitsByEntity.length) return;

        int packed = bitsByEntity[e];
        int prev = DirtyBits.geomSubFromPacked(packed);
        int next = prev & ~(subMask & GeometryDirty.ALL);
        if (next == prev) return;

        bitsByEntity[e] = DirtyBits.geomSubPackInto(packed, next);
    }

    public void clearAllGeomSub(int e) {
        if (e < 0 || bitsByEntity == null || e >= bitsByEntity.length) return;
        int packed = bitsByEntity[e];
        int prev = DirtyBits.geomSubFromPacked(packed);
        if (prev == 0) return;
        bitsByEntity[e] = DirtyBits.geomSubPackInto(packed, 0);
    }

    // ------------------------------------------------------------
    // Lists
    // ------------------------------------------------------------

    private void addToList(int e, int bit) {
        switch (bit) {
            case DirtyBits.GEOMETRY: {
                if (idxGeometry[e] != -1) return;
                idxGeometry[e] = geometry.size;
                geometry.add(e);
                return;
            }
            case DirtyBits.MATERIAL: {
                if (idxMaterial[e] != -1) return;
                idxMaterial[e] = material.size;
                material.add(e);
                return;
            }
            case DirtyBits.COLOR: {
                if (idxColor[e] != -1) return;
                idxColor[e] = color.size;
                color.add(e);
                return;
            }
            case DirtyBits.ORDER: {
                if (idxOrder[e] != -1) return;
                idxOrder[e] = order.size;
                order.add(e);
                return;
            }
            case DirtyBits.LAYER: {
                if (idxLayer[e] != -1) return;
                idxLayer[e] = layer.size;
                layer.add(e);
                return;
            }
            case DirtyBits.CAMERA: {
                if (idxCamera[e] != -1) return;
                idxCamera[e] = camera.size;
                camera.add(e);
                return;
            }
            case DirtyBits.PHYSICS: {
                if (idxPhysics[e] != -1) return;
                idxPhysics[e] = physics.size;
                physics.add(e);
                return;
            }
            case DirtyBits.JOINTS: {
                if (idxJoints[e] != -1) return;
                idxJoints[e] = joints.size;
                joints.add(e);
                return;
            }
            default: { /* ignore */ }
        }
    }

    // ------------------------------------------------------------
    // Query
    // ------------------------------------------------------------

    /**
     * packed bits (coarse + geom sub).
     */
    public int packedBits(int e) {
        if (e < 0 || bitsByEntity == null || e >= bitsByEntity.length) return DirtyBits.NONE;
        return bitsByEntity[e];
    }

    /**
     * coarse only
     */
    public int coarseBits(int e) {
        return packedBits(e) & DirtyBits.COARSE_MASK;
    }

    public boolean isDirty(int e, int coarseMask) {
        return (coarseBits(e) & coarseMask) != 0;
    }

    // ------------------------------------------------------------
    // Consume (coarse lists) - optional if yor prefer direct tight loops
    // ------------------------------------------------------------

    public void consume(int bit, IntConsumer fn) {
        if ((bit & DirtyBits.COARSE_MASK) == DirtyBits.NONE) return;
        if (fn == null) return;

        final IntArray list = listOf(bit);
        if (list == null || list.size == 0) return;

        for (int i = list.size - 1; i >= 0; i--) {
            int e = list.get(i);

            if (!isActive(e)) {
                removeFromList(e, bit);
                if (bitsByEntity != null && e >= 0 && e < bitsByEntity.length) {
                    bitsByEntity[e] &= ~bit;
                    if (bit == DirtyBits.GEOMETRY) bitsByEntity[e] = DirtyBits.geomSubPackInto(bitsByEntity[e], 0);
                }
                continue;
            }

            fn.accept(e);

            // ACK : clear coarse bit + remove ticket list
            if (bitsByEntity != null && e < bitsByEntity.length) {
                bitsByEntity[e] &= ~bit;
                // note: we DO NOT clear geom sub here (this is logical, consumed by UpdateWorldGeometrySystem via clearAllGeomSub)
            }
            removeFromList(e, bit);
        }
    }

    public void consumeMask(int mask, IntConsumer fn) {
        if ((mask & DirtyBits.GEOMETRY) != 0) consume(DirtyBits.GEOMETRY, fn);
        if ((mask & DirtyBits.MATERIAL) != 0) consume(DirtyBits.MATERIAL, fn);
        if ((mask & DirtyBits.COLOR) != 0) consume(DirtyBits.COLOR, fn);
        if ((mask & DirtyBits.ORDER) != 0) consume(DirtyBits.ORDER, fn);
        if ((mask & DirtyBits.LAYER) != 0) consume(DirtyBits.LAYER, fn);
        if ((mask & DirtyBits.CAMERA) != 0) consume(DirtyBits.CAMERA, fn);
        if ((mask & DirtyBits.PHYSICS) != 0) consumePhysics(fn);
        if ((mask & DirtyBits.JOINTS) != 0) consumeJoints(fn);
    }

    public void consumePhysics(IntConsumer fn) {
        if (fn == null) return;
        if (physics.size == 0) return;

        for (int i = physics.size - 1; i >= 0; i--) {
            int e = physics.get(i);

            if (!isActive(e)) {
                removeFromList(e, DirtyBits.PHYSICS);
                if (e >= 0 && e < bitsByEntity.length) bitsByEntity[e] &= ~DirtyBits.PHYSICS;
                if (physicsSubByEntity != null && e >= 0 && e < physicsSubByEntity.length) physicsSubByEntity[e] = 0;
                continue;
            }

            fn.accept(e);

            // ACK
            if (e >= 0 && e < bitsByEntity.length) bitsByEntity[e] &= ~DirtyBits.PHYSICS;
            if (physicsSubByEntity != null && e >= 0 && e < physicsSubByEntity.length) physicsSubByEntity[e] = 0;
            removeFromList(e, DirtyBits.PHYSICS);
        }
    }

    public void consumeJoints(IntConsumer fn) {
        if (fn == null) return;
        if (joints.size == 0) return;

        for (int i = joints.size - 1; i >= 0; i--) {
            int e = joints.get(i);

            if (!isActive(e)) {
                removeFromList(e, DirtyBits.JOINTS);
                if (e >= 0 && e < bitsByEntity.length) bitsByEntity[e] &= ~DirtyBits.JOINTS;
                if (jointSubByEntity != null && e >= 0 && e < jointSubByEntity.length) jointSubByEntity[e] = 0;
                continue;
            }

            fn.accept(e);

            // ACK
            if (e >= 0 && e < bitsByEntity.length) bitsByEntity[e] &= ~DirtyBits.JOINTS;
            if (jointSubByEntity != null && e >= 0 && e < jointSubByEntity.length) jointSubByEntity[e] = 0;
            removeFromList(e, DirtyBits.JOINTS);
        }
    }

    public void joint(int e, int subMask) {
        if (!isActive(e)) return;
        ensureEntityIndex(e);

        if (subMask != 0 && jointSubByEntity != null) {
            jointSubByEntity[e] |= subMask;
        }
        mark(e, DirtyBits.JOINTS);
    }

    public int jointSub(int e) {
        if (e < 0 || jointSubByEntity == null || e >= jointSubByEntity.length) return 0;
        return jointSubByEntity[e];
    }

    private IntArray listOf(int bit) {
        switch (bit) {
            case DirtyBits.GEOMETRY:
                return geometry;
            case DirtyBits.MATERIAL:
                return material;
            case DirtyBits.COLOR:
                return color;
            case DirtyBits.ORDER:
                return order;
            case DirtyBits.LAYER:
                return layer;
            case DirtyBits.CAMERA:
                return camera;
            case DirtyBits.PHYSICS:
                return physics;
            case DirtyBits.JOINTS:
                return joints;
            default:
                return null;
        }
    }

    private void removeFromList(int e, int bit) {
        switch (bit) {
            case DirtyBits.GEOMETRY:
                swapPopRemove(geometry, idxGeometry, e);
                return;
            case DirtyBits.MATERIAL:
                swapPopRemove(material, idxMaterial, e);
                return;
            case DirtyBits.COLOR:
                swapPopRemove(color, idxColor, e);
                return;
            case DirtyBits.ORDER:
                swapPopRemove(order, idxOrder, e);
                return;
            case DirtyBits.LAYER:
                swapPopRemove(layer, idxLayer, e);
                return;
            case DirtyBits.CAMERA:
                swapPopRemove(camera, idxCamera, e);
                return;
            case DirtyBits.PHYSICS:
                swapPopRemove(physics, idxPhysics, e);
                return;
            case DirtyBits.JOINTS:
                swapPopRemove(joints, idxJoints, e);
                return;
            default: { /* ignore */ }
        }
    }

    private static void swapPopRemove(IntArray list, int[] idx, int e) {
        if (e < 0 || idx == null || e >= idx.length) return;

        int i = idx[e];
        if (i < 0) return;

        int lastIndex = list.size - 1;
        if (i != lastIndex) {
            int lastEntity = list.get(lastIndex);
            list.set(i, lastEntity);
            idx[lastEntity] = i;
        }
        list.pop();
        idx[e] = -1;
    }

    // ------------------------------------------------------------
    // Frame flush / Clear
    // ------------------------------------------------------------

    /**
     * Flush de fin de frame (coarse + sub).
     * Goal: remove dependency on "which system consumes what".
     * <p>
     * - Empties all coarse lists.
     * - Clears corresponding coarse bits.
     * - Also clears geomSub for entities still ticketed in GEOMETRY (fail-safe).
     * <p>
     * NB: normal flow = UpdateWorldGeometrySystem clearAllGeomSub(e) itself.
     * Here we force to 0 if something was missed (otherwise submask can "leak").
     */
    public void clearFrame() {
        clearListAndCoarseBit(geometry, idxGeometry, DirtyBits.GEOMETRY, true);
        clearListAndCoarseBit(material, idxMaterial, DirtyBits.MATERIAL, false);
        clearListAndCoarseBit(color, idxColor, DirtyBits.COLOR, false);
        clearListAndCoarseBit(order, idxOrder, DirtyBits.ORDER, false);
        clearListAndCoarseBit(layer, idxLayer, DirtyBits.LAYER, false);
        clearListAndCoarseBit(camera, idxCamera, DirtyBits.CAMERA, false);
        clearPhysicsListAndBits();
        clearJointsListAndBits();
    }

    private void clearListAndCoarseBit(IntArray list, int[] idx, int coarseBit, boolean clearGeomSub) {
        if (list == null || idx == null) return;
        if (bitsByEntity == null) {
            list.clear();
            return;
        }

        for (int i = 0, n = list.size; i < n; i++) {
            int e = list.get(i);
            if (e < 0 || e >= bitsByEntity.length) continue;

            bitsByEntity[e] &= ~coarseBit;

            if (clearGeomSub) {
                bitsByEntity[e] = DirtyBits.geomSubPackInto(bitsByEntity[e], 0);
            }

            if (e < idx.length) idx[e] = -1;
        }
        list.clear();
    }

    private void clearPhysicsListAndBits() {
        if (physics == null || idxPhysics == null) return;
        if (bitsByEntity == null) {
            physics.clear();
            return;
        }

        for (int i = 0, n = physics.size; i < n; i++) {
            int e = physics.get(i);
            if (e >= 0 && e < bitsByEntity.length) bitsByEntity[e] &= ~DirtyBits.PHYSICS;
            if (physicsSubByEntity != null && e >= 0 && e < physicsSubByEntity.length) physicsSubByEntity[e] = 0;
            if (e >= 0 && e < idxPhysics.length) idxPhysics[e] = -1;
        }
        physics.clear();
    }

    private void clearJointsListAndBits() {
        if (joints == null || idxJoints == null) return;
        if (bitsByEntity == null) {
            joints.clear();
            return;
        }

        for (int i = 0, n = joints.size; i < n; i++) {
            int e = joints.get(i);
            if (e >= 0 && e < bitsByEntity.length) bitsByEntity[e] &= ~DirtyBits.JOINTS;
            if (jointSubByEntity != null && e >= 0 && e < jointSubByEntity.length) jointSubByEntity[e] = 0;
            if (e >= 0 && e < idxJoints.length) idxJoints[e] = -1;
        }
        joints.clear();
    }


    public void clearAll() {
        if (bitsByEntity != null) Arrays.fill(bitsByEntity, DirtyBits.NONE);

        clearIdxList(geometry, idxGeometry);
        clearIdxList(material, idxMaterial);
        clearIdxList(color, idxColor);
        clearIdxList(order, idxOrder);
        clearIdxList(layer, idxLayer);
        clearIdxList(camera, idxCamera);
        if (physicsSubByEntity != null) Arrays.fill(physicsSubByEntity, 0);
        clearIdxList(physics, idxPhysics);
        if (jointSubByEntity != null) Arrays.fill(jointSubByEntity, 0);
        clearIdxList(joints, idxJoints);
    }

    private static void clearIdxList(IntArray list, int[] idx) {
        if (list == null || idx == null) return;
        for (int i = 0; i < list.size; i++) {
            int e = list.get(i);
            if (e >= 0 && e < idx.length) idx[e] = -1;
        }
        list.clear();
    }

    public void clearEntity(int e) {
        if (e < 0) return;
        if (bitsByEntity == null || e >= bitsByEntity.length) return;

        int packed = bitsByEntity[e];
        if (packed == DirtyBits.NONE) return;

        int coarse = packed & DirtyBits.COARSE_MASK;

        if ((coarse & DirtyBits.GEOMETRY) != 0) swapPopRemove(geometry, idxGeometry, e);
        if ((coarse & DirtyBits.MATERIAL) != 0) swapPopRemove(material, idxMaterial, e);
        if ((coarse & DirtyBits.COLOR) != 0) swapPopRemove(color, idxColor, e);
        if ((coarse & DirtyBits.ORDER) != 0) swapPopRemove(order, idxOrder, e);
        if ((coarse & DirtyBits.LAYER) != 0) swapPopRemove(layer, idxLayer, e);
        if ((coarse & DirtyBits.CAMERA) != 0) swapPopRemove(camera, idxCamera, e);
        if ((coarse & DirtyBits.PHYSICS) != 0) swapPopRemove(physics, idxPhysics, e);
        if ((coarse & DirtyBits.JOINTS) != 0) swapPopRemove(joints, idxJoints, e);

        if (physicsSubByEntity != null && e < physicsSubByEntity.length) physicsSubByEntity[e] = 0;
        if (jointSubByEntity != null && e < jointSubByEntity.length) jointSubByEntity[e] = 0;

        bitsByEntity[e] = DirtyBits.NONE;
    }

    // ------------------------------------------------------------
    // Access to lists (tight loops)
    // ------------------------------------------------------------

    /**
     * Live list of GEOMETRY-ticketed entities (do not modify).
     */
    public IntArray geometryEntities() {
        return geometry;
    }

    /**
     * Live list of MATERIAL-ticketed entities (do not modify).
     */
    public IntArray materialEntities() {
        return material;
    }

    /**
     * Live list of COLOR-ticketed entities (do not modify).
     */
    public IntArray colorEntities() {
        return color;
    }

    /**
     * Live list of ORDER-ticketed entities (do not modify).
     */
    public IntArray orderEntities() {
        return order;
    }

    /**
     * Live list of LAYER-ticketed entities (do not modify).
     */
    public IntArray layerEntities() {
        return layer;
    }

    /**
     * Live list of CAMERA-ticketed entities (do not modify).
     */
    public IntArray cameraEntities() {
        return camera;
    }

    public IntArray physicsEntities() {
        return physics;
    }

    public IntArray jointsEntities() {
        return joints;
    }


    public void physics(int e, int subMask) {
        if (!isActive(e)) return;
        ensureEntityIndex(e);

        if (subMask != 0 && physicsSubByEntity != null) {
            physicsSubByEntity[e] |= subMask;
        }
        mark(e, DirtyBits.PHYSICS);
    }

    public int physicsSub(int e) {
        if (e < 0 || physicsSubByEntity == null || e >= physicsSubByEntity.length) return 0;
        return physicsSubByEntity[e];
    }

    public void clearPhysicsSub(int e, int subMask) {
        if (subMask == 0) return;
        if (e < 0 || physicsSubByEntity == null || e >= physicsSubByEntity.length) return;
        physicsSubByEntity[e] &= ~subMask;
    }

    public void clearAllPhysicsSub(int e) {
        if (e < 0 || physicsSubByEntity == null || e >= physicsSubByEntity.length) return;
        physicsSubByEntity[e] = 0;
    }

    public void markAll(int coarseMask) {
        if ((coarseMask & DirtyBits.COARSE_MASK) == DirtyBits.NONE) return;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            mark(data[i], coarseMask);
        }
    }


    // ------------------------------------------------------------
    // Fluent facade (single central “class” for devs)
    // ------------------------------------------------------------

    public static final class MarkDirty {
        private final DirtyTrackerSystem dirty;
        private final TransformMark transform = new TransformMark();
        private int e;

        MarkDirty(DirtyTrackerSystem dirty) {
            this.dirty = dirty;
        }

        /**
         * Transform fluent.
         */
        public TransformMark transform(int entityId) {
            this.e = entityId;
            transform.e = entityId;
            transform.dirty = dirty;
            return transform;
        }

        /**
         * Coarse-only helpers (no submask).
         */
        public MarkDirty material(int entityId) {
            dirty.material(entityId);
            return this;
        }

        public MarkDirty color(int entityId) {
            dirty.color(entityId);
            return this;
        }

        public MarkDirty order(int entityId) {
            dirty.order(entityId);
            return this;
        }

        public MarkDirty layer(int entityId) {
            dirty.layer(entityId);
            return this;
        }

        public MarkDirty camera(int entityId) {
            dirty.camera(entityId);
            return this;
        }

        /**
         * Convenience.
         */
        public MarkDirty everything(int entityId) {
            dirty.mark(entityId, DirtyBits.EVERYTHING);
            return this;
        }
    }

    /**
     * Fluent builder transform/geometry submask. Reused: DO NOT STORE.
     */
    public static final class TransformMark {
        private DirtyTrackerSystem dirty;
        private int e;

        // submask accumulated locally to limit writes to bitsByEntity
        private int sub;

        private TransformMark() {
        }

        private void add(int m) {
            sub |= m;
        }

        /**
         * Implicit commit (automatically called at the end of chains)
         */
        private void commitIfNeeded() {
            if (sub != GeometryDirty.NONE) {
                dirty.geometry(e, sub);
                sub = GeometryDirty.NONE;
            } else {
                // allows transform(e).commit() => coarse geometry only if yor want
            }
        }

        // --- fluent ops ---
        public TransformMark position() {
            add(GeometryDirty.POSITION);
            return this;
        }

        public TransformMark origin() {
            add(GeometryDirty.ORIGIN);
            return this;
        }

        public TransformMark rotation() {
            add(GeometryDirty.ROTATION);
            return this;
        }

        public TransformMark scale() {
            add(GeometryDirty.SCALE);
            return this;
        }

        public TransformMark size() {
            add(GeometryDirty.SIZE);
            return this;
        }

        public TransformMark axes() {
            add(GeometryDirty.AXES_MASK);
            return this;
        }

        public TransformMark all() {
            add(GeometryDirty.ALL);
            return this;
        }

        /**
         * Force coarse only (rare).
         */
        public TransformMark coarseOnly() {
            dirty.mark(e, DirtyBits.GEOMETRY);
            sub = GeometryDirty.NONE;
            return this;
        }

        /**
         * Explicit commit to finish a chain: transform(e).position().rotation().commit()
         */
        public void commit() {
            commitIfNeeded();
        }

        // Small ergonomic trick: allows transform(e).position().rotation() without explicit commit
        // if the dev then chains another mark (material, etc.). In that case yor must commit
        // on caller side. So: we do NOT auto-commit here.
        //
        // => simple rule to communicate: "end with .commit()" (or use the helpers above).
    }

    // ------------------------------------------------------------
    // Tiny functional interface
    // ------------------------------------------------------------

    @FunctionalInterface
    public interface IntConsumer {
        void accept(int e);
    }
}
