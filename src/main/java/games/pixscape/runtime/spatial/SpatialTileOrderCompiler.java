package games.pixscape.runtime.spatial;

import games.pixscape.runtime.tiled.TiledMapLayerData;

import java.util.Arrays;

/** Compiles actor-independent lower-edge constraints into one canonical tile rank per occupied cell. */
public final class SpatialTileOrderCompiler {
    private static final float EPSILON = SpatialLineRelation.EPSILON;
    private static final long ORDER_CAPACITY = 1L << 30;

    private int nodeCount;
    private int[] nodeGx = new int[0];
    private int[] nodeGy = new int[0];
    private int[] nodeByCell = new int[0];

    private int segmentCount;
    private int[] segmentNode = new int[0];
    private int[] segmentStructure = new int[0];
    private int[] segmentFace = new int[0];
    private float[] segmentMinX = new float[0];
    private float[] segmentMaxX = new float[0];
    private float[] segmentSlope = new float[0];
    private float[] segmentIntercept = new float[0];
    private int[] segmentOrder = new int[0];
    private int[] active = new int[0];
    private long[] edges = new long[0];
    private int[] edgeStructure = new int[0];
    private int[] edgeFace = new int[0];
    private int edgeCount;
    private final float[] projected = new float[4];

    public void compile(int layerEntity,
                        TiledMapLayerData map,
                        SpatialCompiledLayerCache compiled,
                        SpatialTileOrderCache target) {
        if (map.mapWidth < 0 || map.mapHeight < 0
                || (long) map.mapWidth * (long) map.mapHeight > Integer.MAX_VALUE) {
            throw new SpatialTileOrderInvariantException("Spatial tile order layer " + layerEntity
                    + " has an unsupported map capacity.");
        }
        buildNodes(map);
        if ((long) nodeCount > ORDER_CAPACITY) {
            throw new SpatialTileOrderInvariantException("Spatial tile order layer " + layerEntity
                    + " has " + nodeCount + " occupied tiles; the 30-bit order capacity is "
                    + ORDER_CAPACITY + ".");
        }
        buildSegments(layerEntity, map, compiled);
        buildEdges(layerEntity);
        int[] ranks = topologicalRanks(layerEntity);
        int[] rankByCell = new int[map.mapWidth * map.mapHeight];
        Arrays.fill(rankByCell, -1);
        for (int node = 0; node < nodeCount; node++) {
            rankByCell[nodeGy[node] * map.mapWidth + nodeGx[node]] = ranks[node];
        }
        target.publish(rankByCell, nodeCount, segmentCount, edgeCount);
    }

    int[] compileGraphForTest(int layerEntity, int[] gx, int[] gy, int[] from, int[] to) {
        if (gx.length != gy.length || from.length != to.length) throw new IllegalArgumentException();
        ensureNodeCapacity(gx.length);
        nodeCount = gx.length;
        System.arraycopy(gx, 0, nodeGx, 0, nodeCount);
        System.arraycopy(gy, 0, nodeGy, 0, nodeCount);
        edgeCount = 0;
        for (int i = 0; i < from.length; i++) addEdge(from[i], to[i], 1, i);
        if (edgeCount > 1) sortEdgesWithSources(0, edgeCount - 1);
        return topologicalRanks(layerEntity);
    }

    private void buildNodes(TiledMapLayerData map) {
        int cells = map.mapWidth * map.mapHeight;
        ensureNodeCapacity(cells);
        if (nodeByCell.length < cells) nodeByCell = new int[cells];
        Arrays.fill(nodeByCell, 0, cells, -1);
        nodeCount = 0;
        for (int gy = 0; gy < map.mapHeight; gy++) {
            for (int gx = 0; gx < map.mapWidth; gx++) {
                if (map.getTile(gx, gy) <= 0) continue;
                nodeGx[nodeCount] = gx;
                nodeGy[nodeCount] = gy;
                nodeByCell[gy * map.mapWidth + gx] = nodeCount++;
            }
        }
    }

