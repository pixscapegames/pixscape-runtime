package games.pixscape.runtime.component.physics;

import java.util.concurrent.atomic.AtomicLong;

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

    private final AtomicLong seq = new AtomicLong(1L);

    private FixtureIdSequence() {
    }

    public long next() {
        return seq.getAndIncrement();
    }

    public long ensure(FixtureDefData fixture) {
        if (fixture == null) return -1L;
        if (fixture.fixtureId > 0L) {
            reseed(fixture.fixtureId + 1L);
            return fixture.fixtureId;
        }
        long id = next();
        fixture.fixtureId = id;
        return id;
    }

    public void reseed(long nextMin) {
        if (nextMin <= 0L) return;
        long current;
        do {
            current = seq.get();
            if (current >= nextMin) return;
        } while (!seq.compareAndSet(current, nextMin));
    }

    public void clear() {
        seq.set(1L);
    }
}
