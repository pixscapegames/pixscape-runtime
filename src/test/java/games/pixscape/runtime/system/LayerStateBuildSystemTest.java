package games.pixscape.runtime.system;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.LayerStateSOA;
import org.junit.Assert;
import org.junit.Test;

public class LayerStateBuildSystemTest {

    @Test
    public void tiledSerializedTypeRemainsThree() {
        Assert.assertEquals(3, LayerComponent.TYPE_TILED);
    }

    @Test
    public void scenePhysicsParallaxRemainsGloballyAvailable() {
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
        layer.type = LayerComponent.TYPE_CLASSIC;

        // Act
        world.process();

        // Assert
        Assert.assertEquals(
                "Scene Physics parallax X should remain globally available",
                sceneMeta.physicsParallaxX,
                layerState.physicsParallaxX,
                0.0001f
        );
        Assert.assertEquals(
                "Scene Physics parallax Y should remain globally available",
                sceneMeta.physicsParallaxY,
                layerState.physicsParallaxY,
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
        actorLayer.type = LayerComponent.TYPE_CLASSIC;
        actor.edit().create(EntityIndexComponent.class).layerIndex = 1;
        actor.edit().create(VisibilityComponent.class).visible = false;

        world.process();

        Assert.assertEquals(1, layerState.maxLayerIndex());
        Assert.assertTrue(layerState.enabled[1]);
    }
}
