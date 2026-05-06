package games.pixscape.runtime.service;

import com.artemis.AspectSubscriptionManager;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;

import java.util.ArrayList;
import java.util.List;

public final class ZOrderRuntimeService {
    private final World world;
    private final ComponentMapper<EntityIndexComponent> mIndex;
    private final DirtyTrackerSystem dirty;

    public ZOrderRuntimeService(World world) {
        this.world = world;
        this.mIndex = world.getMapper(EntityIndexComponent.class);
        this.dirty = world.getSystem(DirtyTrackerSystem.class);
    }

    /* ========== Helpers ========== */

    /** Returns the list of layer entities, SORTED by ascending z. */
    private List<Integer> entitiesInLayerSorted(int layer) {
        AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
        IntBag bag = asm.get(Aspect.all(EntityIndexComponent.class)).getEntities();
        int[] data = bag.getData();

        // simple bucket-sort by z (typically small n) -> make a copy and sort once
        List<Integer> list = new ArrayList<>(bag.size());
        for (int i=0, n=bag.size(); i<n; i++) {
            int e = data[i];
            if (mIndex.get(e).getLayerIndex() == layer) list.add(e);
        }
        // sort by ascending z, tie-break by id for deterministic stability
        list.sort((e1, e2) -> {
            int z1 = mIndex.get(e1).getZIndex(), z2 = mIndex.get(e2).getZIndex();
            if (z1 != z2) return Integer.compare(z1, z2);
            return Integer.compare(e1, e2);
        });
        return list;
    }

    /** Reassigns z = index (0..n-1) on the provided list (already sorted). */
    private void writeSequentialZ(List<Integer> sorted) {
        for (int i = 0; i < sorted.size(); i++) {
            int e = sorted.get(i);
            EntityIndexComponent index = mIndex.get(e);
            if (index != null && index.zIndex != i) {
                index.zIndex = i;
                if (dirty != null) dirty.order(e);
            }
        }
    }

    /** Ensures the entity has required components and places it in the layer. */
    private void ensureEntityHasComponents(int e, int layer) {
        EntityIndexComponent index = mIndex.getSafe(e, null);
        if (index == null) index = mIndex.create(e);
        index.layerIndex = layer;
        index.zIndex = 0; // temporary value, corrected right after
        if (dirty != null) {
            dirty.layer(e);
            dirty.order(e);
        }
    }

    /* ========== API ========== */

    /** Adds the entity at TOP of the layer (z = n). Creates components if missing. */
    public void addOnTop(int e, int layer) {
        ensureEntityHasComponents(e, layer);
        List<Integer> L = entitiesInLayerSorted(layer);
        // if entity was not in L (new), place it last
        if (!L.contains(e)) L.add(e);
        else { // already in the layer: move it up
            L.remove((Integer)e);
            L.add(e);
        }
        writeSequentialZ(L); // z = 0..n-1
    }


    /** Moves the entity up by one step (if possible). */
    public void moveUp(int e) {
        if (!mIndex.has(e)) return;
        int layer = mIndex.get(e).getLayerIndex();
        List<Integer> L = entitiesInLayerSorted(layer);
        int idx = L.indexOf(e);
        if (idx == -1 || idx == L.size() - 1) return; // not found or already at top
        // swap with the next
        int tmp = L.get(idx + 1);
        L.set(idx + 1, e);
        L.set(idx, tmp);
        writeSequentialZ(L);
    }

    /** Moves the entity down by one step (if possible). */
    public void moveDown(int e) {
        if (!mIndex.has(e)) return;
        int layer = mIndex.get(e).getLayerIndex();
        List<Integer> L = entitiesInLayerSorted(layer);
        int idx = L.indexOf(e);
        if (idx <= 0) return; // already tout en bas
        int tmp = L.get(idx - 1);
        L.set(idx - 1, e);
        L.set(idx, tmp);
        writeSequentialZ(L);
    }

    /** Envoie en haut (z = n-1). */
    public void moveToTop(int e) {
        if (!mIndex.has(e)) return;
        int layer = mIndex.get(e).getLayerIndex();
        List<Integer> L = entitiesInLayerSorted(layer);
        if (!L.remove((Integer)e)) L.add(e); else L.add(e);
        writeSequentialZ(L);
    }

    /** Envoie en bas (z = 0). */
    public void moveToBottom(int e) {
        if (!mIndex.has(e)) return;
        int layer = mIndex.get(e).getLayerIndex();
        List<Integer> L = entitiesInLayerSorted(layer);
        if (!L.remove((Integer)e)) L.add(0, e); else L.add(0, e);
        writeSequentialZ(L);
    }

    /** Explicitly recompacts an entire layer (safety/maintenance). */
    public void normalizeLayer(int layer) {
        List<Integer> L = entitiesInLayerSorted(layer);
        writeSequentialZ(L);
    }

    /** Moves an entity to another layer and puts it at the top of that layer. */
    public void moveToLayerTop(int e, int targetLayer) {
        ensureEntityHasComponents(e, targetLayer);
        List<Integer> L = entitiesInLayerSorted(targetLayer);
        if (!L.contains(e)) L.add(e); else { L.remove((Integer)e); L.add(e); }
        writeSequentialZ(L);
    }

    /** Moves an entity to another layer and puts it at the bottom. */
    public void moveToLayerBottom(int e, int targetLayer) {
        ensureEntityHasComponents(e, targetLayer);
        List<Integer> L = entitiesInLayerSorted(targetLayer);
        if (!L.contains(e)) L.add(0, e); else { L.remove((Integer)e); L.add(0, e); }
        writeSequentialZ(L);
    }
}
