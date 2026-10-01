"""Write the current Pixscape JSON Schema set from compact field declarations.

Run from any directory: python schemas/generate.py. The emitted JSON files are
the public schema artifacts; this file makes their regeneration deterministic.
"""
import json
import sys
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BASE = "https://pixscape.games/schemas/pixscape/"
DRAFT = "https://json-schema.org/draft/2020-12/schema"


def ref(path):
    return {"$ref": BASE + path}


def obj(props=None, required=(), extra=True, description=None):
    result = {"type": "object", "properties": props or {}}
    if required:
        result["required"] = list(required)
    if not extra:
        result["additionalProperties"] = False
    if description:
        result["description"] = description
    return result


def arr(item):
    return {"type": "array", "items": item}


def val(kind, default=None, has_default=False):
    result = {"type": kind}
    if has_default:
        result["default"] = default
    return result


I, N, B, S = (val(t) for t in ("integer", "number", "boolean", "string"))
POS = {"type": "integer", "minimum": 1}
NONNEG = {"type": "integer", "minimum": 0}
NONS = {"type": "string", "pattern": "\\S"}
NULLABLE_S = {"type": ["string", "null"]}


def write(path, schema):
    schema = {"$schema": DRAFT, "$id": BASE + path, **schema}
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    output = json.dumps(schema, indent=2, ensure_ascii=False) + "\n"
    if "--check" in sys.argv:
        if not target.exists() or target.read_text(encoding="utf-8") != output:
            raise SystemExit(f"Schema differs from generator: {target}")
    else:
        target.write_text(output, encoding="utf-8")


COMMON = "common/1.schema.json"
COMP = "components/3.schema.json"
common_defs = {
    "intArray": obj({"items": {"type": ["array", "null"], "items": I}, "size": NONNEG}, ("size",), description="LibGDX IntArray. Items may be omitted or null at SceneLoader boundary; only the first size entries are active when present."),
    "byteArray": obj({"items": arr(I), "size": NONNEG}, ("size",), description="LibGDX ByteArray. Items may be omitted for zero-filled storage; only the first size entries are active."),
    "propertyValue": obj({"type": {"enum": ["STRING", "BOOLEAN", "INTEGER", "FLOAT", "COLOR", "OBJECT", "CLASS"]}, "stringValue": NULLABLE_S, "booleanValue": B, "integerValue": I, "floatValue": N, "className": NULLABLE_S, "classProperties": {"$ref": "#/$defs/propertySet"}}, ("type",)),
    "propertySet": obj({"values": {"type": "object", "additionalProperties": {"$ref": "#/$defs/propertyValue"}}}),
    "physicsGeometry": obj({"shapeType": {"type": "integer", "enum": [0, 1, 2]}, "offsetX": N, "offsetY": N, "angleDegrees": N, "radius": N, "halfWidth": N, "halfHeight": N, "polygonVertices": arr(N), "polygonVertexCount": NONNEG}),
    "physicsShape": obj({"physicsShapeId": I, "spatialBlockId": I, "spatialFootprint": B, "geometry": {"anyOf": [{"$ref": "#/$defs/physicsGeometry"}, {"type": "null"}]}, "density": N, "friction": N, "restitution": N, "sensor": B, "categoryBits": I, "maskBits": I, "groupIndex": I, "enabled": B}),
    "spatialShape": obj({"shapeType": {"type": "integer", "enum": [0, 1, 2]}, "polyVerts": arr(N), "polyCount": NONNEG, "halfW": N, "halfH": N, "radius": N, "offsetX": N, "offsetY": N, "angleDeg": N, "collisionEnabled": B, "actorOccluder": B, "lightOccluder": B, "particleOccluder": B, "altitude": N, "height": N}),
    "spatialBlock": obj({"id": I, "structureId": I, "name": NULLABLE_S, "x": N, "y": N, "width": N, "depth": N, "altitude": N, "height": N, "actorOccluder": B, "lightOccluder": B, "shadowCaster": B, "particleOccluder": B, "linkedTileRefsAuthored": B, "linkedTileRefs": arr(obj({"gx": I, "gy": I, "tileAssetId": I}))}),
}
common_defaults = {
    "intArray": {"items": [], "size": 0},
    "byteArray": {"items": [], "size": 0},
    "propertyValue": {"type": "STRING", "stringValue": "", "booleanValue": False,
                      "integerValue": 0, "floatValue": 0, "className": "",
                      "classProperties": {"values": {}}},
    "propertySet": {"values": {}},
    "physicsGeometry": {"shapeType": 0, "offsetX": 0, "offsetY": 0,
                        "angleDegrees": 0, "radius": 0.5, "halfWidth": 0.5,
                        "halfHeight": 0.5, "polygonVertices": [], "polygonVertexCount": 0},
    "physicsShape": {"physicsShapeId": 0, "spatialBlockId": 0,
                     "spatialFootprint": False, "geometry": None, "density": 1,
                     "friction": 0.2, "restitution": 0, "sensor": False,
                     "categoryBits": 1, "maskBits": -1, "groupIndex": 0,
                     "enabled": True},
    "spatialShape": {"shapeType": 0, "polyVerts": [], "polyCount": 0,
                     "halfW": 0.5, "halfH": 0.5, "radius": 0.5,
                     "offsetX": 0, "offsetY": 0, "angleDeg": 0,
                     "collisionEnabled": False, "actorOccluder": False,
                     "lightOccluder": False, "particleOccluder": False,
                     "altitude": 0, "height": 0},
    "spatialBlock": {"id": 0, "structureId": 0, "name": None,
                     "x": 0, "y": 0, "width": 0, "depth": 0,
                     "altitude": 0, "height": 128, "actorOccluder": True,
                     "lightOccluder": False, "shadowCaster": False,
                     "particleOccluder": False, "linkedTileRefsAuthored": False,
                     "linkedTileRefs": []},
}
for definition, defaults in common_defaults.items():
    for field, default in defaults.items():
        common_defs[definition]["properties"][field] = deepcopy(common_defs[definition]["properties"][field])
        common_defs[definition]["properties"][field]["default"] = default
