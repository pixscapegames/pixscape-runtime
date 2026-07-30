package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;

import java.util.Comparator;

/**
 * Builds and validates the complete asset index before an atlas is published.
 */
final class AtlasAssetIndexBuilder {

    private static final String ASSET_MARKER = "__a";

    private static final Comparator<TextureAtlas.AtlasRegion> FRAME_ORDER =
            new Comparator<TextureAtlas.AtlasRegion>() {
                @Override
                public int compare(
                        TextureAtlas.AtlasRegion left,
                        TextureAtlas.AtlasRegion right) {
                    if (left.index < right.index) return -1;
                    if (left.index > right.index) return 1;
                    return 0;
                }
            };

    private AtlasAssetIndexBuilder() {
    }

    static AtlasAssetIndex build(String atlasTag, TextureAtlas atlas) {
        if (atlasTag == null || isBlank(atlasTag)) {
            throw new IllegalArgumentException("Atlas tag must not be null or blank.");
        }
        if (atlas == null) {
            throw new IllegalArgumentException(
                    "Atlas '" + atlasTag + "' must not be null.");
        }

        IntMap<MutableAssetGroup> groups = new IntMap<>();
        Array<MutableAssetGroup> orderedGroups = new Array<>();
        Array<TextureAtlas.AtlasRegion> atlasRegions = atlas.getRegions();
        int visits = 0;

        for (int i = 0, n = atlasRegions.size; i < n; i++) {
            visits++;
            TextureAtlas.AtlasRegion region = atlasRegions.get(i);
            if (region == null || region.name == null) continue;

            int marker = region.name.lastIndexOf(ASSET_MARKER);
            if (marker < 0) continue;

            int assetId = parseAssetId(atlasTag, region.name, marker);
            MutableAssetGroup group = groups.get(assetId);
            if (group == null) {
                group = new MutableAssetGroup(assetId, region.name);
                groups.put(assetId, group);
                orderedGroups.add(group);
            } else if (!group.regionGroup.equals(region.name)) {
                throw new IllegalStateException(
                        "Atlas '" + atlasTag + "' uses asset ID " + assetId
                                + " for multiple region groups: '"
                                + group.regionGroup + "' and '" + region.name + "'.");
            }
            group.add(atlasTag, region);
        }

        for (int i = 0, n = orderedGroups.size; i < n; i++) {
            orderedGroups.get(i).validateAndOrder(atlasTag);
        }

        IntMap<AtlasAssetBinding> bindings = new IntMap<>(groups.size);
        for (int i = 0, n = orderedGroups.size; i < n; i++) {
            MutableAssetGroup group = orderedGroups.get(i);
            TextureAtlas.AtlasRegion first = group.regions.first();
            AtlasRuntimeService.CachedRegion cached =
                    new AtlasRuntimeService.CachedRegion(
                            first.name,
                            first.getU(),
                            first.getV(),
                            first.getU2(),
                            first.getV2(),
                            TextureRegistry.handleOf(first.getTexture()),
                            first.getRegionWidth(),
                            first.getRegionHeight());
            AtlasAssetBinding previous = bindings.put(
                    group.assetId,
                    new AtlasAssetBinding(
                            group.assetId,
                            group.regionGroup,
                            first,
                            group.regions,
                            cached));
            if (previous != null) {
                throw new IllegalStateException(
                        "Atlas '" + atlasTag + "' contains a duplicate binding for asset ID "
                                + group.assetId + ".");
            }
        }
        return new AtlasAssetIndex(bindings, visits);
    }

    private static int parseAssetId(String atlasTag, String regionName, int marker) {
        int suffixStart = marker + ASSET_MARKER.length();
        if (suffixStart == regionName.length()) {
            throw invalidAssetId(atlasTag, regionName, "empty suffix");
        }

        int value = 0;
        for (int i = suffixStart; i < regionName.length(); i++) {
            char c = regionName.charAt(i);
            if (c < '0' || c > '9') {
                throw invalidAssetId(atlasTag, regionName, "non-numeric suffix");
            }
            int digit = c - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) {
                throw invalidAssetId(atlasTag, regionName, "integer overflow");
            }
            value = value * 10 + digit;
        }
        if (value <= 0) {
            throw invalidAssetId(atlasTag, regionName, "asset ID must be positive");
        }
        return value;
    }

    private static IllegalStateException invalidAssetId(
            String atlasTag,
            String regionName,
            String reason) {
        return new IllegalStateException(
                "Atlas '" + atlasTag + "' has invalid Pixscape asset suffix in region '"
                        + regionName + "': " + reason + ".");
    }

    private static boolean isBlank(String value) {
        if (value.length() == 0) return true;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) return false;
        }
        return true;
    }

    private static final class MutableAssetGroup {
        final int assetId;
        final String regionGroup;
        final Array<TextureAtlas.AtlasRegion> regions = new Array<>();
        final IntSet frameIndexes = new IntSet();
        int unindexedCount;

        MutableAssetGroup(int assetId, String regionGroup) {
            this.assetId = assetId;
            this.regionGroup = regionGroup;
        }

        void add(String atlasTag, TextureAtlas.AtlasRegion region) {
            if (region.index < -1) {
                throw new IllegalStateException(
                        "Atlas '" + atlasTag + "', asset ID " + assetId
                                + ", group '" + regionGroup
                                + "' has invalid negative frame index " + region.index + ".");
            }
            if (region.index == -1) {
                unindexedCount++;
            } else if (!frameIndexes.add(region.index)) {
                throw new IllegalStateException(
                        "Atlas '" + atlasTag + "', asset ID " + assetId
                                + ", group '" + regionGroup
                                + "' has duplicate frame index " + region.index + ".");
            }
            regions.add(region);
        }

        void validateAndOrder(String atlasTag) {
            if (regions.size > 1 && unindexedCount > 0) {
                throw new IllegalStateException(
                        "Atlas '" + atlasTag + "', asset ID " + assetId
                                + ", group '" + regionGroup
                                + "' mixes indexed and unindexed regions or contains "
                                + "multiple unindexed regions.");
            }
            if (regions.size > 1) {
                regions.sort(FRAME_ORDER);
            }
        }
    }
}
