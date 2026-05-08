package games.pixscape.runtime.component.physics;

/**
 * Simple generator of stable IDs for editor fixtures.
 *
 * IDs live in FixtureDefData. This generator only allocates and reseeds.
 */
public final class FixtureIdSequence {

    private static final FixtureIdSequence INSTANCE = new FixtureIdSequence();

    public static FixtureIdSequence i() {
        return INSTANCE;
    }

    private int seq = 1;

    public int next() {
        return seq++;
    }

    public int ensure(FixtureDefData fixture) {
        if (fixture == null) return -1;

        if (fixture.fixtureId > 0) {
            reseed(fixture.fixtureId + 1);
            return fixture.fixtureId;
        }

        if (fixture.fixtureId == Integer.MAX_VALUE) {
            return fixture.fixtureId;
        }

        int id = next();
        fixture.fixtureId = id;
        return id;
    }

    public void reseed(int nextMin) {
        if (nextMin <= 0) return;
        if (seq > 0 && seq < nextMin) {
            seq = nextMin;
        }
    }

    public void clear() {
        seq = 1;
    }
}