write(COMMON, {"title": "Shared Pixscape value structures", "$defs": common_defs})


def c(fields, special=None):
    # Unknown fields are intentionally tolerated: Artemis/libGDX read behavior
    # is not a documented preservation guarantee. Known fields are typed.
    props = {}
    defaults = {"i": 0, "n": 0, "b": False, "s?": None,
                "ia": {"size": 0}, "ba": {"size": 0}, "na": [], "sa": []}
    for code, names in fields.items():
        schema = {"i": I, "n": N, "b": B, "s": S, "s?": NULLABLE_S,
                  "ia": ref(COMMON + "#/$defs/intArray"), "ba": ref(COMMON + "#/$defs/byteArray"),
                  "na": arr(N), "sa": arr(S)}[code]
        for name in names.split():
            props[name] = deepcopy(schema)
            if code in defaults:
                props[name]["default"] = deepcopy(defaults[code])
    props.update(deepcopy(special or {}))
    return obj(props)


components = {
    "PixscapeIdentityComponent": c({"i": "stableId", "s": "name"}),
    "LayerComponent": c({"i": "layerIndex", "b": "spatialEnabled"}),
    "EntityIndexComponent": c({"i": "layerIndex zIndex"}),
    "LayerParallaxComponent": c({"n": "factorX factorY"}),
    "TransformComponent": c({"n": "x y originX originY rotationRad scaleX scaleY"}),
    "GameObjectComponent": c({"s": "sourceAssetId"}),
    "GameObjectMemberComponent": c({"i": "parentStableId"}),
    "VisibilityComponent": c({"b": "visible culledByFrustum inView", "n": "padding"}),
    "DimensionsComponent": c({"n": "width height"}),
    "AABBComponent": c({"n": "minX minY maxX maxY"}),
    "OrientedBoundsComponent": c({"n": "cx cy ux uy vx vy hx hy"}),
    "AssetRefComponent": c({"i": "assetId", "s": "atlasTag"}),
    "TextureRegionComponent": c({}),
    "TintComponent": c({"i": "rgba"}),
    "AnimationComponent": c({"ia": "animationAssetIds", "s": "currentClip", "n": "fps stateTime", "b": "playing loop", "i": "frame"}),
    "TiledAnimationComponent": c({"i": "animationId"}),
    "RenderMaterialComponent": c({"i": "shaderIdx blendModeId"}),
    "ShaderParamsComponent": c({}, {"floats": arr(obj({"name": S, "value": N}))}),
    "RenderRepeatComponent": c({"b": "repeatX repeatY"}),
    "QuadDeformComponent": c({"n": "blX blY brX brY trX trY tlX tlY"}),
    "PolygonComponent": c({"na": "vertices"}),
    "PolylineComponent": c({"na": "vertices"}),
    "PixscapeTagComponent": c({"sa": "tags"}),
    "CustomPropertiesComponent": c({}, {"properties": ref(COMMON + "#/$defs/propertySet")}),
    "TiledLayerComponent": c({"s": "atlasTag", "i": "tileWidth tileHeight mapWidthCells mapHeightCells chunkSize", "n": "originX originY defaultTileAltitude defaultTileHeight", "b": "spatialEnabled", "ia": "tileXs tileYs tileAssetIds", "ba": "tileTransformFlags"}, {"projection": {"enum": ["ORTHO", "ISO", None]}, "tileAltitudes": {"type": ["array", "null"], "items": N}, "tileHeights": {"type": ["array", "null"], "items": N}, "tileSpatialFlags": {"anyOf": [ref(COMMON + "#/$defs/intArray"), {"type": "null"}]}, "tileSpatialOverrides": {"anyOf": [ref(COMMON + "#/$defs/byteArray"), {"type": "null"}]}}),
    "SpatialHeightComponent": c({"n": "altitude height"}),
    "SpatialShapesComponent": c({}, {"shapes": arr(ref(COMMON + "#/$defs/spatialShape"))}),
    "SpatialBlocksComponent": c({"i": "nextSpatialBlockId"}, {"blocks": arr(ref(COMMON + "#/$defs/spatialBlock"))}),
    "PointLightComponent": c({"n": "r g b intensity radius falloff", "b": "enabled"}),
    "ConeLightComponent": c({"n": "r g b intensity radius falloff coneAngleDeg rotationDeg softness", "b": "enabled"}),
    "ParticleEmitterComponent": c({"s?": "effectPath atlasTag", "b": "autoStart looping autoRemoveWhenComplete paused playRequested restartRequested"}),
    "ParticleOverridesComponent": c({"b": "enabled", "n": "sizeMul alphaMul", "i": "tintRgba"}),
    "PhysicsBodyComponent": c({"i": "type", "b": "fixedRotation bullet allowSleep awake", "n": "gravityScale linearDamping angularDamping"}),
    "PhysicsShapesComponent": c({}, {"shapes": arr(ref(COMMON + "#/$defs/physicsShape"))}),
    "PhysicsJointComponent": c({"i": "type aEid bEid", "b": "collideConnected", "n": "anchorAx anchorAy anchorBx anchorBy"}),
    "PhysicsDistanceJointComponent": c({"n": "lengthM frequencyHz dampingRatio"}),
    "PhysicsRevoluteJointComponent": c({"b": "enableLimit enableMotor", "n": "lowerAngleRad upperAngleRad motorSpeedRad maxMotorTorque"}),
    "PhysicsPrismaticJointComponent": c({"n": "axisX axisY lowerTranslationM upperTranslationM motorSpeedMps maxMotorForce", "b": "enableLimit enableMotor"}),
    "PhysicsPulleyJointComponent": c({"n": "groundAx groundAy groundBx groundBy lengthAM lengthBM ratio"}),
    "PhysicsMouseJointComponent": c({"n": "targetX targetY maxForce stiffness damping"}),
    "PhysicsGearJointComponent": c({"i": "joint1Eid joint2Eid", "n": "ratio"}),
    "PhysicsWheelJointComponent": c({"n": "frequencyHz dampingRatio motorSpeedRad maxMotorTorque axisX axisY", "b": "enableMotor"}),
    "PhysicsWeldJointComponent": c({"n": "referenceAngleRad frequencyHz dampingRatio"}),
    "PhysicsFrictionJointComponent": c({"n": "maxForce maxTorque"}),
    "PhysicsMotorJointComponent": c({"n": "linearOffsetX linearOffsetY angularOffsetRad maxForce maxTorque correctionFactor"}),
}
components["AnimationComponent"]["properties"]["animationAssetIds"] = {"anyOf": [ref(COMMON + "#/$defs/intArray"), {"type": "null"}]}
component_defaults = {
    "PixscapeIdentityComponent": {"stableId": -1, "name": "unnamed"},
    "LayerParallaxComponent": {"factorX": 1, "factorY": 1},
    "TransformComponent": {"scaleX": 1, "scaleY": 1},
    "GameObjectComponent": {"sourceAssetId": ""},
    "GameObjectMemberComponent": {"parentStableId": -1},
    "VisibilityComponent": {"visible": True, "culledByFrustum": True, "padding": 1},
    "AssetRefComponent": {"assetId": -1, "atlasTag": "main"},
    "TintComponent": {"rgba": -1},
    "AnimationComponent": {"animationAssetIds": {"size": 0}, "currentClip": "", "fps": 12, "playing": True, "loop": True, "frame": -1},
    "TiledAnimationComponent": {"animationId": -1},
    "RenderMaterialComponent": {"blendModeId": 1},
    "ShaderParamsComponent": {"floats": []},
    "CustomPropertiesComponent": {"properties": {"values": {}}},
    "TiledLayerComponent": {"atlasTag": "main", "projection": None,
                            "tileAltitudes": None, "tileHeights": None,
                            "tileSpatialFlags": None, "tileSpatialOverrides": None},
    "SpatialBlocksComponent": {"nextSpatialBlockId": 1, "blocks": []},
    "PointLightComponent": {"r": 1, "g": 1, "b": 1, "intensity": 1, "radius": 200, "falloff": 1.5, "enabled": True},
    "ConeLightComponent": {"r": 1, "g": 1, "b": 1, "intensity": 1, "radius": 200, "falloff": 1.5, "enabled": True,
                           "coneAngleDeg": 45, "softness": 0.1},
    "ParticleEmitterComponent": {"autoStart": True, "looping": True},
    "ParticleOverridesComponent": {"enabled": True, "sizeMul": 1, "alphaMul": 1, "tintRgba": -1},
    "PhysicsBodyComponent": {"type": 2, "allowSleep": True, "awake": True, "gravityScale": 1},
    "PhysicsJointComponent": {"aEid": -1, "bEid": -1},
    "PhysicsDistanceJointComponent": {"lengthM": 1},
    "PhysicsPrismaticJointComponent": {"axisX": 1},
    "PhysicsPulleyJointComponent": {"ratio": 1},
    "PhysicsGearJointComponent": {"joint1Eid": -1, "joint2Eid": -1, "ratio": 1},
    "PhysicsWheelJointComponent": {"frequencyHz": 4, "dampingRatio": 0.7, "axisY": 1},
    "PhysicsMotorJointComponent": {"correctionFactor": 0.3},
}
for component, defaults in component_defaults.items():
    for field, default in defaults.items():
        components[component]["properties"][field]["default"] = default
