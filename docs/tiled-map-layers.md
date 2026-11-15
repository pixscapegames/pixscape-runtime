# Tiled maps and their layers

## Ownership and identity

`TiledMapComponent` is the document container. It owns projection, tile size and
map extent. Its `TransformComponent` is the map origin and its `EntityIndexComponent`
places the whole map in a Pixscape scene layer. Maps currently support translation,
not rotation or scaling.

A map reuses the existing composition hierarchy capability (`GameObjectComponent`).
Its direct children carry `TiledMapLayerComponent` (`TILE`, `IMAGE`, or `OBJECT`).
`GameObjectMemberComponent.parentStableId` is the single authored ownership relation;
`EntityIndexComponent.zIndex` is the local composition order. Runtime publishes the
ordered children through the existing hierarchy/composition systems. Stable IDs come
from `IdentityRegistry`, use the scene high-water mark, and never encode order.

Tile layers keep their `TiledLayerComponent`, `TiledLayerData`, chunks and render
references. Their grid bounds and chunk size are local storage configuration. The
map extent describes the document; a layer's existing finite grid bounds remain
independent so importing or converting a grid cannot shift its projection. Projection
and tile-size fields on the tile component are materialized execution values, not
separate authored settings. Studio omits them from attached layers when saving and
loading derives them from the map. The layer transform owns its local offset; its
`originX/Y` component fields are a materialized local cache, while `data.originX/Y`
contains the resolved world origin. Normal Studio saves omit these offset caches too.

An image layer uses its existing sprite entity. An object layer is a composition
node containing the existing object entities. Names use `PixscapeIdentityComponent`;
custom properties stay on their existing component. Visibility inherits through the
map and layer; local parallax uses `LayerParallaxComponent` on the Tiled layer, combined
with scene-layer parallax for display and culling. It does not change physical poses.

Tiled layers, Pixscape scene layers and physical altitude are separate domains.
Reordering changes only local order. Cell references remain qualified by the owning
layer's stable identity and grid coordinates; equal coordinates in two layers have
independent cells and render refs. Spatial blocks, lighting and physics remain on
their tile-layer owner. This does not introduce the spatial-single-map prototype or
cross-layer spatial volumes.

## Composition and persistence

The map contributes one scene composition block. Its ordered children expand into
existing sprite/object output or a tile layer's prepared order. The normal spatial
pass still runs after expansion. Visible tile slices have a frame-local entity index;
it is cleared at the existing visible-output boundary. No cell grids are copied or
sorted per frame. Changed local orders use the existing hierarchy order invalidation.

Scene schema 4 records the map and layer marker components, stable parent relations
and local order through the regular Artemis serializer. Schema 3 is converted in the
isolated load candidate before the live World is cleared: each independent tile grid
gets its own new map, with no grouping heuristics. Existing layer identities, sparse
cells, local origins, custom properties, volumes and links stay on the original entity.
Only new map identities are allocated. The updated schema and high-water mark are
published after successful loading. Subsequent saves use the current model.

The first-stage vocabulary remains: `TiledLayerData`, `TiledLayerRef.layer()`,
`TiledLayerFacade`, `TiledRenderState`, `TiledLayerLightingGeometry`,
`RoundedLayerSupport`, and render-order/visibility methods named for tile layers.
`TiledAPI.ofEntityId/ofStableId` still resolve an individual tile layer.
`EntitiesAPI.findAllByTiledLayerName` returns direct contents of matching Tiled
layers; `findAllByLayerName` retains its separate Pixscape scene-layer meaning.
Name queries use the existing cold index rebuild and invalidation boundary. Runtime
name edits through `IdentityRegistry` invalidate Tiled-name membership; expert raw
name or membership edits must signal `DirtyTrackerSystem.layer(entityId)`.

## Current authoring boundary

Projection and cell size are selected when the map is created/imported. There is no
new map reconfiguration or layer-management UI in this tranche. Layer grid resizing
remains the existing layer operation. Full management of layer insertion, conversion,
reparenting and map-wide grid reconfiguration requires dedicated atomic commands.

## Validation

The final Runtime JVM suite passed all 1,660 tests. Coverage includes mixed
composition, stable identity across local reordering, schema-3 conversion retaining
origins and spatial links, and positive/negative/invalidation checks of layer-name
queries. Existing culling, animation, physics, lighting and Spatial suites remain
in the full run. An earlier allocation-budget assertion reported 208 transient
bytes; its isolated class and the subsequent complete suite both passed without
changing that test or its placement implementation.

The current Runtime compiled in the Studio GWT module and rendered the tiny
mixed-layer export in actual Chrome WebGL2 and Desktop Preview. See the Studio
[validation and fixture instructions](../../pixscape-studio-free/docs/tiled-map-layers.md)
for authoring checks and the generated HTML template refresh boundary.
