# First 2.5D lighting integration

Lighting uses the native ordered image queue, complete authored spatial volumes,
alpha sheets, point/cone sources and an additive RGBA16F field. Spatial sorting
still decides image order. No global 3D depth test or ordered light-mask replay
is involved. HUD and Studio overlays follow the world composition.

## Coordinate contract

XYZ is measured in native world pixels before camera, zoom and layer parallax.
Z points up. The common projection is `screenX = X - Y` and
`screenY = (X + Y)/2 + Z`. Given an image point and a chosen physical altitude,
`X = screenY - Z + screenX/2`, `Y = screenY - Z - screenX/2`.

Tiled placement retains the existing `projectSpatialPoint` result, including its
single cell anchor. The drawn-plane altitude is `defaultTileAltitude` (D).
Physical Z is the authored absolute altitude A, and XY is reconstructed from
that projected point at Z=A. Thus the standard lighting plane is derived as
P=D; there is no independent serialized or editable lighting plane. Changing
map D changes the common XY registration and planar receivers, without moving
native tile images. Block A, block height H, map D and render index are distinct.
Map ID, layer index and zIndex never establish a floor.

Feature-era files containing `lightingPlaneAltitude` are rejected before load
or export. Explicit conversion must remove that field and review D and absolute
block/tile altitudes under the single convention. No automatic repair or dual
registration remains. Removing the old field does not repair incompatible
footprints or linked-tile regions. Rebase a map separately from a wall join;
when preserving projected block faces, shift absolute block/override altitudes
by the same D delta. Review unassociated planar receivers separately.

Automatic wall reconstruction supports ISO maps. Plain planar receivers also
support ordinary ORTHO maps; ORTHO/ISO volume joins are unsupported. ISO cell
sizes establish the metric; no cell size or image dimension implies a wall
height. Local tile descriptions remain offsets from the cell's physical anchor
at Z=D, including local Z offsets. Their XYZ/UV registration is checked against
the native image. Sprite descriptions remain relative to their absolute-altitude
sheet foot; source heights and sheet altitudes remain absolute.

A light's native quad center supplies its ground XY, independently of image
culling; `height` supplies Z. Radius bounds the 3D attenuation sphere. Cone
direction uses the resolved quad's horizontal axis, including hierarchy
rotation, converted to common XY and normalized. Opening is the full angle of
the 3D cone around this horizontal direction, with the existing softness band.
There is no editable inclination. Logical/layer/hierarchy visibility and the
light's enabled flag still apply; camera culling does not remove its influence.

## Authored geometry and derived preparation

Unassociated tiles receive a plane at the registered map altitude. ISO spatial
volumes provide their visible two sides and top; every linked cell membership
is retained. Their projected faces are clipped to the image and form a disjoint
UV partition. Undescribed image regions retain coverage with a zero normal and
receive ambient only. Box relief and rounded corners are approximations.

Static map vertices and a cell membership index are prepared on map/block,
asset, tile transform, plane or description changes. They are reused during
ordinary frames. A separate conservative grid indexes complete volume casters,
including obstacles outside the visible queue. Selection uses radius plus a
256-pixel margin; attenuation still uses the original radius. Oversized queries
and oversized casters use an explicit conservative spill path.

`SurfaceLightingComponent` describes an ordinary sprite or animation sheet:
`receiveLight` defaults true, `shadowCaster` false, `altitude` zero, foot
`anchorU=.5`, `anchorV=0`, horizontal `directionX=1`, `directionY=-1`, and caster
`alphaThreshold=.5`. UV anchors use the complete untrimmed image, bottom-left
origin. Set the foot to the authored trunk/character contact point, retaining
transparent margins and pixels below the foot. The surface projects exactly
onto the resolved native quad; its orientation never follows the light.
Texture regions, flips and animation frames come from the same render-domain
state as the image. Animated tile caster bindings are refreshed without
reconstructing their static geometry.

Optional `description` supplies local `xyz`, normalized image `uv`, `triangles`
and `normals`, plus receiver/caster/two-sided roles. Map assets use
`lightingDescriptions`, unique `assetId` entries whose `geometry` uses the map
cell's spatial anchor as origin. These replace automatic geometry. Values must
be finite, triangles valid, normals nonzero, UV inside the image, and UV
triangles non-overlapping. Uncovered UV retains unlit image coverage. XYZ/UV
registration must project onto the existing image. Sprite local geometry uses
the sheet foot as origin; its projected local coordinates must equal
`width*(u-anchorU)`, `height*(v-anchorV)`. Resolved affine transforms lift XYZ
and transform normals by the inverse transpose. Singular transforms fail.

Local descriptions are prepared at first use or replacement. Replace a sprite
description object when changing its topology. Map description edits require
the usual geometry/material dirty notification; derived vertices are never
serialized. Scene and Game Object snapshots deep-copy these authored fields.
The first integration has properties for placement and roles, but no mesh editor.

