package games.pixscape.runtime.gameobject;

import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.property.PropertySet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authored, hierarchical data stored in one {@value #EXTENSION} asset. */
public final class GameObjectAsset {
    public static final String EXTENSION = ".gameobject";
    public static final int SCHEMA_VERSION = 2;

    public int schemaVersion = SCHEMA_VERSION;
    public int rootSourceEntityId = -1;
    public List<GameObjectEntityData> entities = new ArrayList<>();

    /** One authored entity. IDs and parent references are local to this asset. */
    public static final class GameObjectEntityData {
        public int sourceEntityId;
        public int parentSourceEntityId = -1;
        public TransformData transform;
        public EntityIndexData entityIndex;
        public MetaData meta;
        public IdentityData identity;
        public TagsData tags;
        public PropertySet customProperties;
        public VisibilityData visibility;
        public BoundsFlagsData boundsFlags;
        public DimensionsData dimensions;
        public QuadDeformData quadDeform;
        public RenderMaterialData renderMaterial;
        public AssetRefData assetRef;
        public TintData tint;
        public AnimationData animation;
        public ShaderParamsData shaderParams;
        public RepeatData repeat;
        public PointLightData pointLight;
        public ConeLightData coneLight;
        /** Authored Body definition; runtime Box2D state is intentionally absent. */
        public PhysicsBodyData physicsBody;
        /** Ordered asset-local shapes; their IDs are never Scene physicsShapeIds. */
        public List<PhysicsShapeData> physicsShapes = new ArrayList<>();
        /** Present for the top-level root and nested Game Object roots. */
        public GameObjectData gameObject;
    }

    public static final class TransformData {
        public float x, y, rotationRad, scaleX, scaleY, originX, originY;
    }

    /** zIndex is local to the immediate parent. Effective/global Layer is derived scene state. */
    public static final class EntityIndexData { public int zIndex; }
    public static final class MetaData { public String kind; }
    public static final class IdentityData { public String name; }
    public static final class TagsData { public List<String> values = new ArrayList<>(); }
    public static final class VisibilityData { public boolean visible; }
    public static final class BoundsFlagsData { public boolean hasAabb, hasObb; }
    public static final class DimensionsData { public float width, height; }
    public static final class QuadDeformData {
        public float blX, blY, brX, brY, trX, trY, tlX, tlY;
    }
    public static final class RenderMaterialData { public int shaderIdx, blendModeId; }
    public static final class AssetRefData { public int assetId; public String atlasTag; }
    public static final class TintData { public int rgba; }
    public static final class AnimationData {
        public IntArray animationAssetIds = new IntArray();
        public float fps;
        public boolean playing, loop;
        public float stateTime;
        public int frame;
        public String currentClip;
    }
    public static final class ShaderParamsData {
        public Map<String, Float> floats = new LinkedHashMap<>();
    }
    public static final class RepeatData { public boolean repeatX, repeatY; }
    public static final class PointLightData {
        public float r, g, b, intensity, radius, falloff;
        public boolean enabled;
    }
    public static final class ConeLightData {
        public float r, g, b, intensity, radius, coneAngleDeg, rotationDeg, softness, falloff;
        public boolean enabled;
    }
    public static final class PhysicsBodyData {
        public int type;
        public boolean fixedRotation, bullet, allowSleep = true, awake = true;
        public float gravityScale = 1f, linearDamping, angularDamping;
    }
    public static final class PhysicsShapeData {
        public int localShapeId;
        public PhysicsGeometryData geometry;
        public float density = 1f, friction = .2f, restitution;
        public boolean sensor;
        public short categoryBits = 0x0001, maskBits = (short) 0xFFFF, groupIndex;
        public boolean enabled = true;
    }
    /** Marker only: scene-specific sourceAssetId is intentionally absent. */
    public static final class GameObjectData { }
}