    private void buildSegments(int layerEntity, TiledMapLayerData map, SpatialCompiledLayerCache compiled) {
        segmentCount = 0;
        for (int structureIndex = 0; structureIndex < compiled.structureCount(); structureIndex++) {
            CompiledSpatialStructure structure = compiled.structure(structureIndex);
            CompiledSpatialStructure.FaceSet faces = structure.actorOccluder();
            for (int face = 0; face < faces.faceCount(); face++) {
                int anchorStart = faces.anchorCellStart(face);
                int anchorEnd = anchorStart + faces.anchorCellCount(face);
                for (int anchor = anchorStart; anchor < anchorEnd; anchor++) {
                    int gx = faces.anchorGx(anchor);
                    int gy = faces.anchorGy(anchor);
                    int node = nodeAt(map, gx, gy);
                    if (node < 0) {
                        throw new SpatialTileOrderInvariantException("Spatial tile order layer " + layerEntity
                                + " has unresolved anchor (" + gx + "," + gy + ") for structure "
                                + structure.structureId() + " face " + face + ".");
                    }
                    if (!SpatialAnchoredSegmentProjection.project(faces, face, gx, gy,
                            structure.altitude(), map, projected)) continue;
                    addSegment(node, structure.structureId(), face,
                            projected[0], projected[1], projected[2], projected[3]);
                }
            }
        }
        canonicalizeSegments();
    }

    private void addSegment(int node, int structure, int face, float x1, float y1, float x2, float y2) {
        ensureSegmentCapacity(segmentCount + 1);
        if (x2 < x1) {
            float swap = x1; x1 = x2; x2 = swap;
            swap = y1; y1 = y2; y2 = swap;
        }
        int segment = segmentCount++;
        segmentNode[segment] = node;
        segmentStructure[segment] = structure;
        segmentFace[segment] = face;
        segmentMinX[segment] = x1;
        segmentMaxX[segment] = x2;
        segmentSlope[segment] = (y2 - y1) / (x2 - x1);
        segmentIntercept[segment] = y1 - segmentSlope[segment] * x1;
    }

    private void canonicalizeSegments() {
        ensureSegmentOrderCapacity(segmentCount);
        for (int i = 0; i < segmentCount; i++) segmentOrder[i] = i;
        sortSegments(0, segmentCount - 1);
        int[] nextNode = new int[segmentCount];
        int[] nextStructure = new int[segmentCount];
        int[] nextFace = new int[segmentCount];
        float[] nextMinX = new float[segmentCount];
        float[] nextMaxX = new float[segmentCount];
        float[] nextSlope = new float[segmentCount];
        float[] nextIntercept = new float[segmentCount];
        int write = 0;
        for (int i = 0; i < segmentCount; i++) {
            int source = segmentOrder[i];
            if (i > 0 && equivalentSegment(segmentOrder[i - 1], source)) continue;
            nextNode[write] = segmentNode[source];
            nextStructure[write] = segmentStructure[source];
            nextFace[write] = segmentFace[source];
            nextMinX[write] = segmentMinX[source];
            nextMaxX[write] = segmentMaxX[source];
            nextSlope[write] = segmentSlope[source];
            nextIntercept[write] = segmentIntercept[source];
            write++;
        }
        segmentNode = nextNode; segmentStructure = nextStructure; segmentFace = nextFace;
        segmentMinX = nextMinX; segmentMaxX = nextMaxX; segmentSlope = nextSlope;
        segmentIntercept = nextIntercept;
        segmentCount = write;
        for (int i = 0; i < segmentCount; i++) segmentOrder[i] = i;
    }

    private boolean equivalentSegment(int canonical, int candidate) {
        return segmentNode[canonical] == segmentNode[candidate]
                && near(segmentMinX[canonical], segmentMinX[candidate])
                && near(segmentMaxX[canonical], segmentMaxX[candidate])
                && near(segmentSlope[canonical], segmentSlope[candidate])
                && near(segmentIntercept[canonical], segmentIntercept[candidate]);
    }

