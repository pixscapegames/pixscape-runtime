package games.pixscape.runtime.spatial;

/** Authored, owner-local voluntary relation. Zero faces describe an existing overlap. */
public final class SpatialBlockLink {
    public static final int MIN_X = 1, MAX_X = 2, MIN_Y = 4, MAX_Y = 8;
    public int firstBlockId;
    public int secondBlockId;
    public int firstFace;
    public int secondFace;

    public SpatialBlockLink() { }

    public SpatialBlockLink(int first, int second, int face, int otherFace) {
        firstBlockId = first;
        secondBlockId = second;
        firstFace = face;
        secondFace = otherFace;
    }

    public SpatialBlockLink copy() {
        return new SpatialBlockLink(firstBlockId, secondBlockId, firstFace, secondFace);
    }

    public boolean joins(int a, int b) {
        return firstBlockId == a && secondBlockId == b || firstBlockId == b && secondBlockId == a;
    }
}
