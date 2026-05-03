# Studio Prefab Instantiation Audit (V1)

## Scope
This audit documents the **current Studio-side prefab V1 pipeline** for capture, serialization, browser lifecycle, drag/drop instantiation, and ECS restoration behavior.

It is intentionally descriptive (no implementation proposals).

---

## End-to-end flow (current behavior)

1. **Create prefab from current selection (Studio context menu)**
    - `StudioContextMenu#createPrefabFromSelection` sanitizes the requested prefab name, grabs selection, captures an `EntityGraph`, saves `.pixprefab`, writes PNG preview, then publishes `EventFlow.PrefabsChanged`.
    - If selection is empty or unsupported, creation is blocked.

2. **Capture selection as `EntityGraph`**
    - `EntityGraphCaptureService#capture` filters unsupported entities, then calls `ClipboardPhysicsJointGraph.filterCopyableSelection(...)` to auto-include valid joints (including wheel/gear with constraints), then snapshots each entity via `GenericEntityInitializer.syncFrom`.

3. **Serialize prefab**
    - `PrefabAssetService#savePrefab` builds a `PrefabAsset` (type `pixscape-prefab`, version `1`) and maps each `EntityGraphEntry` through `PrefabEntityDataMapper#fromGraphEntry`.
    - File is written as UTF-8 JSON (`.pixprefab`).

4. **Generate preview PNG**
    - `PrefabPreviewWriter.writePrefabPreview(...)` renders preview from graph visual data; fallback placeholder is used when graph has no resolvable visuals or render fails.

5. **Asset browser visibility and deletion**
    - `AssetsThumbsView` scans prefabs via `PrefabBrowserService`, loads `*.pixprefab` + preview PNG, supports delete through `PrefabBrowserService.deletePrefab`, and refreshes on `EventFlow.PrefabsChanged`.

6. **Drag prefab to scene**
    - `WorldCanvas#handlePrefabDrop` loads prefab graph via `PrefabAssetService#loadPrefab`, computes prefab origin center, rebuilds prefab identity registry, instantiates via `EntityGraphInstantiationService#instantiate(...)`, then selects created entities.

---

## How `PrefabAsset` is loaded

- Loading entry point is `PrefabAssetService#loadPrefab(FileHandle)`.
- JSON is deserialized into `games.pixscape.runtime.prefab.PrefabAsset`.
- Validation gates:
    - asset non-null
    - `asset.type == "pixscape-prefab"`
    - `asset.version == 1`
- Null `entities` list is normalized to empty list.
- Each serialized `PrefabEntityData` is converted into an `EntityGraphEntry` using `PrefabEntityDataMapper#toGraphEntry(world, data)`.
- Result is returned as immutable `EntityGraph`.

**Note:** `PrefabAsset` DTO type lives in `games.pixscape.runtime.prefab` namespace and is shared as data contract; Studio’s loading/writing service wraps it.

---

## How `PrefabEntityDataMapper` converts prefab data

### Graph -> Prefab (`fromGraphEntry`)
- Starts from `GenericEntityInitializer#toSnapshotData(sourceEntityId)`.
- Copies component-presence flags + payload fields into nested prefab DTO sections:
    - transform, entityIndex, meta, identity, visibility
    - bounds flags (`hasAabb`, `hasObb`)
    - dimensions, texture region, render material, asset ref, tint
    - animation (including clip map)
    - shader param float map
    - physics body + fixture array
    - authored polygons (source verts, convex parts, fixture IDs, physical params)
    - joint base + per-joint subtype data (distance/revolute/prismatic/wheel/friction/motor/weld/pulley/gear)

### Prefab -> Graph (`toGraphEntry`)
- Reconstructs a `GenericEntitySnapshotData` from prefab DTO sections.
- Rebuilds animation clips map (`String -> AnimationComponent.Clip`).
- Rebuilds physics authoring polygon structures (`AuthoredPolygonData`, `ConvexPolygonPartData`) by deep-copying arrays.
- Creates `GenericEntityInitializer` and applies snapshot via `applySnapshotData`.
- Returns `EntityGraphEntry(sourceEntityId, initializer)`.

---

## How `GenericEntitySnapshotData` is populated

