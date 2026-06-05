package games.pixscape.runtime.system;

final class SpatialActorDebugState {
    static final boolean ENABLED = Boolean.getBoolean("pixscape.debugSpatialActors");
    static final String[] NAME_FILTER = parseNames(System.getProperty("pixscape.debugSpatialActors.names", ""));

    private static int[] originalDrawIndex = new int[0];
    private static int[] frameIds = new int[0];
    private static boolean[] spatialEnabledLayer = new boolean[0];
    private static int frameId = 1;

    private SpatialActorDebugState() {
    }

    static void beginFrame(int capacity) {
        if (!ENABLED) return;
        ensureCapacity(capacity);
        frameId++;
        if (frameId == 0) {
            for (int i = 0, n = frameIds.length; i < n; i++) {
                frameIds[i] = 0;
            }
            frameId = 1;
        }
    }

    static void recordOriginal(int slot, int originalIndex, boolean layerSpatialEnabled) {
        if (!ENABLED || slot < 0) return;
        ensureCapacity(slot + 1);
        originalDrawIndex[slot] = originalIndex;
        spatialEnabledLayer[slot] = layerSpatialEnabled;
        frameIds[slot] = frameId;
    }

    static int originalDrawIndex(int slot) {
        if (!hasRecord(slot)) return -1;
        return originalDrawIndex[slot];
    }

    static boolean spatialEnabledLayer(int slot) {
        return hasRecord(slot) && spatialEnabledLayer[slot];
    }

    static boolean hasRecord(int slot) {
        return ENABLED
                && slot >= 0
                && slot < frameIds.length
                && frameIds[slot] == frameId;
    }

    static boolean nameAllowed(String name) {
        if (!ENABLED) return false;
        if (NAME_FILTER.length == 0) return true;
        if (name == null) return false;
        for (String allowed : NAME_FILTER) {
            if (name.equals(allowed)) return true;
        }
        return false;
    }

    private static void ensureCapacity(int required) {
        if (required <= originalDrawIndex.length) return;

        int next = Math.max(8, originalDrawIndex.length);
        while (required > next) next <<= 1;

        int[] expandedIndex = new int[next];
        System.arraycopy(originalDrawIndex, 0, expandedIndex, 0, originalDrawIndex.length);
        originalDrawIndex = expandedIndex;

        int[] expandedFrames = new int[next];
        System.arraycopy(frameIds, 0, expandedFrames, 0, frameIds.length);
        frameIds = expandedFrames;

        boolean[] expandedSpatial = new boolean[next];
        System.arraycopy(spatialEnabledLayer, 0, expandedSpatial, 0, spatialEnabledLayer.length);
        spatialEnabledLayer = expandedSpatial;
    }

    private static String[] parseNames(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new String[0];
        String[] parts = raw.split(",");
        int count = 0;
        for (int i = 0; i < parts.length; i++) {
            String name = parts[i].trim();
            if (!name.isEmpty()) {
                parts[count++] = name;
            }
        }
        String[] out = new String[count];
        System.arraycopy(parts, 0, out, 0, count);
        return out;
    }
}
