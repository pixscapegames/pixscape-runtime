package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import org.junit.Assert;
import org.junit.Test;

public class AtlasRuntimeServicePublicationRevisionTest {

    @Test
    public void protectedOwnedPublicationUsesNormalPublicationLifecycle() {
        TestAtlasRuntimeService service = new TestAtlasRuntimeService();
        TrackingTextureAtlas first = new TrackingTextureAtlas();
        TrackingTextureAtlas replacement = new TrackingTextureAtlas();

        service.publishOwned("main", first);
        int firstRevision = service.publicationRevision("main");
        service.markPublicationPending("main");

        service.publishOwned("main", replacement);

        Assert.assertTrue(first.disposed);
        Assert.assertFalse(replacement.disposed);
        Assert.assertSame(replacement, service.getAtlas("main"));
        Assert.assertNotEquals(firstRevision, service.publicationRevision("main"));
        Assert.assertFalse(service.isPublicationPending("main"));

        service.unload("main");
        Assert.assertTrue(replacement.disposed);
    }

    @Test
    public void revisionAdvancesOnlyWhenUsableAtlasIsPublished() {
        AtlasRuntimeService service = new AtlasRuntimeService();

        Assert.assertFalse(service.hasPublishedAtlases());
        Assert.assertEquals(0, service.publicationRevision("main"));
        try {
            service.loadBorrowed("main", null);
            Assert.fail("Null atlas publication must fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        Assert.assertEquals(0, service.publicationRevision("main"));

        TextureAtlas first = new TextureAtlas();
        service.loadBorrowed("main", first);
        Assert.assertTrue(service.hasPublishedAtlases());
        int firstRevision = service.publicationRevision("main");
        Assert.assertNotEquals(0, firstRevision);

        service.markPublicationPending("main");
        Assert.assertTrue(service.isPublicationPending("main"));
        Assert.assertEquals("A request is not a usable publication", firstRevision,
                service.publicationRevision("main"));

        service.unload("main");
        Assert.assertFalse(service.hasPublishedAtlases());
        Assert.assertEquals(firstRevision, service.publicationRevision("main"));

        TextureAtlas replacement = new TextureAtlas();
        service.loadBorrowed("main", replacement);
        Assert.assertNotEquals(firstRevision, service.publicationRevision("main"));
        Assert.assertFalse("Successful publication clears the pending marker",
                service.isPublicationPending("main"));
    }

    private static final class TestAtlasRuntimeService extends AtlasRuntimeService {
        void publishOwned(String tag, TextureAtlas atlas) {
            publishOwnedAtlas(tag, atlas);
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
}
