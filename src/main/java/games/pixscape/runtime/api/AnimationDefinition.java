package games.pixscape.runtime.api;

/**
 * Read-only authored animation metadata.
 *
 * <p>Definitions are project-scoped. Callers should resolve them again after a project reload.</p>
 */
public interface AnimationDefinition {
    int assetId();

    String name();

    float fps();

    String currentClip();

    int frameCount();

    int clipCount();

    boolean hasClip(String clipName);
}
