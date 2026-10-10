package games.pixscape.runtime.render.lighting;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.ShaderRegistry;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Common Runtime/Studio lighting consumer. GPU preparation is confined to the frame boundary. */
public final class LightingRenderer {
    private ShaderProgram receiver,shadow,accumulate;
    private LightingBatch batch;
    private FrameBuffer scratch,atlas;
    private Mesh screen;
    private int width,height,resolution;
    private World preparedWorld;
    private EntitySubscription lightSubscription;
    private ComponentMapper<PointLightComponent> pointMapper;
    private ComponentMapper<ConeLightComponent> coneMapper;
    private final float[] quad=new float[8];
    private final FloatBuffer zero=BufferUtils.newFloatBuffer(4);
    private final IntBuffer saved=BufferUtils.newIntBuffer(4);
    private final int[] savedViewport=new int[4];
    private final Matrix4 projection=new Matrix4(),view=new Matrix4(),matrix=new Matrix4();
    private final Vector3 eye=new Vector3(),target=new Vector3(),up=new Vector3();
    private static final float[] DIRECTIONS={1,0,0,-1,0,0,0,1,0,0,-1,0,0,0,1,0,0,-1};
    private static final float[] UPS={0,-1,0,0,-1,0,0,0,1,0,0,-1,0,-1,0,0,-1,0};
    public long shadowCpuNs,receiverCpuNs,accumulationCpuNs;
    public int selectedCasters,shadowDraws,receiverDraws;
    private final LightingFrame.Light diagnosticLight=new LightingFrame.Light();
    {diagnosticLight.radius=1;diagnosticLight.falloff=1;diagnosticLight.resolution=512;}
    private final LightingSurface missing=new LightingSurface(new float[]{
            0,0,0,0,0,0,0,0, 0,0,0,0,1,0,0,0, 0,0,0,1,1,0,0,0,
            0,0,0,0,0,0,0,0, 0,0,0,1,1,0,0,0, 0,0,0,1,0,0,0,0});