    private void buildEdges(int layerEntity) {
        edgeCount = 0;
        int activeCount = 0;
        ensureActiveCapacity(segmentCount);
        for (int ordered = 0; ordered < segmentCount; ordered++) {
            int current = segmentOrder[ordered];
            int retained = 0;
            for (int i = 0; i < activeCount; i++) {
                int candidate = active[i];
                if (segmentMaxX[candidate] - segmentMinX[current] > EPSILON) active[retained++] = candidate;
            }
            activeCount = retained;
            for (int i = 0; i < activeCount; i++) compareSegments(layerEntity, active[i], current);
            active[activeCount++] = current;
        }
        if (edgeCount == 0) return;
        sortEdgesWithSources(0, edgeCount - 1);
        int write = 1;
        for (int read = 1; read < edgeCount; read++) {
            if (edges[read] == edges[write - 1]) continue;
            edges[write] = edges[read];
            edgeStructure[write] = edgeStructure[read];
            edgeFace[write] = edgeFace[read];
            write++;
        }
        edgeCount = write;
    }

    private void compareSegments(int layerEntity, int first, int second) {
        int firstNode = segmentNode[first];
        int secondNode = segmentNode[second];
        if (firstNode == secondNode) return;
        float overlapMin = Math.max(segmentMinX[first], segmentMinX[second]);
        float overlapMax = Math.min(segmentMaxX[first], segmentMaxX[second]);
        float width = overlapMax - overlapMin;
        if (width <= EPSILON) return;
        float inset = Math.min(width * 0.25f, EPSILON);
        float leftDifference = lineY(first, overlapMin + inset) - lineY(second, overlapMin + inset);
        float rightDifference = lineY(first, overlapMax - inset) - lineY(second, overlapMax - inset);
        int leftSign = sign(leftDifference);
        int rightSign = sign(rightDifference);
        if (leftSign != 0 && rightSign != 0 && leftSign != rightSign) {
            throw new SpatialTileOrderInvariantException("Spatial tile order layer " + layerEntity
                    + " has crossing anchored segments at nodes " + anchor(firstNode) + " and "
                    + anchor(secondNode) + ", structures " + segmentStructure[first] + "/"
                    + segmentStructure[second] + ", faces " + segmentFace[first] + "/" + segmentFace[second] + ".");
        }
        float witnessX = (overlapMin + overlapMax) * 0.5f;
        float firstY = lineY(first, witnessX);
        float secondY = lineY(second, witnessX);
        if (Math.abs(firstY - secondY) <= EPSILON) return;
        float witnessY = (firstY + secondY) * 0.5f;
        byte firstRelation = SpatialLineRelation.relation(firstY, witnessY);
        int earlier = firstRelation == SpatialFaceRelationSolver.ACTOR_BEHIND_FACE ? secondNode : firstNode;
        int later = earlier == firstNode ? secondNode : firstNode;
        int source = earlier == firstNode ? first : second;
        addEdge(earlier, later, segmentStructure[source], segmentFace[source]);
    }

    private int[] topologicalRanks(int layerEntity) {
        int[] outgoingCount = new int[nodeCount];
        int[] indegree = new int[nodeCount];
        for (int i = 0; i < edgeCount; i++) {
            int from = edgeFrom(i);
            int to = edgeTo(i);
            outgoingCount[from]++;
            indegree[to]++;
        }
        int[] outgoingStart = new int[nodeCount];
        int total = 0;
        for (int node = 0; node < nodeCount; node++) {
            outgoingStart[node] = total;
            total += outgoingCount[node];
        }
        int[] cursor = Arrays.copyOf(outgoingStart, nodeCount);
        int[] outgoing = new int[edgeCount];
        for (int i = 0; i < edgeCount; i++) outgoing[cursor[edgeFrom(i)]++] = edgeTo(i);

        int[] heap = new int[nodeCount];
        int heapSize = 0;
        for (int node = 0; node < nodeCount; node++) if (indegree[node] == 0) heapSize = heapAdd(heap, heapSize, node);
        int[] ranks = new int[nodeCount];
        Arrays.fill(ranks, -1);
        int emitted = 0;
        while (heapSize > 0) {
            int node = heap[0];
            heapSize = heapRemove(heap, heapSize);
            ranks[node] = emitted++;
            int end = outgoingStart[node] + outgoingCount[node];
            for (int at = outgoingStart[node]; at < end; at++) {
                int next = outgoing[at];
                if (--indegree[next] == 0) heapSize = heapAdd(heap, heapSize, next);
            }
        }
        if (emitted != nodeCount) throw cycle(layerEntity, emitted, indegree);
        return ranks;
    }

