package games.pixscape.runtime.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** Real desktop GL30 smoke for the complete project-screen-to-HudBatch lifecycle. */
public class HudMaterializerGlSmokeTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rendersProjectHudScreenLifecycleThroughRealGl30HudBatch() throws Exception {
        File projectDirectory = temporaryFolder.newFolder("hud-gl-smoke");
        Throwable[] failure = {null};

        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Pixscape HUD GL30 smoke");
        configuration.setWindowedMode(320, 180);
        configuration.setInitialVisible(false);
        configuration.setOpenGLEmulation(
                Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        configuration.disableAudio(true);

        new Lwjgl3Application(new ApplicationAdapter() {
            private ShaderProgram shader;
            private HudScreenRuntime runtime;

            @Override
            public void create() {
                try {
                    Assert.assertNotNull("A real GL30 context is required", Gdx.gl30);
                    FileHandle root = new FileHandle(projectDirectory);
                    HudResourcesTest.writeHudFiles(root);
                    root.child("hud").mkdirs();
                    root.child("hud/game.hudscreen").writeString(
                            "{\"schemaVersion\":1,"
                                    + "\"documentId\":\"hud/game.json\","
                                    + "\"skinId\":\"ui/game.json\","
                                    + "\"atlasId\":\"ui/game.atlas\","
                                    + "\"textureProfileId\":\""
                                    + HudTextureProfile.DEFAULT_ID + "\"}",
                            false, "UTF-8");
                    root.child("hud/game.json").writeString(
                            new FileHandle(
                                    "src/test/resources/games/pixscape/runtime/hud/document/v1/"
                                            + "materializer-smoke.json").readString("UTF-8"),
                            false, "UTF-8");
                    shader = new ShaderProgram(
                            Gdx.files.internal(
                                    "shaders/core/desktop-gl30/hud-texture-array.vert"),
                            Gdx.files.internal(
                                    "shaders/core/desktop-gl30/hud-texture-array.frag"));
                    Assert.assertTrue(shader.getLog(), shader.isCompiled());
                    runtime = new HudScreenRuntime(root, shader);
                    ActiveHudScreen active = runtime.show("game");
                    HudResources resources = active.resources();
                    HudSession session = active.session();
                    MaterializedHud hud = active.materializedHud();
                    int layersBefore = resources.textureArrayBundle().handle2layer.size;
                    Object bundleBefore = resources.textureArrayBundle();
                    Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    runtime.act(0f);
                    runtime.draw();
                    runtime.resize(640, 360);
                    runtime.draw();
                    runtime.resize(40, 20, 240, 120);
                    runtime.draw();

                    Assert.assertSame(bundleBefore, session.hudBatch().getTextureArrayBundle());
                    Assert.assertEquals(layersBefore,
                            resources.textureArrayBundle().handle2layer.size);
                    Assert.assertNotNull(hud.actor("smoke-image"));
                    Assert.assertNotNull(hud.actor("smoke-label"));
                    Assert.assertNotNull(hud.actor("smoke-button"));
                    Assert.assertTrue(((Container<?>) hud.actor("smoke-clip")).getClip());
                    Assert.assertNull(ScissorStack.peekScissors());
                } catch (Throwable smokeFailure) {
                    failure[0] = smokeFailure;
                } finally {
                    Gdx.app.exit();
                }
            }

            @Override
            public void dispose() {
                try {
                    if (runtime != null) runtime.dispose();
                    if (shader != null) shader.dispose();
                    InternalTextures.dispose();
                    TextureRegistry.clear();
                } catch (Throwable disposalFailure) {
                    if (failure[0] == null) failure[0] = disposalFailure;
                }
            }
        }, configuration);

        if (failure[0] != null) {
            throw new AssertionError("Real desktop GL30 HUD materialization smoke failed.", failure[0]);
        }
    }

    @Test
    public void rendersLayoutOnlyHudWithoutSkinAtlasOrTextureArray() throws Exception {
        File projectDirectory = temporaryFolder.newFolder("layout-only-hud-gl-smoke");
        Throwable[] failure = {null};

        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Pixscape layout-only HUD GL30 smoke");
        configuration.setWindowedMode(320, 180);
        configuration.setInitialVisible(false);
        configuration.setOpenGLEmulation(
                Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        configuration.disableAudio(true);

        new Lwjgl3Application(new ApplicationAdapter() {
            private ShaderProgram shader;
            private HudScreenRuntime runtime;

            @Override
            public void create() {
                try {
                    Assert.assertNotNull("A real GL30 context is required", Gdx.gl30);
                    FileHandle root = new FileHandle(projectDirectory);
                    root.child("hud").mkdirs();
                    root.child("hud/layout.hudscreen").writeString(
                            "{\"schemaVersion\":1,"
                                    + "\"documentId\":\"hud/layout.json\"}",
                            false, "UTF-8");
                    root.child("hud/layout.json").writeString(
                            "{\"schemaVersion\":2,\"root\":{\"id\":\"root\","
                                    + "\"kind\":\"GROUP\",\"children\":[{"
                                    + "\"placementKind\":\"DIRECT\",\"node\":{"
                                    + "\"id\":\"stack\",\"kind\":\"STACK\","
                                    + "\"children\":[]}}]}}", false, "UTF-8");
                    shader = new ShaderProgram(
                            Gdx.files.internal(
                                    "shaders/core/desktop-gl30/hud-texture-array.vert"),
                            Gdx.files.internal(
                                    "shaders/core/desktop-gl30/hud-texture-array.frag"));
                    Assert.assertTrue(shader.getLog(), shader.isCompiled());
                    runtime = new HudScreenRuntime(root, shader);

                    ActiveHudScreen active = runtime.show("layout");
                    Assert.assertNull(active.resources().textureArrayBundle());
                    Assert.assertNull(active.session().hudBatch().getTextureArrayBundle());
                    Assert.assertNotNull(active.materializedHud().actor("root"));
                    Assert.assertNotNull(active.materializedHud().actor("stack"));

                    Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    runtime.act(0f);
                    runtime.draw();
                    runtime.resize(640, 360);
                    runtime.draw();
                } catch (Throwable smokeFailure) {
                    failure[0] = smokeFailure;
                } finally {
                    Gdx.app.exit();
                }
            }

            @Override
            public void dispose() {
                try {
                    if (runtime != null) runtime.dispose();
                    if (shader != null) shader.dispose();
                    InternalTextures.dispose();
                    TextureRegistry.clear();
                } catch (Throwable disposalFailure) {
                    if (failure[0] == null) failure[0] = disposalFailure;
                }
            }
        }, configuration);

        if (failure[0] != null) {
            throw new AssertionError("Real desktop GL30 layout-only HUD smoke failed.", failure[0]);
        }
    }
}