    public void prepare(World world,WorldLightComposition composition) {
        // Admit pending component additions without running gameplay or rendering systems.
        if(world.getInvocationStrategy() instanceof games.pixscape.runtime.loading.SceneLoadingInvocationStrategy)
            ((games.pixscape.runtime.loading.SceneLoadingInvocationStrategy)world.getInvocationStrategy()).synchronizeEntitySubscriptions();
        saved.clear();Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,saved);int framebuffer=saved.get(0);
        saved.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,saved);for(int i=0;i<4;i++)savedViewport[i]=saved.get(i);
        try { prepareResources(world,composition); }
        catch(RuntimeException | Error failure){dispose();throw failure;}
        finally{Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,framebuffer);
            Gdx.gl.glViewport(savedViewport[0],savedViewport[1],savedViewport[2],savedViewport[3]);}
    }
    private void prepareResources(World world,WorldLightComposition composition) {
        if(preparedWorld!=world){
            preparedWorld=world;
            lightSubscription=world.getAspectSubscriptionManager().get(Aspect.one(PointLightComponent.class,ConeLightComponent.class));
            pointMapper=world.getMapper(PointLightComponent.class);coneMapper=world.getMapper(ConeLightComponent.class);
        }
        if(Gdx.gl30==null)throw new IllegalStateException("2.5D lighting requires GL3/GLES3/WebGL2 float render targets");
        if(receiver==null){
            String version=Gdx.app.getType()==Application.ApplicationType.Desktop?"#version 330 core\n":
                    "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n";
            String attributes="in vec3 a_position;in vec2 a_uv;in vec3 a_normal;in vec4 a_color;in vec4 a_flags;in vec2 a_screen;";
            String vary="out vec3 p;out vec2 uv;out vec3 n;out vec4 color;flat out vec4 flags;";
            String vertex=version+attributes+vary+"uniform mat4 u_matrix;void main(){p=a_position;uv=a_uv;n=a_normal;color=a_color;flags=a_flags;gl_Position=u_matrix*vec4(a_screen,0.,1.);}";
            String sample="uniform sampler2D u_tex0,u_tex1,u_tex2,u_tex3;vec4 image(){if(flags.x<.5)return texture(u_tex0,uv);if(flags.x<1.5)return texture(u_tex1,uv);if(flags.x<2.5)return texture(u_tex2,uv);return texture(u_tex3,uv);}";
            String inputs="in vec3 p;in vec2 uv;in vec3 n;in vec4 color;flat in vec4 flags;";
            receiver=compile(vertex,version+inputs+sample+RECEIVER);
            shadow=compile(version+attributes+vary+"uniform mat4 u_matrix;void main(){p=a_position;uv=a_uv;n=a_normal;color=a_color;flags=a_flags;gl_Position=u_matrix*vec4(p,1.);}",
                    version+inputs+sample+"uniform vec3 u_light;uniform float u_range;out float distanceValue;void main(){if(image().a*color.a<flags.w)discard;distanceValue=length(p-u_light)/u_range;}");
            accumulate=compile(version+"in vec3 a_position;in vec2 a_texCoord0;out vec2 uv;void main(){uv=a_texCoord0;gl_Position=vec4(a_position,1.);}",
                    version+"in vec2 uv;uniform sampler2D u_original,u_contribution;out vec4 result;void main(){vec3 base=texture(u_original,uv).rgb,c=texture(u_contribution,uv).rgb;result=vec4(c/max(base,vec3(1./65536.)),0.);}");
            receiver.bind();samplers(receiver);shadow.bind();samplers(shadow);batch=new LightingBatch();
            screen=new Mesh(true,4,6,VertexAttribute.Position(),VertexAttribute.TexCoords(0));
            screen.setVertices(new float[]{-1,-1,0,0,0,-1,1,0,0,1,1,1,0,1,1,1,-1,0,1,0});screen.setIndices(new short[]{0,1,2,2,3,0});
        }
        int w=composition.targetWidth(),h=composition.targetHeight();
        if(scratch==null || width!=w || height!=h){FrameBuffer next=floatTarget(w,h,false);if(scratch!=null)scratch.dispose();scratch=next;width=w;height=h;}
        int required=0;
        IntBag entities=lightSubscription.getEntities();
        for(int i=0;i<entities.size();i++){
            int e=entities.get(i);PointLightComponent p=pointMapper.getSafe(e,null);
            ConeLightComponent c=coneMapper.getSafe(e,null);
            if(p!=null && p.enabled && p.shadowQuality!=0)required=Math.max(required,p.shadowResolution);
            if(c!=null && c.enabled && c.shadowQuality!=0)required=Math.max(required,c.shadowResolution);
        }
        if(required!=0 && required!=256 && required!=512 && required!=1024)throw new IllegalArgumentException("Unsupported shadow resolution");
        if(required!=resolution){FrameBuffer next=required==0?null:floatTarget(required*3,required*2,true);if(atlas!=null)atlas.dispose();atlas=next;resolution=required;}
    }
    private static ShaderProgram compile(String v,String f){ShaderProgram s=new ShaderProgram(v,f);if(!s.isCompiled()){String log=s.getLog();s.dispose();throw new IllegalStateException("2.5D lighting shader: "+log);}return s;}
    private static void samplers(ShaderProgram s){for(int i=0;i<4;i++)s.setUniformi("u_tex"+i,i);}
    private static FrameBuffer floatTarget(int w,int h,boolean depth){
        GLFrameBuffer.FrameBufferBuilder b=new GLFrameBuffer.FrameBufferBuilder(w,h);
        b.addFloatAttachment(depth?GL30.GL_R32F:GL30.GL_RGBA16F,depth?GL30.GL_RED:GL20.GL_RGBA,GL20.GL_FLOAT,true);
        if(depth)b.addDepthRenderBuffer(GL30.GL_DEPTH_COMPONENT24);
        FrameBuffer f=b.build();f.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
        return f;
    }
    public void render(FrameRenderQueue queue,LayerStateSOA layers,OrthographicCamera camera,WorldLightComposition composition,RenderStats stats){
        LightingFrame frame=queue.lighting;shadowCpuNs=receiverCpuNs=accumulationCpuNs=0;selectedCasters=shadowDraws=receiverDraws=0;
        composition.beginField();
        int lightCount=frame.diagnostic==0?frame.lights.size:1;
        for(int li=0;li<lightCount;li++){
            LightingFrame.Light l=frame.diagnostic==0?frame.lights.get(li):diagnosticLight;long start=System.nanoTime();int draws=stats.drawCalls;
            if(frame.timer!=null)frame.timer.begin(LightingPassTimer.SHADOW);
            if(l.quality!=0) shadows(frame,l,stats);
            if(frame.timer!=null)frame.timer.end(LightingPassTimer.SHADOW);
            shadowDraws+=stats.drawCalls-draws;shadowCpuNs+=System.nanoTime()-start;
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,scratch.getFramebufferHandle());Gdx.gl.glViewport(0,0,width,height);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_CULL_FACE);Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            zero.clear();Gdx.gl30.glClearBufferfv(GL30.GL_COLOR,0,zero);
            start=System.nanoTime();draws=stats.drawCalls;
            if(frame.timer!=null)frame.timer.begin(LightingPassTimer.RECEIVERS);
            receiver.bind();receiver.setUniformMatrix("u_matrix",camera.combined);receiver.setUniformf("u_light",l.x,l.y,l.z);
            receiver.setUniformf("u_color",l.r*l.intensity,l.g*l.intensity,l.b*l.intensity);
            receiver.setUniformf("u_range",l.radius);receiver.setUniformf("u_falloff",l.falloff);
            receiver.setUniformi("u_quality",l.quality);receiver.setUniformf("u_resolution",l.resolution);
            receiver.setUniformf("u_atlasScale",resolution==0?1f:(float)l.resolution/resolution);
            receiver.setUniformi("u_shadow",4);receiver.setUniformi("u_diagnostic",frame.diagnostic);
            receiver.setUniformf("u_cone",l.cone?1:0,l.cosOuter,l.cosInner);
            receiver.setUniformf("u_direction",l.dx,l.dy,l.dz);
            if(atlas!=null)atlas.getColorBufferTexture().bind(4);
            else composition.originalTexture().bind(4); // Sampler must be complete even in an unshadowed branch.
            batch.begin(receiver,stats);
            for(int i=0;i<queue.size;i++){
                if(queue.light[i]!=0 || queue.textureHandle[i]==0)continue;
                int layer=queue.layerIndex[i];if(layers.maxLayerIndex()>=0 && (layer<0 || layer>layers.maxLayerIndex() || !layers.enabled[layer]))continue;
                int blend=queue.blend[i];if(blend==3 || blend==4)continue;
                LightingSurface s=queue.sourceDomain[i]==FrameRenderQueue.SOURCE_TILED?frame.tiles.get(queue.sourceSlot[i]):frame.entities.get(queue.sourceEntity[i]);
                if(s==null || frame.diagnostic==0 && !s.receiveLight)s=missing;
                quad[0]=queue.x1[i];quad[1]=queue.y1[i];quad[2]=queue.x2[i];quad[3]=queue.y2[i];
                quad[4]=queue.x3[i];quad[5]=queue.y3[i];quad[6]=queue.x4[i];quad[7]=queue.y4[i];
                boolean supported=ShaderRegistry.getOrigin(queue.shader[i])==ShaderOrigin.CORE && queue.repeatFlags[i]==0;
                if(!supported && frame.diagnostic==0)throw new IllegalArgumentException(
                        "2.5D lighting requires standard coverage and color; unsupported shader/repeat at source entity "+queue.sourceEntity[i]);
                batch.draw(s,queue.textureHandle[i],queue.u1[i],queue.v1[i],queue.u2[i],queue.v2[i],queue.colorPacked[i],blend,quad,0,0,false,supported);
            }
            batch.flush();receiverDraws+=stats.drawCalls-draws;receiverCpuNs+=System.nanoTime()-start;
            if(frame.timer!=null)frame.timer.end(LightingPassTimer.RECEIVERS);
            if(frame.timer!=null)frame.timer.begin(LightingPassTimer.ACCUMULATION);
            start=System.nanoTime();composition.resumeField();Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_ONE,GL20.GL_ONE);
            composition.originalTexture().bind(0);scratch.getColorBufferTexture().bind(1);
            accumulate.bind();accumulate.setUniformi("u_original",0);accumulate.setUniformi("u_contribution",1);screen.render(accumulate,GL20.GL_TRIANGLES);
            stats.drawCalls++;stats.textureBinds+=2;accumulationCpuNs+=System.nanoTime()-start;
            if(frame.timer!=null)frame.timer.end(LightingPassTimer.ACCUMULATION);
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }
    private void shadows(LightingFrame frame,LightingFrame.Light l,RenderStats stats){
        if(atlas==null || l.resolution>resolution)
            throw new IllegalStateException("Shadow quality/resolution changed after frame preparation; prepare targets before submission");
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,atlas.getFramebufferHandle());
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glDisable(GL20.GL_BLEND);Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);Gdx.gl.glDepthMask(true);Gdx.gl.glDepthFunc(GL20.GL_LESS);
        zero.clear();zero.put(0,1);Gdx.gl30.glClearBufferfv(GL30.GL_COLOR,0,zero);
        zero.clear();zero.put(0,1);Gdx.gl30.glClearBufferfv(GL30.GL_DEPTH,0,zero);zero.put(0,0);
        shadow.bind();shadow.setUniformf("u_light",l.x,l.y,l.z);shadow.setUniformf("u_range",l.radius);
        projection.setToProjection(.05f,l.radius+256,90,1);eye.set(l.x,l.y,l.z);
        frame.casterIndex.query(frame,l,256,frame.staticCasterCount);
        selectedCasters+=frame.casterIndex.candidates.size;
        for(int face=0;face<6;face++){
            Gdx.gl.glViewport((face%3)*l.resolution,(face/3)*l.resolution,l.resolution,l.resolution);
            target.set(eye).add(DIRECTIONS[face*3],DIRECTIONS[face*3+1],DIRECTIONS[face*3+2]);
            up.set(UPS[face*3],UPS[face*3+1],UPS[face*3+2]);view.setToLookAt(eye,target,up);matrix.set(projection).mul(view);
            shadow.bind();shadow.setUniformMatrix("u_matrix",matrix);batch.begin(shadow,stats);
            for(int i=0;i<frame.casterIndex.candidates.size;i++){
                LightingFrame.Caster c=frame.casters.get(frame.casterIndex.candidates.get(i));
                batch.draw(c.surface,c.texture,c.u1,c.v1,c.u2,c.v2,Color.WHITE.toFloatBits(),0,null,0,0,true,true);
            }
            batch.flush();
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }
    public void abort(){if(batch!=null)batch.abort();Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);}
    public long textureBytes(){return (long)width*height*8+(long)resolution*resolution*6*8;}
    public void dispose(){if(batch!=null)batch.dispose();if(receiver!=null)receiver.dispose();if(shadow!=null)shadow.dispose();
        if(accumulate!=null)accumulate.dispose();if(screen!=null)screen.dispose();if(scratch!=null)scratch.dispose();if(atlas!=null)atlas.dispose();
        batch=null;receiver=shadow=accumulate=null;screen=null;scratch=atlas=null;width=height=resolution=0;}

    private static final String RECEIVER =
        "uniform vec3 u_light,u_color,u_direction,u_cone;uniform float u_range,u_falloff,u_resolution,u_atlasScale;uniform int u_quality,u_diagnostic;uniform sampler2D u_shadow;out vec4 result;"+
        "float depthAt(vec3 d){vec3 a=abs(d);vec2 q;int f;if(a.x>=a.y&&a.x>=a.z){if(d.x>0.){f=0;q=vec2(-d.z,-d.y)/a.x;}else{f=1;q=vec2(d.z,-d.y)/a.x;}}else if(a.y>=a.z){if(d.y>0.){f=2;q=vec2(d.x,d.z)/a.y;}else{f=3;q=vec2(d.x,-d.z)/a.y;}}else{if(d.z>0.){f=4;q=vec2(d.x,-d.y)/a.z;}else{f=5;q=vec2(-d.x,-d.y)/a.z;}}q=clamp(q*.5+.5,vec2(.5/u_resolution),vec2(1.-.5/u_resolution));return texture(u_shadow,u_atlasScale*(q+vec2(float(f%3),float(f/3)))/vec2(3.,2.)).r*u_range;}"+
        "float visible(vec3 d,float distance,float bias){return distance-bias<=depthAt(d)?1.:0.;}"+
        "void main(){vec4 texel=image();if(flags.w>=0.&&texel.a<flags.w)discard;vec4 t=texel*color;if(flags.w!= -2.&&t.a<=0.)discard;float alpha=flags.w== -2.||flags.w>=0.?1.:t.a;vec3 outputColor=t.rgb;"+
        "float value=0.;vec3 delta=p-u_light;float distance=length(delta);float normalLength=length(n);if(flags.y>.5&&normalLength>.0001){vec3 normal=n/normalLength;float cosine=dot(normal,-delta/max(distance,.0001));if(flags.z>.5)cosine=abs(cosine);float visibility=1.;float c=clamp(abs(cosine),.1,1.);float bias=2.304+1.5*distance/u_resolution*sqrt(max(0.,1.-c*c))/c;"+
        "if(u_quality==1)visibility=visible(delta,distance,bias);if(u_quality==2){vec3 direction=normalize(delta),reference=abs(direction.z)<.9?vec3(0,0,1):vec3(0,1,0);vec3 a=normalize(cross(direction,reference)),b=cross(direction,a);visibility=0.;for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){vec3 tap=normalize(direction+2./u_resolution*(float(x)*a+float(y)*b));float den=dot(tap,normal);float dist=abs(den)>.0001?dot(delta,normal)/den:distance;visibility+=visible(tap,max(.001,dist),bias);}visibility/=9.;}"+
        "float cone=1.;if(u_cone.x>.5){float angle=dot(normalize(delta),u_direction);cone=u_cone.z<=u_cone.y+.00001?step(u_cone.y,angle):smoothstep(u_cone.y,u_cone.z,angle);}value=pow(max(0.,1.-distance/u_range),max(.001,u_falloff))*max(.1,cosine)*visibility*cone;}"+
        "result=vec4(outputColor*u_color*value*(flags.w< -2.5?1.:alpha),alpha);if(u_diagnostic==1)result=vec4(fract(abs(p)/256.)*alpha,alpha);if(u_diagnostic==2)result=vec4((n*.5+.5)*alpha,alpha);if(u_diagnostic==3)result=vec4((normalLength<.0001?vec3(1,0,1):abs(flags.y)>1.5?vec3(.8,.6,.1):vec3(.1,.8,.4))*alpha,alpha);}";
}
