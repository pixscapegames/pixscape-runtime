package games.pixscape.runtime.engine;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

public class PixscapeEnginePrefabAssetResolveTest {

    @Test
    public void resolveAssetRefsForEntities_appliesTextureRegionAndMaterial() {
        World world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(16)).build());

        int eid = world.create();
        AssetRefComponent src = world.getMapper(AssetRefComponent.class).create(eid);
        src.assetId = 7;
        src.atlasTag = "sceneA";

        TextureRegionComponent tr = world.getMapper(TextureRegionComponent.class).create(eid);
        tr.valid = false;
        RenderMaterialComponent mat = world.getMapper(RenderMaterialComponent.class).create(eid);
        mat.textureHandle = 0;

        IntBag created = new IntBag();
        created.add(eid);

        AtlasRuntimeService atlas = new AtlasRuntimeService();
        atlas.cache(7, "sceneA", 0.1f, 0.2f, 0.3f, 0.4f, 24, 32, 99);

        PixscapeEngine.resolveAssetRefsForEntities(world, atlas, created);

        Assert.assertTrue("Texture region should be marked valid after resolve", tr.valid);
        Assert.assertEquals(0.1f, tr.u1, 1e-6f);
        Assert.assertEquals(24, tr.pixW);
        Assert.assertNotEquals("Material handle should be non-zero after resolve", 0, mat.textureHandle);
    }
}
