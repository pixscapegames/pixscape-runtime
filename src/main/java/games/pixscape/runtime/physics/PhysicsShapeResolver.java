package games.pixscape.runtime.physics;

import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/**
 * Pure authored-to-resolved physics shape mapping.
 */
public final class PhysicsShapeResolver {
    public ResolvedPhysicsShape resolve(PhysicsShapeData source) {
        if (source == null) {
            throw new IllegalArgumentException("PhysicsShapeData cannot be null.");
        }
        source.validateStructure();
        if (source.spatialBlockId > 0) {
            throw new IllegalArgumentException(
                    "Linked physics shape resolution is not available before "
                            + "the Spatial resolver slice.");
        }
        return ResolvedPhysicsShape.fromGeometry(source);
    }

    public ResolvedPhysicsShape resolveLinked(
            PhysicsShapeData source,
            SpatialBlockData block,
            TiledMapLayerData map,
            float bodyWorldX,
            float bodyWorldY,
            float bodyRotationRad,
            float pixelsPerMeter,
            int ownerEntityId) {
        if (source == null) {
            throw invalidLinked(0, 0, ownerEntityId,
                    "source must not be null.");
        }
        try {
            source.validateStructure();
        } catch (IllegalArgumentException failure) {
            throw invalidLinked(source.physicsShapeId, source.spatialBlockId,
                    ownerEntityId, "source structure is invalid: "
                            + failure.getMessage());
        }
        if (source.spatialBlockId <= 0) {
            throw invalidLinked(source, ownerEntityId,
                    "spatialBlockId must be positive.");
        }
        if (source.geometry != null) {
            throw invalidLinked(source, ownerEntityId,
                    "linked geometry must be null.");
        }
        if (block == null) {
            throw invalidLinked(source, ownerEntityId,
                    "referenced SpatialBlockData is absent.");
        }
        if (block.id != source.spatialBlockId) {
            throw invalidLinked(source, ownerEntityId,
                    "block.id does not match spatialBlockId (block.id="
                            + block.id + ").");
        }
        requireFinite(source, ownerEntityId, block.x, "block.x");
        requireFinite(source, ownerEntityId, block.y, "block.y");
        requirePositiveFinite(source, ownerEntityId, block.width, "block.width");
        requirePositiveFinite(source, ownerEntityId, block.depth, "block.depth");
        requireFinite(source, ownerEntityId, block.altitude, "block.altitude");
        if (map == null) {
            throw invalidLinked(source, ownerEntityId,
                    "map must not be null.");
        }
        if (map.projection == null) {
            throw invalidLinked(source, ownerEntityId,
                    "map.projection must not be null.");
        }
        if (map.tileWidth <= 0) {
            throw invalidLinked(source, ownerEntityId,
                    "map.tileWidth must be positive.");
        }
        if (map.tileHeight <= 0) {
            throw invalidLinked(source, ownerEntityId,
                    "map.tileHeight must be positive.");
        }
        requireFinite(source, ownerEntityId, bodyWorldX, "bodyWorldX");
        requireFinite(source, ownerEntityId, bodyWorldY, "bodyWorldY");
        requireFinite(source, ownerEntityId, bodyRotationRad, "bodyRotationRad");
        requirePositiveFinite(source, ownerEntityId, pixelsPerMeter,
                "pixelsPerMeter");
        if (ownerEntityId < 0) {
            throw invalidLinked(source, ownerEntityId,
                    "ownerEntityId must be non-negative.");
        }

        float x1 = block.x + block.width;
        float y1 = block.y + block.depth;
        requireFinite(source, ownerEntityId, x1, "block.x + block.width");
        requireFinite(source, ownerEntityId, y1, "block.y + block.depth");

        float[] vertices = new float[8];
        map.projectSpatialPoint(block.x, block.y, block.altitude, vertices, 0);
        map.projectSpatialPoint(x1, block.y, block.altitude, vertices, 2);
        map.projectSpatialPoint(x1, y1, block.altitude, vertices, 4);
        map.projectSpatialPoint(block.x, y1, block.altitude, vertices, 6);

        float cos = MathUtils.cos(bodyRotationRad);
        float sin = MathUtils.sin(bodyRotationRad);
        for (int i = 0; i < vertices.length; i += 2) {
            float dx = vertices[i] - bodyWorldX;
            float dy = vertices[i + 1] - bodyWorldY;
            vertices[i] = (cos * dx + sin * dy) / pixelsPerMeter;
            vertices[i + 1] = (-sin * dx + cos * dy) / pixelsPerMeter;
        }

        ResolvedPhysicsShape resolved = new ResolvedPhysicsShape();
        resolved.physicsShapeId = source.physicsShapeId;
        resolved.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        resolved.polygonVertices = vertices;
        resolved.polygonVertexCount = 4;
        resolved.offsetX = 0f;
        resolved.offsetY = 0f;
        resolved.angleDegrees = 0f;
        resolved.density = source.density;
        resolved.friction = source.friction;
        resolved.restitution = source.restitution;
        resolved.sensor = source.sensor;
        resolved.categoryBits = source.categoryBits;
        resolved.maskBits = source.maskBits;
        resolved.groupIndex = source.groupIndex;
        resolved.enabled = source.enabled;
        resolved.diagnosticSource =
                "spatial-block(" + source.spatialBlockId + ")";
        return resolved;
    }

    private static void requireFinite(
            PhysicsShapeData source, int ownerEntityId, float value, String field) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw invalidLinked(source, ownerEntityId,
                    field + " must be finite.");
        }
    }

    private static void requirePositiveFinite(
            PhysicsShapeData source, int ownerEntityId, float value, String field) {
        requireFinite(source, ownerEntityId, value, field);
        if (value <= 0f) {
            throw invalidLinked(source, ownerEntityId,
                    field + " must be positive.");
        }
    }

    private static IllegalArgumentException invalidLinked(
            PhysicsShapeData source, int ownerEntityId, String detail) {
        return invalidLinked(source.physicsShapeId, source.spatialBlockId,
                ownerEntityId, detail);
    }

    private static IllegalArgumentException invalidLinked(
            int physicsShapeId, int spatialBlockId, int ownerEntityId, String detail) {
        return new IllegalArgumentException(
                "Invalid linked PhysicsShapeData: physicsShapeId="
                        + physicsShapeId + ", spatialBlockId=" + spatialBlockId
                        + ", ownerEntityId=" + ownerEntityId + ": " + detail);
    }
}