`GenericEntitySnapshotData` is the in-memory flat snapshot schema used by Studio initializer mapping.

Population path:
1. `EntityGraphCaptureService` calls `GenericEntityInitializer.syncFrom(entityId)`.
2. `GenericEntityInitializer#toSnapshotData(sourceEntityId)` copies all captured fields into `GenericEntitySnapshotData`.
3. `PrefabEntityDataMapper#fromGraphEntry` consumes that snapshot.

Coverage includes transform/layer/meta/identity/visibility, render/sprite material, animation clips, shader floats, physics body/fixtures/authoring polygons, joint base + subtype payloads.

---

## How `GenericEntityInitializer` applies data

- `PrefabEntityDataMapper#toGraphEntry` creates initializer using `new GenericEntityInitializer(world).applySnapshotData(snapshot)`.
- Instantiation later calls `CreateEntityCommand`, which invokes `initializer.init(eid)`.

`GenericEntityInitializer#init` behavior:
- Creates or fills ECS components per `has*` flags.
- Restores visual/material/animation/shader/tint fields.
- Restores physics body + fixture defs + authoring polygons + joint components.
- Triggers dirty tracker for render/physics relevant fields:
    - `dirty.material`, `dirty.color`, `dirty.geometry(...)`, `dirty.physics(..., PhysicsDirtyBits.ALL)` as appropriate.

---

## How `EntityGraph` is created

- `EntityGraph` is an immutable list wrapper around `EntityGraphEntry`.
- Creation path for prefabs:
    - `StudioContextMenu#createPrefabFromSelection`
    - `EntityGraphCaptureService#capture(selection)`
- Capture filtering:
    - Excludes unsupported entity kinds (e.g., lights in this capture path).
    - Includes joints through `ClipboardPhysicsJointGraph` if both joint endpoints are valid selected bodies with fixtures.
    - Gear joints only included when referenced source joints are also accepted.

---

## How `EntityGraphInstantiationService` instantiates entities

`instantiate(graph, activeLayerIndex, dx, dy, commandName)`:

1. For each `EntityGraphEntry`:
    - duplicate initializer
    - override target layer index
    - translate transform by `(dx, dy)`
    - assign fresh stable ID via `identityRegistry.allocateStableId()`
    - wrap in `CreateEntityCommand` with callback storing `source -> created` mapping

2. Execute all create commands as one `CompositeCommand` via `HistoryManager.execute`.

3. Post-pass remap of joint references:
    - for each pasted entity, call `ClipboardPhysicsJointGraph.remapJointReferences(world, pastedId, sourceToCreated)`
    - remaps joint `aEid/bEid`; for gear joints also remaps `joint1Eid/joint2Eid`
    - hard-fails (`IllegalStateException`) if remap preconditions fail.

4. Return `EntityGraphInstantiationResult` with created IDs and source→created map.

---

## How `CreateEntityCommand` participates

- Each prefab entity instantiation is a `CreateEntityCommand`.
- `redo()`:
    - creates ECS entity
    - binds/ensures history ID (`HistoryIdRegistry`)
    - calls `initializer.init(entityId)` to apply full snapshot
    - invokes callback for created ID tracking
- `undo()`:
    - re-snapshots current state via `initializer.syncFrom(entityId)`
    - deletes entity and unbinds history mapping

This means prefab instantiation is fully undoable/redoable through history commands.

---

## Identity and stable ID generation/remapping

- Snapshot apply intentionally resets identity stable ID to `UNASSIGNED` inside `applySnapshotData`.
- Instantiation assigns a **new stable ID** per entity (`identityRegistry.allocateStableId()`) before command execution.
- History identity is separately tracked via `HistoryIdRegistry` inside `CreateEntityCommand`.
- Source entity IDs from prefab are not reused as ECS IDs; they are only keys for post-instantiate remap map.

---

## Joint remapping

- Source joint references are serialized as source entity IDs.
- After entity creation, remap pass translates those IDs to created IDs using `sourceToCreated` map.
- For non-gear joints: remap `PhysicsJointComponent.aEid/bEid`.
- For gear joints: additionally remap `PhysicsGearJointComponent.joint1Eid/joint2Eid`.
- Remap fails when dependencies are missing, invalid, or collapse to same entity.

