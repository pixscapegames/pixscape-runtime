# Runtime maintenance scans — 2026-11-08

## Scope and result

Baseline: `develop` / release 0.2.4, commit `cc404e1a1a190bfb5960b8246a9020f8aae67631`.
Reviewed the 25 systems under `src/main/java/games/pixscape/runtime/system`, their
frame entry points, and relevant cache, registry, API and lifecycle helpers.
This is a source-level audit of recurring maintenance, with executable operation-count
regressions for Box2D. It is not a CPU/GPU benchmark of every subsystem.

Implemented: remove Box2D's unconditional body-existence/cache checks and whole-world
native-body search during removal. Other findings below are audited follow-up work,
not changes hidden inside this physics patch.

The reported post-spawn `body == null` is a separate issue. This change preserves the
existing synchronization phase and does not promise synchronous native bodies at spawn.
It neither confirms the user's configuration nor establishes the cause of that report.

## Why the old Box2D scans existed

`git blame` / `git log -L` trace the two unconditional reconciliation loops to
`f382189635212f33de41e1e34f31f9c2a75b2c36` (2026-03-11), **Initial standalone runtime extraction**.
That commit contains no design justification for their per-frame frequency. The available
history cannot establish who originally chose the algorithm or what their intent was.

Their observable function was recovery: after processing explicitly dirty entities, one
loop validated every authored body's compiled cache and created missing native bodies;
the other removed native bodies whose authored definition/transform/collision eligibility
had disappeared. This covered component changes even when no dirty mark was supplied.
Deleting the loops without adding membership notifications would lose that behavior.

Subsequent changes added important constraints around those loops:

| Commit | Constraint to preserve |
| --- | --- |
| `2dd17e8` | Prepared compiled fixtures are required; frame processing must not compile shapes. |
| `91560d4` | A valid empty compiled fixture cache does not materialize a native body. |
| `920a47b` | Authored body-component presence determines whether a body is wanted. |
| `334fa04` | Tiled collision enablement gates native body existence. |
| `c762476` | Body removal must preserve indexed joint/gear dependency cleanup. |

Existing `PhysicsStableFrameTest` checked compilation and rebuild counts, but not eligibility
or cache-validation counts. It therefore passed while both scans still ran on every frame.
This was a coverage gap: avoiding rebuilds alone does not prove the absence of global polling.

## Implemented lifecycle

Source: [Box2dSyncSystem](../../src/main/java/games/pixscape/runtime/system/Box2dSyncSystem.java).

1. Subscribe to authored body+transform membership, compiled-cache membership and Tiled
   component membership. Deduplicate affected entity IDs in a retained notification queue.
2. At the existing physics synchronization phase, transfer those IDs to the existing physics
   dirty queue; reconcile each affected entity. Existing field setters/services already mark
   physics dirty. A bare World without a dirty tracker can reconcile structural notifications
   directly, as its previous existence scan did.
3. Keep full reconciliation at initial preparation, explicit re-enable and service rebind.
   Notifications survive disabled frames. This is not a recurring render-frame operation.
4. Retain native body ownership in `IntMap<Body>` by entity ID. Delete through that index even
   when Artemis has removed/reset the pooled runtime component. No `World.getBodies` fallback.
   The index is updated only after successful atomic body replacement and cleared on rebind.
5. Preserve Box2D stepping, bootstrap stepping, fixture provenance, atomic replacement,
   joint dependency invalidation and authoring/runtime pose authority.

The live-membership guard uses the `Aspect.all()` subscription bitset. Artemis's entity
manager may still report an entity active while removal notifications are delivered;
using only `EntityManager.isActive` can recreate a body for a deleted entity. The regression
test includes deletion and subsequent entity-ID reuse.

| Transition | Mechanism / preserved behavior |
| --- | --- |
| Scene load / physics bind | One full reconciliation; compiled-cache validation retained. |
| Game Object or expert ECS insertion after bootstrap | Subscription notification, then existing physics phase. |
| Remove or restore Transform / PhysicsBody | Membership notification destroys or recreates only the affected body. |
| Add/remove compiled component | Membership notification; missing/unprepared cache still fails explicitly. |
| Publish new compiled data in place | Publisher's caller must mark physics dirty, as for other authored edits. |
| Empty compiled fixtures | No native body; existing contract tests retained. |
| Add/remove Tiled component | Membership notification reevaluates collision eligibility. |
| Toggle Tiled collisions | Existing `TiledMapFacade.setCollisionEnabled` marks physics dirty. |
| Delete entity or remove pooled runtime component | Ownership index destroys the old native body without a world search. |
| Delete before runtime subscription observes the body | Whole-entity removal listener also releases indexed ownership. |
| Remove runtime component while authored body remains | Reconcile and recreate; never leave an extra native body behind. |
| Disabled frames and re-enable | Retained notifications plus explicit full reconciliation. |
| Failed body/fixture/joint operation | Existing atomic replacement and dirty acknowledgement behavior retained. |
| Scene/world teardown | Existing engine teardown contract retained; engine lifecycle tests pass. |