    private SpatialTileOrderInvariantException cycle(int layerEntity, int emitted, int[] indegree) {
        StringBuilder message = new StringBuilder(192);
        message.append("Spatial tile order cycle in layer ").append(layerEntity)
                .append(": nodes=").append(nodeCount).append(", emitted=").append(emitted)
                .append(", cycleNodes=").append(nodeCount - emitted).append(", anchors=");
        int shown = 0;
        for (int node = 0; node < nodeCount && shown < 6; node++) {
            if (indegree[node] <= 0) continue;
            if (shown++ > 0) message.append(',');
            message.append(anchor(node));
        }
        message.append(", sources=");
        shown = 0;
        for (int edge = 0; edge < edgeCount && shown < 6; edge++) {
            if (indegree[edgeFrom(edge)] <= 0 || indegree[edgeTo(edge)] <= 0) continue;
            if (shown++ > 0) message.append(',');
            message.append(segmentSource(edgeStructure[edge], edgeFace[edge]));
        }
        return new SpatialTileOrderInvariantException(message.toString());
    }

    private int heapAdd(int[] heap, int size, int node) {
        int at = size++;
        while (at > 0) {
            int parent = (at - 1) >>> 1;
            if (compareNodes(heap[parent], node) <= 0) break;
            heap[at] = heap[parent];
            at = parent;
        }
        heap[at] = node;
        return size;
    }

    private int heapRemove(int[] heap, int size) {
        int replacement = heap[--size];
        if (size == 0) return 0;
        int at = 0;
        while (true) {
            int left = at * 2 + 1;
            if (left >= size) break;
            int right = left + 1;
            int child = right < size && compareNodes(heap[right], heap[left]) < 0 ? right : left;
            if (compareNodes(replacement, heap[child]) <= 0) break;
            heap[at] = heap[child];
            at = child;
        }
        heap[at] = replacement;
        return size;
    }

    /** Earlier means larger gx, then larger gy, then the stable row-major node identity. */
    int compareNodes(int first, int second) {
        if (nodeGx[first] != nodeGx[second]) return nodeGx[first] > nodeGx[second] ? -1 : 1;
        if (nodeGy[first] != nodeGy[second]) return nodeGy[first] > nodeGy[second] ? -1 : 1;
        return first < second ? -1 : first == second ? 0 : 1;
    }

    private void addEdge(int from, int to, int structure, int face) {
        ensureEdgeCapacity(edgeCount + 1);
        edges[edgeCount] = ((long) from << 32) | (to & 0xffffffffL);
        edgeStructure[edgeCount] = structure;
        edgeFace[edgeCount] = face;
        edgeCount++;
    }

    private int edgeFrom(int edge) { return (int) (edges[edge] >>> 32); }
    private int edgeTo(int edge) { return (int) edges[edge]; }
    private float lineY(int segment, float x) { return segmentSlope[segment] * x + segmentIntercept[segment]; }
    private int sign(float value) { return value < -EPSILON ? -1 : value > EPSILON ? 1 : 0; }
    private boolean near(float first, float second) { return Math.abs(first - second) <= EPSILON; }
    private int nodeAt(TiledMapLayerData map, int gx, int gy) {
        return gx < 0 || gy < 0 || gx >= map.mapWidth || gy >= map.mapHeight
                ? -1 : nodeByCell[gy * map.mapWidth + gx];
    }
    private String anchor(int node) { return "(" + nodeGx[node] + "," + nodeGy[node] + ")"; }
    private String segmentSource(int structure, int face) { return structure + "/" + face; }

