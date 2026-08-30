package games.pixscape.runtime.hierarchy;

import com.badlogic.gdx.math.Affine2;
import org.junit.Assert;
import org.junit.Test;
import games.pixscape.runtime.component.TransformComponent;

public class GameObjectTransformMathTest {
    private static final float EPSILON = 0.001f;

    @Test
    public void identityParentPreservesChildTransform() {
        assertTransform(transform(3f, -2f, 0.4f, 2f, 0.5f, 7f, 9f),
                world(identity(), transform(3f, -2f, 0.4f, 2f, 0.5f, 7f, 9f)));
    }

    @Test
    public void parentTranslationIsApplied() {
        assertPosition(world(transform(10f, 20f, 0f, 1f, 1f),
                transform(3f, 4f, 0f, 1f, 1f)), 13f, 24f);
    }

    @Test
    public void parentRotationIsApplied() {
        assertPosition(world(transform(0f, 0f, (float) Math.PI * 0.5f, 1f, 1f),
                transform(2f, 0f, 0f, 1f, 1f)), 0f, 2f);
    }

    @Test
    public void parentUniformScaleIsApplied() {
        TransformComponent result = world(transform(0f, 0f, 0f, 3f, 3f),
                transform(2f, -4f, 0f, 2f, 0.5f));
        assertPosition(result, 6f, -12f);
        Assert.assertEquals(6f, result.scaleX, EPSILON);
        Assert.assertEquals(1.5f, result.scaleY, EPSILON);
    }

    @Test
    public void nonZeroParentOriginDefinesRotationPivot() {
        TransformComponent parent = transform(
                10f, 20f, (float) Math.PI * 0.5f, 1f, 1f, 5f, 7f);

        assertPosition(world(parent, transform(5f, 7f, 0f, 1f, 1f)), 15f, 27f);
        assertPosition(world(parent, transform(0f, 0f, 0f, 1f, 1f)), 22f, 22f);
    }

    @Test
    public void nonZeroParentOriginDefinesScalePivot() {
        TransformComponent parent = transform(10f, 20f, 0f, 2f, 2f, 5f, 7f);

        assertPosition(world(parent, transform(5f, 7f, 0f, 1f, 1f)), 15f, 27f);
        assertPosition(world(parent, transform(0f, 0f, 0f, 1f, 1f)), 5f, 13f);
    }

    @Test
    public void translationAndRotationCompose() {
        assertPosition(world(transform(10f, 5f, (float) Math.PI * 0.5f, 1f, 1f),
                transform(2f, 0f, 0.25f, 1f, 1f)), 10f, 7f);
    }

    @Test
    public void translationRotationAndUniformScaleCompose() {
        TransformComponent result = world(
                transform(10f, 5f, (float) Math.PI * 0.5f, 2f, 2f),
                transform(2f, 0f, 0.25f, 1.5f, 0.5f));
        assertPosition(result, 10f, 9f);
        Assert.assertEquals((float) Math.PI * 0.5f + 0.25f, result.rotationRad, EPSILON);
        Assert.assertEquals(3f, result.scaleX, EPSILON);
        Assert.assertEquals(1f, result.scaleY, EPSILON);
    }

    @Test
    public void nestedCompositionUsesImmediateParentWorld() {
        TransformComponent root = transform(10f, 1f, 0.5f, 2f, 2f);
        TransformComponent middleWorld = world(root, transform(3f, 2f, -0.2f, 0.5f, 0.5f));
        TransformComponent leafWorld = world(middleWorld, transform(-4f, 1f, 0.3f, 2f, 1f));

        Affine2 rootFrame = GameObjectTransformMath.toFrame(root, new Affine2());
        Affine2 middleLocal = GameObjectTransformMath.toFrame(
                transform(3f, 2f, -0.2f, 0.5f, 0.5f), new Affine2());
        Affine2 leafLocal = GameObjectTransformMath.toFrame(
                transform(-4f, 1f, 0.3f, 2f, 1f), new Affine2());
        Affine2 expected = GameObjectTransformMath.multiply(rootFrame, middleLocal, new Affine2());
        GameObjectTransformMath.multiply(expected, leafLocal, expected);
        Affine2 actual = GameObjectTransformMath.toFrame(leafWorld, new Affine2());
        assertFrame(expected, actual);
    }

