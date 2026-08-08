# Pixscape Runtime API Support Policy

Policy date: 2026-08-08

## 1. Executive policy

Pixscape Runtime exposes three support levels:

```text
HIGH_LEVEL
SUPPORTED_EXPERT
INTERNAL
```

The high-level API is the normal gameplay surface. The supported expert API deliberately preserves
Artemis, LibGDX, Box2D, rendering, diagnostics, loading, and tooling escape hatches. Internal
implementation types remain free to evolve even when Java visibility makes them technically
accessible.

Java `public` is therefore necessary for both supported layers but is not, by itself, a support or
compatibility promise. Classification follows intended use, lifecycle contract, and sustainable
extension value rather than package name or technical depth.

The policy objective is:

```text
simple high-level API
+ deliberately powerful expert API
+ implementation freedom underneath
```

## 2. The three API levels

### `HIGH_LEVEL`

The high-level API is intended for ordinary game development. It is discoverable from
`PixscapeEngine` and `PixscapeAPI`, uses facades/factories/refs, and has documented identity,
absence, failure, ownership, and mutation behavior. Ordinary use should not require knowledge of
component aspects, dirty bits, render slots, SOA stores, compiler caches, or system order.

This level includes the engine's normal lifecycle and the domain APIs under
`games.pixscape.runtime.api`, except the explicitly expert `ECSAPI` and the internal
`PixscapeApiImpl`. Some high-level types contain expert overloads or getters; those members inherit
the expert contract stated in their own Javadocs rather than promoting their returned internals to
high-level status.

### `SUPPORTED_EXPERT`

The supported expert API is intended for experienced LibGDX/Artemis users, custom renderers,
tooling, diagnostics, and advanced gameplay systems. It is intentionally public and supported. It
may expose authored ECS layout, borrowed native/derived objects, phase ordering, allocation
constraints, or stronger preconditions.

Expert support is not “use at your own risk.” Pixscape treats these integration points seriously,
but preserves less structural compatibility than for high-level facades. Lifecycle, ownership,
authored-versus-derived authority, and observable integration semantics are the contract; every
mutable field or implementation class is not automatically frozen.

### `INTERNAL`

Internal implementation types express pipeline choreography, transient caches, atomic-publication
candidates, renderer construction, synchronization bookkeeping, or other replaceable machinery.
They may remain Java-public for Artemis, serialization, testing, first-party tooling, or package
access reasons, but they are not consumer compatibility contracts.

External code can technically reference them; such use is outside the supported API and may require
changes on any Runtime upgrade. `INTERNAL` is a support classification, not an instruction to change
visibility in this policy pass.

## 3. Stability and compatibility expectations

Before Runtime 1.0, all three levels may receive deliberate breaking changes. High-level changes
should already be requirement-driven, documented, and tested; expert changes should identify
lifecycle or migration impact; internal changes need no external compatibility shim.

After 1.0:

| Level | Compatibility expectation |
|---|---|
| `HIGH_LEVEL` | Strongest SemVer surface. Preserve source and documented behavior whenever reasonably possible. Normally deprecate before removal and provide migration guidance for unavoidable breaks. |
| `SUPPORTED_EXPERT` | Compatibility is taken seriously, especially lifecycle, ownership, phase, and integration behavior. Architecture- or performance-driven breaks may occur, but must be intentional, called out in release notes, and accompanied by practical migration guidance when feasible. Exact mutable layouts receive a weaker promise than named expert boundaries. |
| `INTERNAL` | Excluded from compatibility guarantees. Types may be renamed, moved, reshaped, or removed between releases, regardless of Java visibility. |

SemVer should describe supported API, not every accessible class file. A change to an internal type
is not automatically a public API break; a silent break to a supported expert integration point is
not automatically acceptable merely because the type is low-level.

## 4. Current public surface classification

The following inventory groups types only where they share a real policy. Member-level exceptions
are explicit.

