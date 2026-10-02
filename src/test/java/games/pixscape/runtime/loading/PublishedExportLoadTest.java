package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetLoader;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.service.AnimationRegistry;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.tiled.TiledMapOwnership;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;

/** GL-free loading evidence for the published example and the optional local demo corpus. */
public class PublishedExportLoadTest {
    @Test
    public void minimalExampleLoadsWithRuntimeAndAtlasDescriptor() {
        FileHandle root = new FileHandle(new File("examples/minimal-export"));
        Assert.assertTrue(root.child("project.json").exists());
        loadEveryScene(root);
        TextureAtlas.TextureAtlasData atlas = new TextureAtlas.TextureAtlasData(
                root.child("atlases/scene1.atlas"), root.child("atlases"), false);
        Assert.assertEquals(1, atlas.getPages().size);
        Assert.assertEquals("square__a1", atlas.getRegions().first().name);
    }

    @Test
    public void optionalLocalDemoExportLoadsBothScenesAndAssociatedDocuments() {
        FileHandle root = new FileHandle(new File("../tiled-iso-demo/assets/pixscape-project"));
        Assume.assumeTrue("The sibling tiled-iso-demo checkout is optional",
                root.child("project.json").exists());
        loadEveryScene(root);
        new GameObjectAssetLoader().load(root.child("gameobjects/snake.gameobject"));
        new GameObjectAssetLoader().load(root.child("gameobjects/car.gameobject"));
        for (FileHandle descriptor : root.child("hud").list(".hudscreen")) {
            HudScreenAsset asset = new HudScreenAssetLoader().load(root,
                    "hud/" + descriptor.nameWithoutExtension());
            Assert.assertTrue(new HudDocumentValidator().validate(
                    new HudDocumentCodec().read(root.child(asset.documentId))).isValid());
        }
    }

    private static void loadEveryScene(FileHandle root) {
        RuntimeConfig project = RuntimeProjectIO.loadProject(root);
        RuntimeProjectIO.loadAnimations(root, new AnimationRegistry());
        RuntimeProjectIO.loadTileAnimations(root, new TileAnimationRegistry());
        RuntimeProjectIO.loadTilesetProfiles(root);
        World world = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            for (SceneMetaRuntime meta : project.scenes.values()) {
                FileHandle scene = root.child(project.scenesDir).child(meta.file);
                SceneLoader.loadScene(world, scene, true, meta);
                TiledMapOwnership.validateWorld(world);
                String tag = meta.file.substring(0, meta.file.lastIndexOf('.'));
                TextureAtlas.TextureAtlasData atlas = new TextureAtlas.TextureAtlasData(
                        root.child(project.atlasesDir).child(tag + ".atlas"),
                        root.child(project.atlasesDir), false);
                Assert.assertTrue(atlas.getPages().size > 0);
            }
        } finally {
            world.dispose();
        }
    }
}
