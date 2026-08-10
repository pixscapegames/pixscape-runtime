package games.pixscape.runtime.service;

import games.pixscape.runtime.animation.AnimationClipDefData;
import games.pixscape.runtime.animation.AnimationClipDef;
import games.pixscape.runtime.animation.AnimationDef;
import games.pixscape.runtime.animation.AnimationDefData;
import org.junit.Assert;
import org.junit.Test;

public class AnimationRegistryTest {

    @Test
    public void putAndGetByNameAndAssetId() {
        AnimationDefData data = new AnimationDefData();
        data.assetId = 123;
        data.name = "hero";
        data.fps = 12f;
        data.currentClip = "idle";
        data.frameCount = 8;
        data.clips.add(clip("idle", 0, 3));
        data.clips.add(clip("attack", 4, 7));

        AnimationRegistry registry = new AnimationRegistry();
        registry.put(data);

        Assert.assertTrue(registry.containsName("hero"));
        Assert.assertTrue(registry.containsAssetId(123));
        Assert.assertSame(registry.getByName("hero"), registry.getByAssetId(123));

        AnimationDef def = registry.getByName("hero");
        Assert.assertEquals(123, def.assetId());
        Assert.assertEquals("hero", def.name());
        Assert.assertEquals("idle", def.currentClip());
        Assert.assertEquals(2, def.clipCount());
        Assert.assertEquals("idle", def.clip("idle").name());
        Assert.assertEquals("attack", def.clip("attack").name());
        Assert.assertNull(def.clip("missing"));
        Assert.assertNull(def.clip(null));
    }

    @Test
    public void rejectsAssetIdAndNameCollisionsWithoutChangingIndexes() {
        AnimationRegistry registry = new AnimationRegistry();
        AnimationDef original = new AnimationDef(definition(1, "hero"));
        registry.put(original);

        assertRejected(registry, definition(1, "other"), "asset id 1");
        assertRejected(registry, definition(2, "hero"), "name 'hero'");

        Assert.assertEquals(1, registry.size());
        Assert.assertSame(original, registry.getByAssetId(1));
        Assert.assertSame(original, registry.getByName("hero"));
        Assert.assertNull(registry.getByAssetId(2));
        Assert.assertNull(registry.getByName("other"));
    }

    @Test
    public void clearAllowsACompleteReplacement() {
        AnimationRegistry registry = new AnimationRegistry();
        registry.put(definition(1, "hero"));

        registry.clear();
        registry.put(definition(1, "replacement"));

        Assert.assertNull(registry.getByName("hero"));
        Assert.assertSame(registry.getByAssetId(1), registry.getByName("replacement"));
    }

    @Test
    public void definitionsOwnImmutableClipCopies() {
        AnimationDefData data = definition(1, "hero");
        AnimationClipDefData sourceClip = data.clips.first();
        AnimationDef def = new AnimationDef(data);

        sourceClip.name = "changed";
        sourceClip.start = 3;

        AnimationClipDef clip = def.clip("default");
        Assert.assertNotNull(clip);
        Assert.assertEquals("default", clip.name());
        Assert.assertEquals(0, clip.start());
        Assert.assertNull(def.clip("changed"));
    }

    @Test
    public void rejectsStructurallyInvalidDefinitions() {
        AnimationDefData noClips = definition(1, "no-clips");
        noClips.clips.clear();
        assertDefinitionRejected(noClips, "at least one clip");

        AnimationDefData missingCurrent = definition(2, "missing-current");
        missingCurrent.currentClip = "missing";
        assertDefinitionRejected(missingCurrent, "currentClip");

        AnimationDefData invalidFps = definition(3, "invalid-fps");
        invalidFps.fps = 0f;
        assertDefinitionRejected(invalidFps, "fps");

        AnimationDefData invalidFrameCount = definition(4, "invalid-frames");
        invalidFrameCount.frameCount = 0;
        assertDefinitionRejected(invalidFrameCount, "frameCount");
    }

    private static AnimationDefData definition(int assetId, String name) {
        AnimationDefData data = new AnimationDefData();
        data.assetId = assetId;
        data.name = name;
        data.fps = 12f;
        data.currentClip = "default";
        data.frameCount = 4;
        data.clips.add(clip("default", 0, 3));
        return data;
    }

    private static void assertRejected(
            AnimationRegistry registry, AnimationDefData data, String message) {
        try {
            registry.put(data);
            Assert.fail("Expected registry collision.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private static void assertDefinitionRejected(AnimationDefData data, String message) {
        try {
            new AnimationDef(data);
            Assert.fail("Expected invalid animation definition.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private static AnimationClipDefData clip(String name, int start, int end) {
        AnimationClipDefData clip = new AnimationClipDefData();
        clip.name = name;
        clip.start = start;
        clip.end = end;
        return clip;
    }
}
