package games.pixscape.runtime.engine;

import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Test;
import static org.junit.Assert.*;

public class LightingDefaultTest {
    @Test public void engineDefaultsForwardAndExplicitLegacyIsRetained() throws Exception {
        PixscapeEngine engine = new PixscapeEngine();
        java.lang.reflect.Field selection = PixscapeEngine.class.getDeclaredField("forwardLighting");
        selection.setAccessible(true);
        assertTrue(selection.getBoolean(engine));
        engine.setForwardLighting(false);
        assertFalse(selection.getBoolean(engine));
    }
    @Test public void implicitSubmitUsesForwardButExplicitLegacyStillExists() {
        RenderSubmitSystem implicit = new RenderSubmitSystem(null,null,null,1,1,1,null,null,null);
        RenderSubmitSystem explicit = new RenderSubmitSystem(null,null,null,1,1,1,null,null,null,false);
        assertNotNull(implicit.forwardRenderer());
        assertNull(explicit.forwardRenderer());
    }
}
