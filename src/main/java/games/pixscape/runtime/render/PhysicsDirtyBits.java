package games.pixscape.runtime.render;

public final class PhysicsDirtyBits {
    private PhysicsDirtyBits() {
    }

    public static final int NONE = 0;
    public static final int BODY = 1 << 0;
    public static final int FIXTURE = 1 << 1;
    public static final int FILTER = 1 << 2;

    public static final int ALL = BODY | FIXTURE | FILTER;
}
