package games.pixscape.runtime.render;

import games.pixscape.runtime.render.lighting.ForwardTopology;
import games.pixscape.runtime.render.lighting.LightingSurface;
import games.pixscape.runtime.spatial.SpatialProjectedFaceCache;

/** Two immutable image regions borrowing one native tile, material and set of casters. */
public final class TileCornerSplit {
    public final Piece back;
    public final Piece front;

    private TileCornerSplit(float left, float right, float height, float top, float bottom,
                            float[] quad, LightingSurface receiver) {
        back = new Piece(left, 1, 1, right, height, top, bottom, quad, receiver);
        front = new Piece(0, left, right, 0, height, top, bottom, quad, receiver);
    }

    TileCornerSplit withReceiver(float[] quad, LightingSurface receiver) {
        float height = quad[3] - quad[1];
        return new TileCornerSplit(front.y[1] / height, front.y[2] / height, height,
                back.v[1], front.v[0], quad, receiver);
    }

    public static TileCornerSplit prepare(SpatialProjectedFaceCache faces, int frontFace, float[] quad,
                                          float atlasTop, float atlasBottom, LightingSurface receiver) {
        float slope = faces.slope[frontFace];
        float intercept = faces.intercept[frontFace] + faces.faceHeight[frontFace];
        float height = quad[3] - quad[1];
        float left = (slope * quad[0] + intercept - quad[1]) / height;
        float right = (slope * quad[6] + intercept - quad[7]) / height;
        if (!(left > 0 && left < 1 && right > 0 && right < 1)) return null;
        return new TileCornerSplit(left, right, height, atlasTop, atlasBottom, quad, receiver);
    }

    public static final class Piece {
        /** Offsets from each native bottom corner in BL, TL, TR, BR order. */
        public final float[] y, v;
        public final ForwardTopology topology;
        public final float[] forwardAttributes;
        public final LightingSurface receiver;

        private Piece(float bl, float tl, float tr, float br, float height, float top, float bottom,
                      float[] quad, LightingSurface source) {
            y = new float[]{height * bl, height * tl, height * tr, height * br};
            v = new float[]{bottom + bl * (top-bottom), bottom + tl * (top-bottom),
                    bottom + tr * (top-bottom), bottom + br * (top-bottom)};
            if (source == null) {
                topology = null;
                forwardAttributes = null;
                receiver = null;
            } else {
                topology = ForwardTopology.compile(source, quad, new double[]{0,bl,0,tl,1,tr,1,br});
                forwardAttributes = topology.immutableAttributes(source);
                receiver = clippedReceiver(source, topology);
            }
        }
    }

    /** Materialize the legacy receiver from exactly the same cold triangulation used by Forward. */
    private static LightingSurface clippedReceiver(LightingSurface source, ForwardTopology topology) {
        float[] physical = source.receiverVertices == null ? source.vertices : source.receiverVertices;
        int physicalVertices = 0;
        for (int offset : topology.source) if (offset >= 0) physicalVertices++;
        float[] data = new float[physicalVertices * 8];
        LightingSurface result = new LightingSurface(new float[data.length]);
        result.receiverVertices = data;
        result.sourceReceiver = source;
        result.sourceTriangleOffsets = new int[physicalVertices / 3];
        result.normalMap = source.normalMap;
        result.approximate = source.approximate;
        result.receiveLight = source.receiveLight;
        if (source.curveNormals != null) result.curveNormals = new float[physicalVertices * 3];
        for (int i = 0, vertex = 0; i < topology.vertexCount; i++) {
            int offset = topology.source[i];
            // Native-only completion contributes color in Forward, never a physical Legacy receiver.
            if (offset < 0) continue;
            result.sourceTriangleOffsets[vertex / 3] = offset;
            int w = i * 5;
            float a = topology.weights[w+1], b = topology.weights[w+2], c = 1-a-b;
            for (int j = 0; j < 8; j++)
                data[vertex*8+j] = a*physical[offset+j] + b*physical[offset+8+j] + c*physical[offset+16+j];
            // Legacy uses the original triangle normal to distinguish completed image coverage.
            System.arraycopy(source.vertices, offset+5, result.vertices, vertex*8+5, 3);
            if (result.curveNormals != null) {
                int n = offset/8*3;
                for (int j = 0; j < 3; j++)
                    result.curveNormals[vertex*3+j] = a*source.curveNormals[n+j]
                            + b*source.curveNormals[n+3+j] + c*source.curveNormals[n+6+j];
            }
            vertex++;
        }
        return result;
    }
}
