package games.pixscape.runtime.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** Real desktop GL30 smoke for the complete validated-document-to-HudBatch path. */
public class HudMaterializerGlSmokeTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rendersMaterializedWidgetsThroughRealGl30HudBatch() throws Exception {
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
            private HudResources resources;
            private ShaderProgram shader;
            private HudSession session;

            @Override
            public void create() {
                try {
                    Assert.assertNotNull("A real GL30 context is required", Gdx.gl30);
                    FileHandle root = new FileHandle(projectDirectory);
                    HudResourcesTest.writeHudFiles(root);
                    HudScreenAsset asset = new HudScreenAsset();
                    asset.referenceWidth = 320;
                    asset.referenceHeight = 180;
                    asset.skinId = "ui/game.json";
                    asset.atlasId = "ui/game.atlas";
                    resources = HudResources.prepare(asset, root);
                    shader = new ShaderProgram(
                            Gdx.files.internal(
                                    "shaders/core/desktop-gl30/hud-texture-array.vert"),
                            Gdx.files.internal(
                                    "shaders/core/desktop-gl30/hud-texture-array.frag"));
                    Assert.assertTrue(shader.getLog(), shader.isCompiled());

                    HudValidationResult validation = new HudDocumentValidator().validate(
                            new HudDocumentCodec().read(new FileHandle(
                                    "src/test/resources/games/pixscape/runtime/hud/document/v1/"
                                            + "materializer-smoke.json")),
                            resources);
                    Assert.assertTrue(validation.issues().toString(), validation.isValid());
                    MaterializedHud hud = new HudMaterializer().materialize(
                            validation.validatedDocument(), resources);
                    session = HudSession.create(asset, resources, shader);
                    session.install(hud);

                    int layersBefore = resources.textureArrayBundle().handle2layer.size;
                    Object bundleBefore = resources.textureArrayBundle();
                    Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    session.act(0f);
                    session.draw();

                    Assert.assertSame(bundleBefore, session.hudBatch().getTextureArrayBundle());
                    Assert.assertEquals(layersBefore,
                            resources.textureArrayBundle().handle2layer.size);
                    Assert.assertNotNull(hud.actor("smoke-image"));
                    Assert.assertNotNull(hud.actor("smoke-label"));
                    Assert.assertNotNull(hud.actor("smoke-button"));
                } catch (Throwable smokeFailure) {
                    failure[0] = smokeFailure;
                } finally {
                    Gdx.app.exit();
                }
            }

            @Override
            public void dispose() {
                try {
                    if (session != null) session.dispose();
                    if (shader != null) shader.dispose();
                    if (resources != null) resources.dispose();
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
}
