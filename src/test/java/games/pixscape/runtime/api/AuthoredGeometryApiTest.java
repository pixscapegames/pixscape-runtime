package games.pixscape.runtime.api;

import com.artemis.World;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PolygonComponent;
import games.pixscape.runtime.component.PolylineComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TintComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AuthoredGeometryApiTest {
    private PixscapeEngine engine;
    private World world;

    @Before
    public void setUp() throws Exception {
        world = new World();
        engine = new PixscapeEngine();
        setField(engine, "world", world);
    }

    @After
    public void tearDown() {
        world.dispose();
    }

    @Test
    public void unsupportedEntityUsesNoneAndRejectsEveryVertexIndex() {
        int entity = world.create();
        world.process();
        AuthoredGeometryFacade geometry = ref(entity).geometry();

        Assert.assertFalse(geometry.exists());
        Assert.assertEquals(AuthoredGeometryKind.NONE, geometry.kind());
        Assert.assertEquals(0f, geometry.width(), 0f);
        Assert.assertEquals(0f, geometry.height(), 0f);
        Assert.assertEquals(0, geometry.vertexCount());
        Assert.assertFalse(geometry.closed());
        assertIndexRejected(geometry, -1);
        assertIndexRejected(geometry, 0);
    }

    @Test
    public void rectangleExposesAuthoredLocalVerticesWithoutQuadTransformingThem() {
        int entity = regularSprite(12f, 8f);
        QuadDeformComponent quad = world.getMapper(QuadDeformComponent.class).create(entity);
        quad.blX = -5f;
        quad.trY = 7f;
        world.process();
        AuthoredGeometryFacade geometry = ref(entity).geometry();

        Assert.assertTrue(geometry.exists());
        Assert.assertEquals(AuthoredGeometryKind.RECTANGLE, geometry.kind());
        Assert.assertEquals(12f, geometry.width(), 0f);
        Assert.assertEquals(8f, geometry.height(), 0f);
        Assert.assertEquals(4, geometry.vertexCount());
        Assert.assertTrue(geometry.closed());
        assertVertex(geometry, 0, 0f, 0f);
        assertVertex(geometry, 1, 12f, 0f);
        assertVertex(geometry, 2, 12f, 8f);
        assertVertex(geometry, 3, 0f, 8f);
        assertIndexRejected(geometry, 4);
    }

    @Test
    public void polygonHasPriorityAndReadsBackingVerticesWithoutExposingThem() {
        int entity = rectangle(20f, 30f);
        PolygonComponent polygon = world.getMapper(PolygonComponent.class).create(entity);
        float[] source = new float[]{1f, 2f, 3f, 4f, 5f, 6f};
        polygon.setVertices(source);
        source[0] = 99f;
        world.process();
        float[] backing = polygon.vertices;
        AuthoredGeometryFacade geometry = ref(entity).geometry();

        Assert.assertEquals(AuthoredGeometryKind.POLYGON, geometry.kind());
        Assert.assertEquals(20f, geometry.width(), 0f);
        Assert.assertEquals(30f, geometry.height(), 0f);
        Assert.assertEquals(3, geometry.vertexCount());
        Assert.assertTrue(geometry.closed());
        assertVertex(geometry, 0, 1f, 2f);
        assertVertex(geometry, 1, 3f, 4f);
        assertVertex(geometry, 2, 5f, 6f);
        Assert.assertSame(backing, polygon.vertices);
        Assert.assertArrayEquals(new float[]{1f, 2f, 3f, 4f, 5f, 6f},
                polygon.vertices, 0f);
        assertIndexRejected(geometry, -1);
        assertIndexRejected(geometry, 3);
    }

    @Test
    public void polylineHasPriorityOverDimensionsAndRemainsOpen() {
        int entity = rectangle(7f, 9f);
        PolylineComponent polyline = world.getMapper(PolylineComponent.class).create(entity);
        polyline.setVertices(new float[]{-1f, -2f, 3f, 4f});
        world.process();
        AuthoredGeometryFacade geometry = ref(entity).geometry();

        Assert.assertEquals(AuthoredGeometryKind.POLYLINE, geometry.kind());
        Assert.assertEquals(2, geometry.vertexCount());
        Assert.assertFalse(geometry.closed());
        assertVertex(geometry, 0, -1f, -2f);
        assertVertex(geometry, 1, 3f, 4f);
    }

    @Test
    public void polygonTakesPriorityOverPolyline() {
        int entity = world.create();
        PolylineComponent polyline = world.getMapper(PolylineComponent.class).create(entity);
        polyline.setVertices(new float[]{1f, 2f});
        PolygonComponent polygon = world.getMapper(PolygonComponent.class).create(entity);
        polygon.setVertices(new float[]{3f, 4f, 5f, 6f});
        world.process();

        AuthoredGeometryFacade geometry = ref(entity).geometry();
        Assert.assertEquals(AuthoredGeometryKind.POLYGON, geometry.kind());
        Assert.assertEquals(2, geometry.vertexCount());
        assertVertex(geometry, 0, 3f, 4f);
    }

    @Test
    public void cachedFacadeBecomesEmptyAndNeverRetargetsRecycledId() {
        int entity = rectangle(4f, 5f);
        world.process();
        EntityRef oldRef = ref(entity);
        AuthoredGeometryFacade oldGeometry = oldRef.geometry();
        Assert.assertSame(oldGeometry, oldRef.geometry());

        world.delete(entity);
        world.process();
        int replacement = rectangle(100f, 200f);
        Assert.assertEquals(entity, replacement);

        Assert.assertFalse(oldGeometry.exists());
        Assert.assertEquals(AuthoredGeometryKind.NONE, oldGeometry.kind());
        Assert.assertEquals(0f, oldGeometry.width(), 0f);
        Assert.assertEquals(0f, oldGeometry.height(), 0f);
        Assert.assertEquals(0, oldGeometry.vertexCount());
        Assert.assertFalse(oldGeometry.closed());
        assertIndexRejected(oldGeometry, 0);

        AuthoredGeometryFacade current = ref(replacement).geometry();
        Assert.assertEquals(AuthoredGeometryKind.RECTANGLE, current.kind());
        Assert.assertEquals(100f, current.width(), 0f);
    }

    @Test
    public void publicFacadeDoesNotLeakComponentsCollectionsOrArrays() {
        for (Method method : AuthoredGeometryFacade.class.getMethods()) {
            Class<?> type = method.getReturnType();
            Assert.assertFalse(type.getName().startsWith("com.artemis"));
            Assert.assertFalse(type.getName().startsWith("com.badlogic.gdx"));
            Assert.assertNotEquals(float[].class, type);
            Assert.assertNotEquals(PolygonComponent.class, type);
            Assert.assertNotEquals(PolylineComponent.class, type);
            Assert.assertTrue(type.isPrimitive() || type == AuthoredGeometryKind.class);
        }
    }

    private EntityRef ref(int entity) {
        return engine.api().entities().ofEntityId(entity);
    }

    private int rectangle(float width, float height) {
        int entity = world.create();
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entity);
        dimensions.width = width;
        dimensions.height = height;
        return entity;
    }

    private int regularSprite(float width, float height) {
        int entity = rectangle(width, height);
        world.getMapper(TransformComponent.class).create(entity);
        world.getMapper(AssetRefComponent.class).create(entity);
        world.getMapper(VisibilityComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(TintComponent.class).create(entity);
        return entity;
    }

    private static void assertVertex(
            AuthoredGeometryFacade geometry, int index, float x, float y) {
        Assert.assertEquals(x, geometry.localX(index), 0f);
        Assert.assertEquals(y, geometry.localY(index), 0f);
    }

    private static void assertIndexRejected(
            final AuthoredGeometryFacade geometry, final int index) {
        try {
            geometry.localX(index);
            Assert.fail("Expected localX index rejection: " + index);
        } catch (IndexOutOfBoundsException expected) {
            Assert.assertTrue(expected.getMessage().contains("vertex index"));
        }
        try {
            geometry.localY(index);
            Assert.fail("Expected localY index rejection: " + index);
        } catch (IndexOutOfBoundsException expected) {
            Assert.assertTrue(expected.getMessage().contains("vertex index"));
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
