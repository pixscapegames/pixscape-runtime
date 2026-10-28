package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Json;
import org.junit.Test;

import static org.junit.Assert.*;

public class ActorSpatialRectanglesTest {
    @Test public void authoredVolumeTypesAreRegisteredWithBothGwtReflectionCaches() throws Exception {
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
    @Test public void staticAndClipDataRoundTripAndCopyWithoutAliasing(){
        ActorSpatialRectangles rectangles=new ActorSpatialRectangles();
        ActorSpatialRectangleData east=new ActorSpatialRectangleData();east.pivotX=37.25f;east.x=9;east.width=17;
        rectangles.put(12,"east",east);rectangles.put(12,"west",new ActorSpatialRectangleData());
        ActorSpatialRectangles restored=new Json().fromJson(ActorSpatialRectangles.class,new Json().toJson(rectangles));
        restored.validate();assertTrue(east.same(restored.get(12,"east")));
        assertNull(restored.staticRectangle);assertEquals(0,restored.get(12,"west").pivotX,0);
        ActorSpatialRectangles copy=restored.copy();copy.get(12,"east").width=3;
        assertEquals(17,restored.get(12,"east").width,0);
        ActorSpatialRectangles stat=new ActorSpatialRectangles();stat.staticRectangle=east.copy();
        assertTrue(east.same(new Json().fromJson(ActorSpatialRectangles.class,new Json().toJson(stat)).get(0,null)));
        assertEquals(0,stat.clips.size);
    }
    @Test public void fixedEnvelopeIgnoresShadowPivot(){
        ActorSpatialRectangleData r=new ActorSpatialRectangleData();r.x=4;r.y=-3;r.width=40;r.height=70;
        float[] bounds=new float[4];
        ActorSpatialRectanglePlacement.influenceBounds(r,100,200,bounds,0);
        assertArrayEquals(new float[]{104,197,144,267},bounds,0);
        r.pivotX=93;r.pivotY=-50;
        ActorSpatialRectanglePlacement.influenceBounds(r,100,200,bounds,0);
        assertArrayEquals(new float[]{104,197,144,267},bounds,0);
    }
    @Test(expected=com.badlogic.gdx.utils.SerializationException.class)
    public void obsoleteYawFormatIsNotSilentlyLoaded(){
        new Json().fromJson(ActorSpatialRectangleData.class,"{orientation:45}");
    }
    @Test(expected=IllegalArgumentException.class) public void emptyVolumeSetIsNotALegacyFallback(){new ActorSpatialRectangles().validate();}
    @Test(expected=IllegalArgumentException.class) public void invalidDimensionsAreRejected(){ActorSpatialRectangleData v=new ActorSpatialRectangleData();v.width=Float.NaN;v.validate();}
}
