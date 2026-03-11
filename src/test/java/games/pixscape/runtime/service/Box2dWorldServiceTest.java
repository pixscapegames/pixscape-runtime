package games.pixscape.runtime.service;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.Assert;
import org.junit.Test;

public class Box2dWorldServiceTest {

    @Test
    public void setDoSleepDisablesSleepingAndWakesBodies() {
        // Arrange
        GdxNativesLoader.load();
        Box2dWorldService service = new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        Body body = service.world.createBody(bodyDef);
        body.setAwake(false);

        // Act
        service.setDoSleep(false);

        // Assert
        Assert.assertFalse("Service should disable sleeping", service.isDoSleep());
        Assert.assertFalse("Body should not allow sleeping", body.isSleepingAllowed());
        Assert.assertTrue("Body should be awake when sleeping is disabled", body.isAwake());
    }

    @Test
    public void stepClampsToMaxSubstepsForLargeDelta() {
        // Arrange
        GdxNativesLoader.load();
        Box2dWorldService service = new Box2dWorldService(100f, new Vector2(0f, -9.8f));

        // Act
        service.step(1f);

        // Assert
        Assert.assertEquals("Step should clamp to max substeps", 5, service.lastSubsteps);
    }
}
