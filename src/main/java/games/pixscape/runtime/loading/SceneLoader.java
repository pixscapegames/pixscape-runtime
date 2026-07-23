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
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;

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
            validatePhysicsShapeIdentities(world, sceneMeta);
            return format;

        } catch (Exception e) {
            clearWorldContent(world);
            throw new RuntimeException("Error while loading scene: " + inFile.path(), e);
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

    private static void validatePhysicsShapeIdentities(
            World world, SceneMetaRuntime sceneMeta) {
        if (sceneMeta == null) {
            throw new IllegalArgumentException(
                    "Scene metadata is required to validate physicsShapeId identities.");
        }

        EntitySubscription subscription = world.getAspectSubscriptionManager().get(
                Aspect.all(PhysicsShapesComponent.class));
        IntBag entities = subscription.getEntities();
        ComponentMapper<PhysicsShapesComponent> mapper =
                world.getMapper(PhysicsShapesComponent.class);
        IntArray ids = new IntArray();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            PhysicsShapesComponent shapes = mapper.get(data[i]);
            if (shapes == null || shapes.shapes == null) continue;
            for (int shapeIndex = 0; shapeIndex < shapes.shapes.size; shapeIndex++) {
                PhysicsShapeData shape = shapes.shapes.get(shapeIndex);
                if (shape == null) {
                    throw new IllegalArgumentException(
                            "Scene contains a null PhysicsShapeData source.");
                }
                ids.add(shape.physicsShapeId);
            }
        }

        new PhysicsShapeIdAllocator(sceneMeta)
                .validatePersistedPhysicsShapeIds(ids.toArray());
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
