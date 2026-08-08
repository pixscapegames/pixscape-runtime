package games.pixscape.runtime.loading;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

/**
 * Internal, LibGDX-backed driver for making files and realized resources available.
 *
 * <p>This service owns its {@link AssetManager}. It does not download or cache file
 * bytes itself. On GWT, AssetManager delegates bootstrap-excluded file acquisition
 * to the existing GWT preloader. The lightweight file loader retains only a
 * {@link FileHandle}; existing Runtime parsers remain responsible for reading and
 * parsing the file after it is available.</p>
 *
 * <p>If a typed request realizes a disposable heavy object, consumers must use the
 * exact manager-owned object returned by {@link #get(String, Class)}. They must not
 * construct an equivalent independent object from the same file. The service must
 * outlive every borrowed manager-owned object.</p>
 */
public final class FileAvailabilityService implements AutoCloseable {

    private final AssetManager assetManager;
    private boolean disposed;

    public FileAvailabilityService() {
        this(new AssetManager(new InternalFileHandleResolver()));
    }

    FileAvailabilityService(AssetManager assetManager) {
        if (assetManager == null) {
            throw new IllegalArgumentException("assetManager is null");
        }
        this.assetManager = assetManager;
        this.assetManager.setLoader(AvailableFile.class,
                new AvailableFileLoader(assetManager.getFileHandleResolver()));
    }

    /** Requests file availability without parsing or copying the file contents. */
    public void requestFile(String path) {
        request(path, AvailableFile.class);
    }

    /**
     * Requests a resource through the owned AssetManager.
     *
     * <p>This supports both lightweight file availability and manager-owned heavy
     * resources such as a future {@code TextureAtlas} request.</p>
     */
    public <T> void request(String path, Class<T> type) {
        requireOpen();
        String normalized = normalizePath(path);
        if (type == null) {
            throw new IllegalArgumentException("type is null for: " + normalized);
        }

        if (assetManager.contains(normalized)) {
            if (!assetManager.contains(normalized, type)) {
                throw new IllegalArgumentException(
                        "Asset already requested with a different type: " + normalized);
            }
            return;
        }

        assetManager.load(normalized, type);
    }

    /** Advances queued work once and returns whether all requests are complete. */
    public boolean update() {
        requireOpen();
        return assetManager.update();
    }

    public float progress() {
        requireOpen();
        return assetManager.getProgress();
    }

    public boolean isComplete() {
        requireOpen();
        return assetManager.isFinished();
    }

    public boolean isFileAvailable(String path) {
        requireOpen();
        return assetManager.isLoaded(normalizePath(path), AvailableFile.class);
    }

    public FileHandle file(String path) {
        requireOpen();
        String normalized = normalizePath(path);
        return assetManager.get(normalized, AvailableFile.class).file;
    }

    public <T> boolean isAvailable(String path, Class<T> type) {
        requireOpen();
        if (type == null) throw new IllegalArgumentException("type is null");
        return assetManager.isLoaded(normalizePath(path), type);
    }

    /** Returns the exact manager-owned resource. The caller does not own disposal. */
    public <T> T get(String path, Class<T> type) {
        requireOpen();
        if (type == null) throw new IllegalArgumentException("type is null");
        return assetManager.get(normalizePath(path), type);
    }

    /**
     * Blocks only on native backends.
     *
     * <p>GWT lazy downloads require browser event-loop turns for XHR/image callbacks,
     * so busy-looping AssetManager.finishLoading() there cannot complete an asset
     * excluded from bootstrap.</p>
     */
    public void finishLoadingOnNative() {
        requireOpen();
        if (Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.WebGL) {
            throw new IllegalStateException(
                    "HTML file availability must be driven progressively with update().");
        }
        assetManager.finishLoading();
    }

    @Override
    public void close() {
        dispose();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        // On GWT this releases manager-owned realized objects, but LibGDX's global
        // Preloader source entry is not individually evictable.
        assetManager.dispose();
    }

    private void requireOpen() {
        if (disposed) {
            throw new IllegalStateException("FileAvailabilityService is disposed.");
        }
    }

    private static String normalizePath(String path) {
        if (path == null) throw new IllegalArgumentException("path is null");
        String normalized = path.replace('\\', '/').trim();
        if (normalized.length() == 0) throw new IllegalArgumentException("path is blank");
        return normalized;
    }

    private static final class AvailableFile {
        final FileHandle file;

        AvailableFile(FileHandle file) {
            this.file = file;
        }
    }

    private static final class AvailableFileLoader
            extends SynchronousAssetLoader<AvailableFile, com.badlogic.gdx.assets.AssetLoaderParameters<AvailableFile>> {

        AvailableFileLoader(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        public AvailableFile load(AssetManager manager,
                                  String fileName,
                                  FileHandle file,
                                  com.badlogic.gdx.assets.AssetLoaderParameters<AvailableFile> parameter) {
            if (!file.exists()) {
                throw new IllegalStateException("Required file is unavailable: " + fileName);
            }
            return new AvailableFile(file);
        }

        @Override
        public Array<AssetDescriptor> getDependencies(
                String fileName,
                FileHandle file,
                com.badlogic.gdx.assets.AssetLoaderParameters<AvailableFile> parameter) {
            return null;
        }
    }
}
