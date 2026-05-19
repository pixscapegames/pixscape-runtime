package games.pixscape.runtime.animation;

import com.badlogic.gdx.utils.Array;

public final class AnimationDef {

    private final int assetId;
    private final String name;
    private final float fps;
    private final String currentClip;
    private final int frameCount;
    private final Array<AnimationClipDefData> clips;

    public AnimationDef(AnimationDefData source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (source.assetId <= 0) {
            throw new IllegalArgumentException("assetId must be > 0");
        }
        if (isBlank(source.name)) {
            throw new IllegalArgumentException("name must not be blank");
        }

        this.assetId = source.assetId;
        this.name = source.name;
        this.fps = source.fps > 0f ? source.fps : 12f;
        this.frameCount = Math.max(1, source.frameCount);
        this.clips = new Array<>(AnimationClipDefData[]::new);

        if (source.clips != null) {
            for (int i = 0, n = source.clips.size; i < n; i++) {
                AnimationClipDefData clip = source.clips.get(i);
                if (clip == null) continue;
                addClipCopy(clip);
            }
        }

        if (clips.size == 0) {
            AnimationClipDefData fallback = new AnimationClipDefData();
            fallback.name = "default";
            fallback.start = 0;
            fallback.end = Math.max(0, this.frameCount - 1);
            fallback.flipX = false;
            clips.add(fallback);
        }

        String requestedClip = source.currentClip;
        if (!isBlank(requestedClip) && hasClip(requestedClip)) {
            this.currentClip = requestedClip;
        } else {
            this.currentClip = clips.first().name;
        }
    }

    public int assetId() {
        return assetId;
    }

    public String name() {
        return name;
    }

    public float fps() {
        return fps;
    }

    public String currentClip() {
        return currentClip;
    }

    public int frameCount() {
        return frameCount;
    }

    public Array<AnimationClipDefData> clips() {
        return clips;
    }

    public boolean hasClip(String clipName) {
        if (isBlank(clipName)) return false;
        for (int i = 0, n = clips.size; i < n; i++) {
            AnimationClipDefData clip = clips.get(i);
            if (clip != null && clipName.equals(clip.name)) {
                return true;
            }
        }
        return false;
    }

    private void addClipCopy(AnimationClipDefData source) {
        if (isBlank(source.name)) {
            throw new IllegalArgumentException("clip name must not be blank");
        }
        int start = Math.max(0, source.start);
        int end = Math.max(start, source.end);
        if (frameCount > 0) {
            end = Math.min(end, frameCount - 1);
        }

        AnimationClipDefData copy = new AnimationClipDefData();
        copy.name = source.name;
        copy.start = start;
        copy.end = end;
        copy.flipX = source.flipX;
        clips.add(copy);
    }

    private static boolean isBlank(String s) {
        if (s == null || s.length() == 0) return true;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
