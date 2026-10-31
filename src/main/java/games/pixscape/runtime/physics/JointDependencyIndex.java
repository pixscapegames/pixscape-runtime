package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.IntArray;
import java.util.Arrays;

/** INTERNAL joint relations owned by one World's Box2dSyncSystem, even without native Physics. */
public final class JointDependencyIndex {
    private final Links bodies = new Links();
    private final Links sources = new Links();

    public void publish(int joint, int bodyA, int bodyB, int sourceA, int sourceB) {
        bodies.publish(joint, bodyA, bodyB);
        sources.publish(joint, sourceA, sourceB);
    }

    public void remove(int joint) {
        bodies.remove(joint);
        sources.remove(joint);
    }

    /** Borrowed lists. Do not modify or retain them across relation publication. */
    public IntArray jointsForBody(int body) { return bodies.get(body); }
    public IntArray gearsForSource(int source) { return sources.get(source); }

    /** Two outgoing relations per joint, with reverse bucket positions for O(1) unlinking. */
    private static final class Links {
        private IntArray[] bySource = new IntArray[16];
        private int[] a = empty(16), b = empty(16), slotA = empty(16), slotB = empty(16);

        IntArray get(int source) {
            return source >= 0 && source < bySource.length ? bySource[source] : null;
        }

        void publish(int joint, int first, int second) {
            if (second == first) second = -1;
            ensure(Math.max(joint, Math.max(first, second)));
            if (a[joint] == first && b[joint] == second) return;
            remove(joint);
            a[joint] = first;
            b[joint] = second;
            slotA[joint] = add(first, joint);
            slotB[joint] = add(second, joint);
        }

        void remove(int joint) {
            if (joint < 0 || joint >= a.length) return;
            unlink(a[joint], slotA[joint]);
            unlink(b[joint], slotB[joint]);
            a[joint] = b[joint] = slotA[joint] = slotB[joint] = -1;
        }

        private int add(int source, int joint) {
            if (source < 0) return -1;
            IntArray list = bySource[source];
            if (list == null) bySource[source] = list = new IntArray(false, 4);
            int slot = list.size;
            list.add(joint);
            return slot;
        }

        private void unlink(int source, int slot) {
            if (slot < 0) return;
            IntArray list = bySource[source];
            int moved = list.peek();
            list.removeIndex(slot);
            if (slot < list.size) {
                if (a[moved] == source) slotA[moved] = slot;
                else slotB[moved] = slot;
            }
        }

        private void ensure(int entity) {
            if (entity < a.length) return;
            int old = a.length, size = old;
            while (size <= entity) size <<= 1;
            bySource = Arrays.copyOf(bySource, size);
            a = grow(a, old, size); b = grow(b, old, size);
            slotA = grow(slotA, old, size); slotB = grow(slotB, old, size);
        }

        private static int[] empty(int size) {
            int[] result = new int[size];
            Arrays.fill(result, -1);
            return result;
        }

        private static int[] grow(int[] values, int old, int size) {
            int[] result = Arrays.copyOf(values, size);
            Arrays.fill(result, old, size, -1);
            return result;
        }
    }
}
