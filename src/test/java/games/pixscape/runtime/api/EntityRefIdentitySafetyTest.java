package games.pixscape.runtime.api;

import com.artemis.World;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEffectPool;
import games.pixscape.runtime.particle.ParticleRuntimeAvailability;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;

public class EntityRefIdentitySafetyTest {
    private Application previousApp;
    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;
    private Graphics previousGraphics;
    private Files previousFiles;

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installLibGdxProxies() {
        previousApp = Gdx.app;
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;
        previousGraphics = Gdx.graphics;
        previousFiles = Gdx.files;

        Gdx.app = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        int[] nextHandle = {1};
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class<?>[]{GL30.class},
                (proxy, method, args) -> glDefaultValue(
                        method.getName(), args, method.getReturnType(), nextHandle));
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Gdx.files = (Files) Proxy.newProxyInstance(
                Files.class.getClassLoader(),
                new Class<?>[]{Files.class},
                (proxy, method, args) -> {
                    if (args != null && args.length == 1
                            && args[0] instanceof String
                            && FileHandle.class.equals(method.getReturnType())) {
                        String path = (String) args[0];
                        if ("internal".equals(method.getName())
                                || "classpath".equals(method.getName())) {
                            return new FileHandle("src/main/resources").child(path);
                        }
                        return new FileHandle(path);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    @After
    public void restoreLibGdx() {
        Gdx.app = previousApp;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
        Gdx.graphics = previousGraphics;
        Gdx.files = previousFiles;
    }

    @Test
    public void recycledEntityIdDoesNotRetargetRefsOrChildFacades() {
        PixscapeEngine engine = new PixscapeEngine();
        try {
            engine.initEmptyRuntime();
            World world = engine.getWorld();
            int first = world.create();
            world.getMapper(TransformComponent.class).create(first).x = 10f;
            TiledMapLayerData firstMap = tiled(world, first, 2);
            world.process();

            EntityRef oldEntity = engine.api().entities().requireEntityId(first);
            TransformFacade oldTransform = oldEntity.transform();
            SpriteFacade oldSprite = oldEntity.sprite();
            AnimationFacade oldAnimation = oldEntity.animation();
            ParticleFacade oldParticles = oldEntity.particles();
            ShaderFacade oldShader = oldEntity.shader();
            RenderOrderFacade oldRenderOrder = oldEntity.renderOrder();
            TiledMapRef oldTiled = engine.api().tiled().requireEntityId(first);

            world.delete(first);
            world.process();

            int replacement = world.create();
            Assert.assertEquals("The regression must exercise Artemis ID reuse",
                    first, replacement);
            TransformComponent replacementTransform =
                    world.getMapper(TransformComponent.class).create(replacement);
            replacementTransform.x = 100f;
            AnimationComponent replacementAnimation =
                    world.getMapper(AnimationComponent.class).create(replacement);
            replacementAnimation.fps = 17f;
            TiledMapLayerData replacementMap = tiled(world, replacement, 4);

            Assert.assertFalse(oldEntity.exists());
            Assert.assertEquals(0f, oldTransform.x(), 0f);
            Assert.assertEquals(0, oldTiled.map().width());
            Assert.assertFalse(oldSprite.exists());
            Assert.assertFalse(oldShader.exists());
            Assert.assertFalse(oldRenderOrder.exists());
            Assert.assertFalse(oldTiled.map().isInside(0, 0));

            oldTransform.setX(200f);
            oldTransform.setPosition(Float.NaN, Float.POSITIVE_INFINITY);
            oldAnimation.setFps(30f);
            oldAnimation.setClip(null);
            oldSprite.setAsset(-1, "");
            oldParticles.play();
            oldParticles.setEffect("", "");
            oldShader.use("").setFloat("", 1f);
            oldTiled.map().setVisible(false);
            oldEntity.remove();

            Assert.assertEquals(100f, replacementTransform.x, 0f);
            Assert.assertEquals(17f, replacementAnimation.fps, 0f);
            Assert.assertFalse(world.getMapper(ParticleEmitterComponent.class).has(replacement));
            Assert.assertTrue(replacementMap.visible);
            Assert.assertTrue(world.getEntityManager().isActive(replacement));

            EntityRef freshEntity = engine.api().entities().ofEntityId(replacement);
            TiledMapRef freshTiled = engine.api().tiled().ofEntityId(replacement);
            Assert.assertTrue(freshEntity.exists());
            Assert.assertTrue(freshTiled.exists());
            Assert.assertEquals(4, freshTiled.map().width());
            Assert.assertTrue(firstMap.visible);
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void supportedWorldRebuildPermanentlyInvalidatesOldRefs() {
        PixscapeEngine engine = new PixscapeEngine();
        try {
            engine.initEmptyRuntime();
            World firstWorld = engine.getWorld();
            int first = firstWorld.create();
            firstWorld.getMapper(TransformComponent.class).create(first).x = 10f;
            EntityRef oldRef = engine.api().entities().ofEntityId(first);
            TransformFacade oldTransform = oldRef.transform();

            engine.rebuildWorldOnly(engine.config(), new FileHandle("."));
            World replacementWorld = engine.getWorld();
            int replacement = replacementWorld.create();
            Assert.assertEquals(first, replacement);
            TransformComponent replacementTransform =
                    replacementWorld.getMapper(TransformComponent.class).create(replacement);
            replacementTransform.x = 100f;

            Assert.assertFalse(oldRef.exists());
            Assert.assertEquals(0f, oldTransform.x(), 0f);
            oldTransform.setX(200f);
            oldRef.remove();

            Assert.assertEquals(100f, replacementTransform.x, 0f);
            Assert.assertTrue(replacementWorld.getEntityManager().isActive(replacement));
            Assert.assertTrue(engine.api().entities().ofEntityId(replacement).exists());
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void stableIdLookupBindsOneIncarnationAndDoesNotRecyclePersistentIdentity() {
        PixscapeEngine engine = new PixscapeEngine();
        try {
            engine.initEmptyRuntime();
            World world = engine.getWorld();
            SceneMetaRuntime sceneMeta = new SceneMetaRuntime();
            sceneMeta.nextEntityStableId = 10;
            engine.getIdentityRegistry().bind(world, sceneMeta);

            int first = world.create();
            world.process();
            int firstStableId = engine.api().entities().ensureStableId(first);
            EntityRef oldRef = engine.api().entities().requireStableId(firstStableId);
            world.process();

            world.delete(first);
            world.process();
            int replacement = world.create();
            Assert.assertEquals(first, replacement);
            int replacementStableId = engine.api().entities().ensureStableId(replacement);

            Assert.assertNotEquals(firstStableId, replacementStableId);
            Assert.assertFalse(oldRef.exists());
            Assert.assertFalse(engine.api().entities().ofStableId(firstStableId).exists());
            EntityRef freshRef = engine.api().entities().ofStableId(replacementStableId);
            Assert.assertTrue(freshRef.exists());
            Assert.assertEquals(replacement, freshRef.entityId());
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void factoryRefCapturesCurrentIncarnationBeforeWorldProcessing() throws Exception {
        PixscapeEngine engine = new PixscapeEngine();
        try {
            engine.initEmptyRuntime();
            prepareParticlePool(
                    engine.getWorld().getSystem(RenderParticleSyncSystem.class),
                    "main", "effects/smoke.p");

            ParticleRef particle = engine.api().particles().spawn("effects/smoke", 3f, 4f);

            Assert.assertTrue(particle.entity().exists());
            Assert.assertTrue(particle.particles().exists());
            Assert.assertEquals(3f, particle.transform().x(), 0f);
            Assert.assertEquals(4f, particle.transform().y(), 0f);
        } finally {
            engine.dispose();
        }
    }

    @SuppressWarnings("unchecked")
    private static void prepareParticlePool(
            RenderParticleSyncSystem system, String atlasTag, String effectPath)
            throws Exception {
        Field availabilityField = RenderParticleSyncSystem.class
                .getDeclaredField("particleAvailability");
        availabilityField.setAccessible(true);
        ParticleRuntimeAvailability availability =
                (ParticleRuntimeAvailability) availabilityField.get(system);
        Field poolsField = ParticleRuntimeAvailability.class.getDeclaredField("pools");
        poolsField.setAccessible(true);
        ObjectMap<String, ParticleEffectPool> pools =
                (ObjectMap<String, ParticleEffectPool>) poolsField.get(availability);
        pools.put(atlasTag + "|" + effectPath,
                new ParticleEffectPool(new ParticleEffect(), 0, 4));
    }

    private static TiledMapLayerData tiled(World world, int entityId, int width) {
        TiledMapLayerData data = new TiledMapLayerData(width, 1, 16, 16, 1);
        world.getMapper(TiledLayerComponent.class).create(entityId).data = data;
        return data;
    }

    private static Object glDefaultValue(
            String name, Object[] args, Class<?> returnType, int[] nextHandle) {
        if ("glCreateShader".equals(name)
                || "glCreateProgram".equals(name)
                || "glGenTexture".equals(name)
                || "glGenBuffer".equals(name)
                || "glGenVertexArray".equals(name)) {
            return nextHandle[0]++;
        }
        if ("glGetShaderiv".equals(name) && args != null && args.length >= 3) {
            ((java.nio.IntBuffer) args[2]).put(0, 1);
            return null;
        }
        if ("glGetProgramiv".equals(name) && args != null && args.length >= 3) {
            int parameter = (Integer) args[1];
            int value = parameter == GL20.GL_LINK_STATUS
                    || parameter == GL20.GL_VALIDATE_STATUS ? 1 : 0;
            ((java.nio.IntBuffer) args[2]).put(0, value);
            return null;
        }
        if (name.startsWith("glGen") && args != null && args.length >= 2
                && args[1] instanceof java.nio.IntBuffer) {
            java.nio.IntBuffer handles = (java.nio.IntBuffer) args[1];
            int count = (Integer) args[0];
            for (int i = 0; i < count; i++) handles.put(i, nextHandle[0]++);
            return null;
        }
        if ("glCheckFramebufferStatus".equals(name)) {
            return GL20.GL_FRAMEBUFFER_COMPLETE;
        }
        return defaultValue(returnType);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return (char) 0;
        return null;
    }
}
