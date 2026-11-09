package games.pixscape.runtime.tiled.profile;

import games.pixscape.runtime.property.PropertySet;

/** Serialized definition metadata, owned by its tileset profile. No per-cell values. */
public final class TileDefinitionData {
    public int assetId;
    public int localId = -1;
    public PropertySet properties = new PropertySet();
}
