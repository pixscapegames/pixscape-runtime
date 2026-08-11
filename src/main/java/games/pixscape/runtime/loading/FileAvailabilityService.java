package games.pixscape.runtime.loading;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Thin, LibGDX-backed driver for Pixscape file and resource availability.
 *
 * <p>The service owns only Pixscape request bookkeeping. It does not own a
 * downloader, browser cache, byte cache, or parsed-content cache. Lightweight
 * file requests retain only their normal {@link FileHandle}. Heavy resources
 * must be consumed through the exact object returned by {@link #get}.</p>
 */
public final class FileAvailabilityService implements AutoCloseable {

    private final AssetManager assetManager;
    private final boolean ownsAssetManager;
    private final ObjectMap<String, Request> requests = new ObjectMap<>();
    private boolean disposed;

    /** Creates a standalone service with an internally owned manager. */
    public FileAvailabilityService() {
        this(new AssetManager(new InternalFileHandleResolver()), true);
    }

    /**
     * Internal engine integration constructor. Ownership is explicit so a
     * supplied manager can never be disposed accidentally.
     */
    public FileAvailabilityService(AssetManager assetManager, boolean ownsAssetManager) {
        if (assetManager == null) throw new IllegalArgumentException("assetManager is null");
        this.assetManager = assetManager;
        this.ownsAssetManager = ownsAssetManager;
        assetManager.setLoader(AvailableFile.class,
                new AvailableFileLoader(assetManager.getFileHandleResolver()));
    }

    public void requestFile(String path) {
        request(path, AvailableFile.class);
    }

    public void releaseFile(String path) {
        release(path, AvailableFile.class);
    }

    /** Acquires one Pixscape lease for the normalized key and required type. */
    public <T> void request(String path, Class<T> type) {
        requireOpen();
        final String normalized = normalizePath(path);
        if (type == null) throw new IllegalArgumentException("type is null for: " + normalized);

        Request existingRequest = requests.get(normalized);
        if (existingRequest != null) {
            if (existingRequest.type != type) {
                throw wrongType(normalized, existingRequest.type, type);
            }
            existingRequest.leases++;
            return;
        }

        Class existingType = assetManager.getAssetType(normalized);
        if (existingType != null && existingType != type) {
            throw wrongType(normalized, existingType, type);
        }
        if (assetManager.contains(normalized) && !assetManager.contains(normalized, type)) {
            throw new IllegalArgumentException("Asset type collision for path '" + normalized
                    + "': existing queued type is incompatible with required type "
                    + type.getName() + ".");
        }

        final Request request = new Request(normalized, type);
        requests.put(normalized, request);
        try {
            assetManager.load(normalized, type, parameters(type, request));
        } catch (RuntimeException failure) {
            requests.remove(normalized);
            throw failure;
        }
    }

    /** Releases one Pixscape lease and unloads only Pixscape's acquired reference. */
    public void release(String path, Class<?> type) {
        requireOpen();
        String normalized = normalizePath(path);
        Request request = requests.get(normalized);
        if (request == null) return;
        if (type == null || request.type != type) {
            throw wrongType(normalized, request.type, type);
        }
        request.leases--;
        if (request.leases > 0) return;
        requests.remove(normalized);
        unloadRequest(request);
    }

    /** Advances the shared manager once and reports Pixscape-scoped completion. */
    public boolean update() {
        requireOpen();
        assetManager.update();
        detectFailedRequests();
        return isComplete();
    }

    /** Deterministic item-based progress for Pixscape requests only. */
    public float progress() {
        requireOpen();
        if (requests.size == 0) return 1f;
        int complete = 0;
        for (ObjectMap.Values<Request> it = requests.values(); it.hasNext(); ) {
            if (it.next().acquired) complete++;
        }
        return (float) complete / (float) requests.size;
    }

    public boolean isComplete() {
        requireOpen();
        for (ObjectMap.Values<Request> it = requests.values(); it.hasNext(); ) {
            if (!it.next().acquired) return false;
        }
        return true;
    }

    public boolean isFileAvailable(String path) {
        return isAvailable(path, AvailableFile.class);
    }

    public FileHandle file(String path) {
        return get(path, AvailableFile.class).file;
    }

    public <T> boolean isAvailable(String path, Class<T> type) {
        requireOpen();
        Request request = requests.get(normalizePath(path));
        return request != null && request.type == type && request.acquired;
    }

    /** Returns the exact manager-owned resource. The caller does not own disposal. */
    public <T> T get(String path, Class<T> type) {
        requireOpen();
        String normalized = normalizePath(path);
        Request request = requests.get(normalized);
        if (request == null || request.type != type || !request.acquired) {
            throw new IllegalStateException("Pixscape resource is not available: " + normalized);
        }
        return assetManager.get(normalized, type);
    }

    /**
     * Drives Pixscape requests synchronously on native targets. A shared manager
     * remains one queue, so this may also advance application-owned requests.
     */
    public void finishLoadingOnNative() {
        requireOpen();
        if (Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.WebGL) {
            throw new IllegalStateException(
                    "HTML file availability must be driven progressively with update().");
        }
        while (!isComplete()) update();
    }

    @Override
    public void close() {
        dispose();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (ObjectMap.Values<Request> it = requests.values(); it.hasNext(); ) {
            unloadRequest(it.next());
        }
        requests.clear();
        if (ownsAssetManager) assetManager.dispose();
    }

    AssetManager assetManager() {
        return assetManager;
    }

    boolean ownsAssetManager() {
        return ownsAssetManager;
    }

    private void detectFailedRequests() {
        for (ObjectMap.Values<Request> it = requests.values(); it.hasNext(); ) {
            Request request = it.next();
            if (!request.acquired && !assetManager.contains(request.path)) {
                throw new GdxRuntimeException("Required Pixscape asset failed to load: "
                        + request.path + " (required type " + request.type.getName() + ")");
            }
        }
    }

    private void unloadRequest(Request request) {
        if (request.acquired || assetManager.contains(request.path)) {
            assetManager.unload(request.path);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> AssetLoaderParameters<T> parameters(Class<T> type, final Request request) {
        AssetLoaderParameters parameters;
        if (type == TextureAtlas.class) {
            parameters = new TextureAtlasLoader.TextureAtlasParameter();
        } else {
            parameters = new AssetLoaderParameters();
        }
        parameters.loadedCallback = new AssetLoaderParameters.LoadedCallback() {
            @Override
            public void finishedLoading(AssetManager manager, String fileName, Class loadedType) {
                if (request.path.equals(fileName) && request.type == loadedType) {
                    request.acquired = true;
                }
            }
        };
        return parameters;
    }

    private void requireOpen() {
        if (disposed) throw new IllegalStateException("FileAvailabilityService is disposed.");
    }

    private static IllegalArgumentException wrongType(
            String path, Class<?> existing, Class<?> required) {
        return new IllegalArgumentException("Asset type collision for path '" + path
                + "': existing type " + typeName(existing)
                + ", required type " + typeName(required) + ".");
    }

    private static String typeName(Class<?> type) {
        return type != null ? type.getName() : "null";
    }

    public static String normalizePath(String path) {
        if (path == null) throw new IllegalArgumentException("path is null");
        String normalized = path.replace('\\', '/').trim();
        if (normalized.length() == 0) throw new IllegalArgumentException("path is blank");
        return normalized;
    }

    private static final class Request {
        final String path;
        final Class<?> type;
        int leases = 1;
        boolean acquired;

        Request(String path, Class<?> type) {
            this.path = path;
            this.type = type;
        }
    }

    private static final class AvailableFile {
        final FileHandle file;

        AvailableFile(FileHandle file) {
            this.file = file;
        }
    }

    private static final class AvailableFileLoader
            extends SynchronousAssetLoader<AvailableFile, AssetLoaderParameters<AvailableFile>> {

        AvailableFileLoader(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        public AvailableFile load(AssetManager manager, String fileName, FileHandle file,
                                  AssetLoaderParameters<AvailableFile> parameter) {
            if (!file.exists()) {
                throw new IllegalStateException("Required file is unavailable: " + fileName);
            }
            return new AvailableFile(file);
        }

        @Override
        public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file,
                AssetLoaderParameters<AvailableFile> parameter) {
            return null;
        }
    }
}
