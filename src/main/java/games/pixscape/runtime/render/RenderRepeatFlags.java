package games.pixscape.runtime.render;

public final class RenderRepeatFlags {
    public static final byte NONE = 0;
    public static final byte REPEAT_X = 1;
    public static final byte REPEAT_Y = 2;
    public static final byte ANY = REPEAT_X | REPEAT_Y;

    private RenderRepeatFlags() {
    }

    public static byte sanitize(byte flags) {
        return (byte) (flags & ANY);
    }
}