    private int compareSegments(int first, int second) {
        int result = Float.compare(segmentMinX[first], segmentMinX[second]);
        if (result == 0) result = Float.compare(segmentMaxX[first], segmentMaxX[second]);
        if (result == 0) result = compareNodes(segmentNode[first], segmentNode[second]);
        if (result == 0) result = Float.compare(segmentSlope[first], segmentSlope[second]);
        if (result == 0) result = Float.compare(segmentIntercept[first], segmentIntercept[second]);
        if (result == 0) result = segmentStructure[first] - segmentStructure[second];
        if (result == 0) result = segmentFace[first] - segmentFace[second];
        return result;
    }

    private void sortSegments(int low, int high) {
        int i = low, j = high;
        if (low >= high) return;
        int pivot = segmentOrder[(low + high) >>> 1];
        while (i <= j) {
            while (compareSegments(segmentOrder[i], pivot) < 0) i++;
            while (compareSegments(segmentOrder[j], pivot) > 0) j--;
            if (i <= j) {
                int swap = segmentOrder[i]; segmentOrder[i] = segmentOrder[j]; segmentOrder[j] = swap;
                i++; j--;
            }
        }
        if (low < j) sortSegments(low, j);
        if (i < high) sortSegments(i, high);
    }

    private void sortEdgesWithSources(int low, int high) {
        int i = low, j = high;
        if (low >= high) return;
        long pivot = edges[(low + high) >>> 1];
        while (i <= j) {
            while (edges[i] < pivot) i++;
            while (edges[j] > pivot) j--;
            if (i <= j) {
                long edge = edges[i]; edges[i] = edges[j]; edges[j] = edge;
                int value = edgeStructure[i]; edgeStructure[i] = edgeStructure[j]; edgeStructure[j] = value;
                value = edgeFace[i]; edgeFace[i] = edgeFace[j]; edgeFace[j] = value;
                i++; j--;
            }
        }
        if (low < j) sortEdgesWithSources(low, j);
        if (i < high) sortEdgesWithSources(i, high);
    }

    private void ensureNodeCapacity(int required) {
        if (required <= nodeGx.length) return;
        nodeGx = new int[required];
        nodeGy = new int[required];
    }
    private void ensureSegmentCapacity(int required) {
        if (required <= segmentNode.length) return;
        int next = capacity(segmentNode.length, required);
        segmentNode = grow(segmentNode, next); segmentStructure = grow(segmentStructure, next);
        segmentFace = grow(segmentFace, next); segmentMinX = grow(segmentMinX, next);
        segmentMaxX = grow(segmentMaxX, next); segmentSlope = grow(segmentSlope, next);
        segmentIntercept = grow(segmentIntercept, next);
    }
    private void ensureSegmentOrderCapacity(int required) { if (required > segmentOrder.length) segmentOrder = new int[required]; }
    private void ensureActiveCapacity(int required) { if (required > active.length) active = new int[required]; }
    private void ensureEdgeCapacity(int required) {
        if (required <= edges.length) return;
        int next = capacity(edges.length, required);
        long[] expanded = new long[next]; System.arraycopy(edges, 0, expanded, 0, edges.length); edges = expanded;
        edgeStructure = grow(edgeStructure, next); edgeFace = grow(edgeFace, next);
    }
    private int capacity(int current, int required) { int next = Math.max(8, current); while (next < required) next <<= 1; return next; }
    private int[] grow(int[] source, int next) { int[] out = new int[next]; System.arraycopy(source, 0, out, 0, source.length); return out; }
    private float[] grow(float[] source, int next) { float[] out = new float[next]; System.arraycopy(source, 0, out, 0, source.length); return out; }
}
