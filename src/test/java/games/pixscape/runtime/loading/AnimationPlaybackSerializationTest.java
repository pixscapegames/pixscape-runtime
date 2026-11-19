package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.VisualFlipComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;

public class AnimationPlaybackSerializationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void visualFlipIsExcludedFromSceneAndGenericJsonSerialization() throws Exception {
        World source = serializationWorld();
        World target = serializationWorld();
        try {
            int entity = source.create();
            source.getMapper(AnimationComponent.class).create(entity).currentClip = "idle";
            VisualFlipComponent flip = source.getMapper(VisualFlipComponent.class).create(entity);
            flip.flipX = flip.flipY = true;
            String generic = new Json().toJson(flip);
            Assert.assertFalse(generic.contains("flipX"));
            Assert.assertFalse(generic.contains("flipY"));
            source.process();
            WorldSerializationManager serialization = source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat request = new SaveFileFormat();
            request.entities.add(entity);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            serialization.save(out, request);
            Assert.assertFalse(out.toString("UTF-8").contains("VisualFlipComponent"));
            FileHandle file = new FileHandle(temporaryFolder.newFile("visual-flip.json"));
            file.writeBytes(out.toByteArray(), false);
            SaveFileFormat loaded = SceneLoader.loadScene(target, file, true,
                    new SceneMetaRuntime("test", file.path()));
            Assert.assertFalse(target.getMapper(VisualFlipComponent.class).has(loaded.entities.get(0)));
            Assert.assertEquals("idle", target.getMapper(AnimationComponent.class)
                    .get(loaded.entities.get(0)).currentClip);
        } finally {
            source.dispose();
            target.dispose();
        }
    }

    @Test
    public void sceneRoundTripPreservesSubFloatPlaybackPrecision() throws Exception {
        World source = serializationWorld();
        World target = serializationWorld();
        try {
            int entity = source.create();
            AnimationComponent before = source.getMapper(AnimationComponent.class).create(entity);
            before.stateTime = 100344.640123d;
            before.currentClip = "idle_090";
            source.process();
            WorldSerializationManager serialization = source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat request = new SaveFileFormat();
            request.entities.add(entity);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            serialization.save(out, request);
            FileHandle file = new FileHandle(temporaryFolder.newFile("animation.json"));
            file.writeBytes(out.toByteArray(), false);

            SaveFileFormat loaded = SceneLoader.loadScene(target, file, true,
                    new SceneMetaRuntime("test", file.path()));
            AnimationComponent after = target.getMapper(AnimationComponent.class)
                    .get(loaded.entities.get(0));
            Assert.assertEquals(before.stateTime, after.stateTime, 0d);
            Assert.assertEquals(before.currentClip, after.currentClip);
        } finally {
            source.dispose();
            target.dispose();
        }
    }

    @Test
    public void gameObjectAnimationPreservesPrecisionAndReadsExistingNumericTime() {
        Json json = new Json();
        GameObjectAsset.AnimationData data = new GameObjectAsset.AnimationData();
        data.stateTime = 100344.640123d;
        GameObjectAsset.AnimationData loaded = json.fromJson(GameObjectAsset.AnimationData.class,
                json.toJson(data));
        Assert.assertEquals(data.stateTime, loaded.stateTime, 0d);
        Assert.assertEquals(100344.64d, json.fromJson(GameObjectAsset.AnimationData.class,
                "{\"stateTime\":100344.64}").stateTime, 0d);
    }

    private static World serializationWorld() {
        return new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
    }
}
