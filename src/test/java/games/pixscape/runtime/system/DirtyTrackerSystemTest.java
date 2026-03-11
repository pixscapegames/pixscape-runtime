package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.GeometryDirty;
import org.junit.Assert;
import org.junit.Test;

public class DirtyTrackerSystemTest {

    @Test
    public void clearFrameResetsPackedBitsForGeometry() {
        // Arrange
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        World world = new World(new WorldConfigurationBuilder().with(dirty).build());
        int entityId = world.createEntity().getId();
        world.process();
        dirty.geometry(entityId, GeometryDirty.POSITION);

        // Act
        dirty.clearFrame();

        // Assert
        Assert.assertEquals(
                "clearFrame should reset packed bits for geometry",
                DirtyBits.NONE,
                dirty.packedBits(entityId)
        );
    }
}