for field in ("effectPath", "atlasTag"):
    components["ParticleEmitterComponent"]["properties"][field].pop("default", None)
    components["ParticleEmitterComponent"]["properties"][field]["description"] = (
        "Fresh component is null; a recycled pooled component resets this field to an empty string."
    )
for component, field in [("ShaderParamsComponent", "floats"), ("PhysicsShapesComponent", "shapes"),
                         ("SpatialShapesComponent", "shapes"), ("SpatialBlocksComponent", "blocks")]:
    components[component]["properties"][field].setdefault("default", [])
components["LayerComponent"]["not"] = {"required": ["type"]}
components["LayerComponent"]["description"] = "Only scene Layer entities may carry this. The obsolete type field is forbidden."
component_paths = {}
for name in components:
    package = "games.pixscape.runtime.component"
    if name.startswith("Physics"):
        package += ".physics"
    elif name.startswith("Spatial"):
        package += ".spatial"
    elif name in ("PointLightComponent", "ConeLightComponent"):
        package += ".light"
    component_paths[package + "." + name] = name
write(COMP, {"title": "Pixscape scene component payloads (scene schema 3)",
             "description": "Resolve each document's componentIdentifiers first; these definitions are selected by fully qualified type ID, never by a globally fixed alias.",
             "x-pixscape-components": component_paths, "$defs": components})

