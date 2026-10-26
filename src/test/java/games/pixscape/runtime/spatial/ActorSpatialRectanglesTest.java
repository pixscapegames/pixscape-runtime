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
        ActorSpatialRectangleData east=new ActorSpatialRectangleData();east.orientation=37.25f;east.offsetX=9;east.width=17;
        rectangles.put(12,"east",east);rectangles.put(12,"west",new ActorSpatialRectangleData());
        ActorSpatialRectangles restored=new Json().fromJson(ActorSpatialRectangles.class,new Json().toJson(rectangles));
        restored.validate();assertTrue(east.same(restored.get(12,"east")));
        assertNull(restored.staticRectangle);assertEquals(0,restored.get(12,"west").orientation,0);
        ActorSpatialRectangles copy=restored.copy();copy.get(12,"east").width=3;
        assertEquals(17,restored.get(12,"east").width,0);
        ActorSpatialRectangles stat=new ActorSpatialRectangles();stat.staticRectangle=east.copy();
        assertTrue(east.same(new Json().fromJson(ActorSpatialRectangles.class,new Json().toJson(stat)).get(0,null)));
        assertEquals(0,stat.clips.size);
    }
    @Test public void cornersRotateAroundLowerEdgeCentre(){
        ActorSpatialRectangleData r=new ActorSpatialRectangleData();r.width=40;r.height=70;
        r.offsetX=4;r.offsetY=-3;r.offsetZ=11;float[] out=new float[12];
        for(float angle:new float[]{0,45,90,37.25f,359.5f,360.5f}){
            r.orientation=angle;r.corners(100,200,50,out);
            assertEquals(104,(out[0]+out[9])*.5f,.0001);assertEquals(197,(out[1]+out[10])*.5f,.0001);
            assertEquals(61,out[2],0);assertEquals(61,out[11],0);
            assertEquals(131,out[5],0);assertEquals(131,out[8],0);
            assertEquals(40,Math.hypot(out[9]-out[0],out[10]-out[1]),.0001);
            assertEquals(out[0],out[3],0);assertEquals(out[1],out[4],0);
        }
    }
    @Test(expected=IllegalArgumentException.class) public void emptyVolumeSetIsNotALegacyFallback(){new ActorSpatialRectangles().validate();}
    @Test(expected=IllegalArgumentException.class) public void invalidDimensionsAreRejected(){ActorSpatialRectangleData v=new ActorSpatialRectangleData();v.width=Float.NaN;v.validate();}
}
