package games.pixscape.application;

import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.hud.MaterializedHud;
import org.junit.Assert;
import org.junit.Test;

/** Verifies that gameplay code outside Runtime implementation packages can use the HUD API. */
public class HudPublicApiSurfaceTest {
    @Test
    public void hudAccessorsArePublicAndNullOutsideAnActiveLifecycle() {
        PixscapeEngine engine = new PixscapeEngine();

        MaterializedHud activeHud = engine.getActiveHud();
        Throwable hudFailure = engine.getLastHudFailure();
        Assert.assertNull(activeHud);
        Assert.assertNull(hudFailure);

        engine.dispose();
        Assert.assertNull(engine.getActiveHud());
        Assert.assertNull(engine.getLastHudFailure());
    }
}
