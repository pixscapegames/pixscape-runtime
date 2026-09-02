package games.pixscape.runtime.gameobject;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.render.SortKey64;

/** Strict reader/writer for the independent Game Object asset schema. */
public final class GameObjectAssetLoader {
    private final Json json = createJson();

    public GameObjectAsset load(FileHandle file) {
        requireExtension(file);
        if (!file.exists()) throw failure(file, "asset does not exist");
        String serialized = file.readString("UTF-8");
        requireSchemaVersion(new JsonReader().parse(serialized), file);
        GameObjectAsset asset = json.fromJson(GameObjectAsset.class, serialized);
        validate(asset, file);
        return asset;
    }

    public void save(FileHandle file, GameObjectAsset asset) {
        requireExtension(file);
        validate(asset, file);
        if (file.parent() != null) file.parent().mkdirs();
        file.writeString(json.prettyPrint(asset), false, "UTF-8");
    }

    public String toJson(GameObjectAsset asset) {
        validate(asset, null);
        return json.prettyPrint(asset);
    }

    public GameObjectAsset fromJson(String serialized) {
        if (serialized == null) throw failure(null, "JSON is required");
        JsonValue root = new JsonReader().parse(serialized);
        requireSchemaVersion(root, null);
        GameObjectAsset asset = json.fromJson(GameObjectAsset.class, serialized);
        validate(asset, null);
        return asset;
    }

    public void validate(GameObjectAsset asset, FileHandle file) {
        if (asset == null) throw failure(file, "asset is null");
        if (asset.schemaVersion != GameObjectAsset.SCHEMA_VERSION) {
            throw failure(file, "schemaVersion must be " + GameObjectAsset.SCHEMA_VERSION
                    + ", found " + asset.schemaVersion);
        }
        if (asset.entities == null || asset.entities.isEmpty()) {
            throw failure(file, "entities must contain exactly one top-level root");
        }
        IntMap<GameObjectAsset.GameObjectEntityData> byId = new IntMap<>();
        int topLevelRoots = 0;
        for (GameObjectAsset.GameObjectEntityData entity : asset.entities) {
            if (entity == null) throw failure(file, "entities contains null");
            if (entity.sourceEntityId < 0) {
                throw failure(file, "sourceEntityId must be non-negative, found "
                        + entity.sourceEntityId);
            }
            if (byId.containsKey(entity.sourceEntityId)) {
                throw failure(file, "duplicate sourceEntityId " + entity.sourceEntityId);
            }
            byId.put(entity.sourceEntityId, entity);
            if (entity.parentSourceEntityId == -1) topLevelRoots++;
            validateAuthoredEntity(entity, file);
        }
        if (topLevelRoots != 1) {
            throw failure(file, "exactly one top-level root is required, found " + topLevelRoots);
        }
        GameObjectAsset.GameObjectEntityData root = byId.get(asset.rootSourceEntityId);
        if (root == null) {
            throw failure(file, "rootSourceEntityId " + asset.rootSourceEntityId + " is missing");
        }
        if (root.parentSourceEntityId != -1) {
            throw failure(file, "rootSourceEntityId must identify the top-level entity");
        }
        if (root.gameObject == null) {
            throw failure(file, "top-level root requires Game Object root semantics");
        }
        for (GameObjectAsset.GameObjectEntityData entity : asset.entities) {
            if (entity == root) continue;
            GameObjectAsset.GameObjectEntityData parent = byId.get(entity.parentSourceEntityId);
            if (parent == null) {
                throw failure(file, "sourceEntityId " + entity.sourceEntityId
                        + " references missing parentSourceEntityId "
                        + entity.parentSourceEntityId);
            }
            if (parent.gameObject == null) {
                throw failure(file, "parent sourceEntityId " + parent.sourceEntityId
                        + " must have Game Object root semantics");
            }
        }
        validateAcyclic(asset, byId, file);
        for (GameObjectAsset.GameObjectEntityData entity : asset.entities) {
            validateObjectReferences(entity.customProperties, byId, file, entity.sourceEntityId);
        }
        validatePhysics(asset, byId, file);
    }

