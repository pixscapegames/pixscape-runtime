package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Json;
import org.junit.Test;

import static org.junit.Assert.*;

public class ActorSpatialRectanglesTest {
    @Test
    public void authoredVolumeTypesAreRegisteredWithBothGwtReflectionCaches() throws Exception {
        // A JVM round trip cannot detect a missing entry in the generated Artemis cache.
        String module = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/games/pixscape/runtime/PixscapeRuntime.gwt.xml")),
                java.nio.charset.StandardCharsets.UTF_8);
        for (Class<?> type : new Class<?>[]{ActorSpatialRectangles.class, ActorSpatialRectangleData.class}) {
            assertTrue(type.getName(), module.contains("name=\"artemis.reflect.include\" value=\""
                    + type.getName() + "\""));
        }
        assertTrue(module.contains("name=\"gdx.reflect.include\" value=\"games.pixscape.runtime.spatial\""));
        assertTrue(module.contains("name=\"gdx.reflect.include\" value=\"com.badlogic.gdx.utils.IntMap\""));
    }

    @Test
    public void staticAndClipDataRoundTripAndCopyWithoutAliasing() {
        ActorSpatialRectangles rectangles = new ActorSpatialRectangles();
        ActorSpatialRectangleData east = new ActorSpatialRectangleData();
        east.pivotX = 37.25f;
        east.x = 9;
        east.width = 17;
        rectangles.put(12, "east", east);
        rectangles.put(12, "west", new ActorSpatialRectangleData());
        ActorSpatialRectangles restored = new Json().fromJson(ActorSpatialRectangles.class, new Json().toJson(rectangles));
        restored.validate();
        assertTrue(east.same(restored.get(12, "east")));
        assertNull(restored.staticRectangle);
        assertEquals(0, restored.get(12, "west").pivotX, 0);
        ActorSpatialRectangles copy = restored.copy();
        copy.get(12, "east").width = 3;
        assertEquals(17, restored.get(12, "east").width, 0);
        ActorSpatialRectangles stat = new ActorSpatialRectangles();
        stat.staticRectangle = east.copy();
        assertTrue(east.same(new Json().fromJson(ActorSpatialRectangles.class, new Json().toJson(stat)).get(0, null)));
        assertEquals(0, stat.clips.size);
    }

    @Test(expected = com.badlogic.gdx.utils.SerializationException.class)
    public void obsoleteYawFormatIsNotSilentlyLoaded() {
        new Json().fromJson(ActorSpatialRectangleData.class, "{orientation:45}");
    }

    @Test
    public void missingOptionalSettingsMeanTheFullImage() {
        new ActorSpatialRectangles().validate();
    }

    @Test
    public void configuredDimensionsMustBeFiniteAndStrictlyPositiveForStaticAndClipSettings() {
        for (float invalid : new float[]{0f, -0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            for (boolean width : new boolean[]{false, true}) {
                ActorSpatialRectangleData zone = new ActorSpatialRectangleData();
                if (width) {
                    zone.width = invalid;
                } else {
                    zone.height = invalid;
                }
                assertThrows(IllegalArgumentException.class, zone::validate);
                ActorSpatialRectangles settings = new ActorSpatialRectangles();
                assertThrows(IllegalArgumentException.class, () -> settings.put(8, "walk", zone));
                assertEquals(0, settings.clips.size);
                settings.staticRectangle = zone;
                assertThrows(IllegalArgumentException.class, settings::validate);
            }
        }
    }

    @Test
    public void positiveZoneOutsideImageRemainsValidAuthoredData() {
        ActorSpatialRectangleData zone = new ActorSpatialRectangleData();
        zone.x = 2;
        zone.width = zone.height = .5f;
        zone.validate();
    }
}
