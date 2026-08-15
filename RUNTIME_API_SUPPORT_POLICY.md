# Pixscape Runtime API Support Policy

Pixscape Runtime exposes three API support levels:

```text
HIGH_LEVEL
SUPPORTED_EXPERT
INTERNAL
```

These levels describe which parts of the Runtime are intended for application developers, how much compatibility they provide, and which parts remain free to evolve with the implementation.

Java visibility alone does not define the supported API. A `public` class may still be an internal Runtime detail when public visibility is required by Artemis, serialization, tooling, testing, or other technical constraints.

Support levels describe API compatibility and maintenance intent. They do not define a support SLA or a commercial support commitment.

## 1. API levels

### `HIGH_LEVEL`

`HIGH_LEVEL` is the primary API for normal game development.

It includes `PixscapeEngine`, `PixscapeAPI`, scene loading, entity and asset references, and the domain APIs exposed through `games.pixscape.runtime.api`.

The high-level API is designed to hide Runtime implementation details such as render storage, synchronization systems, dirty tracking, compiler caches, and internal ECS state.

Use this level whenever it provides the functionality you need.

### `SUPPORTED_EXPERT`

`SUPPORTED_EXPERT` provides deliberate access to lower-level Runtime features for experienced LibGDX and Artemis users.

Typical expert use includes:

- custom Artemis systems and ECS access;
- authored component access;
- custom render integration;
- diagnostics and profiling;
- advanced physics integration;
- native Box2D access;
- Tiled and Spatial tooling or queries;
- Runtime loaders, registries, compilers, and authoring tools intended for extension.

These APIs are supported integration points, not accidental implementation leaks.

They may expose stronger lifecycle, ownership, ordering, mutation, or performance constraints than the high-level API. Their documented behavior is part of the contract, but their internal layout may evolve more freely.

### `INTERNAL`

`INTERNAL` covers replaceable Runtime implementation machinery.

This includes, for example:

- synchronization and pipeline systems;
- temporary or derived render storage;
- cache and publication intermediates;
- renderer construction details;
- internal loading and preparation machinery;
- implementation-specific Spatial, Tiled, physics, and rendering helpers.

Some of these types are Java-public for technical or first-party tooling reasons. That does not make them part of the supported compatibility API.

Applications may technically reference them, but such code can require changes on any Runtime upgrade.

## 2. Compatibility

Before Runtime 1.0, breaking changes may still occur in supported APIs while the architecture matures.

Changes to `HIGH_LEVEL` APIs should be kept deliberate, documented, and limited to cases where the design genuinely needs to evolve.

Changes to `SUPPORTED_EXPERT` APIs may occur more often, especially when required by architecture or performance work. When they affect a documented integration point, the change should be called out and migration guidance provided when practical.

`INTERNAL` APIs carry no compatibility guarantee.

After Runtime 1.0:

| Level | Compatibility |
|---|---|
| `HIGH_LEVEL` | Strongest SemVer compatibility. Deprecation should normally precede removal, and unavoidable breaking changes should include migration guidance. |
| `SUPPORTED_EXPERT` | Documented integration behavior is maintained seriously, but structural changes remain possible when justified. |
| `INTERNAL` | No compatibility guarantee. Types may be renamed, moved, changed, or removed between releases. |

Semantic versioning applies to the supported API, not to every Java-public class in the Runtime.

## 3. Main supported boundaries

The following table summarizes the intended API surface. It is not an exhaustive class inventory; Javadoc on individual types or members may define more specific rules.

| Area | Level |
|---|---|
| `PixscapeEngine`, `PixscapeAPI`, scene lifecycle and loading handles | `HIGH_LEVEL` |
| Domain APIs, facades, factories, refs and views under `runtime.api` | `HIGH_LEVEL` |
| `ECSAPI` and direct Artemis World/component/system access exposed by the engine | `SUPPORTED_EXPERT` |
| Custom system and render integration hooks exposed by `PixscapeEngine` | `SUPPORTED_EXPERT` |
| Authored ECS components and authored physics, Tiled and Spatial data | `SUPPORTED_EXPERT` |
| Registries, diagnostics, profiling and supported query/compiler tools | `SUPPORTED_EXPERT` |
| Native Box2D objects obtained through supported Runtime APIs | `SUPPORTED_EXPERT` |
| Supported particle authoring and pooling types | `SUPPORTED_EXPERT` |
| Pipeline synchronization systems, transient caches and derived Runtime storage | `INTERNAL` |
| Renderer construction and implementation-specific render state | `INTERNAL` |
| Resource preparation, cache publication and other engine choreography | `INTERNAL` |

A supported type can contain individual members with a different contract. In particular, a high-level API may expose an expert getter or native object without making the returned implementation high-level.

## 4. ECS and component access

Pixscape intentionally keeps Artemis available as an expert extension layer.

