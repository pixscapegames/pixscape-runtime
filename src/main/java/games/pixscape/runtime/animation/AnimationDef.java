package games.pixscape.runtime.animation;

import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.api.AnimationDefinition;

public final class AnimationDef implements AnimationDefinition {

    private final int assetId;
    private final String name;
    private final float fps;
    private final String currentClip;
    private final int frameCount;
    private final ObjectMap<String, AnimationClipDef> clipsByName;

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
        if (source.fps <= 0f || Float.isNaN(source.fps) || Float.isInfinite(source.fps)) {
            throw new IllegalArgumentException("fps must be finite and > 0");
        }
        if (source.frameCount <= 0) {
            throw new IllegalArgumentException("frameCount must be > 0");
        }
        if (source.clips == null || source.clips.size == 0) {
            throw new IllegalArgumentException("at least one clip is required");
        }

        this.fps = source.fps;
        this.frameCount = source.frameCount;
        this.clipsByName = new ObjectMap<>();

        for (int i = 0, n = source.clips.size; i < n; i++) {
            AnimationClipDefData clip = source.clips.get(i);
            if (clip == null) {
                throw new IllegalArgumentException("clip at index " + i + " must not be null");
            }
            addClipCopy(clip);
        }

        String requestedClip = source.currentClip;
        if (isBlank(requestedClip) || !hasClip(requestedClip)) {
            throw new IllegalArgumentException(
                    "currentClip must name a registered clip, got '" + requestedClip + "'");
        }
        this.currentClip = requestedClip;
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

    public int clipCount() {
        return clipsByName.size;
    }

    public boolean hasClip(String clipName) {
        return clip(clipName) != null;
    }

    /** Returns the registry-owned clip in O(1) average time, or {@code null}. */
    public AnimationClipDef clip(String clipName) {
        if (isBlank(clipName)) return null;
        return clipsByName.get(clipName);
    }

    private void addClipCopy(AnimationClipDefData source) {
        if (isBlank(source.name)) {
            throw new IllegalArgumentException("clip name must not be blank");
        }
        if (source.start < 0 || source.start >= frameCount
                || source.end < 0 || source.end >= frameCount) {
            throw new IllegalArgumentException(
                    "clip '" + source.name + "' range [" + source.start + ", " + source.end
                            + "] must stay within frameCount " + frameCount);
        }

        if (clipsByName.containsKey(source.name)) {
            throw new IllegalArgumentException("duplicate clip name: '" + source.name + "'");
        }

        AnimationClipDef copy = new AnimationClipDef(
                source.name, source.start, source.end, source.flipX);
        clipsByName.put(copy.name(), copy);
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
