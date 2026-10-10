# Pixscape JSON Schema set

This directory is the source of the planned public schema files. Run
`python schemas/generate.py` from the Runtime repository root to regenerate the
JSON artifacts deterministically. The site copies only `*.schema.json` from
this tree; do not edit the site's copies. Each `$id` matches its future path
under `https://pixscape.games/schemas/pixscape/`, and `validate.mjs` compiles
every reference from local files without an HTTP request.
`python schemas/generate.py --check` verifies that the committed JSON files
match the generator without changing them.

## Current format documents

| File | In-file format version | Scope |
| --- | --- | --- |
| `project/1.schema.json` | project `"1"` | Export entry point and scene metadata |
| `scene/3.schema.json` | scene `3` | Artemis envelope; its observed `metadata.version: 1` is a distinct serializer marker |
| `components/3.schema.json` | scene `3` | 46 supported Runtime component type IDs, including surface lighting and non-pooled shader parameters |
| `common/1.schema.json` | shared definitions `1` | LibGDX arrays, properties, physics and Spatial payloads |
| `game-object/3.schema.json` | Game Object `3` | Independent definition and local entity/joint IDs |
| `animations/unversioned.schema.json` | none | Sprite animation registry; no version field is emitted |
| `tiled-animations/1.schema.json` | registry `"1"` | Tiled animation frames/durations |
| `tileset-profiles/1.schema.json` | profile `1` | Tileset dimensions, projection and tile IDs |
| `hud-screen/1.schema.json` | HUD screen `1` | Logical descriptor |
| `hud-document/2.schema.json` | HUD document `2` | Recursive Scene2D node/document structure |

These are *file format* numbers. They do not follow the Runtime library's
version. The schemas describe the current Runtime/Studio export, without a
future compatibility promise.

The additive 2.5D lighting fields keep these format versions. Missing light
height/quality/resolution default to `128`, `1` and `512`; the map lighting
plane defaults to `0`. Sprite shadow casting remains opt-in. See
[the geometric and material contract](../docs/lighting-2_5d.md) before adding
local XYZ/UV descriptions or registering maps at different physical altitudes.

## Run the checks

From `schemas/`:

```text
npm ci
npm run validate
npm run validate -- --self-test
npm run validate -- ../examples/minimal-export
npm run validate -- C:/path/to/tiled-iso-demo/assets/pixscape-project
```

The lockfile pins Ajv 8.17.1. The first validator command compiles all ten
Draft 2020-12 schemas and checks local references. With one or more export
directories, it also parses and checks their project, scenes, declared Game
Objects, HUD, registries, tileset profiles, atlas page paths and particle
effect paths. For scene components it resolves each document's
`componentIdentifiers`, expands each entity's archetype and validates explicit
payloads against the matching fully qualified type ID. The nonstandard
`x-pixscape-components` mapping in `components/3.schema.json` provides that
link; the short aliases are **not** global identifiers. The validator also
rejects a Layer/EntityIndex combination inherited entirely from an archetype.
The self-test checks eight representative invalid fixtures, including wrong
versions, a stale Layer field, null primitive, `"NaN"` on an unrelated field,
a plain array in place of `IntArray`, unresolved alias, inherited Layer/content
conflict and a displaced Game Object root.
`SceneLoader.loadScene` now checks the materialized scene in its isolated
validation world before changing the caller's world. This catches
Layer/EntityIndex conflicts inherited from archetypes, including components
whose payload is absent or only partly explicit. Explicit conflicts are also
checked in the serialized scene. The publication validator rejects the same
conflicts independently, and the engine retains its world-level check during
Runtime preparation.

Run the Runtime tests separately for semantic and load evidence:

```text
./gradlew test --tests games.pixscape.runtime.loading.PublishedExportLoadTest
./gradlew test --tests games.pixscape.runtime.loading.SceneJsonToleranceTest
```

The first test loads the included example and, when a sibling
`../tiled-iso-demo` checkout exists, both exported demo scenes plus the demo's
Game Object/HUD documents. It parses the example atlas descriptor without
OpenGL. Studio source data and demo export data are never changed by these
checks.

## Limits and producer guidance

JSON Schema `default` is documentation, not a request to mutate input. A
missing optional value, explicit `null` and an empty object/array have different
effects. The schemas distinguish known nullable fields. The literal `"NaN"`
is accepted for **only** scene `physicsParallaxX/Y` in `project.json`; it is not
a generic JSON number. LibGDX `IntArray`/`ByteArray` use `{ "size": n,
"items": [...] }`, with optional `items` for zero-filled storage; only `size`
entries are active. Computed but currently serialized AABB, oriented bounds,
visibility flags and material fields remain accepted.

Known component fields are typed. A targeted Runtime test confirms that an
unknown `TransformComponent` field is currently ignored, while primitive
`scaleX` and `visible` reject `null`. Isolated `SceneLoader` currently accepts
`AnimationComponent.animationAssetIds = null` and `IntArray.items = null` when
`size = 0`; this is a loader tolerance, **not** a recommendation for producers
or proof that animation playback handles null. The Artemis envelope and
component payload schemas leave unrecognized fields open, but preservation is
not promised; producers should emit only documented fields. Project unknown
fields are ignored by the Runtime's manual reader. Its `version: null` and
`scenesDir: null` normalize to defaults, while `projectFileName: null` is
rejected; these cases are covered by the targeted Runtime test. Game Object
and HUD codecs are stricter. JSON Schema alone cannot establish stable-ID uniqueness and
allocator high-water marks, physical geometry validity, map tuple alignment,
hierarchy cycles, authored property references, HUD widget/resource rules or
rendered appearance. Runtime's loaders/validators cover many of these, and a
running application is needed to test the visual result. This validator checks
the named file dependencies, but is not a substitute for all Runtime asset
loading or a GPU run.

In particular, Runtime Availability declares what a scene may load/spawn; it
does not place entities. A linked Spatial block tile's stored historical
`tileAssetId` is not treated as a missing atlas region without checking the
current map cell, which is what Runtime uses. Scene entity transform defaults
come from components and archetypes; Game Object definition transform fields
have different initial values. The root of a Game Object definition is at
`(0,0)`, has local `zIndex = 0`, and does not carry `layerIndex`.
