package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.animation.AnimationClipDefData;
import games.pixscape.runtime.animation.AnimationDefData;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.system.AnimationSystem;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpatialRepresentationAuthoringTest {
    @Test
    public void preparationCompletesAllClipsWithoutMutatingExistingSettings() {
        AnimationRegistry registry = new AnimationRegistry();
        AnimationDefData def = new AnimationDefData();
        def.assetId = 1;
        def.name = "hero";
        def.frameCount = 2;
        def.currentClip = "idle";
        for (String name : new String[]{"idle", "walk"}) {
            AnimationClipDefData clip = new AnimationClipDefData();
            clip.name = name;
            clip.end = 1;
            def.clips.add(clip);
        }
        registry.put(def);
        World world = new World(new WorldConfigurationBuilder().with(new games.pixscape.runtime.system.DirtyTrackerSystem(16), new AnimationSystem(registry, null)).build());
        try {
            int entity = world.create();
            world.getMapper(TextureRegionComponent.class).create(entity);
            world.getMapper(AssetRefComponent.class).create(entity).assetId = 1;
            AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entity);
            animation.currentClip = "idle";
            animation.animationAssetIds.add(1);
            SpatialRepresentationComponent component = world.getMapper(SpatialRepresentationComponent.class).create(entity);
            component.rectangles = new ActorSpatialRectangles();
            ActorSpatialRectangleData idle = new ActorSpatialRectangleData();
            idle.width = .7f;
            idle.pivotX = .2f;
            component.rectangles.put(1, "idle", idle);
            ActorSpatialRectangles prepared = SpatialRepresentationAuthoring.prepare(world, entity);
            assertNull(component.rectangles.get(1, "walk"));
            assertSame(idle, component.rectangles.get(1, "idle"));
            assertEquals(.7f, prepared.get(1, "idle").width, 0);
            assertEquals(.2f, prepared.get(1, "idle").pivotX, 0);
            assertEquals(1, prepared.get(1, "walk").width, 0);
            prepared.get(1, "idle").width = .5f;
            assertEquals(.7f, idle.width, 0);
            component.rectangles = prepared;
            assertNull(SpatialRepresentationAuthoring.prepare(world, entity));
        } finally {
            world.dispose();
        }
    }
}