    private static void validateAuthoredEntity(
            GameObjectAsset.GameObjectEntityData entity, FileHandle file) {
        if (entity.transform == null) {
            throw failure(file, "sourceEntityId " + entity.sourceEntityId
                    + " requires an authored Transform");
        }
        requireFinite(entity.transform.x, "transform.x", entity.sourceEntityId, file);
        requireFinite(entity.transform.y, "transform.y", entity.sourceEntityId, file);
        requireFinite(entity.transform.rotationRad, "transform.rotationRad", entity.sourceEntityId, file);
        requireFinite(entity.transform.scaleX, "transform.scaleX", entity.sourceEntityId, file);
        requireFinite(entity.transform.scaleY, "transform.scaleY", entity.sourceEntityId, file);
        requireFinite(entity.transform.originX, "transform.originX", entity.sourceEntityId, file);
        requireFinite(entity.transform.originY, "transform.originY", entity.sourceEntityId, file);
        if (entity.gameObject != null
                && (entity.transform.scaleX <= 0f || entity.transform.scaleY <= 0f
                || Float.compare(entity.transform.scaleX, entity.transform.scaleY) != 0)) {
            throw failure(file, "Game Object parent sourceEntityId " + entity.sourceEntityId
                    + " requires positive uniform authored scale");
        }
        if (entity.entityIndex != null
                && (entity.entityIndex.zIndex < SortKey64.MIN_Z
                || entity.entityIndex.zIndex > SortKey64.MAX_Z)) {
            throw failure(file, "sourceEntityId " + entity.sourceEntityId
                    + " zIndex is outside the supported range");
        }
        if (entity.gameObject != null && (entity.assetRef != null || entity.animation != null
                || entity.pointLight != null || entity.coneLight != null)) {
            throw failure(file, "Game Object root sourceEntityId " + entity.sourceEntityId
                    + " must be composition-only");
        }
        if (entity.customProperties != null) {
            try {
                entity.customProperties.validate();
            } catch (RuntimeException ex) {
                throw failure(file, "sourceEntityId " + entity.sourceEntityId
                        + " has invalid Custom Properties: " + ex.getMessage());
            }
        }
        if (entity.physicsBody != null && entity.physicsShapes == null) {
            throw failure(file, "sourceEntityId " + entity.sourceEntityId
                    + " requires a Physics Shapes list when it has a Physics Body");
        }
        if (entity.physicsBody == null && entity.physicsShapes != null && !entity.physicsShapes.isEmpty()) {
            throw failure(file, "sourceEntityId " + entity.sourceEntityId
                    + " has Physics Shapes without a Physics Body");
        }
        if (entity.physicsBody != null) validateBody(entity, file);
        if (entity.physicsShapes != null) validateShapes(entity, file);
    }

    private static void validateBody(GameObjectAsset.GameObjectEntityData entity, FileHandle file) {
        GameObjectAsset.PhysicsBodyData body = entity.physicsBody;
        if (body.type != PhysicsBodyComponent.STATIC
                && body.type != PhysicsBodyComponent.KINEMATIC
                && body.type != PhysicsBodyComponent.DYNAMIC) {
            throw failure(file, "sourceEntityId " + entity.sourceEntityId
                    + " has unsupported Physics Body type " + body.type);
        }
        requireFinite(body.gravityScale, "physicsBody.gravityScale", entity.sourceEntityId, file);
        requireFinite(body.linearDamping, "physicsBody.linearDamping", entity.sourceEntityId, file);
        requireFinite(body.angularDamping, "physicsBody.angularDamping", entity.sourceEntityId, file);
    }

    private static void validateShapes(GameObjectAsset.GameObjectEntityData entity, FileHandle file) {
        IntSet localIds = new IntSet();
        for (int i = 0; i < entity.physicsShapes.size(); i++) {
            GameObjectAsset.PhysicsShapeData source = entity.physicsShapes.get(i);
            if (source == null || source.localShapeId <= 0 || !localIds.add(source.localShapeId)) {
                throw failure(file, "sourceEntityId " + entity.sourceEntityId
                        + " has invalid or duplicate asset-local Physics shape ID");
            }
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = source.localShapeId;
            shape.geometry = source.geometry != null ? source.geometry.copy() : null;
            shape.density = source.density;
            shape.friction = source.friction;
            shape.restitution = source.restitution;
            shape.sensor = source.sensor;
            shape.categoryBits = source.categoryBits;
            shape.maskBits = source.maskBits;
            shape.groupIndex = source.groupIndex;
            shape.enabled = source.enabled;
            try {
                shape.validateStructure();
            } catch (IllegalArgumentException ex) {
                throw failure(file, "sourceEntityId " + entity.sourceEntityId
                        + " has invalid Physics shape: " + ex.getMessage());
            }
        }
    }

