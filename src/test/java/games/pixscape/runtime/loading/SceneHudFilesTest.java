package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.SceneHudFiles;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class SceneHudFilesTest {
    @Test public void partialConstructionReleasesOnlyAcquiredLeasesAndPreservesPrimaryFailure() {
        String atlas = "project/atlases/hud/A/hud.atlas";
        String[] requests = {atlas, "project/hud/a.hudscreen", "project/hud/b.hudscreen", "project/hud/c.hudscreen"};
        for (int failed = 0; failed < requests.length; failed++) {
            FileLeaseTestManager manager = new FileLeaseTestManager();
            FileAvailabilityService availability = new FileAvailabilityService(manager, false);
            manager.failAcquisition = requests[failed];
            // Cleanup itself failing must neither replace the primary error nor skip other leases.
            manager.failCleanup = atlas;
            try {
                assertSame(manager.acquisitionFailure, assertThrows(RuntimeException.class,
                        () -> new SceneHudFiles(availability, new FileHandle("project"),
                                "atlases/hud/A/hud.atlas", Arrays.asList("a", "b", "c"))));
                assertEquals(failed, manager.acquired.size());
                assertEquals(manager.acquired, manager.released);
                assertFalse(manager.released.containsKey(requests[failed]));
                assertEquals(0, manager.getQueuedAssets());
                availability.dispose();
                assertEquals(manager.acquired, manager.released);
            } finally { availability.dispose(); manager.dispose(); }
        }
    }

    @Test public void successfulConstructionRetainsLeasesUntilNormalIdempotentRelease() {
        FileLeaseTestManager manager = new FileLeaseTestManager();
        FileAvailabilityService availability = new FileAvailabilityService(manager, false);
        try {
            SceneHudFiles files = new SceneHudFiles(availability, new FileHandle("project"),
                    "atlases/hud/A/hud.atlas", Arrays.asList("a", "hud/a", "b"));
            assertEquals(3, manager.acquired.size());
            assertTrue(manager.released.isEmpty());
            files.release(); files.release();
            assertEquals(manager.acquired, manager.released);
            assertEquals(0, manager.getQueuedAssets());
        } finally { availability.dispose(); manager.dispose(); }
    }

    @Test public void failedConstructionDoesNotReleaseAnotherOwnersLease() {
        FileLeaseTestManager manager = new FileLeaseTestManager();
        FileAvailabilityService availability = new FileAvailabilityService(manager, false);
        String atlas = "project/atlases/hud/A/hud.atlas";
        try {
            availability.requestFile(atlas); // An independent owner already holds this request.
            manager.failAcquisition = "project/hud/a.hudscreen";
            assertSame(manager.acquisitionFailure, assertThrows(RuntimeException.class,
                    () -> new SceneHudFiles(availability, new FileHandle("project"),
                            "atlases/hud/A/hud.atlas", Arrays.asList("a"))));
            assertTrue(manager.released.isEmpty());
            assertTrue(manager.contains(atlas));
            availability.releaseFile(atlas);
            assertEquals(manager.acquired, manager.released);
        } finally { availability.dispose(); manager.dispose(); }
    }
}