Normal stable runtime body reconciliation is now O(1) empty-queue work, rather than O(B)
eligibility/cache checks. An edit costs work for affected bodies plus their actual joint
dependencies and fixture construction. The ownership index uses O(B) retained references.
This statement does not include Box2D's simulation or required pose writeback.

In-place expert mutations must signal invalidation. A previous scan could accidentally notice
some unannounced changes (for example a Tiled collision flag), but did not reliably update all
authored fields or rebuild existing fixtures. The support policy and system Javadoc now state
the explicit dirty requirement. Internal native/runtime component fields are not caller-owned.

## Validation

New native integration tests in
[Box2dBodyLifecycleTest](../../src/test/java/games/pixscape/runtime/system/Box2dBodyLifecycleTest.java)
cover late insertion, component removal/restoration, isolated edits, Tiled collision changes,
disabled frames, deletion/reuse, runtime component removal and retry after missing compiled data.
[PhysicsStableFrameTest](../../src/test/java/games/pixscape/runtime/system/PhysicsStableFrameTest.java)
now also asserts zero eligibility and cache checks over 1,000 unchanged frames.

| Experiment | Baseline with counters only | Corrected implementation |
| --- | --- | --- |
| 1,001 bodies, 100 unchanged frames | 200,200 eligibility/cache checks | 0 |
| Edit one of those bodies | Not used as a timing comparison | 1 rebuild, fewer than 10 checks, 1,001 bodies retained |
| 1,000 unchanged frames in existing stable-frame test | 1,000 eligibility checks; assertion fails | 0 eligibility checks and 0 cache checks |

The six lifecycle tests plus the stable-frame test were run against the old implementation
with only observer instrumentation: exactly the two new stable-work assertions failed.
This demonstrates that the tests detect the removed work, not just expected final state.

Validation completed:

- All main sources compiled for Java 8; all test sources compiled for Java 21.
- 66 targeted physics/API/engine lifecycle tests passed.
- 1,558 JVM tests passed overall (1,544 in the main run plus 14 from three `public final`
  test classes caught by the test-discovery completeness check). Targeted tests overlap this total.
- `git diff --check` passed.

Environment limitation: the Gradle wrapper could not download Gradle 9.6.1 because the network
connection failed. Compilation used Eclipse ECJ 3.37.0 with the repository's dependency versions
(LibGDX/Box2D 1.14.2, Artemis 2.3.0, TextraTypist 2.5.0) and JUnit 4.13.2, followed by JUnitCore
with desktop native libraries. This is not a successful Gradle task execution. GL-only suites
excluded by the ordinary `test` task, GWT compilation, Android and Studio integration were not run.
There is no measured FPS or millisecond claim in this audit.

## Other recurring work to remove or narrow

Priorities below reflect avoidable work and lifecycle clarity, not measured CPU ranking.
Method names refer to `src/main/java/games/pixscape/runtime/system/` unless specified otherwise.