An automatic volume receiver or map-local replacement with tile transform
flags is rejected explicitly: prepare a separately registered transformed
description in a future extension. Ordinary planar tile images retain flips.
Volume reconstruction currently requires ISO maps. These limitations avoid
silently assigning wrong UV geometry.

## Light and caster semantics

Point/cone components add `height=128`, `shadowQuality=1` and
`shadowResolution=512`. Quality is 0 unshadowed, 1 hard or 2 PCF (3x3 tangent
plane taps); supported resolutions are 256, 512 and 1024 per cube face.
Attenuation is continuous over the 3D radius with the authored falloff and
intensity. The minimum diffuse term is .1, matching the prototype. Sheets
use two-sided diffuse reception. Shadow bias is 2.304 pixels plus a slope term
scaled by distance and resolution; near-grazing geometry can still leak or
shimmer. It is not a substitute for a correct asset description.

For spatial blocks, either `shadowCaster` or `lightOccluder` requests the
complete closed box as a caster. Both remain false by default. `actorOccluder`
continues to control spatial ordering separately. Sprite casting requires its
explicit component flag and, when present, the local description's caster
role. Image visibility does not substitute for casting/reception roles.

Each source owns its shadow visibility and color contribution. Ambient never
passes through a shadow test. Six depth views share one R32F/depth atlas reused
sequentially across all sources, sized for the largest configured resolution.
The atlas is absent when all enabled sources are unshadowed. It is recalculated
each frame; no shadow-result cache is claimed. Off-camera alpha sheets and
volumes remain eligible within the conservative source selection.

## Alpha, materials and batching

The original pass retains the existing texture-array/material parameter path.
Additional ordered triangle batches bind up to four ordinary atlas pages or
Studio fallback textures, reuse one CPU/VBO buffer and preserve native order.
This first version uses the existing texture array for original colors; new
passes use page batching to share the same path with standalone Studio images.
They do not introduce an atlas or reserve user parameter slots.

For each light, XYZ/normals are rasterized as interpolants of its receiver
triangles, and the shader computes a colored contribution before compositing
its coverage. **Positions are never alpha-blended.** ALPHA composites premultiplied
contribution color; PREMULT_ALPHA keeps its already premultiplied color.
OPAQUE and CUTOUT replace coverage; CUTOUT tests the texture's alpha at .5,
matching the standard original shader. Partial alpha therefore receives light,
including its background share, without conversion to CUTOUT.

The per-light contribution is normalized by the original RGB, then added to the
HDR field, preserving `original * (ambient + field)` composition A. Original
RGBA8 quantization and RGBA16F rounding remain. The normalization epsilon is
1/65536; extremely dark channels and extreme intensities are precision limits.

Additive images retain original RGB and do not supply receiver or occluder
coverage; their established original/ambient behavior remains. Standard
particles without an authored surface have zero-normal coverage and ambient
only. Multiplicative world decor remains rejected by composition A. With an
active light, user/example shaders and repeated geometry lack a registered
surface/coverage adapter and fail explicitly; custom source shaders also fail.
This preserves serialized custom parameters instead of inventing surface data
or silently rendering a custom light as a point source. Standard lighting reads
the point/cone component fields, not the old procedural shader's parameter row.
Custom effects in worlds without enabled lights keep their original path.

## Studio, lifecycle and backends

Free and Pro use the same Runtime geometry/renderer and history commands.
Light properties expose height, quality and resolution; sprite properties
expose reception/casting, foot, altitude, direction and threshold. Map properties
expose `Altitude:` and `Default height:`. F8 in the canvas cycles opt-in position, normal and coverage
diagnostics, then normal rendering. Coverage is amber for approximations,
green for described geometry, magenta for missing normals. Black original RGB
channels can hide diagnostic channels because composition still uses original
RGB. Diagnostic state is transient, off by default and not exported.

GPU preparation occurs at the true frame boundary, including a subscription
barrier for pending light additions. World processing during scene loading does
not authorize Studio rendering. Submission consumes its frame authorization,
aborts both batches on error, and restores the caller framebuffer, viewport and
scissor enable state. Resizing and World replacement own/dispose their targets.
All active new sampler bindings are complete and avoid attached-texture feedback.

GL3/GLES3/WebGL2 and float render targets are required; allocation or shader
failure is explicit, with no lower-precision or unshadowed fallback. Desktop
OpenGL and GWT compilation are exercised for this change. Android and real
WebGL rendering require separate device/browser validation; compilation alone
does not certify either backend.

Flat silhouettes lose apparent thickness and shadow area when the source is
near their plane. Openings, relief, rounded corners, grazing self-shadowing and
fine alpha foliage remain limits of the current descriptions and resolution.
Improve their local descriptions without changing the rendering pipeline.
