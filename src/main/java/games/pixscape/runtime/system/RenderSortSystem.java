package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.VfxRenderState;

/**
 * Trie drawList (indices SOA) par state.sortKey[slot].
 * <p>
 * Important:
 * - drawList contains "slots" RenderStateSOA, not entityId ECS.
 * - STABLE sort (LSD radix) => preserves relative order for equal keys
 * (useful for tie/runtimeOrder).
 */
public final class RenderSortSystem extends BaseSystem implements ProfiledSystem {

    private final RenderStateSOA state;
    private final VfxRenderState vfxState;
    private final DrawList drawList;
    private final int vfxStartInclusive;
    private final int vfxEndExclusive;

    // scratch buffers (reused)
    private int[] tmp = new int[0];
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
        this.vfxStartInclusive = vfxStartInclusive;
        this.vfxEndExclusive = vfxEndExclusive;
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

        final int[] data = drawList.data();
        ensureTmpCapacity(n);

        // LSD radix: 8 passes * 8 bits = 64 bits
        // stable if using: prefix sums + write to tmp in order
        for (int pass = 0; pass < 8; pass++) {
            final int shift = pass * 8;

            // reset count
            for (int i = 0; i < 256; i++) count[i] = 0;

            // histogram
            for (int i = 0; i < n; i++) {
                long key = sortKeyForSlot(data[i]);
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
                int slot = data[i];
                long key = sortKeyForSlot(slot);
                int bucket = (int) ((key >>> shift) & 0xFFL);
                tmp[count[bucket]++] = slot;
            }

            // copy back
            System.arraycopy(tmp, 0, data, 0, n);
        }
    }

    private long sortKeyForSlot(int slot) {
        int vfxIndex = vfxIndex(slot);
        if (vfxIndex >= 0) {
            return vfxState.sortKey[vfxIndex];
        }
        return state.sortKey[slot];
    }

    private int vfxIndex(int slot) {
        if (vfxState == null
                || vfxStartInclusive < 0
                || slot < vfxStartInclusive
                || slot >= vfxEndExclusive) {
            return -1;
        }

        int index = slot - vfxStartInclusive;
        return index >= 0 && index < vfxState.activeCount ? index : -1;
    }

    private void ensureTmpCapacity(int n) {
        if (tmp.length < n) {
            tmp = new int[Math.max(n, tmp.length * 2 + 16)];
        }
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
