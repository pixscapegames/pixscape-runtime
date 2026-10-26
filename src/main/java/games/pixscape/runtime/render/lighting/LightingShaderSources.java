package games.pixscape.runtime.render.lighting;

/** Explicit GLSL fragments shared by the legacy and forward lighting receivers. */
final class LightingShaderSources {
    private LightingShaderSources() {}

    /** Sprite normal-map sample transformed into lighting space. */
    static final String NORMAL_SAMPLE =
            "in vec2 normalUV;"
            + "flat in float normalSlot;"
            + "flat in vec4 normalBasis;"
            + "uniform sampler2D u_normal0,u_normal1,u_normal2,u_normal3;"
            + "vec3 lightingNormal(vec3 geometric){"
            + "if(normalSlot<-.5)return geometric;"
            + "vec3 s;"
            + "if(normalSlot<.5)s=texture(u_normal0,normalUV).rgb;"
            + "else if(normalSlot<1.5)s=texture(u_normal1,normalUV).rgb;"
            + "else if(normalSlot<2.5)s=texture(u_normal2,normalUV).rgb;"
            + "else s=texture(u_normal3,normalUV).rgb;"
            + "s=normalize(s*2.-1.);"
            + "vec2 xy=normalBasis.xy*s.x+normalBasis.zw*s.y;"
            + "return normalize(xy.x*vec3(.7071067812,-.7071067812,0)+xy.y*vec3(.4082482905,.4082482905,.8164965809)+s.z*vec3(-.5773502692,-.5773502692,.5773502692));"
            + "}";

    /** Macro normal used to orient curved facades without changing geometric shadow depth. */
    static final String MACRO_NORMAL =
            "in vec3 curveNormal;"
            + "vec3 macroNormal(vec3 plane){"
            + "return dot(curveNormal,curveNormal)>.0001?normalize(curveNormal):plane;"
            + "}";

    /** Common shadow-map uniforms and receiver output. */
    static final String LIGHT_UNIFORMS =
            "uniform vec3 u_light,u_color,u_direction,u_cone;"
            + "uniform float u_range,u_falloff;"
            + "uniform int u_diagnostic;"
            + "out vec4 result;"
            + "uniform float u_resolution,u_atlasScale;"
            + "uniform int u_quality;"
            + "uniform sampler2D u_shadow,u_shadowIdentity,u_shadowSecond;";

    /** Cube-face atlas coordinates shared by hard shadows and PCF. */
    static final String SHADOW_COORDINATES =
            "vec2 shadowUV(vec3 d){"
            + "vec3 a=abs(d);"
            + "vec2 q;"
            + "int f;"
            + "if(a.x>=a.y&&a.x>=a.z){"
                + "if(d.x>0.){f=0;q=vec2(-d.z,-d.y)/a.x;}"
                + "else{f=1;q=vec2(d.z,-d.y)/a.x;}"
            + "}else if(a.y>=a.z){"
                + "if(d.y>0.){f=2;q=vec2(d.x,d.z)/a.y;}"
                + "else{f=3;q=vec2(d.x,-d.z)/a.y;}"
            + "}else{"
                + "if(d.z>0.){f=4;q=vec2(d.x,-d.y)/a.z;}"
                + "else{f=5;q=vec2(-d.x,-d.y)/a.z;}"
            + "}"
            + "q=clamp(q*.5+.5,vec2(.5/u_resolution),vec2(1.-.5/u_resolution));"
            + "return u_atlasScale*(q+vec2(float(f%3),float(f/3)))/vec2(3.,2.);"
            + "}";

    /** Selects d1 or d2 according to the receiver's exclusion identity. */
    static final String SHADOW_DEPTH_SELECTION =
            "float visible(vec3 d,float distance,float bias){"
            + "vec2 uv=shadowUV(d);"
            + "float first=texture(u_shadow,uv).r*u_range;"
            + "float id=texture(u_shadowIdentity,uv).r;"
            + "float depth=exclusionId>.5&&id==exclusionId?texture(u_shadowSecond,uv).r*u_range:first;"
            + "return distance-bias<=depth?1.:0.;"
            + "}";