AVAIL = obj({"sprites": arr(POS), "animations": arr(POS), "particles": arr(NONS), "gameObjects": arr(NONS), "tiledTiles": arr(POS), "tiledAnimations": arr(POS)})
SCENE_META = obj({
    "sceneSchemaVersion": {"const": 3}, "name": NULLABLE_S, "file": NONS,
    "defaultHudScreenId": {"type": ["string", "null"], "default": None}, "nextEntityStableId": POS,
    "nextPhysicsShapeId": POS, "physicsEnabled": {"type": "boolean", "default": False},
    "pixelsPerMeter": {"type": "number", "default": 100},
    "gravityX": {"type": "number", "default": 0},
    "gravityY": {"type": "number", "default": -9.81},
    "doSleep": {"type": "boolean", "default": True},
    "physicsParallaxX": {"anyOf": [N, {"const": "NaN"}], "default": "NaN"},
    "physicsParallaxY": {"anyOf": [N, {"const": "NaN"}], "default": "NaN"},
    "ambientMulR": {"type": "number", "default": 1},
    "ambientMulG": {"type": "number", "default": 1},
    "ambientMulB": {"type": "number", "default": 1},
    "runtimeParticleEffectPaths": arr(S), "runtimeGameObjectIds": arr(S),
    "runtimeAvailability": {"anyOf": [AVAIL, {"type": "null"}], "default": {}},
}, ("sceneSchemaVersion", "file", "nextEntityStableId", "nextPhysicsShapeId"))
write("project/1.schema.json", {
    "title": "Pixscape Runtime project format 1",
    "type": "object", "required": ["projectFileName", "scenes"],
    "properties": {"projectFileName": NONS,
                   "version": {"anyOf": [{"const": "1"}, {"type": "string", "pattern": "^\\s*$"}, {"type": "null"}], "default": "1"},
                   "projectKind": NULLABLE_S, "runtimeRootDir": {"type": ["string", "null"], "default": None},
                   **{n: {"type": ["string", "null"], "default": n[:-3].lower() if n != "gameObjectsDir" else "gameobjects"}
                      for n in ("scenesDir", "atlasesDir", "effectsDir", "animationsDir", "shadersDir", "audioDir", "gameObjectsDir")},
                   "scenes": {"type": "object", "additionalProperties": SCENE_META},
                   "currentSceneName": NULLABLE_S, "glSamples": {"type": "integer", "default": 0}},
    "description": "Absent or blank version is normalized to 1 by Runtime; the schema documents the canonical value when present. Paths resolve from the directory passed to the engine, not runtimeRootDir."
})

