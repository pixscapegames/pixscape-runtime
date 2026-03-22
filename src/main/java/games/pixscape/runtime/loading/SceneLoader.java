package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.RenderSubmitSystem;

import java.io.InputStream;

public final class SceneLoader {

    private SceneLoader() {}

    /**
     * Loads a scene from the given file.
     *
     * @param world             ECS world
     * @param inFile            scene file (ex: scenes/scene1.json)
     * @param clearContentFirst if true, clears the world before loading
     */
    public static SaveFileFormat loadScene(World world,
                                           FileHandle inFile,
                                           boolean clearContentFirst) {

        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        if (wsm.getSerializer() == null || !(wsm.getSerializer() instanceof JsonArtemisSerializer)) {
            wsm.setSerializer(new JsonArtemisSerializer(world));
        }

        if (clearContentFirst) {
            // 🔥 Reset render cache
            RenderStateSOA state = world.getSystem(RenderSubmitSystem.class).getState();
            state.clearAll();
            // remove existing entities, or only the "scene" content
            clearWorldContent(world);
        }

        if (!inFile.exists()) {
            throw new RuntimeException("Fichier de scène introuvable: " + inFile.path());
        }

        try (InputStream in = inFile.read()) {
            SaveFileFormat format = wsm.load(in, SaveFileFormat.class);

            return format;

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du chargement de la scène: " + inFile.path(), e);
        }
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

        var geometrySub = world.getAspectSubscriptionManager().get(
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

        var renderSub = world.getAspectSubscriptionManager().get(
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
