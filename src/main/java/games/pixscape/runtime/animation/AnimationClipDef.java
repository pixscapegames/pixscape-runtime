package games.pixscape.runtime.animation;

/** Immutable clip semantics owned by an {@link AnimationDef}. */
public final class AnimationClipDef {

    private final String name;
    private final int start;
    private final int end;
    private final boolean flipX;

    AnimationClipDef(String name, int start, int end, boolean flipX) {
        this.name = name;
        this.start = start;
        this.end = end;
        this.flipX = flipX;
    }

    public String name() {
        return name;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public boolean flipX() {
        return flipX;
    }
}