    /** Legacy coverage, cutout and alpha handling before direct lighting. */
    private static final String LEGACY_RECEIVER_OPEN =
            "void main(){"
            + "vec4 texel=image();"
            + "if(flags.w>=0.&&texel.a<flags.w)discard;"
            + "vec4 t=texel*color;"
            + "if(flags.w!= -2.&&t.a<=0.)discard;"
            + "float alpha=flags.w== -2.||flags.w>=0.?1.:t.a;"
            + "vec3 outputColor=t.rgb;";

    /** Normal length, macro facade orientation and the legacy direct-light guard. */
    private static final String LEGACY_DIRECT_LIGHT_SETUP =
            "float value=0.;"
            + "vec3 delta=p-u_light;"
            + "float distance=length(delta);"
            + "float normalLength=length(n);"
            + "if(flags.y>.5&&normalLength>.0001){"
            + "vec3 geometric=n/normalLength;"
            + "vec3 facade=flags.z< -1.5?vec3(-.5773502692,-.5773502692,.5773502692):macroNormal(geometric);";

    /** Cosine, facade gate and geometric bias; the geometric normal remains the depth reference. */
    private static final String DIRECT_LIGHT_BIAS =
            "float cosine=dot(normal,-delta/max(distance,.0001));"
            + "if(flags.z>.5)cosine=abs(cosine);"
            + "float facadeGate=flags.z==0.?smoothstep(0.,.052335956,dot(facade,distance>0.?-delta/distance:vec3(0))):1.;"
            + "float visibility=1.;"
            + "float c=clamp(abs(dot(geometric,-delta/max(distance,.0001))),.1,1.);"
            + "float bias=2.304+1.5*distance/u_resolution*sqrt(max(0.,1.-c*c))/c;";

    /** Hard-shadow and nine-tap PCF visibility. */
    private static final String DIRECT_LIGHT_VISIBILITY =
            "if(u_quality==1&&facadeGate>0.)visibility=visible(delta,distance,bias);"
            + "if(u_quality==2&&facadeGate>0.){"
                + "vec3 direction=normalize(delta),reference=abs(direction.z)<.9?vec3(0,0,1):vec3(0,1,0);"
                + "vec3 a=normalize(cross(direction,reference)),b=cross(direction,a);"
                + "visibility=0.;"
                + "for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){"
                    + "vec3 tap=normalize(direction+2./u_resolution*(float(x)*a+float(y)*b));"
                    + "float den=dot(tap,geometric);"
                    + "float dist=abs(den)>.0001?dot(delta,geometric)/den:distance;"
                    + "visibility+=visible(tap,max(.001,dist),bias);"
                + "}"
                + "visibility/=9.;"
            + "}";

    /** Cone, attenuation and one-sided facade horizon. */
    private static final String DIRECT_LIGHT_CONE_ATTENUATION_HORIZON =
            "float cone=1.;"
            + "if(u_cone.x>.5){"
                + "float angle=dot(normalize(delta),u_direction);"
                + "cone=u_cone.z<=u_cone.y+.00001?step(u_cone.y,angle):smoothstep(u_cone.y,u_cone.z,angle);"
            + "}"
            + "value=pow(max(0.,1.-distance/u_range),max(.001,u_falloff))*max(.1,cosine)*cone*visibility;"
            + "if(flags.z==0.){"
                + "vec3 toLight=distance>0.?-delta/distance:vec3(0);"
                + "float cG=dot(normal,toLight);"
                + "value*=smoothstep(.052335956,.087155743,max(0.,cG))*facadeGate;"
            + "}"
            + "}";

    /** Legacy result and user-visible diagnostics without a normal map. */
    private static final String LEGACY_RESULT =
            "result=vec4(outputColor*u_color*value*(flags.w< -2.5?1.:alpha),alpha);"
            + "if(u_diagnostic==1)result=vec4(fract(abs(p)/256.)*alpha,alpha);"
            + "if(u_diagnostic==2)result=vec4((n*.5+.5)*alpha,alpha);"
            + "if(u_diagnostic==3)result=vec4((normalLength<.0001?vec3(1,0,1):abs(flags.y)>2.5?vec3(1,.2,.1):abs(flags.y)>1.5?vec3(.8,.6,.1):vec3(.1,.8,.4))*alpha,alpha);"
            + "}";

