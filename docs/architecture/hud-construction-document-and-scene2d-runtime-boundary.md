# ADR: HUD Construction Document and Scene2D Runtime Boundary

**Status:** Accepted

**Date:** 2026-09-13

## Context

Scene2D is Pixscape's executable HUD model. It already owns Actor, Group, Stage, widgets,
Table/Cell and other managed layouts, layout invalidation, transforms, clipping, input/focus,
widget state rendering, and Actions. Scene2D does not provide a stable Actor-tree
serialization format, and `Actor.name` is neither mandatory nor a document-wide unique
persisted identity.

Pixscape needs one construction model shared by future Studio WYSIWYG authoring and Runtime.
That model must remain Java-8/GWT safe and allow TextureArray-safe reconstruction through the
existing `HudResources`, `HudSession`, `HudBatch`, and `TextureArray` ownership chain.

External declarative libraries were audited. None is suitable as the V1 Runtime boundary
across the required Java/GWT, round-trip authoring, mixed-layout, resource-ownership, and
dependency-longevity constraints. Skin Composer remains an external resource-authoring tool;
its output may supply Skin, atlas, NinePatch, and bitmap-font resources, but it is not the HUD
hierarchy format.

## Decision

Pixscape owns one versioned `HudDocumentV1` construction document. It is both the future
editable Studio model and the portable Runtime construction representation; V1 has no separate
compiled IR.

The document is data only, Java-8/GWT safe, bounded, explicit, independent from live Scene2D
objects, and directly materializable into native Scene2D. It stores:

- one ordered, persistently identified root hierarchy;
- a bounded node-kind vocabulary rather than Java class names;
- logical Skin/atlas resource and style names rather than resource instances;
- native Table/Cell construction constraints on parent-child placements;
- ordinary direct child placements for native containers; and
- a small free-layout policy containing only parent/root anchors, normalized child pivots,
  and x/y offsets.

Every materializable node has a nonblank String ID intended to be unique document-wide. IDs
persist across edits. A future editor allocator will be the single authority, will not reuse
deleted IDs within an editing history/session, and will remap IDs when cloning. A future
materializer may mirror the ID to `Actor.name` for diagnostics and will build one immutable,
document-wide ID-to-Actor index.

Free placement is relative to the immediate free-layout parent. The root `GROUP` is a technical
layout surface sized to the available logical HUD viewport. A direct child `TABLE` can fill that
surface with Scene2D `setFillParent`; its children use native cells. Horizontal anchors are left,
center, or right;
vertical anchors are bottom, center, or top. Pivots are normalized child-bound coordinates in
the inclusive range `[0,1]`; offsets are logical HUD units. There are no peer targets or
constraint graph.

Scene2D remains authoritative for runtime behavior and managed layout. The document persists
construction inputs, not calculated layout rectangles or runtime state.

## Consequences

### Positive

- Native Scene2D widget, layout, invalidation, input, clipping, transform, and Action semantics
  remain intact.
- Runtime production types remain Java-8/GWT safe.
- Studio and Runtime share one authoritative model.
- Pixscape does not depend on a young external markup project at Runtime.
- Skin/atlas/TextureArray ownership remains centralized in `HudResources`.
- Stable ID-based editing, diagnostics, future bindings, and O(1) Runtime lookup become possible.

### Negative

- Pixscape owns schema evolution and strict boundary validation.
- Pixscape must implement and test a small materializer in a later step.
- Unsupported Scene2D types require explicit future schema additions.
- Free layout is intentionally less expressive than a general constraint solver.

## Explicit V1 non-goals

V1 does not include serialized Actor subclasses, arbitrary Java class names, arbitrary property
maps, CSS, XML Runtime parsing, peer anchors, general constraint solving, a macro/template
language, reusable linked components, dynamic FreeType, listeners or callback Java code,
animation timelines, or Presentation transitions.

The document also never owns or references live Texture, TextureRegion, BitmapFont, Skin, Batch,
ShaderProgram, Stage, Viewport, AssetManager, or any other GL/runtime resource.
