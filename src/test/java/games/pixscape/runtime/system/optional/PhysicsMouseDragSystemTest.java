package games.pixscape.runtime.system.optional;

import org.junit.Assert;
import org.junit.Test;

public class PhysicsMouseDragSystemTest {

    @Test
    public void renderedPhysicsPointConvertsBackToLogicalPointWithParallax() {
        float logicalX = PhysicsMouseDragSystem.toLogicalPhysicsWorld(1200f, 400f, 0.5f);
        float logicalY = PhysicsMouseDragSystem.toLogicalPhysicsWorld(400f, 200f, 0.5f);

        Assert.assertEquals(1000f, logicalX, 0.0001f);
        Assert.assertEquals(300f, logicalY, 0.0001f);
    }

    @Test
    public void parallaxOnePreservesRenderedWorldPoint() {
        Assert.assertEquals(
                1200f,
                PhysicsMouseDragSystem.toLogicalPhysicsWorld(1200f, 400f, 1f),
                0.0001f
        );
        Assert.assertEquals(
                400f,
                PhysicsMouseDragSystem.toLogicalPhysicsWorld(400f, 200f, 1f),
                0.0001f
        );
    }

    @Test
    public void nanParallaxPreservesRenderedWorldPoint() {
        Assert.assertEquals(
                1200f,
                PhysicsMouseDragSystem.toLogicalPhysicsWorld(1200f, 400f, Float.NaN),
                0.0001f
        );
    }
}
