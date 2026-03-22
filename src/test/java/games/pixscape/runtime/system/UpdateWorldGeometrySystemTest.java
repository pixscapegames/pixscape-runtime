package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.GeometryDirty;
import org.junit.Assert;
import org.junit.Test;

public class UpdateWorldGeometrySystemTest {

    @Test
    public void spritePositionUsesBottomLeftAtCreation() {
        // Test: a sprite's position matches the bottom-left corner on creation.
        // Arrange
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        UpdateWorldGeometrySystem geometry = new UpdateWorldGeometrySystem();
        World world = new World(new WorldConfigurationBuilder().with(dirty, geometry).build());

        int entityId = world.create();
        TransformComponent t = world.getMapper(TransformComponent.class).create(entityId);
        DimensionsComponent d = world.getMapper(DimensionsComponent.class).create(entityId);
        world.getMapper(OrientedBoundsComponent.class).create(entityId);
        AABBComponent aabb = world.getMapper(AABBComponent.class).create(entityId);
        world.getMapper(TextureRegionComponent.class).create(entityId);

        t.x = 12f;
        t.y = 34f;
        d.width = 32f;
        d.height = 16f;

        world.process();
        dirty.geometry(entityId, GeometryDirty.ALL);

        // Act
        world.process();

        // Assert
        Assert.assertEquals("Sprite AABB minX should match bottom-left X", t.x, aabb.minX, 0.0001f);
        Assert.assertEquals("Sprite AABB minY should match bottom-left Y", t.y, aabb.minY, 0.0001f);
        Assert.assertEquals("Sprite AABB maxX should match width", t.x + d.width, aabb.maxX, 0.0001f);
        Assert.assertEquals("Sprite AABB maxY should match height", t.y + d.height, aabb.maxY, 0.0001f);
    }

    @Test
    public void animationPositionUsesBottomLeftAtCreation() {
        // Test: an animation's position matches the bottom-left corner on creation.
        // Arrange
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        UpdateWorldGeometrySystem geometry = new UpdateWorldGeometrySystem();
        World world = new World(new WorldConfigurationBuilder().with(dirty, geometry).build());

        int entityId = world.create();
        TransformComponent t = world.getMapper(TransformComponent.class).create(entityId);
        DimensionsComponent d = world.getMapper(DimensionsComponent.class).create(entityId);
        world.getMapper(OrientedBoundsComponent.class).create(entityId);
        AABBComponent aabb = world.getMapper(AABBComponent.class).create(entityId);
        world.getMapper(TextureRegionComponent.class).create(entityId);
        world.getMapper(AnimationComponent.class).create(entityId);

        t.x = -8f;
        t.y = 4f;
        d.width = 48f;
        d.height = 24f;

        world.process();
        dirty.geometry(entityId, GeometryDirty.ALL);

        // Act
        world.process();

        // Assert
        Assert.assertEquals("Animation AABB minX should match bottom-left X", t.x, aabb.minX, 0.0001f);
        Assert.assertEquals("Animation AABB minY should match bottom-left Y", t.y, aabb.minY, 0.0001f);
        Assert.assertEquals("Animation AABB maxX should match width", t.x + d.width, aabb.maxX, 0.0001f);
        Assert.assertEquals("Animation AABB maxY should match height", t.y + d.height, aabb.maxY, 0.0001f);
    }
}