write("scene/3.schema.json", {
    "title": "Artemis scene envelope for Pixscape scene schema 3",
    "type": "object", "required": ["metadata", "componentIdentifiers", "entities", "archetypes"],
    "properties": {
        "metadata": obj({"version": I}, ("version",)),
        "componentIdentifiers": {"type": "object", "additionalProperties": NONS},
        "archetypes": {"type": "object", "patternProperties": {"^[0-9]+$": arr(NONS)}, "additionalProperties": False},
        "entities": {"type": "object", "patternProperties": {"^[0-9]+$": obj({"archetype": NONNEG, "components": {"type": "object"}}, ("archetype",))}, "additionalProperties": False},
    },
    "description": "Artemis IDs and aliases are local to this file. An archetype supplies effective components even when components omits their default-valued payloads. Run validate.mjs for alias resolution and payload validation."
})

write("animations/unversioned.schema.json", {"title": "Pixscape sprite animation registry (no in-file version)", **obj({"animations": arr(obj({"assetId": POS, "name": NONS, "fps": N, "currentClip": S, "frameCount": NONNEG, "clips": arr(obj({"name": NONS, "start": NONNEG, "end": NONNEG, "flipX": B}))}))}, ("animations",))})
write("tiled-animations/1.schema.json", {"title": "Pixscape tiled animation registry 1", **obj({"version": {"const": "1"}, "animations": arr(obj({"id": POS, "name": NONS, "frameAssetIds": arr(POS), "frameDurationsMs": arr(POS)}))}, ("animations",))})
write("tileset-profiles/1.schema.json", {"title": "Pixscape tileset profiles 1", **obj({"format": {"const": "pixscape.tileset-profiles"}, "version": {"const": 1}, "tilesets": arr(obj({"tilesetId": POS, "logicalPath": NONS, "tileWidth": POS, "tileHeight": POS, "referenceCellWidth": POS, "referenceCellHeight": POS, "projection": {"enum": ["orthogonal", "isometric"]}, "anchor": S, "offsetX": N, "offsetY": N, "renderSize": S, "tileAssetIds": arr(POS)}))}, ("format", "version", "tilesets"))})

