package games.pixscape.runtime.render;

public final class RenderSourceDomain {
    public static final byte SOURCE_NONE = 0;
    public static final byte SOURCE_ECS = 1;
    public static final byte SOURCE_TILED = 2;
    public static final byte SOURCE_VFX = 3;
    /** Frame-local composition descriptor only; flattened before extraction. */
    public static final byte SOURCE_GAME_OBJECT = 4;

    private RenderSourceDomain() {
    }
}