| Type / package / family | Current visibility | Support level | Why | Compatibility expectation | Action before 1.0 |
|---|---|---|---|---|---|
| `PixscapeEngine`, `PixscapeAPI` | Public | `HIGH_LEVEL` | Primary lifecycle and gameplay entry points | Strongest | Link this policy; document one-engine rule on `PixscapeEngine` |
| Domain APIs, refs, facades, and views in `runtime.api` (`Assets`, `Entities`, sprites, animations, particles, prefabs, Tiled, Spatial, physics, transform, order, shader, light) | Public | `HIGH_LEVEL` | Normal safe gameplay path | Strongest | Keep facade/failure rules as design policy |
| `PlatformTarget`, `SpawnResult` | Public | `HIGH_LEVEL` | Normal configuration/result values | Strongest | Normal Javadoc maintenance |
| `ECSAPI`; `PixscapeEngine.getWorld/mapper/system` | Public | `SUPPORTED_EXPERT` | Canonical Artemis escape hatch for custom systems and components | Serious expert compatibility | Add one concise expert-boundary reference |
| `PixscapeEngine` pre/post customizers and submit-system supplier | Public | `SUPPORTED_EXPERT` | Deliberate phase and submission integration points | Preserve phase/ownership semantics | Already strong; link from policy/docs |
| `PixscapeApiImpl` | Public | `INTERNAL` | Engine-owned facade implementation; consumers should use cached interfaces | None | Add internal-status Javadoc; consider visibility separately after consumer check |
| `RuntimeConfig`, `SceneMetaRuntime` | Public | `SUPPORTED_EXPERT` | Mutable lifecycle/authored configuration needed by tools and advanced bootstrap | Preserve schema meaning more strongly than layout | Document mutation/build timing |
| `RuntimeProjectIO`, `SceneLoader`, public prefab/animation DTOs and loader results | Public | `SUPPORTED_EXPERT` | Legitimate import/export and first-party/third-party tooling surface | Intentional changes with migration | Separate DTO/schema guarantees from loader implementation |
| `WorldConfigFactory`, `RuntimeSceneAtlasLoader`, `RuntimePrefabFragmentSpawner` | Public | `INTERNAL` | Engine bootstrap/loading choreography now has supported engine hooks/facades | None | Mark internal; do not move yet |
| `IdentityRegistry`, `TagRegistry`, animation/tile-animation registries, `AtlasRuntimeService` and its binding/index metadata, `ShaderRegistry` | Public and exposed by engine | `SUPPORTED_EXPERT` | Legitimate indexed lookup, tooling, and custom-render integration | Preserve lookup/lifecycle contracts; layouts may evolve | Strengthen borrowed/rebuild/thread Javadocs |
| `TextureRegistry`, `ShaderSourcePreprocessor`, atlas index builders | Public | `INTERNAL` | Resource-loading implementation rather than the supported lookup boundary | None | Mark internal after checking first-party tooling |
| Profiling package and `RenderStats`/`RenderStatsSink` | Public | `SUPPORTED_EXPERT` | Deliberate diagnostics and profiling extension surface | Preserve metric meaning where documented | Document reset/lifetime/thread behavior |
| `PhysicsService` authoring/query operations | Public | `SUPPORTED_EXPERT` | Complete advanced ECS physics authoring/tooling service | Preserve authored semantics and atomicity | Identify its internal preparation/publication members in Javadoc |
| `Box2dWorldService`, `Box2dSyncSystem` | Public and exposed by engine | `SUPPORTED_EXPERT` | Intentional low-level physics lifecycle/configuration access | Preserve lifecycle and rebuild contract | Cross-reference `PhysicsAPI`; document borrowing/ownership |
| `PhysicsAPI` borrowed Box2D `World`/`Body` results | Public bridge to native types | `HIGH_LEVEL` bridge; native API is standard expert use | Safe acquisition without hiding LibGDX/Box2D power | Preserve null/lifetime semantics | No wrapper hierarchy needed |
| Physics authored data, validators, polygon tools, and compiler results | Public | `SUPPORTED_EXPERT` | Legitimate custom authoring, validation, import, and diagnostics | Intentional changes with migration | Document authored units and ownership |
| Prepared physics candidates and cache publishers | Public | `INTERNAL` | Atomic-publication transaction intermediates | None | Mark internal; visibility change only in later task |
| Tiled logical data, chunks, flags/packing helpers, animation definitions/lookup, and tileset profiles | Public | `SUPPORTED_EXPERT` | Advanced map tooling, queries, import, and runtime extensions | Preserve logical semantics; storage layout may evolve | Document mutation/dirty and frame/rebuild rules |
| Tiled animation resolver/state-sync helpers | Public | `INTERNAL` | Playback/synchronization implementation behind facades and definitions | None | Mark internal |
| Spatial authored data, query service/results, `SpatialStructureCompiler`, `CompiledSpatialStructure`, and stable geometry diagnostics | Public | `SUPPORTED_EXPERT` | Useful advanced Spatial queries and Studio/tooling compilation without a fork | Preserve authored/compiled semantics; migration for structural breaks | Document allocation and immutable/borrowed result contracts |
| Spatial cache owners, planners, collectors, composers, frame builders, ordering kernels, and runtime registries | Public | `INTERNAL` | Replaceable implementation-specific pipeline intermediates | None | Mark obvious families internal; retain compiled outputs/query services |
| `ParticleEffect`, `ParticleEmitter`, `ParticleEffectPool` | Public | `SUPPORTED_EXPERT` | Deliberate LibGDX-compatible effect authoring/tooling surface used by Runtime and Studio extraction | Preserve `.p`, pooling, and documented borrowed-storage semantics; structural evolution remains possible | Keep fork provenance and expert ownership constraints explicit |
| Pure public geometry/color/parallax/tile helpers | Public | `SUPPORTED_EXPERT` when domain-neutral | Useful allocation-aware building blocks for tools/custom systems | Preserve documented calculation semantics | Add docs only where ownership/units are unclear |
| `RuntimeFs` and engine-specific construction helpers | Public | `INTERNAL` | Loading/bootstrap convenience rather than a durable integration boundary | None | Mark internal if no public tool contract is declared |

