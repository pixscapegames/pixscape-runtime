package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderStateSOA;

/**
 * Trie drawList (indices SOA) par state.sortKey[slot].
 *
 * Important:
 * - drawList contains "slots" RenderStateSOA, not entityId ECS.
 * - STABLE sort (LSD radix) => preserves relative order for equal keys
 *   (useful for tie/runtimeOrder).
 */
public final class RenderSortSystem extends BaseSystem {

    private final RenderStateSOA state;
    private final DrawList drawList;

    // scratch buffers (reused)
    private int[] tmp = new int[0];
    private final int[] count = new int[256]; // 8 bits

    public RenderSortSystem(RenderStateSOA state, DrawList drawList) {
        this.state = state;
        this.drawList = drawList;
    }

    @Override
    protected void processSystem() {
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
                long key = state.sortKey[data[i]];
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
                long key = state.sortKey[slot];
                int bucket = (int) ((key >>> shift) & 0xFFL);
                tmp[count[bucket]++] = slot;
            }

            // copy back
            System.arraycopy(tmp, 0, data, 0, n);
        }
    }

    private void ensureTmpCapacity(int n) {
        if (tmp.length < n) {
            tmp = new int[Math.max(n, tmp.length * 2 + 16)];
        }
    }
}
