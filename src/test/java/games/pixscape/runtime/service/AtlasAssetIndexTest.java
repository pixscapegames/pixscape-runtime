package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AtlasAssetIndexTest {

    @Test
    public void publicNameLookupsDoNotVisitPublishedRegions() throws Exception {
        TestTexture texture = new TestTexture(32, 32);
        CountingTextureAtlas atlas = new CountingTextureAtlas();
        for (int i = 1; i <= 2000; i++) {
            atlas.getRegions().add(region(texture, "folder/item" + i + ".png__a" + i,
                    -1, 0, 0, 8, 8));
        }
        TestAtlasRuntimeService service = new TestAtlasRuntimeService();
        service.publishOwned("main", atlas);
        games.pixscape.runtime.engine.PixscapeEngine engine = engineWithAtlas(service);
        atlas.countedRegions.visits = 0;
        for (int i = 0; i < 100; i++) {
            games.pixscape.runtime.api.AssetRegionRef ref = engine.api().assets().region(" ITEM2000.PNG ");
            Assert.assertEquals(2000, ref.assetId());
            Assert.assertSame(texture, ref.region().getTexture());
            Assert.assertEquals(8f, ref.width(), 0f);
            Assert.assertFalse(engine.api().assets().contains("missing"));
        }
        Assert.assertEquals("Published lookups, including misses, must not visit atlas regions",
                0, atlas.countedRegions.visits);
        atlas.countedRegions.get(1999).name = "renamed__a2000";
        service.publishOwned("main", atlas);
        Assert.assertEquals(2000, service.indexBuildRegionVisits("main"));
        atlas.countedRegions.visits = 0;
        Assert.assertFalse(engine.api().assets().contains("item2000"));
        Assert.assertEquals(2000, engine.api().assets().region("renamed").assetId());
        Assert.assertEquals(100, engine.api().assets().region("item100").assetId());
        Assert.assertEquals(0, atlas.countedRegions.visits);
    }

    private static games.pixscape.runtime.engine.PixscapeEngine engineWithAtlas(
            AtlasRuntimeService service) throws Exception {
        games.pixscape.runtime.engine.PixscapeEngine engine = new games.pixscape.runtime.engine.PixscapeEngine();
        java.lang.reflect.Field field = engine.getClass().getDeclaredField("atlasRuntimeService");
        field.setAccessible(true);
        field.set(engine, service);
        return engine;
    }

    @Test
    public void namesPreserveAliasesHomonymsAndPublicationLifecycle() throws Exception {
        TestTexture texture = new TestTexture(32, 32);
        AtlasRuntimeService service = new AtlasRuntimeService();
        TextureAtlas first = atlas(
                region(texture, "dir/Hero.png__a9", -1, 0, 0, 8, 8),
                region(texture, "other/HERO.jpg__a2", -1, 0, 0, 16, 16),
                region(texture, "\u0130con__a3", -1, 0, 0, 4, 4));
        service.loadBorrowed("main", first);
        games.pixscape.runtime.api.AssetsAPI assets = engineWithAtlas(service).api().assets();
        Assert.assertEquals(9, assets.region(" C:\\folder\\hErO.png ").assetId());
        Assert.assertEquals(2, assets.region(2).assetId());
        Assert.assertEquals(3, assets.region("icon").assetId());
        Assert.assertFalse(assets.contains((String) null));
        Assert.assertFalse(assets.contains("  "));
        Assert.assertFalse(assets.contains("missing"));
        Assert.assertThrows(IllegalArgumentException.class, () -> assets.region("missing"));

        Assert.assertThrows(IllegalStateException.class, () -> service.loadBorrowed("main",
                atlas(region(texture, "bad__a0", -1, 0, 0, 8, 8))));
        Assert.assertEquals(9, assets.region("hero").assetId());
        service.loadBorrowed("other", atlas(region(texture, "hero__a7", -1, 0, 0, 8, 8)));
        service.loadBorrowed("main", atlas(region(texture, "replacement__a4", -1, 0, 0, 8, 8)));
        Assert.assertFalse(assets.contains("hero"));
        Assert.assertEquals(4, assets.region("replacement").assetId());
        Assert.assertEquals(7, service.resolveBindingByName("hero", "other").assetId());
        service.unload("main");
        Assert.assertFalse(assets.contains("replacement"));
        service.loadBorrowed("main", first);
        Assert.assertEquals(9, assets.region("hero").assetId());
        service.unloadAll();
        Assert.assertFalse(assets.contains("hero"));
        Assert.assertNull(service.resolveBindingByName("hero", "other"));
    }

    @Test
    public void nameIndexPreservesSimpleCaseMatchingForSupplementaryLetters() {
        String upper = new String(Character.toChars(0x10400));
        String lower = new String(Character.toChars(0x10428));
        // Compare against the original API predicate on the executing JVM.
        Assert.assertTrue(upper.equalsIgnoreCase(lower));
        AtlasAssetIndex index = AtlasAssetIndexBuilder.build("main", atlas(
                region(new TestTexture(32, 32), upper + "__a1", -1, 0, 0, 8, 8)));
        Assert.assertSame(index.get(1), index.getByName(lower));
    }

    @Test
    public void webGlNamesPreserveGwtLowerCaseAndLengthComparison() {
        com.badlogic.gdx.Application previous = com.badlogic.gdx.Gdx.app;
        com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                com.badlogic.gdx.Application.class.getClassLoader(),
                new Class<?>[]{com.badlogic.gdx.Application.class},
                (proxy, method, args) -> method.getName().equals("getType")
                        ? com.badlogic.gdx.Application.ApplicationType.WebGL : null);
        try {
            TestTexture texture = new TestTexture(32, 32);
            String upper = new String(Character.toChars(0x10400));
            String lower = new String(Character.toChars(0x10428));
            AtlasAssetIndex index = AtlasAssetIndexBuilder.build("main", atlas(
                    region(texture, "\u0130__a1", -1, 0, 0, 8, 8),
                    region(texture, "i\u0307__a2", -1, 0, 0, 8, 8),
                    region(texture, upper + "__a3", -1, 0, 0, 8, 8)));
            Assert.assertNull(index.getByName("i"));
            Assert.assertSame(index.get(1), index.getByName("\u0130"));
            Assert.assertSame(index.get(2), index.getByName("i\u0307"));
            Assert.assertSame(index.get(3), index.getByName(lower));
        } finally {
            com.badlogic.gdx.Gdx.app = previous;
        }
    }

    @Test
    public void namedSpritesAreUsableBeforeProcessingAndAfterReplacement() throws Exception {
        TestTexture texture = new TestTexture(32, 32);
        CountingTextureAtlas atlas = countingAtlas(region(texture, "hero__a9", -1, 0, 0, 8, 8));
        AtlasRuntimeService service = new AtlasRuntimeService();
        service.loadBorrowed("main", atlas);
        games.pixscape.runtime.engine.PixscapeEngine engine = engineWithAtlas(service);
        com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder().build());
        java.lang.reflect.Field field = engine.getClass().getDeclaredField("world");
        field.setAccessible(true);
        field.set(engine, world);
        try {
            atlas.countedRegions.visits = 0;
            games.pixscape.runtime.api.SpriteRef sprite = engine.api().sprites().spawn("HERO", 3f, 4f);
            Assert.assertTrue(sprite.entity().exists());
            Assert.assertEquals(3f, sprite.transform().x(), 0f);
            games.pixscape.runtime.component.TextureRegionComponent rendered = world.getMapper(
                    games.pixscape.runtime.component.TextureRegionComponent.class).get(sprite.entityId());
            Assert.assertTrue(rendered.valid);
            Assert.assertEquals(8f, rendered.pixW, 0f);
            Assert.assertEquals(TextureRegistry.handleOf(texture), world.getMapper(
                    games.pixscape.runtime.component.RenderMaterialComponent.class).get(sprite.entityId()).textureHandle);
            Assert.assertEquals(0, atlas.countedRegions.visits);
            world.process();
            service.loadBorrowed("main", atlas(region(texture, "hero__a10", -1, 0, 0, 16, 16)));
            games.pixscape.runtime.api.SpriteRef next = engine.api().sprites().spawn("hero", 5f, 6f);
            Assert.assertTrue(next.entity().exists());
            Assert.assertEquals(10, world.getMapper(games.pixscape.runtime.component.AssetRefComponent.class)
                    .get(next.entityId()).assetId);
            Assert.assertEquals(16f, world.getMapper(games.pixscape.runtime.component.TextureRegionComponent.class)
                    .get(next.entityId()).pixW, 0f);
        } finally {
            world.dispose();
            service.unloadAll();
        }
    }

    @Before
    public void setUp() {
        TextureRegistry.clear();
    }

    @After
    public void tearDown() {
        TextureRegistry.clear();
    }

    @Test
    public void simpleAssetPublishesCompleteBindingAndMetadata() {
        TestTexture texture = new TestTexture(100, 200);
        TextureAtlas atlas = atlas(
                region(texture, "hero__a17", -1, 10, 20, 30, 40));

        AtlasAssetIndex index = AtlasAssetIndexBuilder.build("main", atlas);
        AtlasAssetBinding binding = index.get(17);

        Assert.assertEquals(1, index.size());
        Assert.assertEquals(1, index.buildRegionVisits());
        Assert.assertEquals(17, binding.assetId());
        Assert.assertEquals("hero__a17", binding.regionGroup());
        Assert.assertSame(binding.firstRegion(), binding.regionAt(0));
        Assert.assertEquals(1, binding.regionCount());
        Assert.assertEquals(0.10f, binding.metadata().u1(), 0.0001f);
        Assert.assertEquals(0.10f, binding.metadata().v1(), 0.0001f);
        Assert.assertEquals(0.40f, binding.metadata().u2(), 0.0001f);
        Assert.assertEquals(0.30f, binding.metadata().v2(), 0.0001f);
        Assert.assertEquals(30, binding.metadata().pixelWidth());
        Assert.assertEquals(40, binding.metadata().pixelHeight());
        Assert.assertEquals(
                TextureRegistry.handleOf(texture),
                binding.metadata().textureHandle());
    }

    @Test
    public void animatedGroupIsSortedOnceAndReusesTheSameCollection() {
        TestTexture texture = new TestTexture(64, 64);
        TextureAtlas.AtlasRegion frame2 =
                region(texture, "run__a923", 2, 20, 0, 10, 10);
        TextureAtlas.AtlasRegion frame0 =
                region(texture, "run__a923", 0, 0, 0, 10, 10);
        TextureAtlas.AtlasRegion frame1 =
                region(texture, "run__a923", 1, 10, 0, 10, 10);
        TestAtlasRuntimeService service = new TestAtlasRuntimeService();

        service.publishOwned("main", atlas(frame2, frame0, frame1));

        AtlasAssetBinding binding = service.resolveBinding(923, "main");
        AtlasAssetBinding second = service.resolveBinding(923, "main");
        Assert.assertSame(binding, second);
        Assert.assertSame(frame0, binding.firstRegion());
        Assert.assertSame(frame0, binding.regionAt(0));
        Assert.assertSame(frame1, binding.regionAt(1));
        Assert.assertSame(frame2, binding.regionAt(2));
        Assert.assertSame(binding.metadata(), service.resolveCached(923, "main"));
    }

    @Test
    public void repeatedPresentAndAbsentLookupsNeverRevisitAtlasRegions() {
        TestTexture texture = new TestTexture(32, 32);
        CountingTextureAtlas atlas = countingAtlas(
                region(texture, "first__a1", -1, 0, 0, 8, 8),
                region(texture, "internal-white", -1, 0, 0, 1, 1),
                region(texture, "middle__a2", -1, 8, 0, 8, 8),
                region(texture, "internal-normal", -1, 0, 0, 1, 1),
                region(texture, "last__a3", -1, 16, 0, 8, 8));
        TestAtlasRuntimeService service = new TestAtlasRuntimeService();
        service.publishOwned("main", atlas);
        int visitsAfterLoad = service.indexBuildRegionVisits("main");
        int getRegionsCallsAfterLoad = atlas.getRegionsCalls;
        int findRegionsCallsAfterLoad = atlas.findRegionsCalls;

        Assert.assertNull(service.resolveBinding(999999, "main"));
        for (int i = 0; i < 10000; i++) {
            Assert.assertNotNull(service.resolveBinding(1, "main"));
            Assert.assertNotNull(service.resolveBinding(2, "main"));
            Assert.assertNotNull(service.resolveBinding(3, "main"));
            Assert.assertNull(service.resolveBinding(999999, "main"));
            Assert.assertNull(service.resolveCached(999999, "main"));
        }

        Assert.assertEquals(5, visitsAfterLoad);
        Assert.assertEquals(visitsAfterLoad, service.indexBuildRegionVisits("main"));
        Assert.assertEquals(getRegionsCallsAfterLoad, atlas.getRegionsCalls);
        Assert.assertEquals(findRegionsCallsAfterLoad, atlas.findRegionsCalls);
    }

    @Test
    public void multiPageGroupKeepsOriginalTextureReferences() {
        TestTexture pageOne = new TestTexture(128, 128);
        TestTexture pageTwo = new TestTexture(256, 256);
        TextureAtlas.AtlasRegion frame0 =
                region(pageOne, "effect__a44", 0, 0, 0, 16, 16);
        TextureAtlas.AtlasRegion frame1 =
                region(pageTwo, "effect__a44", 1, 32, 48, 24, 24);

        AtlasAssetBinding binding =
                AtlasAssetIndexBuilder.build("multi", atlas(frame1, frame0)).get(44);

        Assert.assertSame(pageOne, binding.regionAt(0).getTexture());
        Assert.assertSame(pageTwo, binding.regionAt(1).getTexture());
        Assert.assertSame(frame0, binding.regionAt(0));
        Assert.assertSame(frame1, binding.regionAt(1));
    }

    @Test
    public void nonPixscapeRegionsAndEmptyAtlasesProduceNoBindings() {
        TestTexture texture = new TestTexture(16, 16);
        AtlasAssetIndex internalOnly = AtlasAssetIndexBuilder.build(
                "internal",
                atlas(
                        region(texture, "white", -1, 0, 0, 1, 1),
                        region(texture, "engine-normal", -1, 0, 0, 1, 1)));
        AtlasAssetIndex empty =
                AtlasAssetIndexBuilder.build("empty", new TextureAtlas());

        Assert.assertEquals(0, internalOnly.size());
        Assert.assertEquals(2, internalOnly.buildRegionVisits());
        Assert.assertEquals(0, empty.size());
        Assert.assertEquals(0, empty.buildRegionVisits());
    }

    @Test
    public void rejectsInvalidAssetSuffixes() {
        assertInvalidSuffix("hero__a", "empty suffix");
        assertInvalidSuffix("hero__a0", "must be positive");
        assertInvalidSuffix("hero__a-3", "non-numeric");
        assertInvalidSuffix("hero__anot-a-number", "non-numeric");
        assertInvalidSuffix("hero__a2147483648", "overflow");
    }

    @Test
    public void rejectsOneAssetIdUsedByTwoGroups() {
        TestTexture texture = new TestTexture(16, 16);
        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class,
                () -> AtlasAssetIndexBuilder.build(
                        "characters",
                        atlas(
                                region(texture, "hero__a923", -1, 0, 0, 8, 8),
                                region(texture, "enemy__a923", -1, 8, 0, 8, 8))));

        Assert.assertTrue(failure.getMessage().contains("characters"));
        Assert.assertTrue(failure.getMessage().contains("923"));
        Assert.assertTrue(failure.getMessage().contains("hero__a923"));
        Assert.assertTrue(failure.getMessage().contains("enemy__a923"));
    }

    @Test
    public void rejectsDuplicateAndIncoherentFrameIndexes() {
        TestTexture texture = new TestTexture(16, 16);
        IllegalStateException duplicate = Assert.assertThrows(
                IllegalStateException.class,
                () -> AtlasAssetIndexBuilder.build(
                        "duplicate",
                        atlas(
                                region(texture, "run__a7", 0, 0, 0, 8, 8),
                                region(texture, "run__a7", 0, 8, 0, 8, 8))));
        IllegalStateException mixed = Assert.assertThrows(
                IllegalStateException.class,
                () -> AtlasAssetIndexBuilder.build(
                        "mixed",
                        atlas(
                                region(texture, "run__a7", 0, 0, 0, 8, 8),
                                region(texture, "run__a7", -1, 8, 0, 8, 8))));
        IllegalStateException negative = Assert.assertThrows(
                IllegalStateException.class,
                () -> AtlasAssetIndexBuilder.build(
                        "negative",
                        atlas(region(texture, "run__a7", -2, 0, 0, 8, 8))));

        Assert.assertTrue(duplicate.getMessage().contains("duplicate frame index 0"));
        Assert.assertTrue(mixed.getMessage().contains("mixes indexed and unindexed"));
        Assert.assertTrue(negative.getMessage().contains("invalid negative frame index -2"));
    }

    @Test
    public void reloadAndUnloadKeepAtlasAndIndexLifecycleAligned() {
        TestTexture texture = new TestTexture(32, 32);
        TestAtlasRuntimeService service = new TestAtlasRuntimeService();
        TextureAtlas firstAtlas =
                atlas(region(texture, "first__a1", -1, 0, 0, 8, 8));
        TextureAtlas secondAtlas =
                atlas(region(texture, "second__a2", -1, 8, 0, 8, 8));

        service.publishOwned("main", firstAtlas);
        Assert.assertSame(firstAtlas, service.getAtlas("main"));
        Assert.assertNotNull(service.resolveBinding(1, "main"));

        service.publishOwned("main", secondAtlas);
        Assert.assertSame(secondAtlas, service.getAtlas("main"));
        Assert.assertNull(service.resolveBinding(1, "main"));
        Assert.assertNotNull(service.resolveBinding(2, "main"));

        service.unload("main");
        Assert.assertNull(service.getAtlas("main"));
        Assert.assertNull(service.resolveBinding(2, "main"));
        Assert.assertEquals(-1, service.indexBuildRegionVisits("main"));

        service.publishOwned(
                "one",
                atlas(region(texture, "one__a11", -1, 0, 0, 8, 8)));
        service.publishOwned(
                "two",
                atlas(region(texture, "two__a22", -1, 8, 0, 8, 8)));
        service.unloadAll();
        Assert.assertNull(service.getAtlas("one"));
        Assert.assertNull(service.getAtlas("two"));
        Assert.assertNull(service.resolveBinding(11, "one"));
        Assert.assertNull(service.resolveBinding(22, "two"));
    }

    @Test
    public void borrowedAtlasRemainsOwnedByItsCaller() {
        TestTexture texture = new TestTexture(16, 16);
        TrackingTextureAtlas atlas = new TrackingTextureAtlas();
        atlas.getRegions().add(region(texture, "borrowed__a1", -1, 0, 0, 8, 8));
        AtlasRuntimeService service = new AtlasRuntimeService();

        service.loadBorrowed("main", atlas);
        Assert.assertSame(atlas, service.getAtlas("main"));

        service.unload("main");
        Assert.assertFalse(atlas.disposed);

        atlas.dispose();
        Assert.assertTrue(atlas.disposed);
    }

    @Test
    public void failedReloadLeavesPreviousAtlasAndIndexPublished() {
        TestTexture texture = new TestTexture(32, 32);
        TestAtlasRuntimeService service = new TestAtlasRuntimeService();
        TextureAtlas valid =
                atlas(region(texture, "valid__a1", -1, 0, 0, 8, 8));
        service.publishOwned("main", valid);

        Assert.assertThrows(
                IllegalStateException.class,
                () -> service.publishOwned(
                        "main",
                        atlas(region(texture, "invalid__a0", -1, 8, 0, 8, 8))));

        Assert.assertSame(valid, service.getAtlas("main"));
        Assert.assertNotNull(service.resolveBinding(1, "main"));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveBinding(0, "main"));
    }

    @Test
    public void allServiceLookupsRejectNonPositiveAssetIdsConsistently() {
        AtlasRuntimeService service = new AtlasRuntimeService();

        assertInvalidLookup(() -> service.resolveBinding(0, "main"), 0);
        assertInvalidLookup(() -> service.resolveBinding(-1, "main"), -1);
        assertInvalidLookup(() -> service.resolveCached(0, "main"), 0);
        assertInvalidLookup(() -> service.resolveCached(-1, "main"), -1);
    }

    private static void assertInvalidLookup(Runnable lookup, int assetId) {
        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                lookup::run);
        Assert.assertTrue(failure.getMessage().contains("> 0"));
        Assert.assertTrue(failure.getMessage().contains(Integer.toString(assetId)));
    }

    private static void assertInvalidSuffix(String name, String expectedDiagnostic) {
        TestTexture texture = new TestTexture(16, 16);
        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class,
                () -> AtlasAssetIndexBuilder.build(
                        "validation",
                        atlas(region(texture, name, -1, 0, 0, 8, 8))));
        Assert.assertTrue(failure.getMessage().contains("validation"));
        Assert.assertTrue(failure.getMessage().contains(name));
        Assert.assertTrue(failure.getMessage().contains(expectedDiagnostic));
    }

    private static TextureAtlas atlas(TextureAtlas.AtlasRegion... regions) {
        TextureAtlas atlas = new TextureAtlas();
        for (int i = 0; i < regions.length; i++) {
            atlas.getRegions().add(regions[i]);
        }
        return atlas;
    }

    private static CountingTextureAtlas countingAtlas(
            TextureAtlas.AtlasRegion... regions) {
        CountingTextureAtlas atlas = new CountingTextureAtlas();
        for (int i = 0; i < regions.length; i++) {
            atlas.getRegions().add(regions[i]);
        }
        atlas.getRegionsCalls = 0;
        return atlas;
    }

    private static TextureAtlas.AtlasRegion region(
            Texture texture,
            String name,
            int index,
            int x,
            int y,
            int width,
            int height) {
        TextureAtlas.AtlasRegion region =
                new TextureAtlas.AtlasRegion(texture, x, y, width, height);
        region.name = name;
        region.index = index;
        region.packedWidth = width;
        region.packedHeight = height;
        region.originalWidth = width;
        region.originalHeight = height;
        return region;
    }

    private static final class TestTexture extends Texture {
        private final int width;
        private final int height;

        TestTexture(int width, int height) {
            super();
            this.width = width;
            this.height = height;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public void setFilter(TextureFilter minFilter, TextureFilter magFilter) {
            // No GL context is needed for index tests.
        }
    }

    private static final class CountingTextureAtlas extends TextureAtlas {
        int getRegionsCalls;
        int findRegionsCalls;
        final CountingRegions countedRegions = new CountingRegions();

        @Override
        public Array<TextureAtlas.AtlasRegion> getRegions() {
            getRegionsCalls++;
            return countedRegions;
        }

        @Override
        public Array<TextureAtlas.AtlasRegion> findRegions(String name) {
            findRegionsCalls++;
            return super.findRegions(name);
        }
    }

    private static final class CountingRegions extends Array<TextureAtlas.AtlasRegion> {
        int visits;

        @Override
        public TextureAtlas.AtlasRegion get(int index) {
            visits++;
            return super.get(index);
        }
    }

    private static final class TrackingTextureAtlas extends TextureAtlas {
        boolean disposed;

        @Override
        public void dispose() {
            disposed = true;
            super.dispose();
        }
    }

    private static final class TestAtlasRuntimeService extends AtlasRuntimeService {
        void publishOwned(String tag, TextureAtlas atlas) {
            publishOwnedAtlas(tag, atlas);
        }
    }
}
