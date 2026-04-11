package games.pixscape.runtime.loading;

import com.artemis.World;
import games.pixscape.runtime.service.TileAnimationRegistry;

public final class WorldBootstrapResult {

    private final World world;

    private final int ecsStart;
    private final int ecsEnd;

    private final int tiledStart;
    private final int tiledEnd;

    private final int vfxStart;
    private final int vfxEnd;

    private final int totalCapacity;

    private final TileAnimationRegistry animatedTileRegistry;

    public WorldBootstrapResult(World world,
                                int ecsStart,
                                int ecsEnd,
                                int tiledStart,
                                int tiledEnd,
                                int vfxStart,
                                int vfxEnd,
                                int totalCapacity,
                                TileAnimationRegistry animatedTileRegistry) {

        this.world = world;

        this.ecsStart = ecsStart;
        this.ecsEnd = ecsEnd;

        this.tiledStart = tiledStart;
        this.tiledEnd = tiledEnd;

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

    public int getTiledStart() {
        return tiledStart;
    }

    public int getTiledEnd() {
        return tiledEnd;
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