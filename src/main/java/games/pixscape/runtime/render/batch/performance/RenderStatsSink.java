package games.pixscape.runtime.render.batch.performance;

public final class RenderStatsSink {
    private final float periodSec;
    private float acc;
    private final RenderStats accStats = new RenderStats();

    public RenderStatsSink(float periodSec) { this.periodSec = Math.max(0.25f, periodSec); }

    public void accumulate(RenderStats frameStats, float dt) {
        acc += dt;
        accStats.add(frameStats);
        if (acc >= periodSec) {
            acc = 0f;
            accStats.reset();
        }
    }
}
