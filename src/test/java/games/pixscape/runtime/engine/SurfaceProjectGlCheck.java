package games.pixscape.runtime.engine;

import com.artemis.*;
import com.artemis.io.*;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.*;
import org.junit.Assert;
import java.nio.file.Files;

/** Saved-project integration: production loader, ECS extraction, material rows and mode activation. */
public final class SurfaceProjectGlCheck {
 public static void verify(){
  final Throwable[] failure={null};
  Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
  config.setInitialVisible(false);config.disableAudio(true);config.setWindowedMode(320,240);
  config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
  new Lwjgl3Application(new ApplicationAdapter(){public void create(){
   com.badlogic.gdx.graphics.profiling.GLProfiler profiler=new com.badlogic.gdx.graphics.profiling.GLProfiler(Gdx.graphics);
   profiler.setListener(com.badlogic.gdx.graphics.profiling.GLErrorListener.THROWING_LISTENER);profiler.enable();
   try{run();}catch(Throwable error){failure[0]=error;}finally{profiler.disable();Gdx.app.exit();}
  }},config);
  if(failure[0]!=null)throw new AssertionError("Surface saved-project integration",failure[0]);
 }
 private static void run()throws Exception{
  FileHandle root=new FileHandle(Files.createTempDirectory("pixscape-surface-project-").toFile());
  FileHandle project=root.child(PixscapeEngine.RUNTIME_DIR_NAME);
  StringBuilder scenes=new StringBuilder();
  for(String name:new String[]{"off","legacy","forward"}){
   if(scenes.length()>0)scenes.append(',');
   scenes.append('"').append(name).append("\":{\"name\":\"").append(name).append("\",\"file\":\"").append(name)
    .append(".json\",\"lightingEnabled\":").append(!name.equals("off"))
    .append(",\"forwardLighting\":").append(name.equals("forward"))
    .append(",\"ambientMulR\":0.2,\"ambientMulG\":0.2,\"ambientMulB\":0.2,\"nextEntityStableId\":7,\"nextPhysicsShapeId\":1,\"sceneSchemaVersion\":3}");
  }
  project.child("project.json").writeString("{\"projectFileName\":\"surface\",\"currentSceneName\":\"off\",\"scenes\":{"+scenes+"}}",false,"UTF-8");
  shader(project,"lit","LIT");shader(project,"unlit","UNLIT");
  Pixmap png=new Pixmap(2048,2048,Pixmap.Format.RGBA8888);png.setColor(Color.WHITE);png.fill();
  project.child("atlases").mkdirs();PixmapIO.writePNG(project.child("atlases/white.png"),png);png.dispose();
  for(String name:new String[]{"off","legacy","forward"}){project.child("atlases/white.png").copyTo(project.child("atlases/"+name+".png"));project.child("atlases/"+name+".atlas").writeString(
   name+".png\nsize: 2048,2048\nformat: RGBA8888\nfilter: Nearest,Nearest\nrepeat: none\nwhite__a1\n  rotate: false\n  xy: 0, 0\n  size: 64, 64\n  orig: 64, 64\n  offset: 0, 0\n  index: -1\n",false,"UTF-8");}
  PixscapeEngine engine=new PixscapeEngine();
  try {
   engine.loadProject(root);
   int lit=ShaderRegistry.indexOf("lit"),unlit=ShaderRegistry.indexOf("unlit");Assert.assertTrue(lit>=0 && unlit>=0);
   ShaderRegistry.saveProjectIndices();
   for(String name:new String[]{"off","legacy","forward"})writeScene(project.child("scenes/"+name+".json"),name,lit,unlit);
   FileHandle evidence=new FileHandle("build/surface-validation/project");
   evidence.mkdirs();copyTree(project, evidence.child(PixscapeEngine.RUNTIME_DIR_NAME));
   load(engine,"off");
   RenderSubmitSystem submit=engine.system(RenderSubmitSystem.class);
   Assert.assertFalse(engine.isLightingEnabled());Assert.assertNull(submit.lightingRenderer());Assert.assertNull(submit.forwardRenderer());
   java.lang.reflect.Field queueField=RenderSubmitSystem.class.getDeclaredField("frameQueue");queueField.setAccessible(true);
   FrameRenderQueue queue=(FrameRenderQueue)queueField.get(submit);Assert.assertNull(queue.lighting);
   int first=sample(engine,40,70),second=sample(engine,120,70),unlitOff=sample(engine,240,70);
   Assert.assertEquals("first entity gain",64,first>>>24,2);Assert.assertEquals("second entity gain",191,second>>>24,2);
   Assert.assertTrue(engine.setLightingEnabled(true));Assert.assertNotNull(submit.lightingRenderer());
   engine.setAmbientLight(0,0,0);Assert.assertEquals(0,sample(engine,40,70)>>>24);
   Assert.assertEquals(unlitOff,sample(engine,240,70));
   Assert.assertTrue(engine.setLightingEnabled(false));Assert.assertEquals(first,sample(engine,40,70));
   // A malformed edit must keep the last working program and numeric identity.
   Object working=ShaderRegistry.get("lit");FileHandle body=project.child("shaders/custom/material/lit/surface.glsl");String original=body.readString("UTF-8");
   body.writeString("return missing_value;",false,"UTF-8");
   Assert.assertThrows(RuntimeException.class,()->ShaderRegistry.reloadForProject(project,"shaders",true));
   Assert.assertSame(working,ShaderRegistry.get("lit"));body.writeString(original,false,"UTF-8");
   // A rename keeps the same slot used in the saved ECS data.
   FileHandle old=project.child("shaders/custom/material/lit"),renamed=old.sibling("renamed");old.moveTo(renamed);
   FileHandle metadata=renamed.child("shader.json");metadata.writeString(metadata.readString("UTF-8").replace("\"lit\"","\"renamed\""),false,"UTF-8");
   ShaderRegistry.renameForProject(project,"shaders","lit","renamed");ShaderRegistry.saveProjectIndices();Assert.assertEquals(lit,ShaderRegistry.indexOf("renamed"));
   for(String scene:new String[]{"legacy","forward"}){
    load(engine,scene);Assert.assertFalse("No active light selects Legacy",engine.isForwardLighting());
    engine.setAmbientLight(0,0,0);Assert.assertEquals(unlitOff,sample(engine,240,70));
    engine.api().entities().requireEntityId(engine.findEntityByStableId(6)).light().setEnabled(true);
    int illuminated=sample(engine,40,70);
    Assert.assertTrue("Lit receives a local light",(illuminated>>>24)>0);
    Assert.assertEquals(scene.equals("forward"),engine.isForwardLighting());Assert.assertEquals(unlitOff,sample(engine,240,70));
    // Real entity parameter publication is shared by the visible and caster paths.
    ShaderParamsComponent values=engine.getWorld().getMapper(ShaderParamsComponent.class).get(engine.findEntityByStableId(5));
    values.floats.get(1).value=1.1f;
    Assert.assertEquals("Mask discards the visible image",0,sample(engine,275,150));
    values.floats.get(1).value=0f;Assert.assertTrue((sample(engine,275,150)>>>24)>0);
    FrameRenderQueue q=(FrameRenderQueue)queueField.get(engine.system(RenderSubmitSystem.class));
    boolean linked=false;for(games.pixscape.runtime.render.lighting.LightingFrame.Caster caster:q.lighting.casters)
     if(caster.surface.materialParameters==values){linked=true;Assert.assertEquals(unlit,caster.surface.materialShader);}
    Assert.assertTrue("Unlit still casts, using its entity parameters",linked);
    engine.api().entities().requireEntityId(engine.findEntityByStableId(6)).light().setEnabled(false);
    sample(engine,40,70);Assert.assertFalse(engine.isForwardLighting());
    if(scene.equals("forward")) {
     engine.setLightingEnabled(false);
     int lightId=engine.findEntityByStableId(6);engine.api().entities().requireEntityId(lightId).light().setEnabled(true);
     int extra=engine.findEntityByStableId(4);engine.getWorld().getMapper(PointLightComponent.class).create(extra);
     sample(engine,40,70);
     Assert.assertEquals(2,games.pixscape.runtime.render.lighting.ActiveLights.count(engine.getWorld()));
     Assert.assertThrows(IllegalArgumentException.class,()->engine.setLightingEnabled(true));
     Assert.assertFalse("Failed Forward admission retains Off",engine.isLightingEnabled());
     engine.getWorld().getMapper(PointLightComponent.class).remove(extra);sample(engine,40,70);
     Assert.assertTrue(engine.setLightingEnabled(true));sample(engine,40,70);
     int beforeRestore=sample(engine,240,70);
     com.badlogic.gdx.graphics.glutils.ShaderProgram.invalidateAllShaderPrograms(Gdx.app);
     Texture.invalidateAllTextures(Gdx.app);TextureArray.invalidateAllTextureArrays(Gdx.app);
     String managedTargets=FrameBuffer.getManagedStatus();
     FrameBuffer.invalidateAllFrameBuffers(Gdx.app);
     Assert.assertEquals(managedTargets,FrameBuffer.getManagedStatus());
     Assert.assertEquals("Managed GL resource restoration",beforeRestore,sample(engine,240,70));
    }

   }
   SuppliedProjectGlCheck.run(engine,new FileHandle("build/surface-validation"));
   FrameBuffer external=new FrameBuffer(Pixmap.Format.RGBA8888,352,264,false);
   try{external.begin();Gdx.gl.glViewport(8,6,320,240);engine.setLightingEnabled(false);
    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(22,21,280,190);Gdx.gl.glDepthMask(false);engine.render();
    java.nio.IntBuffer state=BufferUtils.newIntBuffer(4);Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);Assert.assertEquals(external.getFramebufferHandle(),state.get(0));
    state.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,state);Assert.assertEquals(8,state.get(0));Assert.assertEquals(320,state.get(2));
    Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
    state.clear();Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_WRITEMASK,state);Assert.assertEquals(0,state.get(0));
   }finally{Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);external.end();external.dispose();}
   System.out.println("SURFACE_PROJECT_PASS off/lit/unlit/parameters/mask/rename/failure/reload/external-target");
  }finally{engine.dispose();root.deleteDirectory();}
 }
 private static void copyTree(FileHandle source,FileHandle target){if(source.isDirectory()){target.mkdirs();for(FileHandle child:source.list())copyTree(child,target.child(child.name()));}else source.copyTo(target);}
 private static void load(PixscapeEngine engine,String name){
  engine.resize(320,240);Gdx.gl.glViewport(0,0,320,240);SceneLoadHandle load=engine.beginLoadScene(name);
  while(!load.isReady()&&!load.isFailed())load.update();if(load.isFailed())throw new IllegalStateException(load.failure());
  engine.getCamera().viewportWidth=320;engine.getCamera().viewportHeight=240;engine.getCamera().position.set(160,120,0);engine.getCamera().update();
 }
 private static int sample(PixscapeEngine engine,int x,int y){Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);engine.update(0);engine.render();Pixmap p=Pixmap.createFromFrameBuffer(x,y,1,1);try{return p.getPixel(0,0);}finally{p.dispose();}}
 private static void shader(FileHandle project,String name,String lighting){
  FileHandle dir=project.child("shaders/custom/material/"+name);
  dir.child("shader.json").writeString("{\"name\":\""+name+"\",\"contract\":\"surface-v1\",\"lighting\":\""+lighting+"\",\"parameters\":{\"gain\":1,\"cut\":0}}",false,"UTF-8");
  dir.child("surface.glsl").writeString("return vec4(base.rgb*pixscapeEntityFloat(PIXSCAPE_PARAM_gain),base.a);",false,"UTF-8");
  dir.child("mask.glsl").writeString("return step(pixscapeEntityFloat(PIXSCAPE_PARAM_cut),uv.x);",false,"UTF-8");
 }
 private static void writeScene(FileHandle file,String tag,int lit,int unlit)throws Exception{
  World world=new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
  try{
   int layer=world.create();world.getMapper(LayerComponent.class).create(layer).layerIndex=0;world.getMapper(PixscapeIdentityComponent.class).create(layer).stableId=1;
   for(int i=0;i<5;i++){
    int e=world.create();world.getMapper(PixscapeIdentityComponent.class).create(e).stableId=i+2;
    world.getMapper(EntityIndexComponent.class).create(e).layerIndex=0;
    world.getMapper(VisibilityComponent.class).create(e).visible=true;
    TransformComponent t=world.getMapper(TransformComponent.class).create(e);t.x=i<3?20+i*90:250;t.y=i<3?40:125;
    DimensionsComponent d=world.getMapper(DimensionsComponent.class).create(e);d.width=60;d.height=60;
    world.getMapper(OrientedBoundsComponent.class).create(e);world.getMapper(AABBComponent.class).create(e);
    RenderMaterialComponent material=world.getMapper(RenderMaterialComponent.class).create(e);material.shaderIdx=i<2?lit:unlit;material.blendModeId=0;
    if(i==4){PointLightComponent light=world.getMapper(PointLightComponent.class).create(e);light.enabled=false;light.shadowResolution=256;light.shadowQuality=0;light.radius=600;light.height=150;t.x=140;t.y=180;material.shaderIdx=ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());material.blendModeId=3;}
    else {
     AssetRefComponent ref=world.getMapper(AssetRefComponent.class).create(e);ref.assetId=1;ref.atlasTag=tag;
     world.getMapper(TextureRegionComponent.class).create(e);
     ShaderParamsComponent params=world.getMapper(ShaderParamsComponent.class).create(e);params.floats.add(new ShaderFloatParam("gain",i==0?.25f:i==1?.75f:.6f));params.floats.add(new ShaderFloatParam("cut",0));
     world.getMapper(SurfaceLightingComponent.class).create(e).shadowCaster=i==3;
    }
   }
   world.process();WorldSerializationManager manager=world.getSystem(WorldSerializationManager.class);manager.setSerializer(new JsonArtemisSerializer(world));
   file.parent().mkdirs();try(java.io.OutputStream output=file.write(false)){manager.save(output,new SaveFileFormat(world.getAspectSubscriptionManager().get(Aspect.all()).getEntities()));}
  }finally{world.dispose();}
 }
}