## 5. Components classification

Artemis support requires a deliberate authored-component expert layer. Component support is based
on authority, not merely on the `component` package.

| Component family | Classification | Contract |
|---|---|---|
| Authored gameplay/render state: `TransformComponent`, `DimensionsComponent`, `AssetRefComponent`, `AnimationComponent`, particle emitter/overrides, visibility, tint, repeat, layer/index/parallax, identity/tag, light components | `SUPPORTED_EXPERT` | Experts may inspect and deliberately mutate authored state, then follow required validation and dirty/invalidation rules. High-level facades remain preferred for ordinary use. |
| Authored physics body/shapes/joint components and their authored data | `SUPPORTED_EXPERT` | ECS is authoritative. Mutations must use valid IDs/units and trigger the documented rebuild/dirty lifecycle. Native objects are derived. |
| Authored Spatial blocks/shapes/height components | `SUPPORTED_EXPERT` | Authored data is authoritative; compiled faces, footprints, and ordering are derived. Atomic publication and identity rules apply. |
| `TiledLayerComponent` and its logical `TiledMapLayerData` | `SUPPORTED_EXPERT` | Advanced tools may inspect/mutate logical map state while preserving chunk dirty and registry contracts. |
| `RenderMaterialComponent` | `SUPPORTED_EXPERT`, mixed authority | Shader/blend selection is authored expert state; transient texture/debug bindings are derived and caller read/write compatibility is not promised. |
| `AABBComponent`, `OrientedBoundsComponent` | `SUPPORTED_EXPERT` read/diagnostic view | Bounds are useful to custom systems, but engine systems own derivation. Direct writes are phase-sensitive and unsupported unless a documented extension phase says otherwise. |
| `TextureRegionComponent`, `PhysicsCompiledFixturesComponent`, runtime body/joint components, `SpatialPhysicsFootprintComponent` | `INTERNAL` | These are transient synchronization/cache storage. Use `AssetRegionRef`, `PhysicsAPI`, native bridges, stable compiler results, or frame queues instead. |

