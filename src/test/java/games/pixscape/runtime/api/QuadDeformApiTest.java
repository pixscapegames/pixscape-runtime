package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TintComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class QuadDeformApiTest {
    private PixscapeEngine engine;
    private World world;
    private DirtyTrackerSystem dirty;

    @Before
    public void setUp() throws Exception {
        dirty = new DirtyTrackerSystem(64);
        world = new World(new WorldConfigurationBuilder().with(dirty).build());
        engine = new PixscapeEngine();
        setField(engine, "world", world);
        engine.getIdentityRegistry().bind(world, new SceneMetaRuntime());
        engine.getTagRegistry().bind(world);
    }

    @After
    public void tearDown() {
        world.dispose();
    }

    @Test
    public void capabilityAndZeroReadsRequireCompleteSpriteWithoutCreatingComponent() {
        int eligible = createQuadSprite();
        int incomplete = createSpriteCapability();
        world.process();

        QuadDeformFacade quad = engine.api().entities().ofEntityId(eligible).quadDeform();
        Assert.assertTrue(quad.exists());
        Assert.assertFalse(quad.isDeformed());
        assertFacade(quad, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(eligible));

        QuadDeformFacade unsupported =
                engine.api().entities().ofEntityId(incomplete).quadDeform();
        Assert.assertFalse(unsupported.exists());
        Assert.assertFalse(unsupported.isDeformed());
        assertFacade(unsupported, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        unsupported.setTopLeft(3f, 4f);
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(incomplete));
    }

    @Test
    public void cornerSettersLazilyCreateAndMapBlBrTrTlExactly() {
        int entity = createQuadSprite();
        world.process();
        QuadDeformFacade quad = engine.api().entities().ofEntityId(entity).quadDeform();
        dirty.clearAll();

        quad.setTopLeft(7f, 8f);

        Assert.assertTrue(world.getMapper(QuadDeformComponent.class).has(entity));
        assertComponent(entity, 0f, 0f, 0f, 0f, 0f, 0f, 7f, 8f);
        quad.setBottomLeft(1f, 2f)
                .setBottomRight(3f, 4f)
                .setTopRight(5f, 6f);

        assertComponent(entity, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
        assertFacade(quad, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
        Assert.assertTrue(quad.isDeformed());
        Assert.assertEquals(GeometryDirty.QUAD, dirty.geomSub(entity));

        QuadDeformComponent component = world.getMapper(QuadDeformComponent.class).get(entity);
        dirty.clearAll();
        quad.setTopLeft(7f, 8f);
        Assert.assertSame(component, world.getMapper(QuadDeformComponent.class).get(entity));
        Assert.assertEquals(0, dirty.geomSub(entity));
    }

    @Test
    public void completeSetUsesExactBlBrTrTlOrderAndExactZeroRemovesComponent() {
        int entity = createQuadSprite();
        world.process();
        QuadDeformFacade quad = engine.api().entities().ofEntityId(entity).quadDeform();

        quad.set(11f, 12f, 21f, 22f, 31f, 32f, 41f, 42f);
        assertComponent(entity, 11f, 12f, 21f, 22f, 31f, 32f, 41f, 42f);

        dirty.clearAll();
        quad.set(0f, -0f, 0f, 0f, 0f, 0f, 0f, 0f);
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(entity));
        Assert.assertFalse(quad.isDeformed());
        Assert.assertEquals(GeometryDirty.QUAD, dirty.geomSub(entity));
    }

    @Test
    public void finiteValidationRejectsBeforeMutation() {
        int entity = createQuadSprite();
        world.process();
        final QuadDeformFacade quad = engine.api().entities().ofEntityId(entity).quadDeform();
        quad.setBottomLeft(1f, 2f);
        dirty.clearAll();

        assertIllegalArgument(new Runnable() {
            @Override public void run() { quad.setTopLeft(Float.NaN, 4f); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { quad.setBottomRight(3f, Float.POSITIVE_INFINITY); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() {
                quad.set(1f, 2f, 3f, 4f, Float.NEGATIVE_INFINITY, 6f, 7f, 8f);
            }
        });

        assertComponent(entity, 1f, 2f, 0f, 0f, 0f, 0f, 0f, 0f);
        Assert.assertEquals(0, dirty.geomSub(entity));
    }

    @Test
    public void resetRemovesComponentAndOnlyDirtiesForNonZeroAuthoredState() {
        int entity = createQuadSprite();
        world.process();
        QuadDeformFacade quad = engine.api().entities().ofEntityId(entity).quadDeform();
        QuadDeformComponent zero = world.getMapper(QuadDeformComponent.class).create(entity);
        Assert.assertFalse(quad.isDeformed());

        dirty.clearAll();
        quad.reset();
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(entity));
        Assert.assertEquals(0, dirty.geomSub(entity));

        quad.setTopRight(5f, 6f);
        dirty.clearAll();
        quad.reset();
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(entity));
        Assert.assertEquals(GeometryDirty.QUAD, dirty.geomSub(entity));
        Assert.assertEquals(0f, zero.blX, 0f);
    }

    @Test
    public void repeatXOrYRejectsNonZeroQuadMutationAtomically() {
        assertRepeatBlocksQuad(true, false);
        assertRepeatBlocksQuad(false, true);
    }

    @Test
    public void deformationRejectsRepeatActivationAndPreservesBothStates() {
        int entity = createQuadSprite();
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(entity);
        world.process();
        final EntityRef ref = engine.api().entities().ofEntityId(entity);
        ref.quadDeform().setTopLeft(7f, 8f);

        assertIllegalState(new Runnable() {
            @Override public void run() { ref.sprite().setRepeat(true, false); }
        });
        Assert.assertFalse(repeat.repeatX);
        Assert.assertFalse(repeat.repeatY);
        assertComponent(entity, 0f, 0f, 0f, 0f, 0f, 0f, 7f, 8f);

        assertIllegalState(new Runnable() {
            @Override public void run() { ref.sprite().setRepeat(false, true); }
        });
        Assert.assertFalse(repeat.repeatX);
        Assert.assertFalse(repeat.repeatY);
        assertComponent(entity, 0f, 0f, 0f, 0f, 0f, 0f, 7f, 8f);
    }

    @Test
    public void repeatRecoveryAllowsResetAllZeroAndRepeatDisable() {
        int entity = createQuadSprite();
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(entity);
        repeat.repeatX = true;
        QuadDeformComponent expertQuad = world.getMapper(QuadDeformComponent.class).create(entity);
        expertQuad.blX = 1f;
        world.process();
        EntityRef ref = engine.api().entities().ofEntityId(entity);

        ref.sprite().setRepeat(false, false);
        Assert.assertFalse(repeat.repeatX);
        Assert.assertTrue(ref.quadDeform().isDeformed());

        repeat.repeatY = true;
        ref.quadDeform().reset();
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(entity));
        Assert.assertTrue(repeat.repeatY);

        QuadDeformComponent secondExpertQuad =
                world.getMapper(QuadDeformComponent.class).create(entity);
        secondExpertQuad.trY = 2f;
        ref.quadDeform().set(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(entity));
        Assert.assertTrue(repeat.repeatY);

        world.getMapper(QuadDeformComponent.class).create(entity);
        ref.sprite().setRepeat(true, false);
        Assert.assertTrue(repeat.repeatX);
        Assert.assertFalse(repeat.repeatY);
    }

    @Test
    public void staleCachedFacadeUsesDefaultsAndNeverRetargetsRecycledId() {
        int entity = createQuadSprite();
        world.process();
        EntityRef oldRef = engine.api().entities().ofEntityId(entity);
        QuadDeformFacade oldQuad = oldRef.quadDeform();
        Assert.assertSame(oldQuad, oldRef.quadDeform());
        oldQuad.setBottomLeft(1f, 2f);

        world.delete(entity);
        world.process();
        int replacement = createQuadSprite();
        Assert.assertEquals(entity, replacement);
        QuadDeformComponent replacementQuad =
                world.getMapper(QuadDeformComponent.class).create(replacement);
        replacementQuad.tlX = 99f;

        Assert.assertFalse(oldQuad.exists());
        Assert.assertFalse(oldQuad.isDeformed());
        assertFacade(oldQuad, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        oldQuad.set(3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f).reset();
        Assert.assertSame(replacementQuad,
                world.getMapper(QuadDeformComponent.class).get(replacement));
        Assert.assertEquals(99f, replacementQuad.tlX, 0f);
    }

    @Test
    public void publicFacadeDoesNotLeakEcsRendererOrMutableArrayTypes() {
        for (Method method : QuadDeformFacade.class.getMethods()) {
            Class<?> type = method.getReturnType();
            Assert.assertFalse(type.getName().startsWith("com.artemis"));
            Assert.assertFalse(type.getName().startsWith("com.badlogic.gdx.utils"));
            Assert.assertNotEquals(QuadDeformComponent.class, type);
            Assert.assertNotEquals(OrientedBoundsComponent.class, type);
            Assert.assertNotEquals(DirtyTrackerSystem.class, type);
            Assert.assertNotEquals(float[].class, type);
            Assert.assertTrue(type.isPrimitive() || type == QuadDeformFacade.class);
        }
    }

    private void assertRepeatBlocksQuad(boolean repeatX, boolean repeatY) {
        int entity = createQuadSprite();
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(entity);
        repeat.repeatX = repeatX;
        repeat.repeatY = repeatY;
        world.process();
        final QuadDeformFacade quad = engine.api().entities().ofEntityId(entity).quadDeform();

        assertIllegalState(new Runnable() {
            @Override public void run() { quad.setBottomLeft(1f, 2f); }
        });

        Assert.assertFalse(world.getMapper(QuadDeformComponent.class).has(entity));
        Assert.assertEquals(repeatX, repeat.repeatX);
        Assert.assertEquals(repeatY, repeat.repeatY);
    }

    private int createQuadSprite() {
        int entity = createSpriteCapability();
        world.getMapper(OrientedBoundsComponent.class).create(entity);
        world.getMapper(AABBComponent.class).create(entity);
        world.getMapper(TextureRegionComponent.class).create(entity);
        world.getMapper(RenderMaterialComponent.class).create(entity);
        return entity;
    }

    private int createSpriteCapability() {
        int entity = world.create();
        world.getMapper(TransformComponent.class).create(entity);
        world.getMapper(DimensionsComponent.class).create(entity);
        world.getMapper(AssetRefComponent.class).create(entity);
        world.getMapper(VisibilityComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(TintComponent.class).create(entity);
        return entity;
    }

    private void assertComponent(
            int entity,
            float blX, float blY,
            float brX, float brY,
            float trX, float trY,
            float tlX, float tlY) {
        QuadDeformComponent quad = world.getMapper(QuadDeformComponent.class).get(entity);
        Assert.assertNotNull(quad);
        Assert.assertEquals(blX, quad.blX, 0f);
        Assert.assertEquals(blY, quad.blY, 0f);
        Assert.assertEquals(brX, quad.brX, 0f);
        Assert.assertEquals(brY, quad.brY, 0f);
        Assert.assertEquals(trX, quad.trX, 0f);
        Assert.assertEquals(trY, quad.trY, 0f);
        Assert.assertEquals(tlX, quad.tlX, 0f);
        Assert.assertEquals(tlY, quad.tlY, 0f);
    }

    private static void assertFacade(
            QuadDeformFacade quad,
            float blX, float blY,
            float brX, float brY,
            float trX, float trY,
            float tlX, float tlY) {
        Assert.assertEquals(blX, quad.bottomLeftX(), 0f);
        Assert.assertEquals(blY, quad.bottomLeftY(), 0f);
        Assert.assertEquals(brX, quad.bottomRightX(), 0f);
        Assert.assertEquals(brY, quad.bottomRightY(), 0f);
        Assert.assertEquals(trX, quad.topRightX(), 0f);
        Assert.assertEquals(trY, quad.topRightY(), 0f);
        Assert.assertEquals(tlX, quad.topLeftX(), 0f);
        Assert.assertEquals(tlY, quad.topLeftY(), 0f);
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("finite"));
        }
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("Quad deformation"));
            Assert.assertTrue(expected.getMessage().contains("Repeat"));
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
