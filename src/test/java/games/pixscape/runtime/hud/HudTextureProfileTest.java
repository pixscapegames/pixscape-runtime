package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import org.junit.Assert;
import org.junit.Test;

public class HudTextureProfileTest {
    @Test
    public void defaultProfileHasTheV1FixedArrayContract() {
        HudTextureProfile profile = HudTextureProfile.forId(null);

        Assert.assertEquals(HudTextureProfile.DEFAULT_ID, profile.id());
        Assert.assertEquals(2048, profile.pageWidth());
        Assert.assertEquals(2048, profile.pageHeight());
        Assert.assertEquals(Texture.TextureFilter.Linear, profile.minFilter());
        Assert.assertEquals(Texture.TextureFilter.Linear, profile.magFilter());
        Assert.assertEquals(Texture.TextureWrap.ClampToEdge, profile.uWrap());
        Assert.assertEquals(Texture.TextureWrap.ClampToEdge, profile.vWrap());
        Assert.assertFalse(profile.useMipMaps());
        Assert.assertEquals(Pixmap.Format.RGBA8888, profile.outputFormat());
    }

    @Test
    public void knownProfileResolvesAndUnknownProfileFailsClearly() {
        Assert.assertSame(HudTextureProfile.forId(HudTextureProfile.DEFAULT_ID),
                HudTextureProfile.forId("  " + HudTextureProfile.DEFAULT_ID + "  "));

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> HudTextureProfile.forId("hud-missing"));
        Assert.assertEquals("Unknown HUD texture profile: hud-missing.", failure.getMessage());
    }
}
