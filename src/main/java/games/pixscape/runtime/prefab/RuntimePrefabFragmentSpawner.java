package games.pixscape.runtime.prefab;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RuntimePrefabFragmentSpawner {

    private final IdentityRegistry identityRegistry;

    public RuntimePrefabFragmentSpawner(IdentityRegistry identityRegistry) {
        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null");
        }
        this.identityRegistry = identityRegistry;
    }

    public SpawnResult spawn(World world, SaveFileFormat fragment, float offsetX, float offsetY) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (fragment == null) {
            throw new IllegalArgumentException("fragment must not be null");
        }

        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        if (wsm == null) {
            throw new IllegalStateException("WorldSerializationManager is required");
        }
        if (!(wsm.getSerializer() instanceof JsonArtemisSerializer)) {
            wsm.setSerializer(new JsonArtemisSerializer(world));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, fragment);

        SaveFileFormat loaded =
                wsm.load(new ByteArrayInputStream(out.toByteArray()), SaveFileFormat.class);

        IntBag created = new IntBag();
        for (int i = 0; i < loaded.entities.size(); i++) {
            created.add(loaded.entities.get(i));
        }

        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        ComponentMapper<PixscapeIdentityComponent> mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<AssetRefComponent> mAssetRef = world.getMapper(AssetRefComponent.class);
        ComponentMapper<TextureRegionComponent> mTextureRegion = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> mRenderMaterial = world.getMapper(RenderMaterialComponent.class);

        identityRegistry.bind(world);

        for (int i = 0; i < created.size(); i++) {
            int eid = created.get(i);

            TransformComponent t = mTransform.get(eid);
            if (t != null) {
                t.x += offsetX;
                t.y += offsetY;
            }

            PixscapeIdentityComponent id = mIdentity.get(eid);
            if (id != null) {
                id.stableId = IdentityRegistry.UNASSIGNED_STABLE_ID;
            }

            AssetRefComponent ref = mAssetRef.getSafe(eid, null);
            if (ref != null) {
                TextureRegionComponent tr = mTextureRegion.getSafe(eid, null);
                if (tr != null) {
                    tr.valid = false;
                }

                RenderMaterialComponent mat = mRenderMaterial.getSafe(eid, null);
                if (mat != null) {
                    mat.textureHandle = -1;
                }
            }

            identityRegistry.ensureStableId(eid);
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            for (int i = 0; i < created.size(); i++) {
                dirty.mark(
                        created.get(i),
                        DirtyBits.GEOMETRY
                                | DirtyBits.MATERIAL
                                | DirtyBits.COLOR
                                | DirtyBits.ORDER
                                | DirtyBits.LAYER
                                | DirtyBits.PHYSICS
                                | DirtyBits.JOINTS
                );
            }
        }

        return new SpawnResult(created);
    }
}