Authored components may be inspected and deliberately modified by expert code. Such changes must respect the documented validation, dirty/invalidation, identity, unit, and lifecycle rules.

Authored state and derived Runtime state are not equivalent.

For example:

- authored transform, animation, physics, Tiled, Spatial, visibility, tint, layer, identity, and similar gameplay state can form part of the expert API;
- runtime bodies, compiled fixture caches, texture-region synchronization state, render caches, and similar derived data remain implementation details.

A component being public does not imply that every field is caller-owned or safe to mutate.

When a high-level facade exists, it remains the preferred API for ordinary gameplay code.

## 5. Expert lifecycle and ownership

Expert APIs may expose borrowed, native, frame-local, or engine-owned objects.

Their Javadocs define the applicable rules, including:

- ownership and mutation rights;
- lifetime;
- rebuild or invalidation behavior;
- required pipeline phase;
- thread expectations;
- dirty or publication requirements;
- borrowed versus copied results;
- allocation or hot-path constraints where relevant.

Borrowed Runtime objects should not be assumed to survive a World, scene, renderer, physics, or other documented rebuild.

Runtime lifecycle methods and built-in systems execute synchronously on the calling thread, normally the LibGDX render thread. Pixscape Runtime does not provide a general thread-safety guarantee.

## 6. Rendering

High-level rendering behavior is accessed through the normal Pixscape APIs.

Advanced rendering remains intentionally extensible. The engine's documented pre/post system hooks, custom submission integration, render queue access, diagnostics, and supported borrowed render state form the expert boundary.

Internal draw-list construction, sorting, synchronization, renderer construction, concrete batching machinery, and other pipeline stages are not compatibility contracts.

Supporting custom rendering does not require freezing the renderer's internal architecture.

## 7. Physics

`PhysicsAPI` is the normal bridge from gameplay code to Runtime physics.

It may return borrowed native Box2D objects such as `World` and `Body`. Using those objects directly is supported expert integration and follows normal Box2D rules together with Pixscape's documented lifecycle constraints.

Authored physics ECS data, supported physics authoring/query services, validation tools, and compiler utilities also belong to the expert surface.

Runtime body components, compiled caches, preparation candidates, and publication machinery remain internal.

Native objects should normally be acquired through supported APIs rather than by coupling application code to Runtime storage components.

## 8. Tiled and Spatial

High-level Tiled and Spatial functionality is exposed through their normal Runtime APIs.

For expert tooling and custom systems, supported surfaces include authored logical data and documented query, compiler, definition, lookup, and diagnostic types.

Derived render storage, synchronization helpers, cache owners, planners, collectors, ordering intermediates, and similar pipeline implementation details remain internal.

Expert mutation of authored data must follow the documented dirty, rebuild, identity, and playback rules.

## 9. Engine lifecycle

Pixscape supports **one active `PixscapeEngine` per application / LibGDX graphics context**.

An application may rebuild the engine's Artemis `World`, load another scene, or otherwise use the supported lifecycle on that engine.

Disposing an engine and later creating another is supported.

Running multiple independent Pixscape engines concurrently against the same LibGDX graphics context is outside the supported contract.

Entity references, typed references, borrowed ECS objects, render state, physics objects, and other derived objects may become stale after the corresponding World, scene, or Runtime rebuild. Reacquire them as documented.

The `PixscapeEngine` Javadoc is the authoritative reference for engine lifecycle details.

## 10. Scene readiness and Runtime Availability

`SceneLoadPhase.READY` is the Runtime resource-readiness boundary.

Before READY, direct scene dependencies and resources declared through Runtime Availability are acquired. Resource types requiring persistent Runtime preparation are also prepared before the scene becomes ready.

After READY, normal gameplay uses prepared resources and does not implicitly load or prepare undeclared scene resources.

Resources intended for dynamic gameplay use must therefore either:

- already be direct scene dependencies; or
- be declared through Runtime Availability.

Platform delivery may itself be asynchronous or progressive. This includes GWT resource delivery. That does not change the READY contract: the scene becomes ready only after the required declared resources have been acquired and prepared.

READY does not mean that every later gameplay operation is free of CPU or GPU work. Operations such as prefab instantiation or deserialization still perform their normal runtime work.

First-party authoring tools such as Pixscape Studio may explicitly invalidate and rebuild prepared state after an authoring change. That behavior is separate from normal gameplay resource loading.

## 11. Choosing an API level

Use the highest-level API that meets the requirement.

Move into `SUPPORTED_EXPERT` when direct ECS, LibGDX, Box2D, rendering, tooling, diagnostics, or other lower-level integration provides a real benefit.

Avoid dependencies on `INTERNAL` types in application code.

Pixscape deliberately keeps powerful expert access available without turning every implementation detail into a permanent compatibility obligation.