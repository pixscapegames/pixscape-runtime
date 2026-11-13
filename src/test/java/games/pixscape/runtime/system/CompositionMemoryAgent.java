package games.pixscape.runtime.system;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/** Desktop benchmark only: reachable owned Java bytes, excluding borrowed World/native services. */
public final class CompositionMemoryAgent {
    private static Instrumentation instrumentation;

    public static void premain(String arguments, Instrumentation value) {
        instrumentation = value;
    }

    public static long ownedBytes(Object root) throws Exception {
        if (instrumentation == null) {
            return -1;
        }
        var seen = new IdentityHashMap<Object, Boolean>();
        var pending = new ArrayDeque<Object>();
        pending.add(root);
        long bytes = 0;
        while (!pending.isEmpty()) {
            Object value = pending.removeLast();
            if (seen.put(value, true) != null || value instanceof Class<?>
                    || value instanceof com.artemis.World || value instanceof com.artemis.BaseSystem
                    || value instanceof com.artemis.ComponentMapper<?>
                    || value instanceof games.pixscape.runtime.loading.SceneMetaRuntime
                    || value instanceof games.pixscape.runtime.service.IdentityRegistry
                    || value instanceof games.pixscape.runtime.service.AtlasRuntimeService
                    || value instanceof com.badlogic.gdx.physics.box2d.Body
                    || value instanceof com.badlogic.gdx.physics.box2d.Joint) {
                continue;
            }
            bytes += instrumentation.getObjectSize(value);
            Class<?> type = value.getClass();
            if (type.isArray()) {
                if (!type.getComponentType().isPrimitive()) {
                    for (Object element : (Object[]) value) {
                        if (element != null) {
                            pending.add(element);
                        }
                    }
                }
            } else {
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    for (Field field : current.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                            field.setAccessible(true);
                            Object child = field.get(value);
                            if (child != null) {
                                pending.add(child);
                            }
                        }
                    }
                }
            }
        }
        return bytes;
    }
}
