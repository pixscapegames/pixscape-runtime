package games.pixscape.runtime.component.physics;

import games.pixscape.runtime.physics.PhysicsShapeData;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapesComponentTest {
    @Test
    public void componentIsDataOnlyAndResetClearsShapes() {
        PhysicsShapesComponent component = new PhysicsShapesComponent();
        component.shapes.add(new PhysicsShapeData());

        component.reset();

        Assert.assertEquals(0, component.shapes.size);
        Field[] fields = PhysicsShapesComponent.class.getDeclaredFields();
        Assert.assertEquals(1, fields.length);
        Assert.assertEquals("shapes", fields[0].getName());
        for (Method method : PhysicsShapesComponent.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                Assert.fail("Unexpected public business helper: " + method.getName());
            }
        }
    }
}
