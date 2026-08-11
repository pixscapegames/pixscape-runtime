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

public class FileAvailabilityServiceTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void requestFile_updateMakesFileAvailableWithoutContentCopy() throws Exception {
        File root = temp.newFolder("assets");
        File source = new File(root, "scenes/scene1.json");
        assertTrue(source.getParentFile().mkdirs());
        assertTrue(source.createNewFile());

        FileAvailabilityService service = service(root);
        try {
            service.requestFile("scenes/scene1.json");

            assertFalse(service.isFileAvailable("scenes/scene1.json"));
            assertFalse(service.isComplete());

            while (!service.update()) {
                // AssetManager may require more than one update depending on the backend.
            }

            assertTrue(service.isFileAvailable("scenes/scene1.json"));
            assertEquals(source.getCanonicalPath(), service.file("scenes/scene1.json").file().getCanonicalPath());
            assertEquals(1f, service.progress(), 0f);
        } finally {
            service.dispose();
        }
    }

    @Test
    public void requestFile_samePathTwiceQueuesOneAssetManagerRequest() throws Exception {
        File root = temp.newFolder("dedupe");
        File source = new File(root, "effects/Explosion.p");
        assertTrue(source.getParentFile().mkdirs());
        assertTrue(source.createNewFile());

        CountingAssetManager manager = manager(root);
        FileAvailabilityService service = new FileAvailabilityService(manager, true);
        try {
            service.requestFile("effects/Explosion.p");
            service.requestFile("effects/Explosion.p");

            assertEquals(1, manager.loadCalls);
            assertEquals(1, manager.getQueuedAssets());
        } finally {
            service.dispose();
        }
    }

    @Test
    public void request_samePathWithDifferentTypeIsRejected() throws Exception {
        File root = temp.newFolder("wrong-type");
        File source = new File(root, "scenes/scene1.json");
        assertTrue(source.getParentFile().mkdirs());
        assertTrue(source.createNewFile());

        FileAvailabilityService service = service(root);
        try {
            service.requestFile("scenes/scene1.json");
            try {
                service.request("scenes/scene1.json", String.class);
                fail("Expected wrong-type request rejection.");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("type collision"));
            }
        } finally {
            service.dispose();
        }
    }

    @Test
    public void disposedServiceRejectsFurtherUse() {
        FileAvailabilityService service = service(temp.getRoot());
        service.dispose();

        try {
            service.requestFile("scenes/scene1.json");
            fail("Expected disposed service rejection.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("disposed"));
        }
    }

    @Test
    public void unavailableFileFailsDuringUpdateInsteadOfReportingAvailable() {
        FileAvailabilityService service = service(temp.getRoot());
        try {
            service.requestFile("scenes/missing.json");

            assertThrows(RuntimeException.class, service::update);
            assertFalse(service.isFileAvailable("scenes/missing.json"));
        } finally {
            service.dispose();
        }
    }

    private static FileAvailabilityService service(File root) {
        return new FileAvailabilityService(manager(root), true);
    }

    private static CountingAssetManager manager(final File root) {
        FileHandleResolver resolver = new FileHandleResolver() {
            @Override
            public FileHandle resolve(String fileName) {
                return new FileHandle(new File(root, fileName));
            }
        };
        return new CountingAssetManager(resolver);
    }

    private static final class CountingAssetManager extends AssetManager {
        int loadCalls;

        CountingAssetManager(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        public synchronized <T> void load(
                String fileName, Class<T> type, AssetLoaderParameters<T> parameter) {
            loadCalls++;
            super.load(fileName, type, parameter);
        }
    }
}
