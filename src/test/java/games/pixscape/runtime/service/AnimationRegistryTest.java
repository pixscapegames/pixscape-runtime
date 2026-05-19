package games.pixscape.runtime.service;

import games.pixscape.runtime.animation.AnimationClipDefData;
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
        Assert.assertEquals(2, def.clips().size);
    }

    private static AnimationClipDefData clip(String name, int start, int end) {
        AnimationClipDefData clip = new AnimationClipDefData();
        clip.name = name;
        clip.start = start;
        clip.end = end;
        return clip;
    }
}
