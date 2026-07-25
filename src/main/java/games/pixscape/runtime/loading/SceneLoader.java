package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.spatial.SpatialBlockData;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public final class SceneLoader {

    private SceneLoader() {
    }

    /**
     * Loads a scene from the given file.
     *
     * @param world             ECS world
     * @param inFile            scene file (ex: scenes/scene1.json)
     * @param clearContentFirst if true, clears the world before loading
     */
    public static SaveFileFormat loadScene(World world,
                                           FileHandle inFile,
                                           boolean clearContentFirst,
                                           SceneMetaRuntime sceneMeta) {

        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        if (wsm.getSerializer() == null || !(wsm.getSerializer() instanceof JsonArtemisSerializer)) {
            wsm.setSerializer(new JsonArtemisSerializer(world));
        }

        if (!inFile.exists()) {
            throw new RuntimeException("Scene file not found: " + inFile.path());
        }

        String serialized = inFile.readString("UTF-8");
        rejectObsoletePhysicsModel(serialized, inFile);

        if (clearContentFirst) {
            clearWorldContent(world);
        }

        try (InputStream in = new ByteArrayInputStream(serialized.getBytes("UTF-8"))) {
            SaveFileFormat format = wsm.load(in, SaveFileFormat.class);
            validatePersistentIdentities(format, world, sceneMeta, inFile);
            return format;

        } catch (Exception e) {
            clearWorldContent(world);
            String detail = e.getMessage();
            throw new RuntimeException(
                    "Error while loading scene: " + inFile.path()
                            + (detail != null && !detail.isEmpty() ? ": " + detail : ""),
                    e);
        }
    }

    private static void rejectObsoletePhysicsModel(String serialized, FileHandle sceneFile) {
        if (serialized == null) return;
        if (containsAdjacent(serialized, "\"Physics", "Fixtures", "Component\"")
                || containsAdjacent(serialized, "\"Physics", "Authoring", "Component\"")
                || containsAdjacent(serialized, "\"Fixture", "Def", "Data\"")) {
            throw new IllegalArgumentException(
                    "Scene uses the incompatible Physics Model V1 and cannot be loaded: "
                            + sceneFile.path());
        }
    }

    private static boolean containsAdjacent(
            String text, String first, String second, String third) {
        int offset = text.indexOf(first);
        while (offset >= 0) {
            int secondOffset = offset + first.length();
            int thirdOffset = secondOffset + second.length();
            if (text.indexOf(second, secondOffset) == secondOffset
                    && text.indexOf(third, thirdOffset) == thirdOffset) {
                return true;
            }
            offset = text.indexOf(first, offset + 1);
        }
        return false;
    }

    private static void validatePersistentIdentities(SaveFileFormat format, World world,
            SceneMetaRuntime sceneMeta, FileHandle sceneFile) {
        if (sceneMeta == null) {
            throw new IllegalArgumentException("Scene metadata is required to validate persistent identities: "
                    + sceneFile.path());
        }
        if (format == null || format.entities == null) {
            throw new IllegalArgumentException("Serialized entity list is missing: " + sceneFile.path());
        }
        if (sceneMeta.nextEntityStableId <= 0) {
            throw identityFailure(sceneFile, "entityStableId", -1, -1, sceneMeta.nextEntityStableId,
                    "high-water must be positive");
        }

        ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<TransformComponent> transforms = world.getMapper(TransformComponent.class);
        ComponentMapper<PhysicsShapesComponent> physicsShapes = world.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<SpatialBlocksComponent> spatialBlocks = world.getMapper(SpatialBlocksComponent.class);
        IntSet stableIds = new IntSet();
        IntArray physicsIds = new IntArray();
        int maxStableId = 0;
        int[] data = format.entities.getData();
        for (int i = 0; i < format.entities.size(); i++) {
            int entityId = data[i];
            TransformComponent transform = transforms.getSafe(entityId, null);
            if (transform != null) transform.refreshCaches();
            PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
            if (identity != null) {
                if (identity.stableId <= 0) {
                    throw identityFailure(sceneFile, "entityStableId", entityId,
                            identity.stableId, sceneMeta.nextEntityStableId, "ID must be positive");
                }
                if (!stableIds.add(identity.stableId)) {
                    throw identityFailure(sceneFile, "entityStableId", entityId,
                            identity.stableId, sceneMeta.nextEntityStableId, "duplicate ID");
                }
                maxStableId = Math.max(maxStableId, identity.stableId);
            }

            PhysicsShapesComponent shapes = physicsShapes.getSafe(entityId, null);
            if (shapes != null && shapes.shapes != null) {
                for (int shapeIndex = 0; shapeIndex < shapes.shapes.size; shapeIndex++) {
                    PhysicsShapeData shape = shapes.shapes.get(shapeIndex);
                    if (shape == null) {
                        throw new IllegalArgumentException("Scene '" + sceneFile.path()
                                + "' contains a null PhysicsShapeData on entity " + entityId + ".");
                    }
                    if (shape.directGeometry == null) {
                        throw new IllegalArgumentException(
                                "Scene '" + sceneFile.path() + "', entityId " + entityId
                                        + ", physicsShapeId " + shape.physicsShapeId
                                        + ": directGeometry is missing; clean break Physics Model.");
                    }
                    shape.validateStructure();
                    physicsIds.add(shape.physicsShapeId);
                }
            }

            validateSpatialBlockIdentities(spatialBlocks.getSafe(entityId, null), identity, sceneFile, entityId);
        }
        if (sceneMeta.nextEntityStableId <= maxStableId) {
            throw identityFailure(sceneFile, "entityStableId", -1, maxStableId, sceneMeta.nextEntityStableId,
                    "high-water must be greater than max ID");
        }

        try {
            new PhysicsShapeIdAllocator(sceneMeta).validatePersistedPhysicsShapeIds(physicsIds.toArray());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Scene '" + sceneFile.path()
                    + "', domain physicsShapeId, high-water " + sceneMeta.nextPhysicsShapeId
                    + ": " + ex.getMessage(), ex);
        }
    }

    private static void validateSpatialBlockIdentities(SpatialBlocksComponent component,
            PixscapeIdentityComponent ownerIdentity, FileHandle sceneFile, int entityId) {
        if (component == null) return;
        if (ownerIdentity == null || ownerIdentity.stableId <= 0) {
            throw identityFailure(sceneFile, "spatialOwnerStableId", entityId,
                    ownerIdentity != null ? ownerIdentity.stableId : -1, component.nextSpatialBlockId,
                    "owner has no positive entityStableId");
        }
        if (component.nextSpatialBlockId <= 0) {
            throw identityFailure(sceneFile, "spatialBlockId", entityId, ownerIdentity.stableId,
                    component.nextSpatialBlockId, "high-water must be positive");
        }
        IntSet ids = new IntSet();
        int max = 0;
        if (component.blocks != null) {
            for (int i = 0; i < component.blocks.size; i++) {
                SpatialBlockData block = component.blocks.get(i);
                if (block == null) {
                    throw identityFailure(sceneFile, "spatialBlockId", entityId, -1, component.nextSpatialBlockId,
                            "null block");
                }
                if (block.id <= 0) {
                    throw identityFailure(sceneFile, "spatialBlockId", entityId, block.id, component.nextSpatialBlockId,
                            "ID must be positive");
                }
                if (!ids.add(block.id)) {
                    throw identityFailure(sceneFile, "spatialBlockId", entityId, block.id, component.nextSpatialBlockId,
                            "duplicate ID");
                }
                max = Math.max(max, block.id);
            }
        }
        if (component.nextSpatialBlockId <= max) {
            throw identityFailure(sceneFile, "spatialBlockId", entityId, max, component.nextSpatialBlockId,
                    "high-water must be greater than max ID");
        }
    }

    private static IllegalArgumentException identityFailure(FileHandle sceneFile, String domain, int entityId,
            int badId, int highWater, String reason) {
        return new IllegalArgumentException("Scene '" + sceneFile.path() + "', domain " + domain + ", entity "
                + entityId + ", bad/max ID " + badId + ", high-water " + highWater + ": " + reason + ".");
    }

    private static void clearWorldContent(World world) {
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            world.delete(data[i]);
        }

        world.process();
    }

    public static void forceFullRenderDirty(World world) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty == null) return;

        ComponentMapper<VisibilityComponent> mVis = world.getMapper(VisibilityComponent.class);

        EntitySubscription geometrySub = world.getAspectSubscriptionManager().get(
                Aspect.all(TransformComponent.class,
                        DimensionsComponent.class,
                        OrientedBoundsComponent.class,
                        AABBComponent.class)
        );
        IntBag geometryBag = geometrySub.getEntities();
        int[] geometryData = geometryBag.getData();

        for (int i = 0, n = geometryBag.size(); i < n; i++) {
            int e = geometryData[i];
            dirty.geometry(e, GeometryDirty.ALL);
        }

        EntitySubscription renderSub = world.getAspectSubscriptionManager().get(
                Aspect.all(
                        OrientedBoundsComponent.class,
                        RenderMaterialComponent.class,
                        EntityIndexComponent.class,
                        VisibilityComponent.class
                ).one(
                        TextureRegionComponent.class,
                        PointLightComponent.class,
                        ConeLightComponent.class
                )
        );
        IntBag renderBag = renderSub.getEntities();
        int[] renderData = renderBag.getData();

        for (int i = 0, n = renderBag.size(); i < n; i++) {
            int e = renderData[i];
            if (mVis != null && mVis.has(e)) {
                VisibilityComponent vis = mVis.get(e);
                vis.culledByFrustum = false;
                vis.inView = true;
            }
            dirty.mark(e, DirtyBits.EVERYTHING);
        }
    }

    public static int countTiledLayers(FileHandle sceneFile) {

        if (!sceneFile.exists()) return 0;

        JsonValue root = new JsonReader().parse(sceneFile);

        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return 0;

        int count = 0;

        for (JsonValue ent = entities.child; ent != null; ent = ent.next) {

            JsonValue comps = ent.get("components");
            if (comps == null || !comps.isObject()) continue;

            if (comps.has("TiledLayerComponent")) {
                count++;
            }
        }

        return count;
    }
}
