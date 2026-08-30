package games.pixscape.runtime.hierarchy;

import games.pixscape.runtime.component.TransformComponent;
import org.junit.Assert;
import org.junit.Test;

public class WorldTransformStateTest {
    @Test
    public void publishesDerivedPoseAndFrameByEntityId() {
        WorldTransformState state = new WorldTransformState(2);
        TransformComponent transform = new TransformComponent();
        transform.x = 4f;
        transform.y = 7f;
        transform.rotationRad = 0.5f;
        transform.scaleX = 2f;
        transform.scaleY = 3f;

        state.setResolved(37, transform);

        Assert.assertTrue(state.isResolved(37));
        Assert.assertEquals(4f, state.x[37], 0f);
        Assert.assertEquals(7f, state.y[37], 0f);
        Assert.assertEquals(0.5f, state.rotationRad[37], 0f);
        Assert.assertEquals(2f, state.scaleX[37], 0f);
        Assert.assertEquals(3f, state.scaleY[37], 0f);
        Assert.assertTrue(state.getEntityCapacity() > 37);

        state.clear(37);
        Assert.assertFalse(state.isResolved(37));
    }
}
