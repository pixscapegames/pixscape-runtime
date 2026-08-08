package games.pixscape.runtime.particle;

/**
 * Indicates that a particle image is absent from the currently published atlas.
 */
public final class MissingParticleAtlasRegionException extends IllegalArgumentException {

    public MissingParticleAtlasRegionException(String regionName) {
        super("Atlas is missing region: " + regionName);
    }
}
