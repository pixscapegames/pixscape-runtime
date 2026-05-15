package games.pixscape.runtime.render;

public final class JointDirtyBits {
    private JointDirtyBits() {
    }

    public static final int NONE = 0;

    public static final int LINKS = 1 << 0; // aEid/bEid
    public static final int ANCHORS = 1 << 1; // anchorAx/.., anchorBx/..
    public static final int PARAMS = 1 << 2; // distance/freq/damping/limits/motor...
    public static final int TYPE = 1 << 3; // type

    public static final int ALL = LINKS | ANCHORS | PARAMS | TYPE;
}
