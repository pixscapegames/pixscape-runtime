package games.pixscape.runtime.service;

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

    /** Renvoie la liste des entités du layer, TRIÉE par z croissant. */
    private List<Integer> entitiesInLayerSorted(int layer) {
        var asm = world.getAspectSubscriptionManager();
        IntBag bag = asm.get(Aspect.all(EntityIndexComponent.class)).getEntities();
        int[] data = bag.getData();

        // simple bucket-sort par z (petit n typiquement) → on fait une copie et on trie une fois
        List<Integer> list = new ArrayList<>(bag.size());
        for (int i=0, n=bag.size(); i<n; i++) {
            int e = data[i];
            if (mIndex.get(e).getLayerIndex() == layer) list.add(e);
        }
        // tri par z asc, départage par id pour stabilité déterministe
        list.sort((e1, e2) -> {
            int z1 = mIndex.get(e1).getZIndex(), z2 = mIndex.get(e2).getZIndex();
            if (z1 != z2) return Integer.compare(z1, z2);
            return Integer.compare(e1, e2);
        });
        return list;
    }

    /** Repose z = index (0..n-1) sur la liste fournie (déjà triée). */
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

    /** Garantit que l’entité a les composants requis et le place dans le layer. */
    private void ensureEntityHasComponents(int e, int layer) {
        EntityIndexComponent index = mIndex.getSafe(e, null);
        if (index == null) index = mIndex.create(e);
        index.layerIndex = layer;
        index.zIndex = 0; // valeur provisoire, corrigée juste après
        if (dirty != null) {
            dirty.layer(e);
            dirty.order(e);
        }
    }

    /* ========== API ========== */

    /** Ajoute l’entité en HAUT du layer (z = n). Crée les composants si absents. */
    public void addOnTop(int e, int layer) {
        ensureEntityHasComponents(e, layer);
        List<Integer> L = entitiesInLayerSorted(layer);
        // si l'entité n'était pas dans L (nouvelle), on la met en dernier
        if (!L.contains(e)) L.add(e);
        else { // déjà dans le layer : on la remonte
            L.remove((Integer)e);
            L.add(e);
        }
        writeSequentialZ(L); // z = 0..n-1
    }


    /** Monte l’entité d’un cran (si possible). */
    public void moveUp(int e) {
        if (!mIndex.has(e)) return;
        int layer = mIndex.get(e).getLayerIndex();
        List<Integer> L = entitiesInLayerSorted(layer);
        int idx = L.indexOf(e);
        if (idx == -1 || idx == L.size() - 1) return; // pas trouvé ou déjà en haut
        // swap avec le suivant
        int tmp = L.get(idx + 1);
        L.set(idx + 1, e);
        L.set(idx, tmp);
        writeSequentialZ(L);
    }

    /** Descend l’entité d’un cran (si possible). */
    public void moveDown(int e) {
        if (!mIndex.has(e)) return;
        int layer = mIndex.get(e).getLayerIndex();
        List<Integer> L = entitiesInLayerSorted(layer);
        int idx = L.indexOf(e);
        if (idx <= 0) return; // déjà tout en bas
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

    /** Recompacte explicitement tout un layer (sécurité/maintenance). */
    public void normalizeLayer(int layer) {
        List<Integer> L = entitiesInLayerSorted(layer);
        writeSequentialZ(L);
    }

    /** Déplace une entité vers un autre layer et la met en haut de ce layer. */
    public void moveToLayerTop(int e, int targetLayer) {
        ensureEntityHasComponents(e, targetLayer);
        List<Integer> L = entitiesInLayerSorted(targetLayer);
        if (!L.contains(e)) L.add(e); else { L.remove((Integer)e); L.add(e); }
        writeSequentialZ(L);
    }

    /** Déplace une entité vers un autre layer et la met en bas. */
    public void moveToLayerBottom(int e, int targetLayer) {
        ensureEntityHasComponents(e, targetLayer);
        List<Integer> L = entitiesInLayerSorted(targetLayer);
        if (!L.contains(e)) L.add(0, e); else { L.remove((Integer)e); L.add(0, e); }
        writeSequentialZ(L);
    }
}
