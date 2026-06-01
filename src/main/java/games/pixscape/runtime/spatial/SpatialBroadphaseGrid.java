package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;

public final class SpatialBroadphaseGrid {
    private static final float MIN_CELL_SIZE = 0.0001f;

    private final IntMap<IntArray> cells = new IntMap<>();
    private final IntSet dedupe = new IntSet();
    private float cellSize;

    public SpatialBroadphaseGrid(float cellSize) {
        this.cellSize = Math.max(MIN_CELL_SIZE, cellSize);
    }

    public void setCellSize(float cellSize) {
        float next = Math.max(MIN_CELL_SIZE, cellSize);
        if (this.cellSize == next) return;
        this.cellSize = next;
        clear();
    }

    public float getCellSize() {
        return cellSize;
    }

    public void clear() {
        cells.clear();
        dedupe.clear();
    }

    public void insert(int id, SpatialVolume volume) {
        if (id < 0 || volume == null) return;

        int minCellX = toCell(volume.footprintMinX);
        int maxCellX = toCell(volume.footprintMaxX);
        int minCellY = toCell(volume.footprintMinY);
        int maxCellY = toCell(volume.footprintMaxY);

        for (int cy = minCellY; cy <= maxCellY; cy++) {
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                IntArray bucket = cells.get(packCell(cx, cy));
                if (bucket == null) {
                    bucket = new IntArray(false, 4);
                    cells.put(packCell(cx, cy), bucket);
                }
                bucket.add(id);
            }
        }
    }

    public IntArray query(SpatialVolume volume, IntArray out) {
        if (out == null) out = new IntArray(false, 16);
        out.clear();
        if (volume == null) return out;

        dedupe.clear();

        int minCellX = toCell(volume.footprintMinX);
        int maxCellX = toCell(volume.footprintMaxX);
        int minCellY = toCell(volume.footprintMinY);
        int maxCellY = toCell(volume.footprintMaxY);

        for (int cy = minCellY; cy <= maxCellY; cy++) {
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                IntArray bucket = cells.get(packCell(cx, cy));
                if (bucket == null) continue;

                for (int i = 0, n = bucket.size; i < n; i++) {
                    int id = bucket.get(i);
                    if (dedupe.add(id)) {
                        out.add(id);
                    }
                }
            }
        }

        return out;
    }

    public int bucketCount() {
        return cells.size;
    }

    private int toCell(float value) {
        return (int) Math.floor(value / cellSize);
    }

    private int packCell(int cx, int cy) {
        return (cx << 16) ^ (cy & 0xFFFF);
    }
}
