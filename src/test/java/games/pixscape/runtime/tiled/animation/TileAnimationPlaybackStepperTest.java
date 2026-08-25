package games.pixscape.runtime.tiled.animation;

import org.junit.Assert;
import org.junit.Test;

public class TileAnimationPlaybackStepperTest {

    private final TileAnimationPlaybackStepper.Result result =
            new TileAnimationPlaybackStepper.Result();

    @Test
    public void loopingUsesExactVariableDurationsAndPreservesRemainder() {
        TileAnimationDef def = definition(new int[]{11, 12, 13}, new int[]{40, 70, 90});

        TileAnimationPlaybackStepper.advance(def,
                TileAnimationPlayback.PLAYING,
                TileAnimationPlayback.MODE_LOOPING,
                false, false, 0, 0, 325, result);

        Assert.assertEquals(TileAnimationPlayback.PLAYING, result.playbackState);
        Assert.assertEquals(2, result.frameIndex);
        Assert.assertEquals(15, result.frameElapsedMs);
        Assert.assertFalse(result.finished);
    }

    @Test
    public void pausedPlaybackConsumesNoTime() {
        TileAnimationDef def = definition(new int[]{11, 12}, new int[]{40, 70});

        TileAnimationPlaybackStepper.advance(def,
                TileAnimationPlayback.PAUSED,
                TileAnimationPlayback.MODE_PLAY_ONCE,
                false, true, 1, 25, 500, result);

        Assert.assertEquals(TileAnimationPlayback.PAUSED, result.playbackState);
        Assert.assertEquals(1, result.frameIndex);
        Assert.assertEquals(25, result.frameElapsedMs);
        Assert.assertFalse(result.finished);
    }

    @Test
    public void playOnceCanHoldTheLastFrame() {
        TileAnimationDef def = definition(new int[]{11, 12}, new int[]{40, 70});

        TileAnimationPlaybackStepper.advance(def,
                TileAnimationPlayback.PLAYING,
                TileAnimationPlayback.MODE_PLAY_ONCE,
                false, true, 0, 0, 110, result);

        Assert.assertEquals(TileAnimationPlayback.PAUSED, result.playbackState);
        Assert.assertEquals(1, result.frameIndex);
        Assert.assertEquals(0, result.frameElapsedMs);
        Assert.assertTrue(result.finished);
        Assert.assertTrue(result.holdLastFrame);
    }

    @Test
    public void playOnceWithoutHoldReturnsToStoppedFrameZero() {
        TileAnimationDef def = definition(new int[]{11, 12}, new int[]{40, 70});

        TileAnimationPlaybackStepper.advance(def,
                TileAnimationPlayback.PLAYING,
                TileAnimationPlayback.MODE_PLAY_ONCE,
                false, false, 0, 0, 111, result);

        Assert.assertEquals(TileAnimationPlayback.NONE, result.playbackState);
        Assert.assertEquals(0, result.frameIndex);
        Assert.assertEquals(0, result.frameElapsedMs);
        Assert.assertTrue(result.finished);
        Assert.assertFalse(result.holdLastFrame);
    }

    private static TileAnimationDef definition(int[] assets, int[] durations) {
        TileAnimationDefData data = new TileAnimationDefData();
        data.id = 7;
        data.frameAssetIds = assets;
        data.frameDurationsMs = durations;
        return new TileAnimationDef(data);
    }
}
