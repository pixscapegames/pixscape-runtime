package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import org.junit.Assert;
import org.junit.Test;

public class AtlasRuntimeServicePublicationRevisionTest {

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
}