go_transform = obj({n: {"type": "number", "default": 0} for n in "x y rotationRad scaleX scaleY originX originY".split()})
go_entity_index = obj({"zIndex": {"type": "integer", "default": 0}}, extra=False)
go_optional = {
    "meta": obj({"kind": S}), "identity": obj({"name": S}),
    "tags": obj({"values": arr(S)}),
    "customProperties": ref(COMMON + "#/$defs/propertySet"),
    "visibility": obj({"visible": B}),
    "boundsFlags": obj({"hasAabb": B, "hasObb": B}),
    "dimensions": obj({"width": N, "height": N}),
    "quadDeform": components["QuadDeformComponent"],
    "renderMaterial": components["RenderMaterialComponent"],
    "assetRef": obj({"assetId": I, "atlasTag": S}),
    "tint": obj({"rgba": I}),
    "animation": components["AnimationComponent"],
    "shaderParams": obj({"floats": {"type": "object", "additionalProperties": N}}),
    "repeat": components["RenderRepeatComponent"],
    "pointLight": components["PointLightComponent"],
    "coneLight": components["ConeLightComponent"],
    "spatialHeight": components["SpatialHeightComponent"],
    "physicsBody": components["PhysicsBodyComponent"],
    "gameObject": obj(),
}
go_entity = obj({"sourceEntityId": {"type": "integer", "minimum": 0, "default": 0},
                 "parentSourceEntityId": {"type": "integer", "default": -1},
                 "transform": go_transform,
                 "entityIndex": {"anyOf": [go_entity_index, {"type": "null"}], "default": None},
                 "physicsShapes": {**arr(obj({"localShapeId": POS, "geometry": ref(COMMON + "#/$defs/physicsGeometry"), "density": N, "friction": N, "restitution": N, "sensor": B, "categoryBits": I, "maskBits": I, "groupIndex": I, "enabled": B, "spatialFootprint": B})), "default": []},
                 **{name: {"anyOf": [shape, {"type": "null"}], "default": None} for name, shape in go_optional.items()}},
                ("transform",))
joint_base = {"jointLocalId": POS, "type": {"type": "integer", "minimum": 0, "maximum": 9},
              "bodyALocalEntityId": NONNEG, "bodyBLocalEntityId": NONNEG,
              "collideConnected": B, **{n: N for n in "anchorAx anchorAy anchorBx anchorBy".split()}}
