package games.pixscape.runtime.physics;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockGeometry;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/**
 * Pure authored-to-resolved physics shape mapping.
 */
public final class PhysicsShapeResolver {
    public ResolvedPhysicsShape resolve(PhysicsShapeData source) {
        if (source == null) {
            throw new IllegalArgumentException("PhysicsShapeData cannot be null.");
        }
        if (source.directGeometry == null) {
            throw new IllegalArgumentException(
                    "PhysicsShapeData " + source.physicsShapeId
                            + ": external/spatial geometry is unavailable before binding Phase D.");
        }
        source.validateStructure();
        return ResolvedPhysicsShape.fromDirect(source);
    }

    public ResolvedPhysicsShape resolveLinked(PhysicsShapeData source, int spatialOwnerStableId,
                                              SpatialBlockData block, TiledMapLayerData map,
                                              float pixelsPerMeter) {
        if (source == null) throw new IllegalArgumentException("linked PhysicsShapeData cannot be null.");
        try {
            source.validateAuthoredProperties();
        } catch (IllegalArgumentException failure) {
            throw invalid(source, spatialOwnerStableId, block, failure.getMessage(), failure);
        }
        if (source.directGeometry != null) throw invalid(source, spatialOwnerStableId, block, "linked shape must not have directGeometry.");
        if (!source.enabled) throw invalid(source, spatialOwnerStableId, block, "linked shape must be enabled.");
        if (spatialOwnerStableId <= 0) throw invalid(source, spatialOwnerStableId, block, "ownerStableId must be positive.");
        if (block == null) throw invalid(source, spatialOwnerStableId, null, "block must not be null.");
        if (block.id <= 0) throw invalid(source, spatialOwnerStableId, block, "blockId must be positive.");
        if (map == null) throw invalid(source, spatialOwnerStableId, block, "map must not be null.");
        if (Float.isNaN(pixelsPerMeter) || Float.isInfinite(pixelsPerMeter) || pixelsPerMeter <= 0f) throw invalid(source, spatialOwnerStableId, block, "pixelsPerMeter must be positive and finite.");
        float[] pixels = new float[8];
        try {
            SpatialBlockGeometry.projectBaseFootprint(map, block, pixels);
        } catch (IllegalArgumentException failure) {
            throw invalid(source, spatialOwnerStableId, block, failure.getMessage(), failure);
        }
        ResolvedPhysicsShape resolved = new ResolvedPhysicsShape();
        resolved.physicsShapeId = source.physicsShapeId;
        resolved.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
        resolved.polygonVertexCount = 4;
        resolved.polygonVertices = new float[8];
        for (int i = 0; i < 8; i++) resolved.polygonVertices[i] = pixels[i] / pixelsPerMeter;
        resolved.density = source.density; resolved.friction = source.friction; resolved.restitution = source.restitution;
        resolved.sensor = source.sensor; resolved.categoryBits = source.categoryBits; resolved.maskBits = source.maskBits; resolved.groupIndex = source.groupIndex;
        resolved.enabled = true; resolved.sourceKind = ResolvedPhysicsShape.SOURCE_SPATIAL_BLOCK;
        resolved.spatialOwnerStableId = spatialOwnerStableId; resolved.spatialBlockId = block.id;
        return resolved;
    }

    private static IllegalArgumentException invalid(PhysicsShapeData source, int ownerStableId,
                                                     SpatialBlockData block, String detail) {
        return invalid(source, ownerStableId, block, detail, null);
    }

    private static IllegalArgumentException invalid(PhysicsShapeData source, int ownerStableId,
                                                     SpatialBlockData block, String detail,
                                                     Throwable cause) {
        String message = "linked PhysicsShapeData " + source.physicsShapeId
                + ", ownerStableId " + ownerStableId + ", blockId "
                + (block != null ? block.id : "null") + ": " + detail;
        return cause != null ? new IllegalArgumentException(message, cause)
                : new IllegalArgumentException(message);
    }
}
