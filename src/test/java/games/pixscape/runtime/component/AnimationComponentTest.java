package games.pixscape.runtime.component;

import org.junit.Assert;
import org.junit.Test;

public class AnimationComponentTest {

    @Test
    public void resetClearsAvailableAssetsAndPlaybackState() {
        ResettableAnimationComponent animation = new ResettableAnimationComponent();
        animation.animationAssetIds.add(12);
        animation.animationAssetIds.add(34);
        animation.currentClip = "walk";
        animation.fps = 24f;
        animation.stateTime = 2f;
        animation.playing = false;
        animation.loop = false;
        animation.frame = 7;

        animation.resetNow();

        Assert.assertEquals(0, animation.animationAssetIds.size);
        Assert.assertEquals("", animation.currentClip);
        Assert.assertEquals(12f, animation.fps, 0f);
        Assert.assertEquals(0f, animation.stateTime, 0f);
        Assert.assertTrue(animation.playing);
        Assert.assertTrue(animation.loop);
        Assert.assertEquals(-1, animation.frame);
    }

    private static final class ResettableAnimationComponent extends AnimationComponent {
        void resetNow() {
            reset();
        }
    }
}
