package games.pixscape.runtime.service;

import org.junit.Assert;
import org.junit.Test;

public class AtlasRuntimeServiceTextureArrayInvariantTest {

    @Test
    public void exactConfiguredPageDimensionsAreAccepted() {
        AtlasRuntimeService.validateTextureArrayPageDimensions(
                2048, 2048, 2048, 2048, "atlas page 0");
    }

    @Test
    public void narrowerPageIsRejected() {
        assertInvalidDimensions(1024, 2048);
    }

    @Test
    public void shorterPageIsRejected() {
        assertInvalidDimensions(2048, 1024);
    }

    @Test
    public void oversizedPageIsRejected() {
        assertInvalidDimensions(4096, 4096);
    }

    private static void assertInvalidDimensions(int width, int height) {
        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class,
                () -> AtlasRuntimeService.validateTextureArrayPageDimensions(
                        width, height, 2048, 2048, "atlas page 3"));

        Assert.assertTrue(failure.getMessage().contains("atlas page 3"));
        Assert.assertTrue(failure.getMessage().contains("expected 2048x2048"));
        Assert.assertTrue(failure.getMessage().contains(
                "got " + width + "x" + height));
    }
}
