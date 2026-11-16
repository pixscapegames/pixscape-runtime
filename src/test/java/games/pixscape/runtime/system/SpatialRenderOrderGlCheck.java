package games.pixscape.runtime.system;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialRectangleTestData;
import org.junit.Assert;

/** Opt-in GL witness: consumes the real sorted DrawList; diagnostic colored quads, no physics. */
public final class SpatialRenderOrderGlCheck extends ApplicationAdapter {
    private final FileHandle output;
    private SpatialRenderOrderSystemTest.Fixture fixture;
    private ShapeRenderer renderer;
    private OrthographicCamera camera;
    private int actor, wall, frame;
    private Throwable failure;
    private static final Color ACTOR = new Color(.95f, .55f, .12f, 1f);
    private static final Color WALL = new Color(.35f, .48f, .6f, 1f);

    private SpatialRenderOrderGlCheck(FileHandle output) {
        this.output = output;
    }

    public static void main(String[] args) {
        SpatialRenderOrderGlCheck app = new SpatialRenderOrderGlCheck(new FileHandle(args[0]));
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.setWindowedMode(640, 480);
        new Lwjgl3Application(app, config);
        if (app.failure != null) {
            throw new AssertionError(app.failure);
        }
    }

    @Override
    public void create() {
        try {
            output.mkdirs();
            fixture = new SpatialRenderOrderSystemTest.Fixture(512);
            fixture.createLayer(2, true);
            var map = fixture.createBlockMap(16, 16, 16, 16, 300);
            SpatialBlockData block = new SpatialBlockData();
            block.id = block.structureId = 1;
            block.x = 10;
            block.y = 2;
            block.width = 2;
            block.depth = .25f;
            block.height = 16;
            block.beginAuthoredLinkedTileRefs();
            block.addLinkedTileRef(10, 2, 1);
            fixture.createBlockTiledLayer(1, map, block);
            wall = fixture.createLinkedTile(map, 10, 2, 1, 1, 20);
            fixture.tiledState.x1[wall] = fixture.tiledState.x4[wall] = 160;
            fixture.tiledState.x2[wall] = fixture.tiledState.x3[wall] = 192;
            fixture.tiledState.y1[wall] = fixture.tiledState.y2[wall] = 32;
            fixture.tiledState.y3[wall] = fixture.tiledState.y4[wall] = 48;
            actor = fixture.createActor(168, 28, 0, 2, true);
            fixture.setActorCircleFootprint(actor, 1f, 3f, -2f);
            SpatialRectangleTestData.setHeight(fixture.world.getMapper(SpatialRepresentationComponent.class).get(actor), 24);
            Assert.assertFalse(fixture.world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).has(actor));
            renderer = new ShapeRenderer();
            camera = new OrthographicCamera(96, 80);
            camera.position.set(174, 43, 0);
            camera.update();
            renderer.setProjectionMatrix(camera.combined);
        } catch (Throwable error) {
            fail(error);
        }
    }

    @Override
    public void render() {
        if (failure != null) {
            return;
        }
        try {
            float y = frame == 1 ? 40 : 28;
            fixture.setActorPosition(actor, 168, y);
            fixture.process();
            int[] order = fixture.drawOrder();
            Assert.assertEquals(2, order.length);
            Assert.assertEquals(frame == 1 ? actor : wall, order[0]);
            Gdx.gl.glClearColor(.08f, .09f, .11f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.begin(ShapeRenderer.ShapeType.Filled);
            for (int slot : order) {
                renderer.setColor(slot == actor ? ACTOR : WALL);
                if (slot == actor) {
                    renderer.rect(156, y, 24, 24);
                } else {
                    renderer.rect(160, 32, 32, 16);
                }
            }
            renderer.end();
            renderer.begin(ShapeRenderer.ShapeType.Line);
            renderer.setColor(Color.CYAN);
            renderer.circle(171, y - 2, 1, 24);
            renderer.setColor(Color.GREEN);
            renderer.rect(156, y, 24, 24);
            renderer.end();
            Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 640, 480);
            try {
                Vector3 point = camera.project(new Vector3(168, 44, 0));
                Color observed = new Color(pixels.getPixel((int) point.x, (int) point.y));
                Color expected = frame == 1 ? WALL : ACTOR;
                Assert.assertEquals(expected.r, observed.r, .01f);
                Assert.assertEquals(expected.g, observed.g, .01f);
                Assert.assertEquals(expected.b, observed.b, .01f);
                PixmapIO.writePNG(output.child("order-" + frame + (frame == 1 ? "-behind.png" : "-front.png")), pixels, -1, true);
            } finally {
                pixels.dispose();
            }
            if (++frame == 3) {
                output.child("DONE").writeString("PASS: front/back/front without Physics; footprint (3,-2), rectangle (-12,0).", false);
                Gdx.app.exit();
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void fail(Throwable error) {
        failure = error;
        error.printStackTrace();
        Gdx.app.exit();
    }

    @Override
    public void dispose() {
        if (renderer != null) {
            renderer.dispose();
        }
        if (fixture != null) {
            fixture.world.dispose();
        }
    }
}