    @Test
    public void worldLocalWorldRoundTripIsStable() {
        TransformComponent parent = transform(7f, -11f, 0.72f, 2.5f, 2.5f, 13f, 9f);
        TransformComponent original = transform(-3f, 8f, -0.33f, 1.7f, -0.8f, 4f, 6f);
        TransformComponent local = new TransformComponent();
        GameObjectTransformMath.worldToLocal(parent, original, local);
        TransformComponent restored = world(parent, local);

        assertTransform(original, restored);
    }

    @Test
    public void nestedGameObjectOriginsComposeAndRoundTrip() {
        TransformComponent parent = transform(8f, -4f, 0.65f, 2f, 2f, 11f, 6f);
        TransformComponent nestedLocal = transform(
                3f, 5f, -0.3f, 0.75f, 0.75f, 4f, 9f);
        TransformComponent nestedWorld = GameObjectTransformMath.localToWorld(
                parent, nestedLocal, true, new TransformComponent());
        TransformComponent restoredLocal = GameObjectTransformMath.worldToLocal(
                parent, nestedWorld, true, new TransformComponent());

        assertTransform(nestedLocal, restoredLocal);
        Affine2 expected = GameObjectTransformMath.multiply(
                GameObjectTransformMath.toFrame(parent, new Affine2()),
                GameObjectTransformMath.toFrame(nestedLocal, new Affine2()),
                new Affine2());
        assertFrame(expected, GameObjectTransformMath.toFrame(nestedWorld, new Affine2()), 0.005f);
    }

    @Test
    public void reparentPreservesWorldTransform() {
        TransformComponent parentA = transform(5f, 3f, 0.25f, 2f, 2f);
        TransformComponent parentB = transform(-9f, 4f, -0.7f, 0.5f, 0.5f);
        TransformComponent previousWorld = world(parentA,
                transform(6f, -2f, 0.4f, 1.2f, 0.8f, 2f, 3f));
        TransformComponent localB = new TransformComponent();

        GameObjectTransformMath.worldToLocal(parentB, previousWorld, localB);

        assertTransform(previousWorld, world(parentB, localB));
    }

    @Test
    public void detachUsesResolvedWorldAsAuthoredWorld() {
        TransformComponent resolved = world(transform(8f, 1f, 0.6f, 2f, 2f),
                transform(3f, 7f, -0.2f, 1f, 1f, 4f, 5f));
        TransformComponent detached = copy(resolved);

        assertTransform(resolved, detached);
    }

    @Test
    public void childOriginDoesNotAffectHierarchyPosition() {
        TransformComponent first = world(transform(10f, 20f, 0.5f, 2f, 2f, 6f, 8f),
                transform(3f, 4f, 0.2f, 1f, 1f, 0f, 0f));
        TransformComponent second = world(transform(10f, 20f, 0.5f, 2f, 2f, 6f, 8f),
                transform(3f, 4f, 0.2f, 1f, 1f, 100f, -50f));

        assertPosition(second, first.x, first.y);
        Assert.assertEquals(100f, second.originX, 0f);
        Assert.assertEquals(-50f, second.originY, 0f);
    }

