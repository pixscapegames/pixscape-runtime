package games.pixscape.runtime.engine;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class PixscapeEngineAssetManagerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void defaultConfigurationCreatesAndDisposesOneInternalManagerExactlyOnce() {
        final TrackingAssetManager manager = new TrackingAssetManager();
        final int[] creations = {0};
        PixscapeEngine engine = new PixscapeEngine(new PixscapeEngine.AssetManagerFactory() {
            @Override
            public AssetManager create(com.badlogic.gdx.assets.loaders.FileHandleResolver resolver) {
                creations[0]++;
                return manager;
            }
        });

        assertMissingProject(engine);
        engine.dispose();
        engine.dispose();

        assertEquals(1, creations[0]);
        assertEquals(1, manager.disposeCalls);
    }

    @Test
    public void suppliedManagerIsBorrowedAndLateReplacementIsRejected() {
        TrackingAssetManager borrowed = new TrackingAssetManager();
        PixscapeEngine engine = new PixscapeEngine().setAssetManager(borrowed);

        assertMissingProject(engine);
        try {
            engine.setAssetManager(new AssetManager());
            fail("Expected late AssetManager replacement rejection.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("before"));
        }

        engine.dispose();
        assertEquals(0, borrowed.disposeCalls);
        assertTrue(borrowed.update());
        borrowed.dispose();
    }

    @Test
    public void nullBeforeLoadingRestoresInternalManagerSelection() {
        TrackingAssetManager internal = new TrackingAssetManager();
        PixscapeEngine engine = new PixscapeEngine(new PixscapeEngine.AssetManagerFactory() {
            @Override
            public AssetManager create(com.badlogic.gdx.assets.loaders.FileHandleResolver resolver) {
                return internal;
            }
        });

        engine.setAssetManager(new TrackingAssetManager()).setAssetManager(null);
        assertMissingProject(engine);
        engine.dispose();

        assertEquals(1, internal.disposeCalls);
    }

    private void assertMissingProject(PixscapeEngine engine) {
        try {
            engine.loadProject(new FileHandle(temp.getRoot()));
            fail("Expected missing runtime project.");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Missing runtime project directory"));
        }
    }

    private static final class TrackingAssetManager extends AssetManager {
        int disposeCalls;

        @Override
        public synchronized void dispose() {
            disposeCalls++;
            super.dispose();
        }
    }
}
