package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import org.junit.*;
import java.util.*;

public class HudGameplayAccessTest {
    private final games.pixscape.runtime.api.EntityRefIdentitySafetyTest environment = new games.pixscape.runtime.api.EntityRefIdentitySafetyTest();
    @Before public void setup() { com.badlogic.gdx.utils.GdxNativesLoader.load(); environment.installLibGdxProxies(); }
    @After public void cleanup() { environment.restoreLibGdx(); }

    private static MaterializedHud hud(Group root, Map<String,Actor> actors) {
        return new MaterializedHud(root,actors,Collections.<String,Cell<?>>emptyMap(),
                Collections.<Disposable>emptyList(),null,Collections.<Actor,TextTooltip>emptyMap());
    }

    @Test public void typedLookupUsesAuthoredIndexAndNativeWidgetsSurviveScoreChanges() {
        Group root=new Group(){@Override public <T extends Actor> T findActor(String name){throw new AssertionError("Tree lookup forbidden");}};
        BitmapFont font=new BitmapFont(new BitmapFont.BitmapFontData(),new TextureRegion(),false);
        Label label=new Label("0",new Label.LabelStyle(font,null));
        ProgressBar health=new ProgressBar(0,100,1,false,new ProgressBar.ProgressBarStyle());
        Button button=new Button(new Button.ButtonStyle());
        Map<String,Actor> actors=new LinkedHashMap<>();actors.put("score",label);actors.put("health",health);actors.put("menu",button);
        root.addActor(label);root.addActor(health);root.addActor(button);
        MaterializedHud hud=hud(root,actors);
        final int[] changes={0};
        ChangeListener listener=new ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){changes[0]++;}};
        try {
            Assert.assertSame(label,hud.label("score"));Assert.assertSame(health,hud.progressBar("health"));
            label.setName("renamed");label.remove();Assert.assertSame(label,hud.actor("score"));
            Assert.assertNull(hud.actor("absent"));
            Assert.assertThrows(IllegalArgumentException.class,()->hud.label("absent"));
            Assert.assertThrows(IllegalArgumentException.class,()->hud.slider("score"));
            hud.button("menu").addListener(listener);hud.button("menu").addListener(listener);
            button.setChecked(true);Assert.assertEquals(1,changes[0]);
            button.removeListener(listener);button.setChecked(false);Assert.assertEquals(1,changes[0]);
            hud.label("score").setText(42);hud.progressBar("health").setValue(73);
            Assert.assertEquals("42",label.getText().toString());Assert.assertEquals(73,health.getValue(),0);
            Assert.assertSame(root,hud.root());Assert.assertSame(label,hud.label("score"));
            Assert.assertSame(font,label.getStyle().font);
            button.setDisabled(true);button.setVisible(false);Assert.assertTrue(button.isDisabled());Assert.assertFalse(button.isVisible());
        } finally {hud.dispose();font.dispose();}
    }

    @Test public void indexedLookupsStayAllocationFreeAtRepresentativeSizes() {
        java.lang.management.ThreadMXBean bean=java.lang.management.ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean allocations=(com.sun.management.ThreadMXBean)bean;
        Assume.assumeTrue(allocations.isThreadAllocatedMemorySupported());allocations.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().getId();
        for(int count:new int[]{1,100,1000}) {
            Map<String,Actor> actors=new LinkedHashMap<>();String[] ids=new String[count];Button[] buttons=new Button[count];
            for(int i=0;i<count;i++){ids[i]="button-"+i;buttons[i]=new Button(new Button.ButtonStyle());actors.put(ids[i],buttons[i]);}
            MaterializedHud hud=hud(new Group(),actors);
            for(int i=0;i<100000;i++){hud.button(ids[i%count]);hud.actor("missing");}
            long bytes=allocations.getThreadAllocatedBytes(thread),start=System.nanoTime();
            Actor last=null;
            for(int i=0;i<500000;i++){last=hud.button(ids[i%count]);if(hud.actor("missing")!=null)throw new AssertionError();}
            long apiNs=System.nanoTime()-start,apiBytes=allocations.getThreadAllocatedBytes(thread)-bytes;
            Assert.assertSame(buttons[499999%count],last);
            bytes=allocations.getThreadAllocatedBytes(thread);start=System.nanoTime();
            for(int i=0;i<500000;i++){last=hud.actorById().get(ids[i%count]);if(hud.actorById().get("missing")!=null)throw new AssertionError();}
            long nativeNs=System.nanoTime()-start,nativeBytes=allocations.getThreadAllocatedBytes(thread)-bytes;
            Assert.assertSame(buttons[499999%count],last);Assert.assertTrue(apiBytes<=nativeBytes+4096);
            System.out.println("hud-index n="+count+" lookups=1000000 apiNs="+apiNs+" nativeNs="+nativeNs+" apiBytes="+apiBytes+" nativeBytes="+nativeBytes);
            hud.dispose();
        }
    }
}
