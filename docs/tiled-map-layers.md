# Tiled maps and tile layers

The migration starts by naming the existing single-layer execution structures
according to their actual responsibility. This first step does not change the
serialized scene format or introduce a multi-layer map container.

| Previous name | Current name | Responsibility |
| --- | --- | --- |
| `TiledMapLayerData` | `TiledLayerData` | One layer's cells, chunks, animation state and render references |
| `TiledMapRef` | `TiledLayerRef` | Incarnation-safe handle to one tile-layer entity |
| `TiledMapFacade` / `ref.map()` | `TiledLayerFacade` / `ref.layer()` | Operations on that layer's existing grid |
| `TiledMapRenderState` | `TiledRenderState` | Shared render storage for all tile layers in the World |
| `prepareOrderForMap`, `orderForMap`, `removeOrderForMap` | `prepareOrderForLayer`, `orderForLayer`, `removeOrderForLayer` | Prepared order indexed by tile-layer entity |
| `visibleMap*`, `addVisibleMap`, `addPreparedVisibleMap` | `visibleTileLayer*`, `addVisibleTileLayer`, `addPreparedVisibleTileLayer` | Visible slices belonging to individual tile layers |
| `MapLightingGeometry` | `TiledLayerLightingGeometry` | Prepared lighting receivers and casters for one layer |
| `RoundedMapSupport` | `RoundedLayerSupport` | Prepared rounded-wall support for one layer |
| `TiledLayerComponent.createMapData()` | `createLayerData()` | Materialization of one layer's existing dense grid |
| `TiledLayerComponent.validateMapConfiguration()` | `validateGridConfiguration()` | Validation of grid dimensions and projection |

`TiledLayerComponent` already names a tile layer and keeps its name. Normal maps,
collection maps, TMX map documents, and the Studio's existing map authoring
workflow have distinct responsibilities and are not renamed by a blanket
replacement of the word "map".

The Java API rename is intentional: callers use `TiledLayerRef.layer()`.
`TiledAPI.ofEntityId` and `ofStableId` currently resolve an individual tile layer.
They do not resolve an aggregate map or choose a layer by collection position.
Existing stale-handle, collision, painting and animation behavior is retained.

## Model integration still required

The aggregate model must distinguish three independent concepts:

- A Pixscape scene layer owns scene composition and parallax.
- A Tiled map owns common grid configuration and an ordered collection of tile
  layers. Each tile layer keeps a stable identity, name, properties and cells.
- Physical altitude describes spatial geometry. It is never inferred from a
  tile layer's position in the graphical order.

The existing chunks and buffers remain the only cell representation. Persistent
cell references must qualify coordinates with stable layer identity, rather than
an index into the ordered layer collection. Reordering must retain identity and
must not move cells between grids.

Map aggregation, TMX grouping, common-configuration ownership, explicit one-map-
per-existing-grid conversion, aggregate persistence and authoring commands are
not implemented by the naming step. Existing independent maps must never be
combined by matching dimensions, coordinates, names or altitude.

The agreed aggregate includes tile, image and object layers in the TMX order.
Image and object layers retain their current components and tools. They may
occur between tile layers, so they must participate in the same ordered map
composition. They must not be extracted outside a contiguous tile-only group.