for label, numeric, booleans in [
    ("distance", "lengthM frequencyHz dampingRatio", ""),
    ("revolute", "lowerAngleRad upperAngleRad motorSpeedRad maxMotorTorque", "enableLimit enableMotor"),
    ("prismatic", "axisX axisY lowerTranslationM upperTranslationM motorSpeedMps maxMotorForce", "enableLimit enableMotor"),
    ("pulley", "groundAnchorALocalX groundAnchorALocalY groundAnchorBLocalX groundAnchorBLocalY lengthAM lengthBM ratio", ""),
    ("gear", "ratio", ""),
    ("wheel", "frequencyHz dampingRatio motorSpeedRad maxMotorTorque axisX axisY", "enableMotor"),
    ("weld", "referenceAngleRad frequencyHz dampingRatio", ""),
    ("friction", "maxForce maxTorque", ""),
    ("motor", "linearOffsetX linearOffsetY angularOffsetRad maxForce maxTorque correctionFactor", ""),
]:
    fields = {n: N for n in numeric.split()}
    fields.update({n: B for n in booleans.split()})
    if label == "gear":
        fields.update({"jointALocalId": POS, "jointBLocalId": POS})
    joint_base[label] = {"anyOf": [obj(fields), {"type": "null"}]}
go_joint = obj(joint_base, ("jointLocalId", "type", "bodyALocalEntityId", "bodyBLocalEntityId"))
write("game-object/3.schema.json", {
    "title": "Pixscape Game Object definition 3",
    **obj({"schemaVersion": {"const": 3}, "rootSourceEntityId": NONNEG,
           "entities": arr(go_entity), "joints": {**arr(go_joint), "default": []}},
          ("schemaVersion", "rootSourceEntityId", "entities"), extra=False),
    "description": "A definition does not place an instance. The root has x=y=0 and zIndex=0; layerIndex is forbidden in entityIndex. Local member transforms and zIndex survive spawn. Cross-entity references and the unique root are validated semantically."
})

write("hud-screen/1.schema.json", {
    "title": "Pixscape HUD screen descriptor 1",
    **obj({"schemaVersion": {"const": 1}, "documentId": NONS,
           "skinId": NULLABLE_S, "atlasId": NULLABLE_S,
           "textureProfileId": NULLABLE_S}, ("schemaVersion", "documentId"), extra=False)
})

hud_file = "hud-document/2.schema.json"
hud_node = {"$ref": "#/$defs/node"}
hud_child = obj({"node": hud_node, "placementKind": {"enum": ["DIRECT", "CELL", "FREE"], "default": "DIRECT"},
                 "cell": {"anyOf": [{"$ref": "#/$defs/cellConstraints"}, {"type": "null"}], "default": None},
                 "free": {"anyOf": [{"$ref": "#/$defs/freePlacement"}, {"type": "null"}], "default": None}}, ("node",))
hud_cell = obj({"id": NULLABLE_S, "colspan": POS,
                "constraints": {"$ref": "#/$defs/cellConstraints"},
                "content": {"anyOf": [hud_node, {"type": "null"}]}}, ("constraints",))
hud_table = obj({"columns": NONNEG, "rows": arr(obj({"cells": arr(hud_cell)}))})
nullable_int = {"type": ["integer", "null"]}
text_style = {"text": NULLABLE_S, "styleName": NULLABLE_S, "fontAssetId": nullable_int}
image = obj({"source": {"enum": ["REGION", "DRAWABLE"]}, "resourceName": NULLABLE_S})
image_override = {n: {"anyOf": [image, {"type": "null"}]}
                  for n in "imageUp imageDown imageOver imageDisabled imageChecked imageCheckedDown imageCheckedOver".split()}
slider = obj({"orientation": {"enum": ["HORIZONTAL", "VERTICAL"]},
              **{n: N for n in "min max stepSize value".split()},
              "styleName": NULLABLE_S, "disabled": B})
window = {"title": NULLABLE_S, "styleName": NULLABLE_S, "fontAssetId": nullable_int,
          **{n: B for n in "movable resizable modal keepWithinStage".split()}}
