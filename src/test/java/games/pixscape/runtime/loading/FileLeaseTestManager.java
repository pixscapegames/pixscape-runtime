package games.pixscape.runtime.loading;

import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic synchronous acquisition/cleanup failures; no files or GL objects are loaded. */
final class FileLeaseTestManager extends AssetManager {
    final Map<String, Integer> acquired = new LinkedHashMap<>();
    final Map<String, Integer> released = new LinkedHashMap<>();
    final RuntimeException acquisitionFailure = new IllegalStateException("injected acquisition failure");
    final RuntimeException cleanupFailure = new IllegalStateException("injected cleanup failure");
    String failAcquisition;
    String failCleanup;

    FileLeaseTestManager() { super(FileHandle::new); }

    @Override public synchronized <T> void load(String path, Class<T> type, AssetLoaderParameters<T> parameters) {
        if (path.equals(failAcquisition)) throw acquisitionFailure;
        super.load(path, type, parameters);
        acquired.merge(path, 1, Integer::sum);
    }

    @Override public synchronized void unload(String path) {
        released.merge(path, 1, Integer::sum);
        super.unload(path);
        if (path.equals(failCleanup)) throw cleanupFailure;
    }
}
