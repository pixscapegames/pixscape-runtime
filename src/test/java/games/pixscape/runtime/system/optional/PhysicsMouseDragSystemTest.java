package games.pixscape.runtime.system.optional;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.PhysicsAPI;
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

    @Test
    public void publicPhysicsApiRemovesParallaxAndConvertsToMeters() {
        OrthographicCamera camera = new OrthographicCamera();
        camera.position.set(400f, 200f, 0f);
        PhysicsAPI physics = new StubPhysicsApi(100f, 0.5f, 0.5f);
        Vector2 point = new Vector2(1200f, 400f);

        PhysicsMouseDragSystem.toPhysicsMeters(physics, camera, point, point);

        Assert.assertEquals(10f, point.x, 0.0001f);
        Assert.assertEquals(3f, point.y, 0.0001f);
    }

    private static final class StubPhysicsApi implements PhysicsAPI {
        private final float pixelsPerMeter;
        private final float parallaxX;
        private final float parallaxY;

        private StubPhysicsApi(float pixelsPerMeter, float parallaxX, float parallaxY) {
            this.pixelsPerMeter = pixelsPerMeter;
            this.parallaxX = parallaxX;
            this.parallaxY = parallaxY;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public float pixelsPerMeter() {
            return pixelsPerMeter;
        }

        @Override
        public float parallaxX() {
            return parallaxX;
        }

        @Override
        public float parallaxY() {
            return parallaxY;
        }

        @Override
        public Vector2 removeParallax(
                Vector2 renderedWorldPosition, OrthographicCamera camera, Vector2 out) {
            float renderedX = renderedWorldPosition.x;
            float renderedY = renderedWorldPosition.y;
            return out.set(
                    renderedX - (1f - parallaxX) * camera.position.x,
                    renderedY - (1f - parallaxY) * camera.position.y);
        }

        @Override
        public com.badlogic.gdx.physics.box2d.World box2dWorld() {
            return null;
        }

        @Override
        public Body body(EntityRef entity) {
            return null;
        }
    }
}