    /** Legacy result and diagnostics with a normal-map transformed normal. */
    private static final String MAPPED_LEGACY_RESULT =
            "result=vec4(outputColor*u_color*value*(flags.w< -2.5?1.:alpha),alpha);"
            + "if(u_diagnostic==1)result=vec4(fract(abs(p)/256.)*alpha,alpha);"
            + "if(u_diagnostic==2)result=vec4((lightingNormal(normalLength>.0001?n/normalLength:vec3(0))*.5+.5)*alpha,alpha);"
            + "if(u_diagnostic==3)result=vec4((normalLength<.0001?vec3(1,0,1):abs(flags.y)>2.5?vec3(1,.2,.1):abs(flags.y)>1.5?vec3(.8,.6,.1):vec3(.1,.8,.4))*alpha,alpha);"
            + "}";

    /** Forward coverage, cutout, additive emission and alpha handling before direct lighting. */
    private static final String FORWARD_RECEIVER_OPEN =
            "void main(){"
            + "vec4 texel=texture(u_array,vec3(uv,flags.x));"
            + "if(flags.w>=0.&&texel.a<flags.w)discard;"
            + "vec4 t=texel*color;"
            + "if(flags.w!= -2.&&flags.w!= -3.&&mod(materialMode,16.)!=3.&&t.a<=0.)discard;"
            + "float alpha=flags.w== -2.||flags.w>=0.?1.:t.a;";

    /** Forward range and macro-horizon early exit, followed by normal-map direct lighting. */
    private static final String FORWARD_DIRECT_LIGHT_SETUP =
            "float value=0.;"
            + "vec3 delta=p-u_light;"
            + "float distance=length(delta);"
            + "float normalLength=length(n);"
            + "if(flags.y>.5&&normalLength>.0001&&distance<u_range&&(flags.z!=0.||dot(lightingNormal(macroNormal(n/normalLength)),distance>0.?-delta/distance:vec3(0))>.052335956)){"
            + "vec3 geometric=n/normalLength;"
            + "vec3 facade=flags.z< -1.5?vec3(-.5773502692,-.5773502692,.5773502692):macroNormal(geometric);";

    static String legacyReceiver(String version,String inputs,String image) {
        return version+inputs+image+MACRO_NORMAL
                + LIGHT_UNIFORMS+SHADOW_COORDINATES+SHADOW_DEPTH_SELECTION
                + LEGACY_RECEIVER_OPEN+LEGACY_DIRECT_LIGHT_SETUP
                + "vec3 normal=facade;"
                + DIRECT_LIGHT_BIAS+DIRECT_LIGHT_VISIBILITY+DIRECT_LIGHT_CONE_ATTENUATION_HORIZON
                + LEGACY_RESULT;
    }

    static String legacyMappedReceiver(String version,String inputs,String image) {
        return version+inputs+image+MACRO_NORMAL+NORMAL_SAMPLE
                + LIGHT_UNIFORMS+SHADOW_COORDINATES+SHADOW_DEPTH_SELECTION
                + LEGACY_RECEIVER_OPEN+LEGACY_DIRECT_LIGHT_SETUP
                + "vec3 normal=lightingNormal(facade);"
                + DIRECT_LIGHT_BIAS+DIRECT_LIGHT_VISIBILITY+DIRECT_LIGHT_CONE_ATTENUATION_HORIZON
                + MAPPED_LEGACY_RESULT;
    }

    static String forwardReceiver(String version) {
        return version
                + "in vec2 uv;"
                + "in vec3 p,n;"
                + "in vec4 color;"
                + "flat in vec4 flags;"
                + "flat in float exclusionId,materialMode;"
                + "uniform sampler2DArray u_array;"
                + "uniform vec3 u_ambient;"
                + LIGHT_UNIFORMS+SHADOW_COORDINATES+SHADOW_DEPTH_SELECTION+NORMAL_SAMPLE+MACRO_NORMAL
                + FORWARD_RECEIVER_OPEN+FORWARD_DIRECT_LIGHT_SETUP
                + "vec3 normal=lightingNormal(facade);"
                + DIRECT_LIGHT_BIAS+DIRECT_LIGHT_VISIBILITY+DIRECT_LIGHT_CONE_ATTENUATION_HORIZON
                + "vec3 ambient=materialMode>=16.?vec3(1.):u_ambient;"
                + "result=vec4(t.rgb*(ambient+u_color*value),alpha);"
                + "}";
    }
}
