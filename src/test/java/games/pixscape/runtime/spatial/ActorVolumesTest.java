package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Json;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActorVolumesTest {
    @Test public void authoredVolumeTypesAreRegisteredWithBothGwtReflectionCaches() throws Exception {
        // A JVM round trip cannot detect a missing entry in the generated Artemis cache.
        String module = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/games/pixscape/runtime/PixscapeRuntime.gwt.xml")),
                java.nio.charset.StandardCharsets.UTF_8);
        for (Class<?> type : new Class<?>[]{ActorVolumes.class, ActorVolumeData.class}) {
            assertTrue(type.getName(), module.contains("name=\"artemis.reflect.include\" value=\""
                    + type.getName() + "\""));
        }
        assertTrue(module.contains("name=\"gdx.reflect.include\" value=\"games.pixscape.runtime.spatial\""));
        assertTrue(module.contains("name=\"gdx.reflect.include\" value=\"com.badlogic.gdx.utils.IntMap\""));
    }
    @Test public void staticAndClipDataRoundTripAndCopyWithoutAliasing(){
        ActorVolumes volumes=new ActorVolumes();
        ActorVolumeData east=new ActorVolumeData();east.orientation=37.25f;east.offsetX=9;east.depth=17;
        volumes.put(12,"east",east);volumes.put(12,"west",new ActorVolumeData());
        ActorVolumes restored=new Json().fromJson(ActorVolumes.class,new Json().toJson(volumes));
        restored.validate();assertTrue(east.same(restored.get(12,"east")));
        assertNull(restored.staticVolume);assertEquals(0,restored.get(12,"west").orientation,0);
        ActorVolumes copy=restored.copy();copy.get(12,"east").depth=3;
        assertEquals(17,restored.get(12,"east").depth,0);
        ActorVolumes stat=new ActorVolumes();stat.staticVolume=east.copy();
        assertTrue(east.same(new Json().fromJson(ActorVolumes.class,new Json().toJson(stat)).get(0,null)));
        assertEquals(0,stat.clips.size);
    }
    @Test public void cornersRotateAroundFixedBaseWithoutChangingDimensionsOrAltitude(){
        ActorVolumeData box=new ActorVolumeData();box.width=40;box.depth=20;box.height=70;
        box.offsetX=4;box.offsetY=-3;box.offsetZ=11;
        float[] out=new float[24];
        for(float angle:new float[]{0,45,90,37.25f,359.5f,360.5f}){
            box.orientation=angle;box.corners(100,200,50,out);
            float x=0,y=0;
            for(int i=0;i<4;i++){x+=out[3*i];y+=out[3*i+1];assertEquals(61,out[3*i+2],0);assertEquals(131,out[3*(i+4)+2],0);}
            assertEquals(104,x/4,.0001f);assertEquals(197,y/4,.0001f);
            assertEquals(40,Math.hypot(out[3]-out[0],out[4]-out[1]),.0001);
            assertEquals(20,Math.hypot(out[6]-out[3],out[7]-out[4]),.0001);
        }
    }
    @Test(expected=IllegalArgumentException.class) public void emptyVolumeSetIsNotALegacyFallback(){new ActorVolumes().validate();}
    @Test(expected=IllegalArgumentException.class) public void invalidDimensionsAreRejected(){ActorVolumeData v=new ActorVolumeData();v.depth=Float.NaN;v.validate();}
}
