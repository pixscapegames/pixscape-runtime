package games.pixscape.runtime.helper;

import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TransformComponent;
import org.junit.Assert;
import org.junit.Test;

public class QuadGeometryHelperTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void missingComponentProducesNormalObbCorners() {
        Fixture fixture = fixture(0f, 0f, 1f, 1f, 0f);

        QuadGeometryHelper.toWorldCorners(fixture.bounds, fixture.transform, null, fixture.actual);

        assertNormalCorners(fixture);
    }

    @Test
    public void zeroComponentProducesNormalObbCorners() {
        Fixture fixture = fixture(0f, 0f, 1f, 1f, 0f);

        QuadGeometryHelper.toWorldCorners(
                fixture.bounds, fixture.transform, new QuadDeformComponent(), fixture.actual);

        assertNormalCorners(fixture);
    }

    @Test
    public void movingOneCornerOnlyDisplacesThatCorner() {
        Fixture fixture = fixture(0f, 0f, 1f, 1f, 0f);
        QuadDeformComponent deform = new QuadDeformComponent();
        deform.trX = 2f;
        deform.trY = -3f;

        QuadGeometryHelper.toWorldCorners(fixture.bounds, fixture.transform, deform, fixture.actual);

        Assert.assertArrayEquals(
                new float[]{0f, 0f, 10f, 0f, 12f, 7f, 0f, 10f},
                fixture.actual,
                EPSILON);
    }

    @Test
    public void translationMovesEntireDeformedQuad() {
        Fixture fixture = fixture(7f, -4f, 1f, 1f, 0f);
        QuadDeformComponent deform = new QuadDeformComponent();
        deform.blX = 2f;
        deform.blY = 3f;

        QuadGeometryHelper.toWorldCorners(fixture.bounds, fixture.transform, deform, fixture.actual);

        Assert.assertArrayEquals(
                new float[]{9f, -1f, 17f, -4f, 17f, 6f, 7f, 6f},
                fixture.actual,
                EPSILON);
    }

    @Test
    public void rotationRotatesLocalDeformation() {
        Fixture fixture = fixture(0f, 0f, 1f, 1f, 90f);
        QuadDeformComponent deform = new QuadDeformComponent();
        deform.blX = 2f;
        deform.blY = 3f;

        QuadGeometryHelper.toWorldCorners(fixture.bounds, fixture.transform, deform, fixture.actual);

        Assert.assertEquals(-3f, fixture.actual[0], EPSILON);
        Assert.assertEquals(2f, fixture.actual[1], EPSILON);
        assertUnchangedCorners(fixture, 2);
    }

    @Test
    public void positiveScaleScalesLocalDeformation() {
        Fixture fixture = fixture(0f, 0f, 2f, 3f, 0f);
        QuadDeformComponent deform = new QuadDeformComponent();
        deform.blX = 2f;
        deform.blY = 3f;

        QuadGeometryHelper.toWorldCorners(fixture.bounds, fixture.transform, deform, fixture.actual);

        Assert.assertEquals(4f, fixture.actual[0], EPSILON);
        Assert.assertEquals(9f, fixture.actual[1], EPSILON);
        assertUnchangedCorners(fixture, 2);
    }

    @Test
    public void negativeXScaleReversesLocalXDeformation() {
        Fixture fixture = fixture(0f, 0f, -2f, 3f, 0f);
        QuadDeformComponent deform = new QuadDeformComponent();
        deform.blX = 2f;
        deform.blY = 3f;

        QuadGeometryHelper.toWorldCorners(fixture.bounds, fixture.transform, deform, fixture.actual);

        Assert.assertEquals(fixture.normal[0] - 4f, fixture.actual[0], EPSILON);
        Assert.assertEquals(fixture.normal[1] + 9f, fixture.actual[1], EPSILON);
        assertUnchangedCorners(fixture, 2);
    }

    @Test
    public void negativeYScaleReversesLocalYDeformation() {
        Fixture fixture = fixture(0f, 0f, 2f, -3f, 0f);
        QuadDeformComponent deform = new QuadDeformComponent();
        deform.blX = 2f;
        deform.blY = 3f;

        QuadGeometryHelper.toWorldCorners(fixture.bounds, fixture.transform, deform, fixture.actual);

        Assert.assertEquals(fixture.normal[0] + 4f, fixture.actual[0], EPSILON);
        Assert.assertEquals(fixture.normal[1] - 9f, fixture.actual[1], EPSILON);
        assertUnchangedCorners(fixture, 2);
    }

    private static Fixture fixture(float x, float y, float scaleX, float scaleY, float rotationDeg) {
        Fixture fixture = new Fixture();
        fixture.transform.x = x;
        fixture.transform.y = y;
        fixture.transform.scaleX = scaleX;
        fixture.transform.scaleY = scaleY;
        fixture.transform.rotationRad = (float) Math.toRadians(rotationDeg);
        fixture.transform.refreshCaches();

        fixture.bounds.cx = x + fixture.transform.cos * 5f * scaleX
                - fixture.transform.sin * 5f * scaleY;
        fixture.bounds.cy = y + fixture.transform.sin * 5f * scaleX
                + fixture.transform.cos * 5f * scaleY;
        fixture.bounds.ux = fixture.transform.cos;
        fixture.bounds.uy = fixture.transform.sin;
        fixture.bounds.vx = -fixture.transform.sin;
        fixture.bounds.vy = fixture.transform.cos;
        fixture.bounds.hx = 5f * Math.abs(scaleX);
        fixture.bounds.hy = 5f * Math.abs(scaleY);
        OrientedBoundsHelper.toCorners(fixture.bounds, fixture.normal);
        return fixture;
    }

    private static void assertNormalCorners(Fixture fixture) {
        Assert.assertArrayEquals(fixture.normal, fixture.actual, EPSILON);
    }

    private static void assertUnchangedCorners(Fixture fixture, int fromIndex) {
        for (int i = fromIndex; i < fixture.actual.length; i++) {
            Assert.assertEquals(fixture.normal[i], fixture.actual[i], EPSILON);
        }
    }

    private static final class Fixture {
        final TransformComponent transform = new TransformComponent();
        final OrientedBoundsComponent bounds = new OrientedBoundsComponent();
        final float[] normal = new float[8];
        final float[] actual = new float[8];
    }
}
