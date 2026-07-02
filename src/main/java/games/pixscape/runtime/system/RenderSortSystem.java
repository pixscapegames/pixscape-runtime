package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.VfxRenderState;

/**
 * Trie drawList par sortKey de chaque source render.
 * <p>
 * Important:
 * - drawList entries carry a source domain and a source slot/index.
 * - STABLE sort (LSD radix) => preserves relative order for equal keys
 * (useful for tie/runtimeOrder).
 */
public final class RenderSortSystem extends BaseSystem implements ProfiledSystem {

    private final RenderStateSOA state;
    private final VfxRenderState vfxState;
    private final DrawList drawList;

    // scratch buffers (reused)
    private int[] tmpSlots = new int[0];
    private byte[] tmpDomains = new byte[0];
    private final int[] count = new int[256]; // 8 bits
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public RenderSortSystem(RenderStateSOA state, DrawList drawList) {
        this(state, null, drawList, -1, -1);
    }

    public RenderSortSystem(RenderStateSOA state,
                            VfxRenderState vfxState,
                            DrawList drawList,
                            int vfxStartInclusive,
                            int vfxEndExclusive) {
        this.state = state;
        this.vfxState = vfxState;
        this.drawList = drawList;
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
        if ((domain == RenderSourceDomain.SOURCE_ECS || domain == RenderSourceDomain.SOURCE_TILED)
                && slot >= 0
                && slot < state.getCapacity()) {
            return state.sortKey[slot];
        }
        return 0L;
    }

    private void ensureTmpCapacity(int n) {
        if (tmpSlots.length < n) {
            int next = Math.max(n, tmpSlots.length * 2 + 16);
            tmpSlots = new int[next];
            tmpDomains = new byte[next];
        }
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