Support for an authored component does not freeze every public field forever. Its authored meaning,
serialization role, units, identity rules, and mutation propagation are the expert contract.
Derived fields embedded in an otherwise supported component must be explicitly labeled.

## 6. Systems classification

Public Artemis visibility does not make every core system an extension point.

| System / family | Support level | Reason |
|---|---|---|
| User-supplied `BaseSystem` instances registered through pre/post hooks | `SUPPORTED_EXPERT` | Deliberate custom-system integration contract |
| `DirtyTrackerSystem` and its dirty/submask constants | `SUPPORTED_EXPERT` | Required propagation bridge for expert authored ECS mutation |
| `Box2dSyncSystem` | `SUPPORTED_EXPERT` | Intentionally exposed physics enable/step/rebuild coordination |
| `SpatialRenderOrderSystem` public query/diagnostic methods | `SUPPORTED_EXPERT` | Provides effective participation and ordering diagnostics; construction remains pipeline-owned |
| `PhysicsMouseDragSystem` | `SUPPORTED_EXPERT` | Optional, configurable gameplay/tooling integration used by the demo |
| Animation, geometry, layer-build, culling, parallax, Tiled/VFX/sprite sync, Spatial-footprint sync | `INTERNAL` | Core authored-to-derived synchronization choreography |
| Draw-list build/sort, queue extraction, default `RenderSubmitSystem`, `DirtyFlushSystem` | `INTERNAL` | Replaceable pipeline stages; the supported boundary is hooks plus `FrameRenderQueue`/custom submission |

System lookup remains technically available for all Artemis systems. For internal systems, lookup is
useful for Runtime tests and first-party diagnostics but does not promise constructor, order,
enable/disable, or state compatibility. The authoritative built-in order remains an architecture
contract for Runtime maintainers; it does not turn every stage into consumer API.

## 7. Render / Physics / Tiled / Spatial expert boundaries

**Render.** `setPreRenderSystemCustomizer(...)`, `setPostRenderSystemCustomizer(...)`, and
`setRenderSubmitSystemSupplier(...)` are supported expert integration points. `FrameRenderQueue`,
source-domain/kind metadata, `MetricsBatch`, render stats, profiling, `DynamicEntityRenderState`,
`VfxRenderState`, and `LayerStateSOA` are supported borrowed expert objects with their documented
phase and rebuild constraints. The queue is the standard custom-submission contract. `DrawList`,
`TiledMapRenderState`, concrete mesh batches/factories, `RenderContext`, `InternalTextures`, and the
individual build/sort/extract/submit implementations remain internal; exposing one supported queue
does not freeze the renderer underneath it.

**Physics.** `PhysicsAPI` is the high-level bridge and its Box2D `World`/`Body` values are borrowed
native objects. Authored physics ECS, `PhysicsService`, `Box2dWorldService`, `Box2dSyncSystem`, shape
identity/validation/compiler utilities, and polygon tooling are supported expert surfaces. Runtime
native components, compiled-cache components, candidate containers, and cache publishers are
internal. Experts should acquire native objects through `PhysicsAPI` rather than couple to runtime
component storage.

**Tiled.** Facades are high-level. Logical map data, chunks, flags, animation definitions/lookups,
and tileset profiles are supported expert tooling/query surfaces. Chunk mutation must honor dirty
and playback rules. Render-state SOAs and synchronization helpers are internal; custom rendering
consumes `FrameRenderQueue` instead of raw tiled render storage.

**Spatial.** Facades are high-level. Authored blocks/shapes/volumes, `SpatialQueryService`, stable
query/result types, the deterministic structure compiler, compiled structures, and their indexed
diagnostics are supported expert surfaces. Compiled footprints, layer cache owners, broad-phase
grids, planners, actor collectors/sorters, snapshot builders, relation solvers used only by the
pipeline, and draw-list composers are internal. This preserves advanced Spatial work without
freezing every optimization intermediate.

## 8. Lifecycle and one-engine contract

