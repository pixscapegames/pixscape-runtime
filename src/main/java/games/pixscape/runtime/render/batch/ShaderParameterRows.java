package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ShaderFloatParam;

/** CPU staging for a draw's numeric parameters; row zero holds shader defaults. */
public final class ShaderParameterRows {
    public static final int FLOATS_PER_ROW = ShaderParameterLayout.MAX_FLOATS;
    public static final int DEFAULT_CAPACITY = 1024;

    private final int capacity;
    private final float[] rows;
    private final float[] candidate = new float[FLOATS_PER_ROW];
    private final float[] current = new float[FLOATS_PER_ROW];
    private final int[] rowIndex;
    private ShaderParameterLayout layout = ShaderParameterLayout.EMPTY;
    private int rowCount = 1;
    private int currentId;

    public ShaderParameterRows(int capacity) {
        if (capacity < 3) throw new IllegalArgumentException("Parameter row capacity must be at least 3");
        this.capacity = capacity;
        rows = new float[capacity * FLOATS_PER_ROW];
        int indexSize = 1;
        while (indexSize < capacity * 2) indexSize <<= 1;
        rowIndex = new int[indexSize];
    }

    public void setLayout(ShaderParameterLayout next) {
        layout = next == null ? ShaderParameterLayout.EMPTY : next;
        rowCount = 1;
        currentId = 0;
        for (int i = 0; i < FLOATS_PER_ROW; i++) {
            rows[i] = i < layout.size() ? layout.defaultValue(i) : 0f;
            current[i] = rows[i];
        }
        clearIndex();
        indexRow(0);
    }

    public boolean isFull() { return rowCount == capacity; }
    public int capacity() { return capacity; }
    public int rowCount() { return rowCount; }
    public int currentId() { return currentId; }
    public float[] data() { return rows; }

    /** Returns an existing/new row, or -1 when a distinct row requires a draw flush first. */
    public int setEntityParameters(Array<ShaderFloatParam> overrides) {
        int count = overrides == null ? 0 : overrides.size;
        if (count == 0) {
            currentId = 0;
            return 0;
        }
        if (layout.size() == 0) {
            throw new IllegalArgumentException("Shader '" + layout.shaderName()
                    + "' declares no per-entity float parameters");
        }
        System.arraycopy(rows, 0, candidate, 0, FLOATS_PER_ROW);
        int seenSlots = 0;
        for (int i = 0; i < count; i++) {
            ShaderFloatParam param = overrides.get(i);
            if (param == null || param.name == null || Float.isNaN(param.value)
                    || Float.isInfinite(param.value)) {
                throw new IllegalArgumentException("Invalid float parameter for shader '" + layout.shaderName() + "'");
            }
            int slot = layout.slot(param.name);
            if (slot < 0) {
                throw new IllegalArgumentException("Shader '" + layout.shaderName()
                        + "' has no per-entity float parameter '" + param.name + "'");
            }
            int bit = 1 << slot;
            if ((seenSlots & bit) != 0) {
                throw new IllegalArgumentException("Duplicate per-entity float parameter '"
                        + param.name + "' for shader '" + layout.shaderName() + "'");
            }
            seenSlots |= bit;
            candidate[slot] = param.value;
        }
        int existing = findRow(candidate);
        if (existing >= 0) {
            currentId = existing;
            System.arraycopy(candidate, 0, current, 0, FLOATS_PER_ROW);
            return existing;
        }
        if (isFull()) return -1;
        int offset = rowCount * FLOATS_PER_ROW;
        System.arraycopy(candidate, 0, rows, offset, FLOATS_PER_ROW);
        currentId = rowCount++;
        System.arraycopy(candidate, 0, current, 0, FLOATS_PER_ROW);
        indexRow(currentId);
        return currentId;
    }

    /** Called after a draw: old rows may now be reused, while repeated quads retain current values. */
    public void afterFlush() {
        if (rowCount == 1 && currentId == 0) return;
        rowCount = 1;
        clearIndex();
        indexRow(0);
        if (currentId != 0) {
            System.arraycopy(current, 0, rows, FLOATS_PER_ROW, FLOATS_PER_ROW);
            currentId = 1;
            rowCount = 2;
            indexRow(1);
        }
    }

    private void clearIndex() {
        java.util.Arrays.fill(rowIndex, 0);
    }

    private int findRow(float[] values) {
        int slot = hash(values, 0) & (rowIndex.length - 1);
        while (rowIndex[slot] != 0) {
            int row = rowIndex[slot] - 1;
            int offset = row * FLOATS_PER_ROW;
            boolean equal = true;
            for (int i = 0; i < FLOATS_PER_ROW; i++) {
                if (Float.floatToIntBits(rows[offset + i]) != Float.floatToIntBits(values[i])) {
                    equal = false;
                    break;
                }
            }
            if (equal) return row;
            slot = (slot + 1) & (rowIndex.length - 1);
        }
        return -1;
    }

    private void indexRow(int row) {
        int slot = hash(rows, row * FLOATS_PER_ROW) & (rowIndex.length - 1);
        while (rowIndex[slot] != 0) slot = (slot + 1) & (rowIndex.length - 1);
        rowIndex[slot] = row + 1;
    }

    private static int hash(float[] values, int offset) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < FLOATS_PER_ROW; i++) {
            hash = (hash ^ Float.floatToIntBits(values[offset + i])) * 0x01000193;
        }
        return hash;
    }
}
