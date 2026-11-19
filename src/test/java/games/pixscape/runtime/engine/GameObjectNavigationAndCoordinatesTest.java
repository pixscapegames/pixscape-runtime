package games.pixscape.runtime.engine;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static games.pixscape.runtime.engine.PixscapeEngineGameObjectSpawnTest.*;
import static org.junit.Assert.*;

public class GameObjectNavigationAndCoordinatesTest {
    @Test
    public void preparedHierarchyHasIndependentSnapshotsAndReusableBoundedTraversal() throws Exception {
        try (Harness h = new Harness(nestedAsset())) {
            h.ready();
            var instance = h.spawn();
            EntityRef root = instance.root();
            EntityRef nested = instance.requireMember("playerAnimation");
            EntityRef leaf = instance.requireMember("visualOnly");
            assertNull(root.parent());
            assertEquals(root.entityId(), nested.parent().entityId());
            assertEquals(nested.entityId(), leaf.parent().entityId());
            assertEquals(3, root.children().size);
            assertIds(nested.children(), leaf.entityId());
            assertEquals(0, leaf.descendants().size);
            Array<EntityRef> snapshot = root.children();
            snapshot.clear();
            assertEquals(3, root.children().size);
            Array<EntityRef> out = new Array<>();
            out.add(root);
            assertSame(out, root.descendants(out));
            assertEquals(4, out.size);
            assertEquals(4, ids(out).size());
            assertFalse(ids(out).contains(root.entityId()));
            assertTrue(indexOf(out, nested.entityId()) < indexOf(out, leaf.entityId()));
            assertSame(out, root.children(out));
            assertEquals(3, out.size);
            assertSame(out, leaf.children(out));
            assertEquals(0, out.size);
            assertThrows(IllegalArgumentException.class, () -> root.children(null));
            assertThrows(IllegalArgumentException.class, () -> root.descendants(null));

            // Unrelated prepared trees must not be included or trigger a global rebuild on reads.
            for (int i = 0; i < 40; i++) {
                h.spawn();
            }
            int rebuilds = h.hierarchy().rebuildCount();
            for (int i = 0; i < 20; i++) {
                assertEquals(1, nested.descendants(out).size);
                assertEquals(leaf.entityId(), out.first().entityId());
                assertEquals(4, root.descendants(out).size);
            }
            assertEquals(rebuilds, h.hierarchy().rebuildCount());
            assertEquals(1, leaf.parent().children(out).size);
        }
    }

    @Test
    public void liveNavigationTracksPublishedMovesAndDeletionWithoutRetargetingCapturedReferences() throws Exception {
        try (Harness h = new Harness(nestedAsset())) {
            h.ready();
            var first = h.spawn();
            var second = h.spawn();
            EntityRef leaf = first.requireMember("visualOnly");
            EntityRef parent = second.requireMember("otherBody");
            var world = h.engine.getWorld();
            world.getMapper(DimensionsComponent.class).create(parent.entityId());
            world.getMapper(AssetRefComponent.class).create(parent.entityId());
            world.getMapper(VisibilityComponent.class).create(parent.entityId());
            world.getMapper(TintComponent.class).create(parent.entityId());
            h.frame(0);
            assertTrue(parent.sprite().exists());
            var api = h.engine.api().gameObjects();
            api.reparent(leaf, parent);
            assertEquals(parent.entityId(), leaf.parent().entityId());
            assertIds(parent.children(), leaf.entityId());
            assertEquals(3, first.root().descendants().size);
            assertEquals(5, second.root().descendants().size);
            assertEquals(leaf.entityId(), first.requireMember("visualOnly").entityId());
            assertNotEquals(leaf.entityId(), second.requireMember("visualOnly").entityId());
            assertEquals(4, first.members().length);

            Array<EntityRef> captured = parent.children();
            api.reparent(leaf, null);
            assertNull(leaf.parent());
            assertEquals(0, parent.children().size);
            assertTrue(captured.first().exists());
            api.reparent(leaf, parent);
            leaf.remove();
            assertTrue(captured.first().exists());
            assertIds(parent.children(), leaf.entityId());
            h.frame(0);
            assertEquals(0, parent.descendants().size);
            assertFalse(captured.first().exists());
            assertNull(leaf.parent());
            assertSame(captured, leaf.children(captured));
            assertEquals(0, captured.size);
            assertEquals(0, leaf.descendants().size);
            int replacement = world.create();
            assertEquals(leaf.entityId(), replacement);
            h.engine.api().entities().ensureStableId(replacement);
            world.getMapper(TransformComponent.class).create(replacement);
            world.getMapper(EntityIndexComponent.class).create(replacement);
            world.getMapper(GameObjectMemberComponent.class).create(replacement).parentStableId = parent.stableId();
            h.frame(0);
            assertFalse(leaf.exists());
            assertNull(leaf.parent());
            assertEquals(0, leaf.children().size);
            assertTrue(parent.children().first().exists());
            assertEquals(parent.entityId(), parent.children().first().parent().entityId());
            h.engine.loadScene("A");
            assertNull(parent.parent());
            assertEquals(0, parent.descendants().size);
        }
    }

