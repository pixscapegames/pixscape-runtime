package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.SkipWire;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.hierarchy.GameObjectCompositionState;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.*;

/**
 * Trie drawList par sortKey de chaque source render.
 * <p>
 * Important:
 * - drawList entries carry a source domain and a source slot/index.
 * - STABLE sort (LSD radix) => preserves relative order for equal keys
 * (useful for tie/runtimeOrder).
 */
public final class RenderSortSystem extends BaseSystem implements ProfiledSystem {

    private final DynamicEntityRenderState ecsState;
    private final TiledMapRenderState tiledState;
    private final VfxRenderState vfxState;
    private final DrawList drawList;

    // scratch buffers (reused)
    private int[] tmpSlots = new int[0];
    private byte[] tmpDomains = new byte[0];
    private long[] tmpKeys = new long[0];
    private int[] tmpTiledRefs = new int[0];
    private final int[] count = new int[256]; // 8 bits
    private final IntArray hierarchyStack = new IntArray(false, 32);
    private final IntArray childScratch = new IntArray(false, 16);
    private ComponentMapper<GameObjectComponent> gameObjects;
    @SkipWire
    private GameObjectCompositionSystem gameObjectComposition;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public RenderSortSystem(DynamicEntityRenderState ecsState, DrawList drawList) {
        this(ecsState, null, null, drawList, -1, -1);
    }

    public RenderSortSystem(DynamicEntityRenderState ecsState,
                            TiledMapRenderState tiledState,
                            DrawList drawList) {
        this(ecsState, tiledState, null, drawList, -1, -1);
    }

    public RenderSortSystem(DynamicEntityRenderState ecsState,
                            TiledMapRenderState tiledState,
                            VfxRenderState vfxState,
                            DrawList drawList,
                            int vfxStartInclusive,
                            int vfxEndExclusive) {
        this.ecsState = ecsState;
        this.tiledState = tiledState;
        this.vfxState = vfxState;
        this.drawList = drawList;
    }

    @Override
    protected void initialize() {
        gameObjectComposition = world.getSystem(GameObjectCompositionSystem.class);
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.RENDER_SORT);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.RENDER_SORT, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        RenderCompositionList composition = drawList.composition();
        if (composition.size > 0) {
            sortComposition(composition);
            flattenComposition(composition);
            return;
        }

