package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsBodyCompilerTest {
    @Test
    public void compileIsDeterministicAndPreparedTransferIsSingleUse() {
        ResolvedPhysicsShape source = circle(1, 0.5f);
        Array<ResolvedPhysicsShape> candidate =
                new Array<>(true, 1, ResolvedPhysicsShape.class);
        candidate.add(source);

        PreparedCompiledFixtures prepared =
                new PhysicsBodyCompiler().compile(candidate);
        source.radius = 2f;
        candidate.clear();

        Array<CompiledFixtureData> fixtures = prepared.takeFixtures();
        Assert.assertEquals(1, fixtures.size);
        Assert.assertEquals(0.5f, fixtures.first().radius, 0f);
        Assert.assertEquals(1, fixtures.first().physicsShapeId);
        Assert.assertEquals(0, fixtures.first().partIndex);
        try {
            prepared.takeFixtures();
            Assert.fail("Prepared fixtures must be single-use.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("already consumed"));
        }
    }

    @Test
    public void compileRejectsNullResolvedShapeBeforePublication() {
        Array<ResolvedPhysicsShape> candidate =
                new Array<>(true, 1, ResolvedPhysicsShape.class);
        candidate.add(null);

        try {
            new PhysicsBodyCompiler().compile(candidate);
            Assert.fail("Null resolved shape must be rejected.");
        } catch (PhysicsShapeCompilationException expected) {
            Assert.assertTrue(expected.getMessage().contains("null"));
        }
    }

    private static ResolvedPhysicsShape circle(int physicsShapeId, float radius) {
        ResolvedPhysicsShape shape = new ResolvedPhysicsShape();
        shape.physicsShapeId = physicsShapeId;
        shape.shapeType = PhysicsDirectGeometryData.SHAPE_CIRCLE;
        shape.radius = radius;
        shape.enabled = true;
        return shape;
    }
}
