package games.pixscape.runtime.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.render.batch.ShaderParameterLayout;

/** Authored surface-v1 function bodies. No vertex, framebuffer or lighting ownership. */
public final class SurfaceShader {
    public static final String CONTRACT = "surface-v1";
    public enum Lighting { LIT, UNLIT }

    public final Lighting lighting;
    public final String colorBody;
    public final String maskBody;
    public final ShaderParameterLayout parameters;

    public SurfaceShader(Lighting lighting, String colorBody, String maskBody, ShaderParameterLayout parameters) {
        if (lighting == null || parameters == null) throw new IllegalArgumentException("Surface shader requires lighting and parameters");
        validateBody(colorBody, "surface");
        if (maskBody != null) validateBody(maskBody, "mask");
        this.lighting = lighting;
        this.colorBody = colorBody;
        this.maskBody = maskBody;
        this.parameters = parameters;
    }

    /** Missing contract explicitly means the existing complete vertex/fragment shader format. */
    public static SurfaceShader read(JsonValue metadata, FileHandle directory, ShaderParameterLayout parameters) {
        if (metadata == null || !metadata.has("contract")) return null;
        if (!CONTRACT.equals(metadata.getString("contract")))
            throw new IllegalArgumentException("Unsupported surface contract in " + directory.path());
        Lighting lighting;
        try { lighting = Lighting.valueOf(metadata.getString("lighting", "")); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Surface shader requires lighting LIT or UNLIT: " + directory.path(), error); }
        FileHandle color = directory.child("surface.glsl"), mask = directory.child("mask.glsl");
        if (!color.exists()) throw new IllegalArgumentException("Missing surface.glsl in " + directory.path());
        return new SurfaceShader(lighting, color.readString("UTF-8"), mask.exists() ? mask.readString("UTF-8") : null, parameters);
    }

    private static void validateBody(String body, String label) {
        if (body == null || body.trim().length() == 0)
            throw new IllegalArgumentException("Empty " + label + " function body");
        String code = body.replaceAll("/\\*[\\s\\S]*?\\*/", " ").replaceAll("//[^\\r\\n]*", " ");
        int braces=0;
        for(int i=0;i<code.length();i++) {
            char c=code.charAt(i);
            if(c=='{')braces++;
            if(c=='}' && --braces<0)throw new IllegalArgumentException("Unbalanced " + label + " function body");
        }
        if(braces!=0)throw new IllegalArgumentException("Unbalanced " + label + " function body");
        if(code.matches("[\\s\\S]*\\b(gl_|u_|v_|a_)[A-Za-z0-9_]*\\b[\\s\\S]*") || code.contains("surfaceMaterial") || code.contains("surfaceRegion") || code.contains("surfaceImage"))
            throw new IllegalArgumentException("Surface bodies use base, uv, time and pixscapeEntityFloat; renderer globals are not part of the contract");
        // Bodies are compiled inside engine functions: global declarations and helper functions
        // are rejected by GLSL. Preprocessor directives cannot escape that function boundary.
        if (body.indexOf('#') >= 0)
            throw new IllegalArgumentException("Surface function bodies do not accept preprocessor directives");
    }

    public String function(String name, boolean mask) {
        StringBuilder source = new StringBuilder(mask ? "float " : "vec4 ");
        source.append(name).append(mask ? "(vec2 uv,float time){\n" : "(vec4 base,vec2 uv,float time){\n");
        for (int i = 0; i < parameters.size(); i++)
            source.append("const int PIXSCAPE_PARAM_").append(parameters.name(i)).append('=').append(i).append(";\n");
        source.append(mask ? maskBody == null ? "return 1.;" : maskBody : colorBody);
        return source.append("\n}\n").toString();
    }

    /** Surface alpha affects visible coverage only. The explicit mask discards at 0.5 in
     * every variant; surviving shadow pixels retain the existing texture-alpha threshold. */
    public String directFragment(String version) {
        return version + "\nin highp vec2 v_uv;in highp vec4 v_color;flat in highp int v_layer;"
                + "flat in highp int v_paramId;uniform highp sampler2DArray u_array;"
                + "uniform highp sampler2D u_entityParams;uniform highp float u_time,u_cutoutThreshold,u_worldCoverage;"
                + "uniform highp vec3 u_ambientMul;out highp vec4 fragColor;\n"
                + samplingFunctions()
                + "vec4 surfaceImageAt(vec2 p){return texture(u_array,vec3(p,float(v_layer)));}"
                + "vec2 surfaceImageSize(){return vec2(textureSize(u_array,0).xy);}\n"
                + parameterFunction("v_paramId") + function("surfaceColor", false) + function("surfaceMask", true)
                + "void main(){if(surfaceMask(v_uv,u_time)<.5)discard;"
                + "vec4 base=pixscapeSample(pixscapeLocalUV(v_uv));"
                + "vec4 value=surfaceColor(base,v_uv,u_time);"
                + "if(u_cutoutThreshold>=0.&&value.a<u_cutoutThreshold)discard;"
                + "fragColor=value*v_color;fragColor.rgb*=u_ambientMul;"
                + "if(u_worldCoverage>.5)fragColor.a=1.;}";
    }

    public static String directVertex(String version) {
        return version + "in highp vec2 a_position,a_texCoord0;in highp vec4 a_color;"
                + "in highp float a_layer,a_paramId;in highp vec4 a_surfaceRegion;flat out highp vec4 surfaceRegion;uniform highp mat4 u_projTrans;"
                + "out highp vec2 v_uv;out highp vec4 v_color;flat out highp int v_layer,v_paramId;"
                + "void main(){surfaceRegion=a_surfaceRegion;v_uv=a_texCoord0;v_color=a_color;v_layer=int(a_layer+.5);"
                + "v_paramId=int(a_paramId+.5);gl_Position=u_projTrans*vec4(a_position,0.,1.);}";
    }

    public static String parameterFunction(String row) {
        return "float pixscapeEntityFloat(int slot){vec4 values=texelFetch(u_entityParams,ivec2(slot/4,int(" + row + ")),0);return values[slot%4];}\n";
    }

    /** Bounds describe the sampled image, independent of page placement and geometry flips.
     * Outside is transparent; inside is clamped to texel centers to exclude adjacent regions. */
    public static String samplingFunctions() {
        return "flat in highp vec4 surfaceRegion;\n"
                + "vec4 surfaceImageAt(vec2 p);vec2 surfaceImageSize();\n"
                + "vec2 pixscapeLocalUV(vec2 atlasUV){return (atlasUV-surfaceRegion.xy)/max(surfaceRegion.zw-surfaceRegion.xy,vec2(1e-20));}\n"
                + "vec4 pixscapeSample(vec2 localUV){if(any(lessThan(localUV,vec2(0)))||any(greaterThan(localUV,vec2(1))))return vec4(0);"
                + "vec2 halfTexel=.5/surfaceImageSize();vec2 center=(surfaceRegion.xy+surfaceRegion.zw)*.5;"
                + "vec2 lo=min(surfaceRegion.xy+halfTexel,center),hi=max(surfaceRegion.zw-halfTexel,center);"
                + "return surfaceImageAt(clamp(mix(surfaceRegion.xy,surfaceRegion.zw,localUV),lo,hi));}\n";
    }

    /** Same wrapper used to validate an edited candidate before replacing registered sources. */
    public String lightingFunctions(boolean shadow) {
        return "flat in highp vec2 surfaceMaterial;uniform highp float u_time;uniform highp sampler2D u_entityParams;\n"
                + samplingFunctions()
                + parameterFunction("surfaceMaterial.y") + function("candidateMask", true)
                + "float surfaceMask(vec2 uv){return candidateMask(uv,u_time);}\n"
                + (shadow ? "" : function("candidateColor", false)
                + "vec4 surfaceColor(vec4 base,vec2 uv){if(surfaceMask(uv)<.5)discard;return candidateColor(pixscapeSample(pixscapeLocalUV(uv)),uv,u_time);}\n");
    }
}
