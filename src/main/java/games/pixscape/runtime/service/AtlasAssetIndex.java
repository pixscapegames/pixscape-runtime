package games.pixscape.runtime.service;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import java.util.Locale;

/**
 * Immutable lookup index for the Pixscape assets of one loaded atlas.
 */
public final class AtlasAssetIndex {

    private final IntMap<AtlasAssetBinding> byAssetId;
    private final ObjectMap<String, AtlasAssetBinding> byName;
    private final int buildRegionVisits;

    AtlasAssetIndex(IntMap<AtlasAssetBinding> byAssetId,
                    ObjectMap<String, AtlasAssetBinding> byName, int buildRegionVisits) {
        this.byAssetId = byAssetId;
        this.byName = byName;
        this.buildRegionVisits = buildRegionVisits;
    }

    /**
     * Resolves an asset in O(1) average time without scanning atlas regions.
     */
    public AtlasAssetBinding get(int assetId) {
        return byAssetId.get(assetId);
    }

    /** Resolves a logical name, including misses, without visiting any atlas regions. */
    public AtlasAssetBinding getByName(String name) {
        if (name == null) return null;
        name = name.trim();
        return name.isEmpty() ? null : byName.get(nameKey(name.replace('\\', '/')));
    }

    // Canonicalization matches the public API's historical equalsIgnoreCase comparison.
    // Region names are not trimmed; caller input is. Asset suffixes are removed by the builder.
    static String nameKey(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        // GWT 2.11's String.equalsIgnoreCase compares UTF-16 length and lower-case strings.
        // Atlas publication and lookup both run on the same application/platform.
        if (Gdx.app != null && Gdx.app.getType() == ApplicationType.WebGL) {
            return name.length() + ":" + name.toLowerCase(Locale.ROOT);
        }
        StringBuilder key = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < name.length()
                    && Character.isLowSurrogate(name.charAt(i + 1))) {
                // Character's code-point case methods are not available in GWT's emulation.
                key.append(name.substring(i, i + 2).toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT));
                i++;
            } else {
                key.append(Character.toLowerCase(Character.toUpperCase(c)));
            }
        }
        return key.toString();
    }

    public int size() {
        return byAssetId.size;
    }

    int buildRegionVisits() {
        return buildRegionVisits;
    }
}
