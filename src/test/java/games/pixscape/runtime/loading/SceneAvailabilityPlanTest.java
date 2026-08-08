package games.pixscape.runtime.loading;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.configuration.RuntimeConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SceneAvailabilityPlanTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void sceneARequestsDeduplicatedAuthoredAndDeclaredParticlesOnly() throws Exception {
        File root = temp.newFolder("project");
        write(root, "scenes/A.json", "{\"first\":{\"effectPath\":\"shared.p\",\"atlasTag\":\"A\"},"
                + "\"second\":{\"effectPath\":\"onlyA\",\"atlasTag\":\"A\"},"
                + "\"duplicate\":{\"effectPath\":\"shared.p\",\"atlasTag\":\"A\"}}");
        write(root, "atlases/A.atlas", "");
        write(root, "effects/shared.p", "shared");
        write(root, "effects/onlyA.p", "only-a");
        write(root, "effects/dynamicButDeclaredA.p", "dynamic-a");
        write(root, "scenes/B.json", "{}");
        write(root, "atlases/B.atlas", "");
        write(root, "effects/onlyB.p", "only-b");
        write(root, "audio/unrelated.ogg", "audio");
        write(root, "prefabs/unrelated.pixfragment.json", "{}");

        RuntimeConfig config = config();
        config.getSceneMeta("A").runtimeParticleEffectPaths.add("shared.p");
        config.getSceneMeta("A").runtimeParticleEffectPaths.add("dynamicButDeclaredA.p");
        RecordingAssetManager manager = manager(root);
        TrackingAtlas realizedAtlas = new TrackingAtlas();
        manager.setLoader(TextureAtlas.class,
                new StubAtlasLoader(manager.getFileHandleResolver(), realizedAtlas));
        FileAvailabilityService availability = new FileAvailabilityService(manager, false);
        SceneAvailabilityPlan plan = new SceneAvailabilityPlan(
                availability, config, new FileHandle(root), "A");
        try {
            while (!plan.update()) {
                // Progressive driver uses the same staged dependency logic as native.
            }

            assertSame(realizedAtlas, plan.atlas());
            assertEquals(5, manager.loadCounts.size());
            assertEquals(Integer.valueOf(1), manager.loadCounts.get(path(root, "scenes/A.json")));
            assertEquals(Integer.valueOf(1), manager.loadCounts.get(path(root, "atlases/A.atlas")));
            assertEquals(Integer.valueOf(1), manager.loadCounts.get(path(root, "effects/shared.p")));
            assertEquals(Integer.valueOf(1), manager.loadCounts.get(path(root, "effects/onlyA.p")));
            assertEquals(Integer.valueOf(1), manager.loadCounts.get(path(root, "effects/dynamicButDeclaredA.p")));
            assertFalse(manager.loadCounts.containsKey(path(root, "scenes/B.json")));
            assertFalse(manager.loadCounts.containsKey(path(root, "atlases/B.atlas")));
            assertFalse(manager.loadCounts.containsKey(path(root, "effects/onlyB.p")));
            assertFalse(manager.loadCounts.containsKey(path(root, "audio/unrelated.ogg")));
            assertFalse(manager.loadCounts.containsKey(path(root, "prefabs/unrelated.pixfragment.json")));
            assertEquals(1f, plan.progress(), 0f);
        } finally {
            plan.release();
            assertTrue(realizedAtlas.disposed);
            availability.dispose();
            manager.dispose();
        }
    }

    @Test
    public void oldProjectWithoutRuntimeAvailabilityHasEmptyParticleDefaults() {
        com.badlogic.gdx.utils.JsonValue json = new com.badlogic.gdx.utils.JsonReader().parse(
                "{\"sceneSchemaVersion\":2,\"name\":\"A\",\"file\":\"A.json\","
                        + "\"nextEntityStableId\":1,\"nextPhysicsShapeId\":1}");
        SceneMetaRuntime meta = SceneMetaRuntime.fromJson(json, "A");
        assertTrue(meta.runtimeParticleEffectPaths.isEmpty());
    }

    @Test
    public void textureAtlasLoaderDiscoversPageDependenciesFromAtlasMetadata() throws Exception {
        File root = temp.newFolder("atlas-dependencies");
        FileHandle atlas = new FileHandle(write(root, "atlases/A.atlas",
                "page.png\nsize: 1, 1\nformat: RGBA8888\nfilter: Nearest,Nearest\nrepeat: none\n"));
        final FileHandleResolver resolver = resolver(root);
        TextureAtlasLoader loader = new TextureAtlasLoader(resolver);

        Array<AssetDescriptor> dependencies = loader.getDependencies(
                "atlases/A.atlas", atlas, new TextureAtlasLoader.TextureAtlasParameter());

        assertEquals(1, dependencies.size);
        assertTrue(dependencies.first().fileName.replace('\\', '/').endsWith("/atlases/page.png"));
    }

    @Test
    public void missingRequiredParticleFailureReleasesPartialSceneReferences() throws Exception {
        File root = temp.newFolder("failure-cleanup");
        write(root, "scenes/A.json", "{}");
        write(root, "atlases/A.atlas", "");
        RuntimeConfig config = config();
        config.getSceneMeta("A").runtimeParticleEffectPaths.add("missing.p");
        RecordingAssetManager manager = manager(root);
        TrackingAtlas atlas = new TrackingAtlas();
        manager.setLoader(TextureAtlas.class,
                new StubAtlasLoader(manager.getFileHandleResolver(), atlas));
        FileAvailabilityService availability = new FileAvailabilityService(manager, false);
        SceneAvailabilityPlan plan = new SceneAvailabilityPlan(
                availability, config, new FileHandle(root), "A");

        assertThrows(RuntimeException.class, () -> {
            while (!plan.update()) {
                // Drive until the required missing file fails.
            }
        });
        plan.release();

        assertFalse(manager.isLoaded(path(root, "scenes/A.json")));
        assertFalse(manager.isLoaded(path(root, "atlases/A.atlas")));
        assertTrue(atlas.disposed);
        availability.dispose();
        manager.dispose();
    }

    private static RuntimeConfig config() {
        RuntimeConfig config = new RuntimeConfig();
        config.scenes.put("A", meta("A", "A.json"));
        config.scenes.put("B", meta("B", "B.json"));
        config.currentSceneName = "A";
        return config;
    }

    private static SceneMetaRuntime meta(String name, String file) {
        SceneMetaRuntime meta = new SceneMetaRuntime(name, file);
        meta.nextEntityStableId = 1;
        meta.nextPhysicsShapeId = 1;
        return meta;
    }

    private static RecordingAssetManager manager(File root) {
        return new RecordingAssetManager(resolver(root));
    }

    private static FileHandleResolver resolver(final File root) {
        return new FileHandleResolver() {
            @Override
            public FileHandle resolve(String fileName) {
                File path = new File(fileName);
                return new FileHandle(path.isAbsolute() ? path : new File(root, fileName));
            }
        };
    }

    private static File write(File root, String relative, String content) throws Exception {
        File file = new File(root, relative);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String path(File root, String relative) {
        return new File(root, relative).getPath().replace('\\', '/');
    }

    private static final class RecordingAssetManager extends AssetManager {
        final Map<String, Integer> loadCounts = new LinkedHashMap<>();

        RecordingAssetManager(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        public synchronized <T> void load(
                String fileName, Class<T> type, AssetLoaderParameters<T> parameter) {
            String normalized = fileName.replace('\\', '/');
            Integer count = loadCounts.get(normalized);
            loadCounts.put(normalized, count == null ? 1 : count + 1);
            super.load(fileName, type, parameter);
        }
    }

    private static final class StubAtlasLoader extends SynchronousAssetLoader<
            TextureAtlas, TextureAtlasLoader.TextureAtlasParameter> {
        private final TextureAtlas atlas;

        StubAtlasLoader(FileHandleResolver resolver, TextureAtlas atlas) {
            super(resolver);
            this.atlas = atlas;
        }

        @Override
        public TextureAtlas load(AssetManager manager, String fileName, FileHandle file,
                                 TextureAtlasLoader.TextureAtlasParameter parameter) {
            return atlas;
        }

        @Override
        public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file,
                TextureAtlasLoader.TextureAtlasParameter parameter) {
            return null;
        }
    }

    private static final class TrackingAtlas extends TextureAtlas {
        boolean disposed;

        @Override
        public void dispose() {
            disposed = true;
            super.dispose();
        }
    }
}
