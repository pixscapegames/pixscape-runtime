package games.pixscape.runtime.api;

import com.artemis.World;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.TileDefinitionData;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class TilePropertiesApiTest {
    @Test public void sparseLocalIdsAreScopedAndDefinitionsAreSharedReadOnlySnapshots() throws Exception {
        World world = new World();
        try {
            PixscapeEngine engine = new PixscapeEngine();
            Field field = PixscapeEngine.class.getDeclaredField("world");
            field.setAccessible(true);
            field.set(engine, world);
            PropertySet authored = new PropertySet().putString("kind", "water").putBoolean("solid", false)
                    .putInt("cost", 3).putFloat("speed", 0.5f)
                    .put("file", PropertyValue.ofImported("file", "", "<property type=\"file\" value=\"../wave.txt\"/>"));
            RuntimeTilesetProfile first = profile(10, 101, 7, authored);
            engine.getTilesetProfiles().add(first);
            engine.getTilesetProfiles().add(profile(20, 201, 7, new PropertySet().putString("kind", "stone")));
            TiledAPI api = engine.api().tiled();
            TileDefinition water = api.tile(10, 7);
            assertEquals(101, water.assetId());
            assertEquals(7, water.localId());
            assertEquals(10, water.tilesetId());
            assertEquals("stone", api.tile(20, 7).properties().getString("kind", ""));
            assertNull(api.tile(10, 0));
            assertNull(api.tile(10, 101));
            assertNull(api.tile(99, 7));
            assertEquals(9, water.properties().getInt("missing", 9));
            assertNull(water.properties().typeOf("missing"));
            assertNull(water.properties().getImportedValue("missing"));
            assertEquals(PropertyType.IMPORTED, water.properties().typeOf("file"));
            assertEquals("file", water.properties().getImportedValue("file").typeName());
            assertThrows(IllegalStateException.class, () -> water.properties().getInt("kind", 0));
            assertFalse(PropertySet.class.isInstance(water.properties()));
            authored.putString("kind", "mutated");
            assertEquals("water", water.properties().getString("kind", ""));

            int entity = world.create();
            TiledLayerComponent map = world.getMapper(TiledLayerComponent.class).create(entity);
            map.projection = TiledProjection.ORTHO;
            map.mapWidthCells = 4; map.mapHeightCells = 2;
            map.tileWidth = 16; map.tileHeight = 16; map.chunkSize = 8;
            map.data = map.createMapData();
            map.data.setTile(0, 0, 101);
            map.data.setTile(1, 0, 101);
            TileAnimationDefData animation = new TileAnimationDefData();
            animation.id = 301; animation.name = "waves"; animation.baseTileAssetId = 101;
            animation.frameAssetIds = new int[]{201}; animation.frameDurationsMs = new int[]{100};
            engine.getAnimatedTileRegistry().put(animation);
            map.data.setTile(2, 0, 301);
            world.process();
            TileEditFacade cells = api.ofEntityId(entity).tiles();
            assertSame(water, cells.definition(0, 0));
            assertSame(water, cells.definition(1, 0));
            assertSame(water, cells.definition(2, 0)); // Base definition, never the displayed frame.
            assertNull(cells.definition(3, 0));
            assertNull(cells.definition(-1, 0));
            world.delete(entity); world.process();
            assertNull(cells.definition(0, 0));
            engine.getTilesetProfiles().clear();
            assertNull(api.tile(10, 7));
            assertEquals("water", water.properties().getString("kind", ""));
        } finally { world.dispose(); }
    }

    private static RuntimeTilesetProfile profile(int tileset, int asset, int local, PropertySet properties) {
        RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
        profile.tilesetId = tileset; profile.tileAssetIds = new int[]{asset};
        TileDefinitionData tile = new TileDefinitionData();
        tile.assetId = asset; tile.localId = local; tile.properties = properties;
        profile.tiles.add(tile);
        return profile;
    }
}
