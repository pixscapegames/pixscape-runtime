package games.pixscape.runtime.tiled;

public final class TileTransformFlags {
    private TileTransformFlags() {
    }

    public static final byte NONE = 0;
    public static final byte FLIP_H = 1;
    public static final byte FLIP_V = 2;
    public static final byte FLIP_D = 4; // diagonal flag, comme Tiled

    public static byte sanitize(byte flags) {
        return (byte) (flags & 0x7);
    }
}