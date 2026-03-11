package games.pixscape.runtime.system;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.LayerStateSOA;
import org.junit.Assert;
import org.junit.Test;

public class LayerStateBuildSystemTest {

    @Test
    public void physicsLayerUsesSceneParallax() {
        // Arrange
        LayerStateSOA layerState = new LayerStateSOA(4);
        SceneMetaRuntime sceneMeta = new SceneMetaRuntime();
        sceneMeta.physicsParallaxX = 2.5f;
        sceneMeta.physicsParallaxY = -1.25f;
        World world = new World(new WorldConfigurationBuilder()
                .with(new LayerStateBuildSystem(layerState, sceneMeta))
                .build());
        Entity entity = world.createEntity();
        LayerComponent layer = entity.edit().create(LayerComponent.class);
        layer.layerIndex = 1;
        layer.type = LayerComponent.TYPE_PHYSICS;

        // Act
        world.process();

        // Assert
        Assert.assertEquals(
                "Physics layer should use scene parallax X",
                sceneMeta.physicsParallaxX,
                layerState.parallaxX[1],
                0.0001f
        );
        Assert.assertEquals(
                "Physics layer should use scene parallax Y",
                sceneMeta.physicsParallaxY,
                layerState.parallaxY[1],
                0.0001f
        );
    }
}
