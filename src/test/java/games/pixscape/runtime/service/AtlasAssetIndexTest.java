package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AtlasAssetIndexTest {

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
        Assert.assertSame(binding.firstRegion(), binding.regions().first());
        Assert.assertEquals(1, binding.regions().size);
        Assert.assertEquals(0.10f, binding.cachedRegion().u1, 0.0001f);
        Assert.assertEquals(0.10f, binding.cachedRegion().v1, 0.0001f);
        Assert.assertEquals(0.40f, binding.cachedRegion().u2, 0.0001f);
        Assert.assertEquals(0.30f, binding.cachedRegion().v2, 0.0001f);
        Assert.assertEquals(30, binding.cachedRegion().pixW);
        Assert.assertEquals(40, binding.cachedRegion().pixH);
        Assert.assertEquals(
                TextureRegistry.handleOf(texture),
                binding.cachedRegion().textureHandle);
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
        AtlasRuntimeService service = new AtlasRuntimeService();

        service.load("main", atlas(frame2, frame0, frame1));

        Array<TextureAtlas.AtlasRegion> first = service.resolve(923, "main");
        Array<TextureAtlas.AtlasRegion> second = service.resolve(923, "main");
        AtlasAssetBinding binding = service.resolveBinding(923, "main");
        Assert.assertSame(first, second);
        Assert.assertSame(first, binding.regions());
        Assert.assertSame(frame0, binding.firstRegion());
        Assert.assertSame(frame0, first.get(0));
        Assert.assertSame(frame1, first.get(1));
        Assert.assertSame(frame2, first.get(2));
        Assert.assertSame(binding.cachedRegion(), service.resolveCached(923, "main"));
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
        AtlasRuntimeService service = new AtlasRuntimeService();
        service.load("main", atlas);
        int visitsAfterLoad = service.indexBuildRegionVisits("main");
        int getRegionsCallsAfterLoad = atlas.getRegionsCalls;
        int findRegionsCallsAfterLoad = atlas.findRegionsCalls;

        Assert.assertNull(service.resolve(999999, "main"));
        for (int i = 0; i < 10000; i++) {
            Assert.assertNotNull(service.resolve(1, "main"));
            Assert.assertNotNull(service.resolve(2, "main"));
            Assert.assertNotNull(service.resolve(3, "main"));
            Assert.assertNull(service.resolve(999999, "main"));
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

        Assert.assertSame(pageOne, binding.regions().get(0).getTexture());
        Assert.assertSame(pageTwo, binding.regions().get(1).getTexture());
        Assert.assertSame(frame0, binding.regions().get(0));
        Assert.assertSame(frame1, binding.regions().get(1));
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
        AtlasRuntimeService service = new AtlasRuntimeService();
        TextureAtlas firstAtlas =
                atlas(region(texture, "first__a1", -1, 0, 0, 8, 8));
        TextureAtlas secondAtlas =
                atlas(region(texture, "second__a2", -1, 8, 0, 8, 8));

        service.load("main", firstAtlas);
        Assert.assertSame(firstAtlas, service.getAtlas("main"));
        Assert.assertNotNull(service.resolveBinding(1, "main"));

        service.load("main", secondAtlas);
        Assert.assertSame(secondAtlas, service.getAtlas("main"));
        Assert.assertNull(service.resolveBinding(1, "main"));
        Assert.assertNotNull(service.resolveBinding(2, "main"));

        service.unload("main");
        Assert.assertNull(service.getAtlas("main"));
        Assert.assertNull(service.resolveBinding(2, "main"));
        Assert.assertEquals(-1, service.indexBuildRegionVisits("main"));

        service.load(
                "one",
                atlas(region(texture, "one__a11", -1, 0, 0, 8, 8)));
        service.load(
                "two",
                atlas(region(texture, "two__a22", -1, 8, 0, 8, 8)));
        service.unloadAll();
        Assert.assertNull(service.getAtlas("one"));
        Assert.assertNull(service.getAtlas("two"));
        Assert.assertNull(service.resolveBinding(11, "one"));
        Assert.assertNull(service.resolveBinding(22, "two"));
    }

    @Test
    public void failedReloadLeavesPreviousAtlasAndIndexPublished() {
        TestTexture texture = new TestTexture(32, 32);
        AtlasRuntimeService service = new AtlasRuntimeService();
        TextureAtlas valid =
                atlas(region(texture, "valid__a1", -1, 0, 0, 8, 8));
        service.load("main", valid);

        Assert.assertThrows(
                IllegalStateException.class,
                () -> service.load(
                        "main",
                        atlas(region(texture, "invalid__a0", -1, 8, 0, 8, 8))));

        Assert.assertSame(valid, service.getAtlas("main"));
        Assert.assertNotNull(service.resolveBinding(1, "main"));
        Assert.assertNull(service.resolveBinding(0, "main"));
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

        @Override
        public Array<TextureAtlas.AtlasRegion> getRegions() {
            getRegionsCalls++;
            return super.getRegions();
        }

        @Override
        public Array<TextureAtlas.AtlasRegion> findRegions(String name) {
            findRegionsCalls++;
            return super.findRegions(name);
        }
    }
}