widget = {
    "image": image,
    "label": obj(text_style),
    "textraLabel": obj({**text_style, "typingEnabled": B}),
    "textButton": obj(text_style),
    "imageButton": obj({"styleName": NULLABLE_S, **image_override}),
    "imageTextButton": obj({**text_style, **image_override}),
    "textField": obj({**text_style, "messageText": NULLABLE_S, "maxLength": NONNEG, "passwordMode": B}),
    "selectBox": obj({"items": arr(S), "selectedIndex": I, "styleName": NULLABLE_S,
                      "fontAssetId": nullable_int, "maxListCount": NONNEG, "disabled": B}),
    "list": obj({"items": arr(S), "selectedIndex": I, "required": B,
                 "styleName": NULLABLE_S, "fontAssetId": nullable_int}),
    "checkBox": obj({**text_style, "checked": B, "disabled": B}),
    "slider": slider, "progressBar": slider,
    "container": obj({"clip": B}),
    "scrollPane": obj({"styleName": NULLABLE_S,
                       **{n: B for n in "scrollingDisabledX scrollingDisabledY fadeScrollBars flickScroll smoothScrolling overscrollX overscrollY".split()}}),
    "window": obj(window),
    "dialog": obj({**window, "resultButtons": arr(obj({"button": hud_node,
                        "resultId": NULLABLE_S, "closeAfterActivation": B}))}),
}
hud_defs = {
    "cellConstraints": obj({**{n: {"type": ["number", "null"]} for n in "minWidth minHeight prefWidth prefHeight maxWidth maxHeight".split()},
                            **{n: N for n in "padTop padRight padBottom padLeft".split()},
                            **{n: B for n in "fillX fillY expandX expandY uniformX uniformY".split()},
                            "horizontalAlign": {"enum": ["LEFT", "CENTER", "RIGHT"]},
                            "verticalAlign": {"enum": ["BOTTOM", "CENTER", "TOP"]}},
                           ("uniformX", "uniformY")),
    "freePlacement": obj({"horizontalAnchor": {"enum": ["LEFT", "CENTER", "RIGHT"]},
                          "verticalAnchor": {"enum": ["TOP", "CENTER", "BOTTOM"]},
                          **{n: N for n in "pivotX pivotY offsetX offsetY".split()}}),
    "child": hud_child,
    "node": obj({"id": NONS, "kind": {"enum": ["GROUP", "TABLE", "STACK", "CONTAINER", "SCROLL_PANE", "WINDOW", "DIALOG", "IMAGE", "LABEL", "TEXTRA_LABEL", "TEXT_BUTTON", "IMAGE_BUTTON", "IMAGE_TEXT_BUTTON", "TEXT_FIELD", "SELECT_BOX", "LIST", "CHECK_BOX", "SLIDER", "PROGRESS_BAR"]},
                 "actor": obj({"width": {"type": "number", "minimum": 0, "default": 0},
                               "height": {"type": "number", "minimum": 0, "default": 0}}),
                 "fillParent": {"type": "boolean", "default": False},
                 "visible": {"type": "boolean", "default": True},
                 "tooltip": {"anyOf": [obj(text_style), {"type": "null"}]},
                 "windowActions": {**arr(obj({"targetId": NONS, "action": {"enum": ["SHOW", "HIDE"], "default": "SHOW"}})), "default": []},
                 **{n: {"anyOf": [shape, {"type": "null"}]} for n, shape in widget.items()},
                 "table": {"anyOf": [hud_table, {"type": "null"}]},
                 "children": {**arr({"$ref": "#/$defs/child"}), "default": []}}, ("id", "kind")),
}
write(hud_file, {"title": "Pixscape HUD document 2",
                 **obj({"schemaVersion": {"const": 2}, "root": hud_node}, ("schemaVersion", "root"), extra=False),
                 "$defs": hud_defs,
                 "description": "Recursive Scene2D document. Widget fields are typed here; kind/payload matching, layout and resource rules are checked by HudDocumentValidator in Runtime."})