    @Test
    public void vectorsUseLinearFrameDespiteLargeTranslationsOriginsAndSignedLeafScale() throws Exception {
        GameObjectAsset definition = nestedAsset();
        var rootData = definition.entities.get(0).transform;
        rootData.rotationRad = (float) (Math.PI / 2);
        rootData.scaleX = rootData.scaleY = 2;
        rootData.originX = 10;
        rootData.originY = 20;
        var nestedData = definition.entities.get(1).transform;
        nestedData.rotationRad = (float) (-Math.PI / 2);
        nestedData.scaleX = nestedData.scaleY = 3;
        nestedData.originX = -4;
        nestedData.originY = 6;
        var leafData = definition.entities.get(4).transform;
        leafData.scaleX = -2;
        leafData.scaleY = .5f;
        leafData.originX = 9;
        leafData.originY = -11;
        try (Harness h = new Harness(definition)) {
            h.ready();
            var instance = h.engine.api().gameObjects().spawn("declared", 100000000, -100000000);
            var leaf = instance.requireMember("visualOnly").transform();
            assertEquals(9, leaf.originX(), 0);
            assertEquals(-11, leaf.originY(), 0);
            Vector2 out = new Vector2();
            // R(90)*2 * R(-90)*3 * diag(-2,.5) = diag(-12,3).
            assertSame(out, leaf.localVectorToWorld(2, 4, out));
            assertEquals(-24, out.x, .0001f);
            assertEquals(12, out.y, .0001f);
            assertSame(out, leaf.worldVectorToLocal(-24, 12, out));
            assertEquals(2, out.x, .0001f);
            assertEquals(4, out.y, .0001f);
            leaf.localToWorld(2, 4, out);
            assertTrue(Math.abs(out.x) > 90000000);
            instance.root().transform().setPosition(-200000000, 200000000).setOrigin(30, -40);
            h.frame(0);
            leaf.localVectorToWorld(2, 4, out);
            assertEquals(-24, out.x, .0001f);
            assertEquals(12, out.y, .0001f);
            leaf.worldVectorToLocal(out.x, out.y, out);
            assertEquals(2, out.x, .0001f);
            assertEquals(4, out.y, .0001f);
        }
    }

    @Test
    public void originsAndConversionFailuresFollowExistingAbsenceAndSingularityRules() throws Exception {
        try (Harness h = new Harness(nestedAsset())) {
            h.ready();
            EntityRef absent = h.engine.api().entities().requireStableId(1); // Layer without Transform.
            assertNull(absent.parent());
            assertEquals(0, absent.children().size);
            assertEquals(0, absent.transform().originX(), 0);
            assertEquals(0, absent.transform().originY(), 0);
            Vector2 out = new Vector2(17, 19);
            assertThrows(IllegalStateException.class, () -> absent.transform().localVectorToWorld(1, 2, out));
            assertEquals(new Vector2(17, 19), out);
            var instance = h.spawn();
            EntityRef entity = instance.requireMember("visualOnly");
            var transform = entity.transform();
            h.frame(0);
            transform.setOrigin(8, -7);
            assertEquals(8, transform.originX(), 0);
            assertEquals(-7, transform.originY(), 0);
            transform.setScale(0, 1);
            h.frame(0);
            assertThrows(IllegalStateException.class, () -> transform.worldVectorToLocal(1, 2, out));
            assertEquals(new Vector2(17, 19), out);
            assertThrows(IllegalStateException.class, () -> transform.worldToLocal(1, 2, out));
            assertEquals(new Vector2(17, 19), out);
            assertThrows(IllegalArgumentException.class, () -> transform.localVectorToWorld(Float.NaN, 2, out));
            assertThrows(IllegalArgumentException.class, () -> transform.worldVectorToLocal(1, Float.POSITIVE_INFINITY, out));
            assertThrows(IllegalArgumentException.class, () -> transform.localVectorToWorld(1, 2, null));
            assertEquals(new Vector2(17, 19), out);
            entity.remove();
            h.frame(0);
            assertEquals(0, transform.originX(), 0);
            assertEquals(0, transform.originY(), 0);
            assertThrows(IllegalStateException.class, () -> transform.worldVectorToLocal(1, 2, out));
            assertThrows(IllegalStateException.class, () -> transform.localVectorToWorld(1, 2, out));
            assertEquals(new Vector2(17, 19), out);
        }
    }

    private static GameObjectAsset nestedAsset() {
        GameObjectAsset definition = asset();
        definition.joints.clear();
        for (GameObjectAsset.GameObjectEntityData node : definition.entities) {
            node.physicsBody = null;
            node.physicsShapes.clear();
        }
        definition.entities.get(1).gameObject = new GameObjectAsset.GameObjectData();
        definition.entities.get(4).parentSourceEntityId = 2;
        return definition;
    }

    private static int indexOf(Array<EntityRef> refs, int entity) {
        for (int i = 0; i < refs.size; i++) {
            if (refs.get(i).entityId() == entity) {
                return i;
            }
        }
        return -1;
    }

    private static Set<Integer> ids(Array<EntityRef> refs) {
        Set<Integer> ids = new HashSet<>();
        for (EntityRef ref : refs) {
            ids.add(ref.entityId());
        }
        return ids;
    }

    private static void assertIds(Array<EntityRef> refs, int expected) {
        assertEquals(1, refs.size);
        assertEquals(expected, refs.first().entityId());
    }
}
