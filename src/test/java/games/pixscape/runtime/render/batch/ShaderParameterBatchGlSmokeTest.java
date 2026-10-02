package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;

/** Opt-in integration test: requires an actual desktop GL30 context. */
public class ShaderParameterBatchGlSmokeTest {
    private static final String FRAGMENT = "#version 330 core\n"
            + "flat in int v_paramId;\n"
            + "uniform sampler2D u_entityParams;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(texelFetch(u_entityParams, ivec2(0, v_paramId), 0).rgb, 1.0); }\n";
    private static final String GREEN_FRAGMENT = "#version 330 core\n"
            + "flat in int v_paramId;\n"
            + "uniform sampler2D u_entityParams;\n"
            + "out vec4 fragColor;\n"
            + "void main(){fragColor=vec4(0.0,texelFetch(u_entityParams,ivec2(0,v_paramId),0).r,0.0,1.0);}\n";

    @Test
    public void distinctEntityRowsRenderInOneDrawAndSurviveGeometryFlushAndNextFrame() {
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Pixscape shader parameter table smoke");
        config.setWindowedMode(96, 32);
        config.setInitialVisible(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        config.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            ShaderProgram shader;
            ShaderProgram yellowShader;
            ShaderProgram alphaShader;
            ShaderProgram boostShader;
            TextureArrayMeshBatch batch;
            AtlasRuntimeService.TextureArrayBundle bundle;
            FileHandle projectDir;

            @Override public void create() {
                try {
                    Assert.assertNotNull(Gdx.gl30);
                    ShaderRegistry.initDefaults();
                    projectDir = new FileHandle(Files.createTempDirectory("pixscape-shader-index").toFile());
                    writeProjectShader("test", "u_gain", FRAGMENT);
                    writeProjectShader("tint", "u_tint", GREEN_FRAGMENT);
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    ShaderRegistry.saveProjectIndices();
                    int testIndex = ShaderRegistry.indexOf("test");
                    int tintIndex = ShaderRegistry.indexOf("tint");
                    int glowIndex = ShaderRegistry.indexOf("glow_pulse");
                    Assert.assertTrue(testIndex >= 0 && tintIndex >= 0 && glowIndex >= 0);
                    RenderMaterialComponent testMaterial = new RenderMaterialComponent();
                    RenderMaterialComponent tintMaterial = new RenderMaterialComponent();
                    testMaterial.shaderIdx = testIndex;
                    tintMaterial.shaderIdx = tintIndex;
                    DynamicEntityRenderState renderState = new DynamicEntityRenderState(2);
                    int testSlot = renderState.acquireSlotForEntity(101);
                    int tintSlot = renderState.acquireSlotForEntity(202);
                    renderState.shader[testSlot] = testMaterial.shaderIdx;
                    renderState.shader[tintSlot] = tintMaterial.shaderIdx;
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    shader = new ShaderProgram(
                            Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString(), FRAGMENT);
                    Assert.assertTrue(shader.getLog(), shader.isCompiled());
                    yellowShader = new ShaderProgram(
                            Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString(),
                            "#version 330 core\nout vec4 fragColor;\nvoid main(){fragColor=vec4(1,1,0,1);}\n");
                    alphaShader = new ShaderProgram(
                            Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString(),
                            "#version 330 core\nout vec4 fragColor;\nvoid main(){fragColor=vec4(0,0,1,0.5);}\n");
                    Assert.assertTrue(yellowShader.getLog(), yellowShader.isCompiled());
                    Assert.assertTrue(alphaShader.getLog(), alphaShader.isCompiled());
                    boostShader = new ShaderProgram(
                            Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString(),
                            "#version 330 core\nflat in int v_paramId;\nuniform sampler2D u_entityParams;\n"
                                    + "out vec4 fragColor;\nvoid main(){fragColor=vec4(0.0,"
                                    + "texelFetch(u_entityParams,ivec2(0,v_paramId),0).r,0.0,1.0);}\n");
                    Assert.assertTrue(boostShader.getLog(), boostShader.isCompiled());
                    bundle = AtlasRuntimeService.buildTextureArrayFromTextures(new Array<>(), 16, 16, GLCaps.detect());
                    batch = new TextureArrayMeshBatch(2);
                    batch.setTextureArrayBundle(bundle);
                    Array<ShaderFloatParam> defaults = new Array<>();
                    defaults.add(new ShaderFloatParam("red", 0f));
                    defaults.add(new ShaderFloatParam("green", 0f));
                    defaults.add(new ShaderFloatParam("blue", 0f));
                    ShaderParameterLayout layout = new ShaderParameterLayout("smoke", defaults);
                    Array<ShaderFloatParam> namedDefault = new Array<>();
                    namedDefault.add(new ShaderFloatParam("u_gain", 0.5f));
                    ShaderRegistry.testCompile("named-smoke",
                            Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString(),
                            "#version 330 core\n#include \"pixscape_entity_params.glsl\"\n"
                                    + "out vec4 fragColor;\nvoid main(){"
                                    + "float gain=pixscapeEntityFloat(PIXSCAPE_PARAM_u_gain);"
                                    + "fragColor=vec4(gain,gain,gain,1.0);}\n",
                            games.pixscape.runtime.render.ShaderMode.TEXTURE_ARRAY,
                            new ShaderParameterLayout("named-smoke", namedDefault));
                    Matrix4 projection = new Matrix4().setToOrtho2D(0, 0, 96, 32);
                    int whiteHandle = InternalTextures.whiteHandle();

                    clear();
                    RenderStats first = new RenderStats();
                    batch.begin(projection, first);
                    batch.setShader(shader, first);
                    batch.setParameterLayout(layout, first);
                    entity(whiteHandle, 0, 1f, 0f, 0f, first);
                    entity(whiteHandle, 32, 0f, 1f, 0f, first);
                    batch.end(first);
                    Assert.assertEquals(1, first.drawCalls);
                    Assert.assertEquals(1, first.flushes);
                    Assert.assertEquals(1, first.flushEnd);
                    Assert.assertEquals(0, first.flushStateChanges);
                    Assert.assertEquals(0, first.flushCapacity);
                    Assert.assertEquals(0, first.flushParameterCapacity);
                    assertRgb(16, 255, 0, 0);
                    assertRgb(48, 0, 255, 0);

                    clear();
                    RenderStats second = new RenderStats();
                    batch.begin(projection, second);
                    batch.setParameterLayout(layout, second);
                    entity(whiteHandle, 0, 0f, 0f, 1f, second);
                    entity(whiteHandle, 32, 1f, 0f, 0f, second);
                    entity(whiteHandle, 64, 0f, 1f, 0f, second);
                    batch.end(second);
                    Assert.assertEquals(2, second.drawCalls);
                    Assert.assertEquals(1, second.flushCapacity);
                    Assert.assertEquals(1, second.flushEnd);
                    assertRgb(16, 0, 0, 255);
                    assertRgb(48, 255, 0, 0);
                    assertRgb(80, 0, 255, 0);

                    batch.close();
                    batch = new TextureArrayMeshBatch(2048);
                    batch.setTextureArrayBundle(bundle);
                    clear();
                    RenderStats tableLimit = new RenderStats();
                    batch.begin(projection, tableLimit);
                    batch.setShader(shader, tableLimit);
                    batch.setParameterLayout(layout, tableLimit);
                    for (int i = 0; i < 1023; i++) entity(whiteHandle, 0, 1f, 0f, 0f, tableLimit);
                    entity(whiteHandle, 32, 0f, 0f, 1f, tableLimit);
                    entity(whiteHandle, 64, 0f, 1f, 0f, tableLimit);
                    batch.end(tableLimit);
                    Assert.assertEquals(1, tableLimit.drawCalls);
                    Assert.assertEquals(0, tableLimit.flushParameterCapacity);
                    Assert.assertEquals(0, tableLimit.flushCapacity);
                    Assert.assertEquals(1, tableLimit.flushEnd);
                    assertRgb(16, 255, 0, 0);
                    assertRgb(48, 0, 0, 255);
                    assertRgb(80, 0, 255, 0);

                    clear();
                    RenderStats distinctCapacity = new RenderStats();
                    batch.begin(projection, distinctCapacity);
                    batch.setShader(shader, distinctCapacity);
                    batch.setParameterLayout(layout, distinctCapacity);
                    for (int i = 1; i <= 1023; i++) {
                        entity(whiteHandle, 0, i / 1023f, 0f, 0f, distinctCapacity);
                    }
                    entity(whiteHandle, 32, 0f, 0f, 1f, distinctCapacity);
                    batch.end(distinctCapacity);
                    Assert.assertEquals(2, distinctCapacity.drawCalls);
                    Assert.assertEquals(1, distinctCapacity.flushParameterCapacity);
                    Assert.assertEquals(0, distinctCapacity.flushCapacity);
                    assertRgb(16, 255, 0, 0);
                    assertRgb(48, 0, 0, 255);

                    clear();
                    RenderStats shaderChange = new RenderStats();
                    batch.begin(projection, shaderChange);
                    batch.setShader(shader, shaderChange);
                    batch.setParameterLayout(layout, shaderChange);
                    entity(whiteHandle, 0, 1f, 0f, 0f, shaderChange);
                    batch.setShader(yellowShader, shaderChange);
                    batch.setParameterLayout(ShaderParameterLayout.EMPTY, shaderChange);
                    batch.setEntityParameters(null, shaderChange);
                    batch.draw(whiteHandle, 32, 0, 32, 32, 64, 32, 64, 0,
                            0, 0, 1, 1, shaderChange);
                    batch.end(shaderChange);
                    Assert.assertEquals(2, shaderChange.drawCalls);
                    Assert.assertEquals(1, shaderChange.flushStateChanges);
                    assertRgb(16, 255, 0, 0);
                    assertRgb(48, 255, 255, 0);

                    clear();
                    RenderStats blendChange = new RenderStats();
                    batch.begin(projection, blendChange);
                    batch.setShader(shader, blendChange);
                    batch.setParameterLayout(layout, blendChange);
                    batch.setBlendMode(false, GL20.GL_ONE, GL20.GL_ZERO, blendChange);
                    entity(whiteHandle, 0, 1f, 0f, 0f, blendChange);
                    batch.flush(blendChange); // RenderSubmitSystem flushes before applying a blend change.
                    batch.setShader(alphaShader, blendChange);
                    batch.setParameterLayout(ShaderParameterLayout.EMPTY, blendChange);
                    batch.setBlendMode(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, blendChange);
                    batch.setEntityParameters(null, blendChange);
                    batch.draw(whiteHandle, 0, 0, 0, 32, 32, 32, 32, 0,
                            0, 0, 1, 1, blendChange);
                    batch.end(blendChange);
                    Assert.assertEquals(2, blendChange.drawCalls);
                    assertRgbNear(16, 128, 0, 128);

                    Array<ShaderFloatParam> gainDefaults = new Array<>();
                    gainDefaults.add(new ShaderFloatParam("u_gain", 0f));
                    Array<ShaderFloatParam> boostDefaults = new Array<>();
                    boostDefaults.add(new ShaderFloatParam("u_boost", 0f));
                    ShaderParameterLayout gainLayout = new ShaderParameterLayout("test", gainDefaults);
                    ShaderParameterLayout boostLayout = new ShaderParameterLayout("glow_pulse", boostDefaults);
                    transitionFrame(projection, shader, gainLayout, 0, "u_gain", .25f,
                            null, null, null, 0f, 64, 0, 0, 0);
                    transitionFrame(projection, shader, gainLayout, 0, "u_gain", .5f,
                            boostShader, boostLayout, "u_boost", .75f, 128, 0, 0, 191);
                    transitionFrame(projection, boostShader, boostLayout, 32, "u_boost", .4f,
                            null, null, null, 0f, 0, 0, 0, 102);
                    transitionFrame(projection, boostShader, boostLayout, 0, "u_boost", .6f,
                            shader, gainLayout, "u_gain", .8f, 0, 153, 204, 0);
                    transitionFrame(projection, shader, gainLayout, 0, "u_gain", .3f,
                            null, null, null, 0f, 77, 0, 0, 0);

                    transitionFrame(projection, ShaderRegistry.get("test"),
                            ShaderRegistry.getParameterLayout(testIndex), 0, "u_gain", .25f,
                            null, null, null, 0f, 64, 0, 0, 0);
                    ShaderProgram originalTestProgram = ShaderRegistry.get("test");
                    writeProjectShader("alpha", "u_alpha", FRAGMENT);
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    ShaderRegistry.saveProjectIndices();
                    int alphaIndex = ShaderRegistry.indexOf("alpha");
                    Assert.assertTrue(alphaIndex > testIndex && alphaIndex > tintIndex && alphaIndex > glowIndex);
                    Assert.assertNotSame(originalTestProgram, ShaderRegistry.get("test"));
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    transitionFrame(projection, ShaderRegistry.get("test"),
                            ShaderRegistry.getParameterLayout(testIndex), 0, "u_gain", .5f,
                            ShaderRegistry.get("tint"), ShaderRegistry.getParameterLayout(tintIndex),
                            "u_tint", .75f, 128, 0, 0, 191);

                    Assert.assertTrue(projectDir.child("shaders/custom/material/alpha").deleteDirectory());
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    ShaderRegistry.saveProjectIndices();
                    Assert.assertEquals(-1, ShaderRegistry.indexOf("alpha"));
                    Assert.assertNull(ShaderRegistry.getByIdx(alphaIndex));
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    transitionFrame(projection, ShaderRegistry.get("tint"),
                            ShaderRegistry.getParameterLayout(tintIndex), 32, "u_tint", .4f,
                            null, null, null, 0f, 0, 0, 0, 102);

                    ShaderRegistry.disposeAll();
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    Assert.assertNull(ShaderRegistry.getByIdx(alphaIndex));
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    transitionFrame(projection, ShaderRegistry.get("tint"),
                            ShaderRegistry.getParameterLayout(tintIndex), 0, "u_tint", .6f,
                            ShaderRegistry.get("test"), ShaderRegistry.getParameterLayout(testIndex),
                            "u_gain", .8f, 0, 153, 204, 0);
                    transitionFrame(projection, ShaderRegistry.get("test"),
                            ShaderRegistry.getParameterLayout(testIndex), 0, "u_gain", .3f,
                            null, null, null, 0f, 77, 0, 0, 0);

                    writeProjectShader("alpha", "u_alpha", FRAGMENT);
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    ShaderRegistry.saveProjectIndices();
                    Assert.assertTrue(ShaderRegistry.indexOf("alpha") > alphaIndex);
                    Assert.assertNull(ShaderRegistry.getByIdx(alphaIndex));
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);

                    FileHandle indexFile = projectDir.child("shaders/shader-indices.json");
                    String savedIndices = indexFile.readString("UTF-8");
                    ShaderRegistry.disposeAll();
                    indexFile.writeString("{\"nextIndex\":3,\"names\":{\"test\":1,\"tint\":1}}", false);
                    try {
                        ShaderRegistry.reloadForProject(projectDir, "shaders");
                        Assert.fail("Duplicate shader indexes must be rejected");
                    } catch (IllegalStateException expected) {
                        Assert.assertTrue(expected.getMessage().contains("Conflicting shader index"));
                    } finally {
                        indexFile.writeString(savedIndices, false, "UTF-8");
                    }
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    transitionFrame(projection, ShaderRegistry.get("test"),
                            ShaderRegistry.getParameterLayout(testIndex), 0, "u_gain", .5f,
                            ShaderRegistry.get("tint"), ShaderRegistry.getParameterLayout(tintIndex),
                            "u_tint", .75f, 128, 0, 0, 191);
                } catch (Throwable ex) {
                    failure[0] = ex;
                } finally {
                    Gdx.app.exit();
                }
            }

            private void entity(int handle, float x, float red, float green, float blue, RenderStats stats) {
                Array<ShaderFloatParam> params = new Array<>();
                params.add(new ShaderFloatParam("red", red));
                params.add(new ShaderFloatParam("green", green));
                params.add(new ShaderFloatParam("blue", blue));
                batch.setEntityParameters(params, stats);
                batch.draw(handle, x, 0, x, 32, x + 32, 32, x + 32, 0,
                        0, 0, 1, 1, stats);
            }

            private void clear() {
                Gdx.gl.glViewport(0, 0, 96, 32);
                Gdx.gl.glClearColor(0, 0, 0, 1);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            }

            private void transitionFrame(Matrix4 projection, ShaderProgram firstShader,
                                         ShaderParameterLayout firstLayout, float firstX,
                                         String firstName, float firstValue,
                                         ShaderProgram secondShader, ShaderParameterLayout secondLayout,
                                         String secondName, float secondValue,
                                         int leftRed, int leftGreen, int rightRed, int rightGreen) {
                clear();
                RenderStats frame = new RenderStats();
                batch.begin(projection, frame);
                transitionEntity(firstShader, firstLayout, firstX, firstName, firstValue, frame);
                if (secondShader != null) {
                    transitionEntity(secondShader, secondLayout, 32, secondName, secondValue, frame);
                }
                batch.end(frame);
                Assert.assertEquals(secondShader == null ? 1 : 2, frame.drawCalls);
                if (secondShader != null) Assert.assertEquals(1, frame.flushStateChanges);
                assertRgbNear(16, leftRed, leftGreen, 0);
                assertRgbNear(48, rightRed, rightGreen, 0);
            }

            private void transitionEntity(ShaderProgram program, ShaderParameterLayout layout,
                                          float x, String name, float value, RenderStats frame) {
                batch.setShader(program, frame);
                batch.setParameterLayout(layout, frame);
                Array<ShaderFloatParam> values = new Array<>();
                values.add(new ShaderFloatParam(name, value));
                batch.setEntityParameters(values, frame);
                batch.draw(InternalTextures.whiteHandle(), x, 0, x, 32, x + 32, 32, x + 32, 0,
                        0, 0, 1, 1, frame);
            }

            private void writeProjectShader(String name, String parameter, String fragment) {
                FileHandle directory = projectDir.child("shaders/custom/material/" + name);
                directory.mkdirs();
                directory.child("shader.json").writeString("{\"name\":\"" + name
                        + "\",\"mode\":\"TEXTURE_ARRAY\",\"kind\":\"MATERIAL\",\"parameters\":{\""
                        + parameter + "\":0.0}}", false);
                directory.child("desktop-gl30.vert").writeString(
                        Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString(), false);
                directory.child("desktop-gl30.frag").writeString(fragment, false);
            }

            private void assertShaderReference(String name, String parameter,
                                               RenderMaterialComponent material,
                                               DynamicEntityRenderState state, int slot) {
                int index = ShaderRegistry.indexOf(name);
                Assert.assertEquals(index, material.shaderIdx);
                Assert.assertEquals(index, state.shader[slot]);
                Assert.assertSame(ShaderRegistry.get(name), ShaderRegistry.getByIdx(state.shader[slot]));
                Assert.assertEquals(name, ShaderRegistry.getParameterLayout(index).shaderName());
                Assert.assertEquals(0, ShaderRegistry.getParameterLayout(index).slot(parameter));
            }

            private void assertRgb(int x, int red, int green, int blue) {
                assertRgbNear(x, red, green, blue, 0);
            }

            private void assertRgbNear(int x, int red, int green, int blue) {
                assertRgbNear(x, red, green, blue, 2);
            }

            private void assertRgbNear(int x, int red, int green, int blue, int tolerance) {
                ByteBuffer pixel = BufferUtils.newByteBuffer(4);
                Gdx.gl.glReadPixels(x, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
                Assert.assertEquals("red at x=" + x, red, pixel.get(0) & 255, tolerance);
                Assert.assertEquals("green at x=" + x, green, pixel.get(1) & 255, tolerance);
                Assert.assertEquals("blue at x=" + x, blue, pixel.get(2) & 255, tolerance);
            }

            @Override public void dispose() {
                if (batch != null) batch.close();
                if (bundle != null) bundle.textureArray.dispose();
                if (shader != null) shader.dispose();
                if (yellowShader != null) yellowShader.dispose();
                if (alphaShader != null) alphaShader.dispose();
                if (boostShader != null) boostShader.dispose();
                ShaderRegistry.disposeAll();
                InternalTextures.dispose();
                TextureRegistry.clear();
                if (projectDir != null) projectDir.deleteDirectory();
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("GPU parameter table smoke failed", failure[0]);
    }
}
