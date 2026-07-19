package games.pixscape.runtime.loading;

import com.artemis.World;
import games.pixscape.runtime.service.TileAnimationRegistry;

public final class WorldBootstrapResult {

    private final World world;

    private final int ecsStart;
    private final int ecsEnd;

    private final int vfxStart;
    private final int vfxEnd;

    private final int totalCapacity;

    private final TileAnimationRegistry animatedTileRegistry;

    public WorldBootstrapResult(World world,
                                int ecsStart,
                                int ecsEnd,
                                int vfxStart,
                                int vfxEnd,
                                int totalCapacity,
                                TileAnimationRegistry animatedTileRegistry) {

        this.world = world;

        this.ecsStart = ecsStart;
        this.ecsEnd = ecsEnd;

        this.vfxStart = vfxStart;
        this.vfxEnd = vfxEnd;

        this.totalCapacity = totalCapacity;

        this.animatedTileRegistry = animatedTileRegistry;
    }

    public World getWorld() {
        return world;
    }

    public int getEcsStart() {
        return ecsStart;
    }

    public int getEcsEnd() {
        return ecsEnd;
    }

    public int getVfxStart() {
        return vfxStart;
    }

    public int getVfxEnd() {
        return vfxEnd;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public TileAnimationRegistry getAnimatedTileRegistry() {
        return animatedTileRegistry;
    }
}
