package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsBodyCompilerTest {
    @Test
    public void prepareValidatesAndDeepCopiesCandidate() {
        CompiledFixtureData source = circle(1, 0.5f);
        Array<CompiledFixtureData> candidate =
                new Array<>(true, 1, CompiledFixtureData.class);
        candidate.add(source);

        PreparedCompiledFixtures prepared =
                new PhysicsBodyCompiler().prepare(candidate);
        source.radius = 2f;
        candidate.clear();

        Assert.assertEquals(1, prepared.fixtures().size);
        Assert.assertNotSame(source, prepared.fixtures().first());
        Assert.assertEquals(0.5f, prepared.fixtures().first().radius, 0f);
    }

    @Test
    public void prepareRejectsNullFixtureBeforePublication() {
        Array<CompiledFixtureData> candidate =
                new Array<>(true, 1, CompiledFixtureData.class);
        candidate.add(null);

        try {
            new PhysicsBodyCompiler().prepare(candidate);
            Assert.fail("Null compiled fixture must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("null entry"));
        }
    }

    private static CompiledFixtureData circle(int physicsShapeId, float radius) {
        CompiledFixtureData fixture = new CompiledFixtureData();
        fixture.physicsShapeId = physicsShapeId;
        fixture.shapeType = CompiledFixtureData.SHAPE_CIRCLE;
        fixture.radius = radius;
        return fixture;
    }
}