        sortFlatDrawList();
    }

    private void sortComposition(RenderCompositionList composition) {
        final int n = composition.size;
        ensureTmpCapacity(n);

        for (int pass = 0; pass < 8; pass++) {
            final int shift = pass * 8;
            for (int i = 0; i < 256; i++) count[i] = 0;

            for (int i = 0; i < n; i++) {
                count[(int) ((composition.sortKey[i] >>> shift) & 0xFFL)]++;
            }

            int sum = 0;
            for (int bucket = 0; bucket < 256; bucket++) {
                int bucketCount = count[bucket];
                count[bucket] = sum;
                sum += bucketCount;
            }

            for (int i = 0; i < n; i++) {
                long key = composition.sortKey[i];
                int target = count[(int) ((key >>> shift) & 0xFFL)]++;
                tmpDomains[target] = composition.sourceDomain[i];
                tmpSlots[target] = composition.sourceIndex[i];
                tmpKeys[target] = key;
            }

            System.arraycopy(tmpDomains, 0, composition.sourceDomain, 0, n);
            System.arraycopy(tmpSlots, 0, composition.sourceIndex, 0, n);
            System.arraycopy(tmpKeys, 0, composition.sortKey, 0, n);
        }
    }

    private void flattenComposition(RenderCompositionList composition) {
        drawList.clearEntries();
        int[] visibleRefs = tiledState != null ? tiledState.getVisibleRefs() : null;

        for (int i = 0; i < composition.size; i++) {
            byte domain = composition.sourceDomain[i];
            int source = composition.sourceIndex[i];
            if (domain == RenderSourceDomain.SOURCE_GAME_OBJECT) {
                flattenGameObject(source);
                continue;
            }
            if (domain != RenderSourceDomain.SOURCE_TILED) {
                drawList.add(domain, source);
                continue;
            }

            if (tiledState == null || source < 0 || source >= tiledState.getVisibleMapCount()) {
                continue;
            }
            int start = tiledState.visibleMapRefStart(source);
            int refCount = tiledState.visibleMapRefCount(source);
            sortTiledRefSlice(visibleRefs, start, refCount);
            for (int refOffset = 0; refOffset < refCount; refOffset++) {
                int ref = visibleRefs[start + refOffset];
                if (tiledState.isRenderableRef(ref)) drawList.addTiledSlot(ref);
            }
        }
    }

    private void flattenGameObject(int rootEntityId) {
        if (gameObjectComposition == null || ecsState == null) return;
        GameObjectCompositionState state = gameObjectComposition.state();
        if (rootEntityId < 0 || rootEntityId >= state.getEntityCapacity()
                || !state.hierarchyVisible[rootEntityId]) {
            return;
        }
        hierarchyStack.clear();
        pushChildrenReverse(rootEntityId, state);
        while (hierarchyStack.size > 0) {
            int entityId = hierarchyStack.pop();
            if (!state.hierarchyVisible[entityId]) continue;
            if (gameObjects.has(entityId)) {
                pushChildrenReverse(entityId, state);
                continue;
            }
            int slot = ecsState.renderSlotForEntity(entityId);
            if (slot != DynamicEntityRenderState.NO_SLOT
                    && slot < ecsState.activeCount
                    && ecsState.enabled[slot]
                    && ecsState.visible[slot]
                    && ecsState.kind[slot] == RenderKind.SPRITE) {
                drawList.addEcsSlot(slot);
            }
        }
    }

    private void pushChildrenReverse(int parentEntityId, GameObjectCompositionState state) {
        childScratch.clear();
        for (int child = state.orderedFirstChildEntityId[parentEntityId]; child >= 0;
             child = state.orderedNextSiblingEntityId[child]) {
            childScratch.add(child);
        }
        for (int i = childScratch.size - 1; i >= 0; i--) {
            hierarchyStack.add(childScratch.get(i));
        }
    }

    private void sortTiledRefSlice(int[] refs, int start, int refCount) {
        if (refs == null || refCount <= 1) return;
        ensureTiledTmpCapacity(refCount);

        for (int pass = 0; pass < 8; pass++) {
            int shift = pass * 8;
            for (int i = 0; i < 256; i++) count[i] = 0;

            for (int i = 0; i < refCount; i++) {
                long key = tiledState.sortKey[refs[start + i]];
                count[(int) ((key >>> shift) & 0xFFL)]++;
            }

            int sum = 0;
            for (int bucket = 0; bucket < 256; bucket++) {
                int bucketCount = count[bucket];
                count[bucket] = sum;
                sum += bucketCount;
            }

            for (int i = 0; i < refCount; i++) {
                int ref = refs[start + i];
                long key = tiledState.sortKey[ref];
                tmpTiledRefs[count[(int) ((key >>> shift) & 0xFFL)]++] = ref;
            }
            System.arraycopy(tmpTiledRefs, 0, refs, start, refCount);
        }
    }

    private void sortFlatDrawList() {
        final int n = drawList.size;
        if (n <= 1) return;

        final int[] slots = drawList.data();
        final byte[] domains = drawList.domainData();
        ensureTmpCapacity(n);

        // LSD radix: 8 passes * 8 bits = 64 bits
        // stable if using: prefix sums + write to tmp in order
        for (int pass = 0; pass < 8; pass++) {
            final int shift = pass * 8;

            // reset count
            for (int i = 0; i < 256; i++) count[i] = 0;

            // histogram
            for (int i = 0; i < n; i++) {
                long key = sortKeyForEntry(domains[i], slots[i]);
                int bucket = (int) ((key >>> shift) & 0xFFL);
                count[bucket]++;
            }

            // prefix sums -> positions
            int sum = 0;
            for (int b = 0; b < 256; b++) {
                int c = count[b];
                count[b] = sum;
                sum += c;
            }

            // stable scatter into tmp
            for (int i = 0; i < n; i++) {
                int slot = slots[i];
                byte domain = domains[i];
                long key = sortKeyForEntry(domain, slot);
                int bucket = (int) ((key >>> shift) & 0xFFL);
                int target = count[bucket]++;
                tmpSlots[target] = slot;
                tmpDomains[target] = domain;
            }

            // copy back
            System.arraycopy(tmpSlots, 0, slots, 0, n);
            System.arraycopy(tmpDomains, 0, domains, 0, n);
        }
    }

    private long sortKeyForEntry(byte domain, int slot) {
        if (domain == RenderSourceDomain.SOURCE_VFX) {
            return vfxState != null && slot >= 0 && slot < vfxState.activeCount
                    ? vfxState.sortKey[slot]
                    : 0L;
        }
        if (domain == RenderSourceDomain.SOURCE_TILED) {
            return tiledState != null && slot >= 0 && slot < tiledState.getRefCount()
                    ? tiledState.sortKey[slot]
                    : 0L;
        }
        if (domain == RenderSourceDomain.SOURCE_ECS && ecsState != null && slot >= 0 && slot < ecsState.activeCount) {
            return ecsState.sortKey[slot];
        }
        return 0L;
    }

    private void ensureTmpCapacity(int n) {
        if (tmpSlots.length < n) {
            int next = Math.max(n, tmpSlots.length * 2 + 16);
            tmpSlots = new int[next];
            tmpDomains = new byte[next];
            tmpKeys = new long[next];
        }
    }

    private void ensureTiledTmpCapacity(int n) {
        if (tmpTiledRefs.length < n) {
            int next = Math.max(n, tmpTiledRefs.length * 2 + 16);
            tmpTiledRefs = new int[next];
        }
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
