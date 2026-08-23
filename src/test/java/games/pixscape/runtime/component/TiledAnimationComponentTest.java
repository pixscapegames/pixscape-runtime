package games.pixscape.runtime.component;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class TiledAnimationComponentTest {

    @Test
    public void defaultsDescribeAnUnconfiguredFreshPlayback() {
        TiledAnimationComponent animation = new TiledAnimationComponent();

        Assert.assertEquals(-1, animation.animationId);
        Assert.assertEquals(0, animation.frameIndex);
        Assert.assertEquals(0, animation.frameElapsedMs);
        Assert.assertEquals(-1, animation.appliedFrameAssetId);
    }

    @Test
    public void pooledReuseResetsPersistentAndTransientState() {
        World world = new World(new WorldConfiguration());
        int firstEntity = world.create();
        TiledAnimationComponent first = world.getMapper(TiledAnimationComponent.class)
                .create(firstEntity);
        first.animationId = 42;
        first.frameIndex = 3;
        first.frameElapsedMs = 117;
        first.appliedFrameAssetId = 123;
        world.process();

        world.delete(firstEntity);
        world.process();

        int secondEntity = world.create();
        TiledAnimationComponent reused = world.getMapper(TiledAnimationComponent.class)
                .create(secondEntity);

        Assert.assertSame(first, reused);
        Assert.assertEquals(-1, reused.animationId);
        Assert.assertEquals(0, reused.frameIndex);
        Assert.assertEquals(0, reused.frameElapsedMs);
        Assert.assertEquals(-1, reused.appliedFrameAssetId);
        world.dispose();
    }
}