---

## Transform positioning and offset

- Drop target is converted from screen to world logical coordinates.
- Prefab origin is computed as center of min/max transform positions across entries with transform.
- Instantiation offset is:
    - `dx = dropWorldX - prefabOriginX`
    - `dy = dropWorldY - prefabOriginY`
- Each initializer is translated by `(dx, dy)` before create command.

So prefab placement aligns graph center onto drop point (not first-entity pivot).

---

## Bounds initialization

- Snapshot carries `hasAabb/hasObb` flags from initializer.
- Mapper serializes/deserializes those flags (`boundsFlags`).
- On init, geometry/material/tint dirty triggers force recomputation/sync paths where needed; explicit AABB/OBB component reconstruction is not direct in mapper but represented via flags in snapshot model.

**Risk/unknown:** exact runtime system that consumes `hasAabb/hasObb` flags after prefab instantiate is indirect in this audit (not in prefab service classes themselves).

---

## Dirty flags and render sync triggers

In `GenericEntityInitializer#init`, Studio triggers:
- `dirty.material(e)` after texture region/material/light material-related restoration.
- `dirty.color(e)` after tint/light color restoration.
- `dirty.geometry(e, GeometryDirty.*)` after dimensions/light geometry-affecting restoration.
- `dirty.physics(e, PhysicsDirtyBits.ALL)` when physics body or fixtures restored.

This is the key Studio-side render/physics sync handshake during prefab instantiate.

---

## Physics bodies, fixtures, authored polygons restoration

- Physics body fields restored from snapshot into `PhysicsBodyComponent`.
- Fixtures deep-copied into `PhysicsFixturesComponent`; if empty after restore, default fixture may be injected by initializer.
- Studio authoring polygons restored into `PhysicsAuthoringComponent`, preserving:
    - source polygon vertices/count
    - decomposition metadata/hash/version
    - generated fixture IDs
    - material/filter/sensor params
    - convex decomposition parts

This matches prefab V1 requirement to preserve author polygons and fixture authoring data.

---

## Animation clip preservation

- Capture path copies `AnimationComponent` core playback state plus full `clips` map.
- Mapper serializes each clip by name with start/end frame indices.
- Load path reconstructs same clip map into snapshot/initializer.
- Init writes clips back to `AnimationComponent` during entity creation.

Clip names and ranges are preserved across save/load/instantiate.

---

## Studio-only parts that runtime should not reuse directly

The following are Studio/editor implementation details and should be treated as non-runtime APIs:

- `GenericEntityInitializer`, `GenericEntitySnapshotData`, and history command orchestration (`CreateEntityCommand`, `CompositeCommand`, `HistoryManager`) are editor undo/redo machinery.
- `ClipboardPhysicsJointGraph` selection heuristics/remap utility is Studio clipboard/paste logic.
- `PhysicsAuthoringComponent`, `AuthoredPolygonData`, `ConvexPolygonPartData` are authoring/editor data structures.
- `PrefabPreviewWriter`, `PrefabBrowserService`, `AssetsThumbsView`, context-menu + drag/drop UI flow are editor UX systems.
- Event-driven refresh via `EventFlow.PrefabsChanged` is Studio UI refresh contract.

Runtime may consume prefab DTO content contract, but should not depend on Studio history/UI/editor-specific services.

---

## Risks / unknowns found

1. **Prefab DTO source location not in Studio package tree**  
   `PrefabAsset` DTO is referenced from `games.pixscape.runtime.prefab`; in this repository snapshot, its source file is not under Studio-prefab package path. Contract visibility/versioning should be verified before runtime-side parity work.

2. **Bounds flags are persisted but downstream consumer is indirect**  
   `hasAabb/hasObb` are serialized, but concrete recomputation/usage path is not in prefab service layer itself; runtime parity should confirm equivalent post-init bounds behavior.

3. **Strict remap failure behavior**  
   Joint remap throws hard failure if mapping preconditions fail; runtime comparison should explicitly decide whether to match this fail-fast behavior or degrade gracefully.

4. **Identity semantics are dual-layered**  
   Stable IDs are reallocated at paste-time while history IDs are managed separately. Runtime comparisons must avoid conflating editor history identity with runtime entity identity.