    @Test
    public void scaleContractAcceptsOnlyPositiveUniformParents() {
        Assert.assertTrue(GameObjectTransformMath.isPositiveUniformParentScale(
                transform(0f, 0f, 0f, 2f, 2f)));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> GameObjectTransformMath.requirePositiveUniformParentScale(
                        transform(0f, 0f, 0f, 0f, 0f)));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> GameObjectTransformMath.requirePositiveUniformParentScale(
                        transform(0f, 0f, 0f, -1f, -1f)));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> GameObjectTransformMath.requirePositiveUniformParentScale(
                        transform(0f, 0f, 0f, 2f, 3f)));
    }

    @Test
    public void physicsAndSpatialUnitScaleContractIsExact() {
        Assert.assertTrue(GameObjectTransformMath.isUnitParentScale(identity()));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> GameObjectTransformMath.requireUnitParentScale(
                        transform(0f, 0f, 0f, 1.00001f, 1.00001f), "Physics"));
    }

    @Test
    public void affineWorldToLocalRoundTripPreservesFrame() {
        Affine2 parent = GameObjectTransformMath.toFrame(
                transform(5f, -3f, 0.6f, 2f, 2f), new Affine2());
        Affine2 local = GameObjectTransformMath.toFrame(
                transform(7f, 1f, -0.2f, 1.5f, 0.75f), new Affine2());
        Affine2 world = GameObjectTransformMath.multiply(parent, local, new Affine2());
        Affine2 restoredLocal = GameObjectTransformMath.worldToLocal(parent, world, new Affine2());

        assertFrame(local, restoredLocal);
    }

    @Test
    public void decompositionRejectsShear() {
        Affine2 shear = new Affine2();
        shear.m00 = 1f;
        shear.m01 = 0.5f;
        shear.m02 = 0f;
        shear.m10 = 0f;
        shear.m11 = 1f;
        shear.m12 = 0f;

        Assert.assertThrows(IllegalArgumentException.class,
                () -> GameObjectTransformMath.extract(shear, 0f, 0f, new TransformComponent()));
    }

    @Test
    public void repeatedRoundTripsRemainWithinNumericalTolerance() {
        TransformComponent parent = transform(100f, -70f, 1.1f, 3f, 3f);
        TransformComponent world = transform(-20f, 11f, -0.8f, 0.6f, 1.4f, 2f, 9f);
        TransformComponent expected = copy(world);
        for (int i = 0; i < 100; i++) {
            TransformComponent local = new TransformComponent();
            GameObjectTransformMath.worldToLocal(parent, world, local);
            world = world(parent, local);
        }

        assertTransform(expected, world);
    }

    private static TransformComponent world(TransformComponent parent, TransformComponent local) {
        return GameObjectTransformMath.localToWorld(parent, local, new TransformComponent());
    }

    private static TransformComponent identity() {
        return transform(0f, 0f, 0f, 1f, 1f);
    }

    private static TransformComponent transform(
            float x, float y, float rotation, float scaleX, float scaleY) {
        return transform(x, y, rotation, scaleX, scaleY, 0f, 0f);
    }

    private static TransformComponent transform(
            float x, float y, float rotation, float scaleX, float scaleY,
            float originX, float originY) {
        TransformComponent transform = new TransformComponent();
        transform.x = x;
        transform.y = y;
        transform.rotationRad = rotation;
        transform.scaleX = scaleX;
        transform.scaleY = scaleY;
        transform.originX = originX;
        transform.originY = originY;
        transform.refreshCaches();
        return transform;
    }

    private static TransformComponent copy(TransformComponent source) {
        return transform(source.x, source.y, source.rotationRad,
                source.scaleX, source.scaleY, source.originX, source.originY);
    }

    private static void assertPosition(TransformComponent transform, float x, float y) {
        Assert.assertEquals(x, transform.x, EPSILON);
        Assert.assertEquals(y, transform.y, EPSILON);
    }

    private static void assertTransform(TransformComponent expected, TransformComponent actual) {
        Assert.assertEquals(expected.x, actual.x, EPSILON);
        Assert.assertEquals(expected.y, actual.y, EPSILON);
        Assert.assertEquals(expected.rotationRad, actual.rotationRad, EPSILON);
        Assert.assertEquals(expected.scaleX, actual.scaleX, EPSILON);
        Assert.assertEquals(expected.scaleY, actual.scaleY, EPSILON);
        Assert.assertEquals(expected.originX, actual.originX, EPSILON);
        Assert.assertEquals(expected.originY, actual.originY, EPSILON);
    }

    private static void assertFrame(Affine2 expected, Affine2 actual) {
        assertFrame(expected, actual, EPSILON);
    }

    private static void assertFrame(Affine2 expected, Affine2 actual, float epsilon) {
        Assert.assertEquals(expected.m00, actual.m00, epsilon);
        Assert.assertEquals(expected.m01, actual.m01, epsilon);
        Assert.assertEquals(expected.m02, actual.m02, epsilon);
        Assert.assertEquals(expected.m10, actual.m10, epsilon);
        Assert.assertEquals(expected.m11, actual.m11, epsilon);
        Assert.assertEquals(expected.m12, actual.m12, epsilon);
    }
}
