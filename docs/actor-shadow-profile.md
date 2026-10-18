# Actor shadow calibration

Actors cast onto decor and receive only direct light visible through decor.
All actor casters, including the receiver itself, are excluded from that
occlusion test. Ambient remains independent of shadow visibility; normal mapping
and direct-light equations are unchanged. The existing actor/decor provenance
is preserved. Two shared six-face atlases are reused for each selected light:
`atlas` contains all casters for decor receivers; `decorAtlas` contains only
decor casters for every actor receiver. Their number is independent of actor
count; there is no per-actor map, exclusion list or caster identity lookup.

Both receiver classes use the same ordinary or mapped program and PCF/bias.
The batch flushes before switching the atlas on texture unit 4, preserving the
published queue and transparent order. Bounds, alpha threshold, off-camera
casters and six shadow views keep their existing rules. Each map clears to 1
on every retained shadowed light, including empty-category frames. The targets
are prepared together at the loading/frame preparation boundary, never during
submission. Their R32F + depth payload is approximately `96 * R * R` bytes
(depth counted as 4 bytes), restoring one shared atlas compared to the previous
unshadowed-actor contract. The common atlas deliberately reuses the unchanged
all-caster path; decor geometry is submitted to both maps. No new per-light
or per-actor CPU array is created.

`SurfaceLightingComponent.actorShadow` calibrates a static image. For an
animation, `actorShadowClips[animationAssetId][clipName]` owns the settings for
the entire clip, independently of its frame and atlas packing. A missing
profile preserves the current support. Copying, pooling and Game Object
snapshots preserve that ownership and deep-copy authored settings.

The normalized reference image has its origin at the bottom left. Two freely
positioned endpoints `(leftU,leftV)` and `(rightU,rightV)` define the infinite
foot directrix, not a crop or the actor's width. Flips map them into the displayed
quad. Let `(du,dv)` be their difference, canonicalized toward increasing image U
(toward decreasing V for an exactly vertical line), and `s=max(abs(du),abs(dv))`.
Then `a=-dv/s`, `b=du/s`, `c=-(a*leftU+b*leftV)` describe the line without division
by its slope. For the existing two-triangle image mapping `Q(u,v)` and transformed
image height `H`, the calibrated caster and receiver share:

```
z = altitude + H * (a*u + b*v + c)
x = Q.y - z + Q.x / 2
y = Q.y - z - Q.x / 2
```

For the original endpoints on U=0 and U=1, this is exactly the former
`z=altitude+H*(v-leftV-(rightV-leftV)*u)` calibration. Dominant-axis normalization
keeps steep/vertical lines finite and makes shortening a segment independent of
height, alpha coverage and reference width. Coincident/near-coincident endpoints
(normalized distance below 0.00001) are rejected before command creation; the
canvas retains the last valid point. A direct gameplay mutation bypassing
validation falls back to the uncalibrated support if it is degenerate.

Thus the calibrated projection is `Q` and every point on the foot directrix is on the
authored altitude. No selected midpoint defines the actor placement. There
is no independent caster Foot U/V or third handle. For an active profile, the receiver uses these heights at its original six UV
corners and the two calibrated geometric normals. The two original image
triangles and their diagonal remain unchanged. The placement anchor/offsets
remain embedded in Q; anchorV no longer supplies a contradictory reference
height. Without a profile for the current clip, both paths retain the legacy
anchorV support; no other clip is borrowed and no pixel is clamped to the ground.
Pixels below the authored directrix may therefore lie below the reference
altitude: alpha opacity alone does not identify a foot contact.

For compensation, `D` is the unit horizontal foot direction, `N=(-D.y,D.x)`,
and `W` is the metric reference-image width, independent of endpoint span.
The existing slider remains in `[0,0.5]`. It now controls a continuous open-sheet
fold over its full range, rather than a light-dependent correction inside a
15-degree window:

```
depth = 2 * thickness * W
bow = 1 - abs(2 * tangentAxis - 1)
displacement = N * depth * bow * heightWeight(u,v)
```

Zero returns the exact calibrated buffer for every light. At 0.5 the maximum
fold depth equals W. For a rectangular upright support, the full V-shaped
cross-section spans between `2/sqrt(5) * W` and `sqrt(5)/2 * W` transversely
through a horizontal revolution. This is an approximate breadth control, not
an alpha dilation or a physical volume. Actual opaque breadth also depends on
the image alpha, contact height, source altitude/perspective and receiving surface.
It is not guaranteed to be monotonic for every asymmetric animation frame.

`bow` is triangular across the full image, never across the authored endpoint
span. Its original height weight is zero on the entire foot directrix and grows
to one at the corresponding image boundary. For steep lines the stable axes
exchange roles; the line intersection divides only by the dominant coefficient
of magnitude one. The existing partition by the foot line, central row/column
and original color diagonal is unchanged. There is one open alpha sheet,
without global rotation, closure, extrusion, a second side, duplicated UV domain
or dense mesh. Contact vertices, UVs, receiver vertices and receiver normals
remain exactly calibrated. Only caster XY positions are folded.

Every source now sees the same folded sheet: there is no angular cutoff, radial
factor, signed source branch or change when a source crosses a blind direction.
Changing thickness interpolates caster positions linearly. Vertical and coincident
sources keep finite geometry and let the ordinary shadow projection decide their
result. Degenerate foot width still falls back to zero compensation. The caster
broad phase expands by the same `2 * thickness * W` maximum displacement, including
necessary off-camera casters. Shadow quality, filtering, bias, resolutions, passes
and the actor/decor B contract are unchanged.

The scalar amplitude query retains its Light argument as part of the existing
per-light sheet interface, but its value is deliberately source-independent.

Each ordinary image actor reserves two 384-float CPU buffers, a 48-float
weight buffer, 144 UV floats and two 24-float polygon scratch buffers (4,032
payload bytes) during persistent scene preparation, before READY. Calibration
also caches six scalar receiver normal components (24 payload bytes per actor), plus
three height coefficients for reception (12 extra payload bytes per actor).
The arrays and their capacities are unchanged. Legitimate
mutations reuse them. The selected actor/light prepares the compensated buffer
once, then the existing shadow batch replays it for six faces. Culling includes
the maximum `2 * thickness * W` displacement. No texture scan, GPU allocation,
readback, per-light allocation or additional shadow target is involved.

The profile JSON writer emits version 2 and all four normalized coordinates.
Unversioned/version 1 data explicitly converts the old edge heights to
`(0,leftV)` and `(1,rightV)`, preserving thickness. Static/clip ownership, missing
profile fallback and deep copies are unchanged, including Game Objects.

In Studio, select one actor, open Lighting, enable Cast shadow, and
drag either canvas handle freely in the current displayed image. The property
panel contains no image preview. The tool borrows the final current-frame quad
and UV flips; the native world/UI Stage conversions handle camera, zoom and
logical HiDPI coordinates. Studio intentionally displays no layer parallax;
Preview applies authored parallax normally, with the same saved image coordinates.
No reference-frame texture, extra GPU resource or per-light buffer is allocated.
Thickness uses the native 0..0.5 slider (step 0.01), alongside the caster alpha
Threshold slider. Both are indented below Cast shadow. One drag
commits one history command; Escape, native touch-focus cancellation or a
selection/clip change cancels the candidate. Settings persist with the actor.