Pixscape supports **one active `PixscapeEngine` per application / LibGDX graphics context**. A
Runtime application may rebuild the engine's Artemis `World`, load another scene, or otherwise use
the supported lifecycle on that engine. World replacement is supported: entity and typed refs are
bound to World/entity generations and become stale safely.

Running multiple independent engines simultaneously against the same LibGDX graphics context is
outside the supported contract. Static/shared shader, texture, GL, and application services are not
designed as isolated per-engine namespaces. This policy does not forbid disposing one engine and
later creating another; it does not promise concurrent isolation or make cross-engine refs valid.

The authoritative wording should live in `PixscapeEngine` class Javadoc. This policy and README
should link to it rather than duplicate lifecycle details. No static-service redesign is justified
for unsupported simultaneous engines.

Borrowed engine-owned objects must be reacquired after documented scene/Runtime rebuilds when their
Javadocs require it. Runtime APIs and built-in systems execute synchronously on the thread calling
the lifecycle methods, normally the LibGDX render thread; no general thread-safety guarantee exists.

## 9. External consumer evidence

The current `tiled-iso-demo` uses the intended layering:

| Direct usage | Classification | Assessment |
|---|---|---|
| `PixscapeEngine`, `PixscapeAPI`, entity/animation/particle/physics facades | `HIGH_LEVEL` | Normal gameplay path |
| Native Box2D forces, velocities, queries, contacts reached through `PhysicsAPI` | `SUPPORTED_EXPERT` native integration | Deliberate LibGDX/Box2D use |
| Custom Artemis control/follow/trigger systems and `PixscapeIdentityComponent` mappers | `SUPPORTED_EXPERT` | Legitimate expert ECS gameplay |
| `setPostRenderSystemCustomizer(...)` | `SUPPORTED_EXPERT` | Correct named phase for next-frame gameplay/custom systems |
| `PhysicsMouseDragSystem` and `engine.getLayerState()` | `SUPPORTED_EXPERT` | Optional expert system plus borrowed derived layer state; lifecycle coupling should be documented |

No `tiled-iso-demo` dependency was found on a type classified `INTERNAL`. In particular, ordinary
demo physics no longer maps runtime-body components or accesses synchronization services.

The locally available Studio sources use many supported authored components, services, registries,
loaders, compilers, and diagnostics, confirming real tooling value in the expert layer. They also
reference internal candidates such as prepared physics candidates, Spatial cache owners, concrete
render construction/state types, `InternalTextures`, and sync systems. Studio is first-party
tooling that can co-evolve with Runtime; those dependencies do not create a third-party
compatibility promise, but releases must coordinate them before internal changes land. Some Studio
source hits are historical/stale names, so this policy does not treat every textual import as a
current supported dependency.

## 10. Documentation mechanism

Use the lightest maintainable mechanism:

1. Keep this document as the authoritative support-level policy and link it from README/consumer
   documentation.
2. Put focused Javadoc on `PixscapeEngine`, expert entry methods, and individually important expert
   types. Javadoc is where ownership, phase, lifetime, and invalidation are most useful in an IDE.
3. Add the short internal statement from this policy to obvious public internal types during a
   focused documentation batch.
4. Use `package-info.java` only for genuinely homogeneous packages. Current `api`, `component`,
   `system`, `render`, `service`, `physics`, `spatial`, and `tiled` packages intentionally mix
   levels, so a package-wide label would mislead.

Do not add `@ExpertApi`/`@InternalApi` now. Annotations would repeat a still-maturing per-type table,
provide no enforcement by themselves, and add maintenance noise across a large mixed surface. Add
lightweight Java 8/GWT-compatible annotations later only if release tooling, generated API reports,
or compatibility checks will consume them. Do not perform broad package moves merely to encode
support status.

For internal-but-public types, use this consistent sentence:

> Runtime implementation detail. Public Java visibility does not make this type part of the
> supported compatibility API.

## 11. Recommended pre-1.0 cleanup