| Priority | Source and repeated work | Reason it currently exists | Safe replacement / invalidation required |
| --- | --- | --- | --- |
| High | `PhysicsSpatialFootprintSyncSystem.syncCompiledCaches` scans every compiled body, then `invalidateOrphanFootprints` scans every footprint each frame. | Detects changed generations, fixture-array replacement and removed caches. Projector caching avoids reprojection, not these scans. | Membership listeners plus a separate publication notification for changed compiled data. Do not reuse an already-consumed Box2D dirty queue. PPM change is a legitimate global invalidation. Test invalid caches, empty fixtures, removal, generation changes and pooling. |
| High | `GameObjectHierarchySystem.ensureCurrentTopology` calls `detectInPlaceStructuralMutation` on every invocation, scanning members and root identities. Both hierarchy processing and physics writeback call it. | Supports direct in-place parent/stable-ID changes that do not change ECS membership. | Explicit topology/identity revision at mutation boundaries; notifications for insertion/removal. Cover reparenting, identity changes, deletion/reuse and expert/editor integration before removing polling. Merely retaining `topologyDirty` without new producers loses changes. |
| High | `GameObjectCompositionSystem.processSystem` clears composition arrays and calls `rebuildLocalOrder` every frame: ten radix passes over all parented members. | Reads current parent identity, stable identity and z-index without an order revision. | Cache sibling order until topology, stable identity or z-index changes. Preserve dynamic visibility and derived bounds separately; their current-frame dependencies differ from order. |
| High | `RenderTiledSyncSystem.process` calls `ensureAllChunkRenderRefs` for every spatial map every frame, before visibility culling. | Ensures chunk reference ranges exist for spatial ordering, including off-screen chunks. | Allocate at preparation/chunk admission and invalidate on resize, chunk removal, storage reset or map rebind. Keep off-screen spatial references; simply moving this under visibility culling is incorrect. |
| Medium | `TiledAnimationSystem.process` loops through every map chunk before skipping chunks without animations; it already has per-chunk animated tile indices. | Finds chunks whose tile animation state should advance. | Maintain the set of animated chunks at tile/chunk mutation. Intersect with visibility only under the existing `advanceOnlyVisible` policy. Preserve paused/off-screen playback semantics. |
| Medium | `LayerStateBuildSystem` clears/rebuilds all layer state; `RenderParticleSyncSystem.refreshLayerVisibility` independently rebuilds a visibility map even with no emitters; `SpatialRenderOrderSystem.rebuildSpatialLayers` scans layer/map membership. | Multiple consumers derive overlapping layer configuration without a shared revision. | Share published layer configuration/revisions, preserving visibility/parallax/spatial changes, membership and scene switches. An early no-emitter exit is a small independent fix; it does not solve the duplicate ownership. |
| Measure before broader changes | `GameObjectPhysicsWritebackSystem.captureBodyPoses` reads every native body's position and angle (position read twice), then visits the full topology. | Reconstructs local transforms under parents after native physics movement. | Reuse a single position read; evaluate changed/awake-body tracking with ancestor propagation. A sleeping child under a moving parent still needs its local transform reconciled. Do not simply skip all sleeping bodies or remove writeback. |
| Lower priority | `RenderParticleSyncSystem` compares authored effect paths/configuration per emitter before simulation. | Supports in-place authored effect replacement and playback changes. | Explicit effect-configuration invalidation; preserve pool ownership, replay/reset and replacement behavior. Particle advancement itself remains frame work. |

## Coverage and legitimate frame work

| Systems / helpers inspected | Assessment |
| --- | --- |
| `Box2dSyncSystem` | Recurring structural reconciliation fixed here; authoring pose synchronization retained. |
| `PhysicsSpatialFootprintSyncSystem`, `GameObjectHierarchySystem`, `GameObjectCompositionSystem`, `GameObjectPhysicsWritebackSystem` | Findings above. Distinguish structural maintenance from changing world/local poses and bounds. |
| `LayerStateBuildSystem`, `RenderParticleSyncSystem`, `RenderTiledSyncSystem`, `TiledAnimationSystem`, `SpatialRenderOrderSystem` | Findings above. Visibility selection, particle/tile playback and actor-relative spatial ordering still have frame-dependent work. |
| `DirtyTrackerSystem`, `DirtyFlushSystem` | Queue-based work and clearing; no equivalent unconditional full-domain existence scan found. |
| `RenderSpriteSyncSystem`, `UpdateWorldGeometrySystem` | Deduplicated dirty-category union / geometry dirty entities; correct change-driven pattern. |
| `AnimationSystem`, `TiledEntityAnimationSystem` | Time/playback advancement; binding work follows relevant frame/asset changes. A frame loop here is not evidence of redundant structural reconciliation. |
| `CullingSystem`, `ParallaxDisplaySystem` | Camera/visibility/pose-dependent processing. Further caching would need camera and transform invalidation, not load-only initialization. |
| `RenderBuildDrawListSystem`, `RenderSortSystem`, `RenderExtractFrameQueueSystem`, `RenderSubmitSystem` | Current visible render workload. Static drawlist/order reuse may be possible, but no blanket removal is justified by this audit. |
| `LightingGeometrySystem` | Static map geometry/index updates are gated by `tiled.lightingRevision`; dynamic lights/actors/shadow geometry require current frame data. |
| `PhysicsPoseAuthority`, optional `PhysicsMouseDragSystem` | Authority state / input-driven physics queries; no analogous recurring structural sweep identified. |
| `IdentityRegistry`, `TagRegistry`, `SceneLayerResolver` | Event/revision-driven indexes; initial bind/rebuild and lazy invalidation are not repeated full searches per lookup. |
| `MapLightingGeometry`, spatial compiled/projected/tile-order caches | Expensive rebuild paths have revision gates. Repeated O(1) `ensure` calls should not be described as unconditional full recompilation. |
| `Box2dWorldService.setDoSleep` | Whole-body loop only when sleep policy actually changes; return guard on identical value. This is legitimate domain-wide invalidation. |

## Follow-up acceptance criteria

For each next correction, define the mutations that invalidate the derived state, instrument
actual visits as well as rebuilds, and test both stable frames and one changed item in a large
unchanged domain. Stable structural maintenance should be zero; a local change should not
visit unrelated entities. Keep initial publication, exceptional global settings changes and
actual per-frame simulation/rendering separate. Audit raw field writes in both Runtime and
Studio before removing polling that currently serves editor/expert mutation paths.
