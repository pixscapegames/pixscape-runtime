package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;

import java.util.Arrays;

/** Runtime-owned, transactionally published compilation of every Spatial V3 structure in one layer. */
public final class SpatialCompiledLayerCache {
    private SpatialBlocksComponent source;
    private int sourceRevision = Integer.MIN_VALUE;
    private CompiledSpatialStructure[] structures = new CompiledSpatialStructure[0];
    private int structureCount;
    private int revision;
    private int compilationCount;

    public boolean ensure(SpatialBlocksComponent component) {
        int requestedRevision = component != null ? component.revision : 0;
        if (source == component && sourceRevision == requestedRevision) return false;

        int[] ids = collectStructureIds(component);
        CompiledSpatialStructure[] next = new CompiledSpatialStructure[ids.length];
        for (int i = 0; i < ids.length; i++) {
            next[i] = SpatialStructureCompiler.compile(component.blocks, ids[i]);
        }
        source = component;
        sourceRevision = requestedRevision;
        structures = next;
        structureCount = next.length;
        revision++;
        compilationCount++;
        return true;
    }

    public int revision() { return revision; }
    public int structureCount() { return structureCount; }
    public CompiledSpatialStructure structure(int index) { return structures[index]; }
    public int compilationCount() { return compilationCount; }

    private static int[] collectStructureIds(SpatialBlocksComponent component) {
        if (component == null || component.blocks == null || component.blocks.size == 0) return new int[0];
        int[] ids = new int[component.blocks.size];
        int count = 0;
        for (int i = 0; i < component.blocks.size; i++) {
            SpatialBlockData wall = component.blocks.get(i);
            if (wall == null || wall.structureId <= 0) continue;
            boolean seen = false;
            for (int j = 0; j < count; j++) if (ids[j] == wall.structureId) { seen = true; break; }
            if (!seen) ids[count++] = wall.structureId;
        }
        Arrays.sort(ids, 0, count);
        return Arrays.copyOf(ids, count);
    }
}