    private static void validatePhysics(GameObjectAsset asset,
                                        IntMap<GameObjectAsset.GameObjectEntityData> byId,
                                        FileHandle file) {
        for (GameObjectAsset.GameObjectEntityData entity : asset.entities) {
            if (entity.physicsBody == null) continue;
            int parentId = entity.parentSourceEntityId;
            while (parentId != -1) {
                GameObjectAsset.GameObjectEntityData parent = byId.get(parentId);
                if (parent == null || parent.gameObject == null) {
                    throw failure(file, "sourceEntityId " + entity.sourceEntityId
                            + " has invalid Physics hierarchy parent");
                }
                if (Float.compare(parent.transform.scaleX, 1f) != 0
                        || Float.compare(parent.transform.scaleY, 1f) != 0) {
                    throw failure(file, "sourceEntityId " + entity.sourceEntityId
                            + " requires every Game Object Physics ancestor scale to be (1,1)");
                }
                parentId = parent.parentSourceEntityId;
            }
        }
    }

    private static void validateAcyclic(
            GameObjectAsset asset, IntMap<GameObjectAsset.GameObjectEntityData> byId,
            FileHandle file) {
        IntSet visiting = new IntSet();
        IntSet complete = new IntSet();
        for (GameObjectAsset.GameObjectEntityData entity : asset.entities) {
            int current = entity.sourceEntityId;
            visiting.clear();
            while (current != -1 && !complete.contains(current)) {
                if (!visiting.add(current)) {
                    throw failure(file, "hierarchy contains a cycle at sourceEntityId " + current);
                }
                GameObjectAsset.GameObjectEntityData currentEntity = byId.get(current);
                if (currentEntity == null) break;
                current = currentEntity.parentSourceEntityId;
            }
            IntSet.IntSetIterator iterator = visiting.iterator();
            while (iterator.hasNext) complete.add(iterator.next());
        }
    }

    private static void validateObjectReferences(
            PropertySet properties, IntMap<GameObjectAsset.GameObjectEntityData> byId,
            FileHandle file, int ownerSourceId) {
        if (properties == null) return;
        Array<String> names = new Array<>();
        properties.copyNamesTo(names);
        for (String name : names) {
            PropertyValue value = properties.valueCopy(name);
            if (value.type() == PropertyType.OBJECT) {
                int referencedSourceId = value.asObjectStableId();
                if (referencedSourceId != -1 && !byId.containsKey(referencedSourceId)) {
                    throw failure(file, "sourceEntityId " + ownerSourceId
                            + " Custom Property '" + name
                            + "' contains unsupported external OBJECT reference "
                            + referencedSourceId);
                }
            } else if (value.type() == PropertyType.CLASS) {
                validateObjectReferences(value.classPropertiesCopy(), byId, file, ownerSourceId);
            }
        }
    }

    private static void requireExtension(FileHandle file) {
        if (file == null) throw failure(null, "file is required");
        if (!file.name().endsWith(GameObjectAsset.EXTENSION)) {
            throw failure(file, "unsupported extension; expected " + GameObjectAsset.EXTENSION);
        }
    }

    private static void requireSchemaVersion(JsonValue root, FileHandle file) {
        JsonValue value = root != null && root.isObject() ? root.get("schemaVersion") : null;
        if (value == null || !value.isLong()) {
            throw failure(file, "numeric schemaVersion " + GameObjectAsset.SCHEMA_VERSION
                    + " is required");
        }
        if (value.asInt() != GameObjectAsset.SCHEMA_VERSION) {
            throw failure(file, "unsupported schemaVersion " + value.asInt()
                    + "; expected " + GameObjectAsset.SCHEMA_VERSION);
        }
    }

    private static void requireFinite(float value, String field, int sourceId, FileHandle file) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw failure(file, "sourceEntityId " + sourceId + " has non-finite " + field);
        }
    }

    private static Json createJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(false);
        json.setUsePrototypes(false);
        return json;
    }

    private static IllegalArgumentException failure(FileHandle file, String detail) {
        return new IllegalArgumentException("Invalid Game Object asset "
                + (file != null ? file.path() : "<memory>") + ": " + detail + ".");
    }
}
