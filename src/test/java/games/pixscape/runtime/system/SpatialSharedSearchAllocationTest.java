package games.pixscape.runtime.system;

import com.sun.management.ThreadMXBean;
import games.pixscape.runtime.profiling.SystemProfiler;
import org.junit.Assume;
import org.junit.Test;

import java.lang.management.ManagementFactory;

import static org.junit.Assert.assertEquals;

public class SpatialSharedSearchAllocationTest {
    @Test
    public void movingActorsAllocateNoSpatialBytesAfterCapacityReservation() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(bean instanceof ThreadMXBean);
        ThreadMXBean allocations = (ThreadMXBean) bean;
        Assume.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        for (int maps : new int[]{1, 2, 8}) {
            try (SpatialSharedSearchFixture f = new SpatialSharedSearchFixture(maps, 32, false)) {
                long[] bytes = new long[1];
                f.spatial.setSystemProfiler(new SystemProfiler() {
                    public boolean enabled() {
                        return true;
                    }

                    public void beginFrame() {
                    }

                    public long begin(int phase) {
                        return allocations.getThreadAllocatedBytes(thread);
                    }

                    public void end(int phase, long start) {
                        bytes[0] += allocations.getThreadAllocatedBytes(thread) - start;
                    }
                });
                for (int i = 0; i < 8192; i++) {
                    f.move(i);
                    f.world.process();
                }
                bytes[0] = 0;
                for (int i = 0; i < 1024; i++) {
                    f.move(i);
                    f.world.process();
                }
                assertEquals("Spatial bytes for " + maps + " maps", 0, bytes[0]);
            }
        }
    }
}
