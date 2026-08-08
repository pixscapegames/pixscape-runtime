package games.pixscape.runtime.loading;

import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.*;

public class FileAvailabilitySharedManagerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void alreadyLoadedApplicationAssetGetsOnePixscapeReferenceAndTargetedUnload() throws Exception {
        File file = temp.newFile("shared.txt");
        TrackingAssetManager manager = manager(temp.getRoot());
        manager.setLoader(Marker.class, new MarkerLoader(manager.getFileHandleResolver()));
        manager.load("shared.txt", Marker.class);
        manager.finishLoading();
        Marker applicationObject = manager.get("shared.txt", Marker.class);
        assertEquals(1, manager.getReferenceCount("shared.txt"));

        FileAvailabilityService service = new FileAvailabilityService(manager, false);
        service.request("shared.txt", Marker.class);
        while (!service.update()) {
            // Drive the exact Pixscape descriptor to its loaded callback.
        }

        assertSame(applicationObject, service.get("shared.txt", Marker.class));
        assertEquals(2, manager.getReferenceCount("shared.txt"));
        service.release("shared.txt", Marker.class);
        assertEquals(1, manager.getReferenceCount("shared.txt"));

        service.dispose();
        assertEquals(0, manager.disposeCalls);
        assertSame(applicationObject, manager.get("shared.txt", Marker.class));
        manager.dispose();
    }

    @Test
    public void borrowedServiceDisposalReleasesOnlyItsOwnFileReference() throws Exception {
        temp.newFile("application.txt");
        TrackingAssetManager manager = manager(temp.getRoot());
        FileAvailabilityService service = new FileAvailabilityService(manager, false);
        service.requestFile("application.txt");
        while (!service.update()) {
            // Complete Pixscape's reference.
        }

        service.dispose();

        assertEquals(0, manager.disposeCalls);
        assertFalse(manager.isLoaded("application.txt"));
        assertTrue(manager.update());
        manager.dispose();
    }

    @Test
    public void pixscapeProgressIgnoresApplicationAssetQueuedAfterItsRequest() throws Exception {
        temp.newFile("pixscape.txt");
        temp.newFile("application.txt");
        TrackingAssetManager manager = manager(temp.getRoot());
        manager.setLoader(Marker.class, new MarkerLoader(manager.getFileHandleResolver()));
        FileAvailabilityService service = new FileAvailabilityService(manager, false);
        service.requestFile("pixscape.txt");
        manager.load("application.txt", Marker.class);

        assertTrue(service.update());

        assertEquals(1f, service.progress(), 0f);
        assertFalse(manager.isFinished());
        service.dispose();
        manager.finishLoading();
        manager.dispose();
    }

    @Test
    public void loadedWrongTypeCollisionNamesPathExistingAndRequiredTypes() throws Exception {
        temp.newFile("collision.txt");
        TrackingAssetManager manager = manager(temp.getRoot());
        manager.setLoader(Marker.class, new MarkerLoader(manager.getFileHandleResolver()));
        manager.load("collision.txt", Marker.class);
        manager.finishLoading();
        FileAvailabilityService service = new FileAvailabilityService(manager, false);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.requestFile("collision.txt"));

        assertTrue(failure.getMessage().contains("collision.txt"));
        assertTrue(failure.getMessage().contains(Marker.class.getName()));
        assertTrue(failure.getMessage().contains("AvailableFile"));
        service.dispose();
        manager.dispose();
    }

    private static TrackingAssetManager manager(final File root) {
        return new TrackingAssetManager(new FileHandleResolver() {
            @Override
            public FileHandle resolve(String fileName) {
                return new FileHandle(new File(root, fileName));
            }
        });
    }

    private static final class Marker {
    }

    private static final class MarkerLoader
            extends com.badlogic.gdx.assets.loaders.SynchronousAssetLoader<Marker, AssetLoaderParameters<Marker>> {
        private final Marker marker = new Marker();

        MarkerLoader(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        public Marker load(AssetManager assetManager, String fileName, FileHandle file,
                           AssetLoaderParameters<Marker> parameter) {
            return marker;
        }

        @Override
        public com.badlogic.gdx.utils.Array<com.badlogic.gdx.assets.AssetDescriptor> getDependencies(
                String fileName, FileHandle file, AssetLoaderParameters<Marker> parameter) {
            return null;
        }
    }

    private static final class TrackingAssetManager extends AssetManager {
        int disposeCalls;

        TrackingAssetManager(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        public synchronized void dispose() {
            disposeCalls++;
            super.dispose();
        }
    }
}
