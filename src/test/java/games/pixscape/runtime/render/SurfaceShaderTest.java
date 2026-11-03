package games.pixscape.runtime.render;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import games.pixscape.runtime.render.batch.ShaderParameterLayout;
import org.junit.Test;
import static org.junit.Assert.*;
public class SurfaceShaderTest {
 @Test public void missingContractPreservesLegacyAndExplicitContractRequiresItsBody() {
  assertNull(SurfaceShader.read(new JsonReader().parse("{}"),new FileHandle("missing"),ShaderParameterLayout.EMPTY));
  assertThrows(IllegalArgumentException.class,()->SurfaceShader.read(new JsonReader().parse("{contract:unknown}"),new FileHandle("missing"),ShaderParameterLayout.EMPTY));
 }
 @Test public void multilineCommentsDoNotExposeRendererIdentifiers() {
  new SurfaceShader(SurfaceShader.Lighting.LIT,"/* u_array\n gl_FragCoord */\nreturn base; // v_color",null,ShaderParameterLayout.EMPTY);
  assertThrows(IllegalArgumentException.class,()->new SurfaceShader(SurfaceShader.Lighting.LIT,"float value=1.;\nreturn vec4(u_time);",null,ShaderParameterLayout.EMPTY));
 }
 @Test public void authoredBodyCannotEscapeTheSurfaceFunctionOrAccessRendererGlobals() {
  for(String body:new String[]{"} void main(){", "#define X 1", "return texture(u_array,uv);", "return vec4(gl_FragCoord);"})
   assertThrows(IllegalArgumentException.class,()->new SurfaceShader(SurfaceShader.Lighting.LIT,body,null,ShaderParameterLayout.EMPTY));
  new SurfaceShader(SurfaceShader.Lighting.LIT,"if(time>0.){return base;}return vec4(base.rgb,base.a);",null,ShaderParameterLayout.EMPTY);
 }
}
