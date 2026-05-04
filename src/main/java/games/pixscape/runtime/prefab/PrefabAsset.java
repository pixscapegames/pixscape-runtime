package games.pixscape.runtime.prefab;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.physics.FixtureDefData;

import java.util.ArrayList;
import java.util.List;

public final class PrefabAsset {
    public String type = "pixscape-prefab";
    public int version = 1;
    public String name;
    public List<PrefabEntityData> entities = new ArrayList<>();

    public static final class PrefabEntityData {
        public int sourceEntityId;
        public TransformData transform;
        public EntityIndexData entityIndex;
        public MetaData meta;
        public IdentityData identity;
        public VisibilityData visibility;
        public BoundsFlagsData boundsFlags;
        public DimensionsData dimensions;
        public TextureRegionData textureRegion;
        public RenderMaterialData renderMaterial;
        public AssetRefData assetRef;
        public TintData tint;
        public AnimationData animation;
        public ShaderParamsData shaderParams;
        public PhysicsBodyData physicsBody;
        public Array<FixtureDefData> fixtures = new Array<>();
        public PhysicsAuthoringData physicsAuthoring;
        public JointBaseData joint;
        public DistanceJointData distanceJoint;
        public RevoluteJointData revoluteJoint;
        public PrismaticJointData prismaticJoint;
        public WheelJointData wheelJoint;
        public FrictionJointData frictionJoint;
        public MotorJointData motorJoint;
        public WeldJointData weldJoint;
        public PulleyJointData pulleyJoint;
        public GearJointData gearJoint;
    }

    public static final class TransformData {
        public float x, y, rotationRad, scaleX, scaleY, originX, originY;
    }

    public static final class EntityIndexData {
        public int layerIndex, zIndex;
    }
    public static final class MetaData { public String kind; }
    public static final class IdentityData { public String name; }
    public static final class VisibilityData { public boolean visible; }
    public static final class BoundsFlagsData { public boolean hasAabb, hasObb; }
    public static final class DimensionsData { public float width, height; }
    public static final class TextureRegionData {
        public float u1, v1, u2, v2;
        public int pixW, pixH;
        public boolean valid;
    }
    public static final class RenderMaterialData {
        public int shaderIdx, blendModeId, textureHandle;
        public String debugAtlasTag;
    }
    public static final class AssetRefData { public int assetId; public String atlasTag; }
    public static final class TintData { public int rgba; }
    public static final class AnimationData {
        public String name;
        public float fps;
        public boolean playing, loop;
        public float stateTime;
        public int frame;
        public String currentClip;
        public ObjectMap<String, AnimationClipData> clips = new ObjectMap<>();
    }
    public static final class AnimationClipData {
        public int start;
        public int end;

        public AnimationClipData() {
        }

        public AnimationClipData(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
    public static final class ShaderParamsData {
        public java.util.Map<String, Float> floats = new java.util.LinkedHashMap<>();
    }
    public static final class PhysicsBodyData {
        public int type;
        public boolean fixedRotation, bullet, allowSleep, awake, enabled;
        public float gravityScale, linearDamping, angularDamping;
    }
    public static final class PhysicsAuthoringData {
        public List<AuthoredPolygonDto> polygons = new ArrayList<>();
    }
    public static final class AuthoredPolygonDto {
        public long authoringId;
        public float[] sourceVerts;
        public int sourceCount;
        public int decompositionAlgorithmVersion;
        public long sourceHash;
        public List<ConvexPolygonPartDto> convexParts = new ArrayList<>();
        public long[] generatedFixtureIds;
        public float density, friction, restitution;
        public boolean isSensor;
        public short categoryBits, maskBits, groupIndex;
        public float offsetX, offsetY, angleDeg;
    }
    public static final class ConvexPolygonPartDto {
        public float[] verts;
        public int count;
    }

    public static final class JointBaseData {
        public int type, aEid, bEid;
        public boolean collideConnected;
        public float anchorAx, anchorAy, anchorBx, anchorBy;
    }

    public static final class DistanceJointData {
        public float lengthM, frequencyHz, dampingRatio;
    }

    public static final class RevoluteJointData {
        public boolean enableLimit, enableMotor;
        public float lowerAngleRad, upperAngleRad, motorSpeedRad, maxMotorTorque;
    }

    public static final class PrismaticJointData {
        public float axisX, axisY, lowerTranslationM, upperTranslationM, motorSpeedMps, maxMotorForce;
        public boolean enableLimit, enableMotor;
    }

    public static final class WheelJointData {
        public float axisX, axisY, motorSpeedRad, maxMotorTorque, frequencyHz, dampingRatio;
        public boolean enableMotor;
    }

    public static final class FrictionJointData {
        public float maxForce, maxTorque;
    }

    public static final class MotorJointData {
        public float linearOffsetX, linearOffsetY, angularOffsetRad, maxForce, maxTorque, correctionFactor;
    }

    public static final class WeldJointData {
        public float referenceAngleRad, frequencyHz, dampingRatio;
    }

    public static final class PulleyJointData {
        public float groundAx, groundAy, groundBx, groundBy, lengthAM, lengthBM, ratio;
    }

    public static final class GearJointData {
        public int joint1Eid, joint2Eid;
        public float ratio;
    }
}
