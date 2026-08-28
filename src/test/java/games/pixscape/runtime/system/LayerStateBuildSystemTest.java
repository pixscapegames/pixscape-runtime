package games.pixscape.runtime.system;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
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

    @Test
    public void renderedActorLayerComponentDoesNotReplaceSceneLayerState() {
        LayerStateSOA layerState = new LayerStateSOA(4);
        World world = new World(new WorldConfigurationBuilder()
                .with(new LayerStateBuildSystem(layerState, new SceneMetaRuntime()))
                .build());
        Entity sceneLayer = world.createEntity();
        LayerComponent sceneLayerComponent = sceneLayer.edit().create(LayerComponent.class);
        sceneLayerComponent.layerIndex = 1;
        sceneLayerComponent.type = LayerComponent.TYPE_CLASSIC;

        Entity actor = world.createEntity();
        LayerComponent actorLayer = actor.edit().create(LayerComponent.class);
        actorLayer.layerIndex = 1;
        actorLayer.type = LayerComponent.TYPE_PHYSICS;
        actor.edit().create(EntityIndexComponent.class).layerIndex = 1;

        world.process();

        Assert.assertEquals(sceneLayer.getId(), layerState.entityId[1]);
        Assert.assertEquals(LayerComponent.TYPE_CLASSIC, layerState.type[1]);
    }
}
