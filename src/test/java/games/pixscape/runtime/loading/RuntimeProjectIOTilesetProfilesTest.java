package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetRenderSize;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;

public class RuntimeProjectIOTilesetProfilesTest {

    @Test
    public void missingTilesetProfilesJsonReturnsEmptyRegistry() {
        File dir = makeTempDir("pixscape-runtime-no-tileset-profiles");

        RuntimeTilesetProfiles profiles = RuntimeProjectIO.loadTilesetProfiles(new FileHandle(dir));

        Assert.assertEquals(0, profiles.size());
        Assert.assertNull(profiles.profileForTileAsset(101));
    }

    @Test
    public void tilesetProfilesJsonLoadsLookupByTileAssetId() throws Exception {
        File dir = makeTempDir("pixscape-runtime-tileset-profiles");
        File file = new File(dir, "tileset-profiles.json");
        FileWriter writer = new FileWriter(file);
        writer.write("{\"format\":\"pixscape.tileset-profiles\",\"version\":1,\"tilesets\":[{\"tilesetId\":7,\"logicalPath\":\"tiles/terrain\",\"tileWidth\":64,\"tileHeight\":96,\"referenceCellWidth\":32,\"referenceCellHeight\":16,\"projection\":\"isometric\",\"anchor\":\"top-center\",\"offsetX\":3,\"offsetY\":-5,\"renderSize\":\"native\",\"tileAssetIds\":[101,102]}]}");
        writer.close();

        RuntimeTilesetProfiles profiles = RuntimeProjectIO.loadTilesetProfiles(new FileHandle(dir));
        RuntimeTilesetProfile profile = profiles.profileForTileAsset(102);

        Assert.assertEquals(1, profiles.size());
        Assert.assertNotNull(profile);
        Assert.assertSame(profile, profiles.profileForTileset(7));
        Assert.assertEquals("tiles/terrain", profile.logicalPath);
        Assert.assertEquals(64, profile.tileWidth);
        Assert.assertEquals(96, profile.tileHeight);
        Assert.assertEquals(32, profile.referenceCellWidth);
        Assert.assertEquals(16, profile.referenceCellHeight);
        Assert.assertEquals(SceneMetaRuntime.TiledProjection.ISO, profile.projection);
        Assert.assertEquals(RuntimeTilesetAnchor.TOP_CENTER, profile.anchor);
        Assert.assertEquals(3, profile.offsetX);
        Assert.assertEquals(-5, profile.offsetY);
        Assert.assertEquals(RuntimeTilesetRenderSize.NATIVE, profile.renderSize);
        Assert.assertNull(profiles.profileForTileAsset(999));
    }

    @Test(expected = GdxRuntimeException.class)
    public void unsupportedManifestVersionThrows() throws Exception {
        File dir = makeTempDir("pixscape-runtime-bad-tileset-profiles");
        File file = new File(dir, "tileset-profiles.json");
        FileWriter writer = new FileWriter(file);
        writer.write("{\"format\":\"pixscape.tileset-profiles\",\"version\":99,\"tilesets\":[]}");
        writer.close();

        RuntimeProjectIO.loadTilesetProfiles(new FileHandle(dir));
    }

    private static File makeTempDir(String prefix) {
        File root = new File(System.getProperty("java.io.tmpdir"));
        File dir = new File(root, prefix + "-" + System.nanoTime());
        if (!dir.mkdirs()) {
            throw new IllegalStateException("Could not create temp dir: " + dir.getAbsolutePath());
        }
        dir.deleteOnExit();
        return dir;
    }
}