| Priority | Action |
|---|---|
| `P0` | None. No correctness blocker was found in the support-policy pass. |
| `P1_BEFORE_1_0` | Publish/link this policy and make `PixscapeEngine` Javadoc the authority for the one-active-engine rule. |
| `P1_BEFORE_1_0` | Add/complete expert Javadocs for ECS/dirty tracking, render hooks and borrowed states, `PhysicsService`/Box2D lifecycle, registries, Tiled mutation, and Spatial compiler/query boundaries. |
| `P1_BEFORE_1_0` | Mark the most obvious public internal types consistently: `PixscapeApiImpl`, render construction/context/internal textures, pipeline systems, prepared physics/cache publishers, Tiled sync helpers, and Spatial cache/planner/composer families. |
| `COMPLETE` | Classify standalone particle effect/emitter/pool types as `SUPPORTED_EXPERT`; preserve `.p`, pooling, and Runtime/Studio extraction contracts. |
| `P1_BEFORE_1_0` | Audit active Studio dependencies before changing internal visibility; coordinate first-party migrations without promoting implementation types to supported API. |
| `P2_BEFORE_1_0` | Reduce `PhysicsMouseDragSystem`'s manual `LayerStateSOA` wiring if a simple self-binding path exists; current expert use remains supported. |
| `P2_BEFORE_1_0` | Add field-level authored/derived notes to mixed components such as `RenderMaterialComponent`. |
| `DEFER` | Support annotations, broad package reorganization, immutable diagnostic snapshots, and visibility changes without a demonstrated compatibility/tooling benefit. |
| `DROP` | Making everything outside `runtime.api` internal; treating every public class as supported; mass package moves; redesigning shared services for simultaneous engines. |

## 12. Consumer-facing policy text

> Pixscape Runtime exposes three API levels.
>
> **`HIGH_LEVEL`** is the primary surface for normal game development. It provides the strongest
> source and behavioral compatibility promise through `PixscapeEngine`, `PixscapeAPI`, and the
> documented domain facades, factories, refs, and lifecycle methods.
>
> **`SUPPORTED_EXPERT`** is intended for advanced LibGDX/Artemis integration, custom rendering,
> tooling, diagnostics, and native Box2D access. These APIs are deliberately public and supported,
> but expose lower-level ownership, phase, lifetime, and performance constraints and may evolve
> more often than the high-level API.
>
> **`INTERNAL`** includes replaceable Runtime machinery. Some internal types remain
> Java-public for technical or first-party integration reasons, but they are not compatibility
> contracts and may change between releases.
>
> High-level convenience does not remove expert access. Use the highest-level API that meets the
> need, and cross into the expert layer deliberately when lower-level control is required.

## 13. Expert API documentation checklist

For each supported expert type, document only the applicable items:

- whether the data is authored, derived, frame-local, native, or diagnostic;
- who owns it, who may mutate it, and who disposes it;
- lifetime and whether it must be reacquired after World/scene/engine rebuild;
- expected thread and pipeline phase/order;
- mutation publication, dirty/invalidation, and failure semantics;
- units, identifier domain, and identity/reuse rules where relevant;
- borrowed versus copied results and retention rules;
- allocation/complexity or hot-path restrictions when material;
- which high-level API should be preferred for ordinary use.

Not every type needs every item. A small precise contract is better than restating implementation
details that are free to change.

## 14. Final recommendations

Adopt the three-level policy now. Treat the high-level facade/failure architecture as complete and
avoid another convenience-API project. Preserve the ECS, authored data, native physics, render
integration, diagnostics, compiler/query, and tooling boundaries as deliberate expert power while
labeling replaceable pipeline machinery as internal support-wise.

The standalone particle implementation trio is `SUPPORTED_EXPERT`: it retains the familiar
LibGDX effect/emitter/pool model for tooling and preview while Pixscape adds its allocation-free
Runtime/Studio extraction bridge. Public mutable storage is borrowed, emitter-owned state rather
than caller-owned persistent data.

Before 1.0, prioritize communication over refactoring: publish this policy, document the one-engine
contract, strengthen the few major expert boundaries, mark obvious internals, and coordinate
first-party Studio dependencies. Do not add annotations or move packages until a concrete tool or
compatibility workflow benefits from them.
