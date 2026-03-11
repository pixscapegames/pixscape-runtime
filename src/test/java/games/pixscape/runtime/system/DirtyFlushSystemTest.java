package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.DirtyBits;
import org.junit.Assert;
import org.junit.Test;

public class DirtyFlushSystemTest {

    @Test
    public void processSystemClearsDirtyFrame() {
        // Arrange
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        World world = new World(new WorldConfigurationBuilder()
                .with(dirty, new DirtyFlushSystem())
                .build());
        int entityId = world.createEntity().getId();
        world.process();
        dirty.order(entityId);

        // Act
        world.process();

        // Assert
        Assert.assertFalse(
                "DirtyFlushSystem should clear dirty flags",
                dirty.isDirty(entityId, DirtyBits.ORDER)
        );
    }
}
