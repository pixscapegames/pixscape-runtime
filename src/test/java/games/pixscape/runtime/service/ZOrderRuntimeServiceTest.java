package games.pixscape.runtime.service;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

public class ZOrderRuntimeServiceTest {

    @Test
    public void moveDownDecrementsZIndexWhenPossible() {
        // Arrange
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        World world = new World(new WorldConfigurationBuilder().with(dirty).build());
        world.process();
        Entity bottomEntity = world.createEntity();
        EntityIndexComponent bottom = bottomEntity.edit().create(EntityIndexComponent.class);
        bottom.layerIndex = 0;
        bottom.zIndex = 0;
        Entity topEntity = world.createEntity();
        EntityIndexComponent top = topEntity.edit().create(EntityIndexComponent.class);
        top.layerIndex = 0;
        top.zIndex = 1;
        ZOrderRuntimeService service = new ZOrderRuntimeService(world);

        // Act
        service.moveDown(topEntity.getId());

        // Assert
        Assert.assertEquals(
                "moveDown should decrement z index when possible",
                0,
                top.zIndex
        );
    }
}
