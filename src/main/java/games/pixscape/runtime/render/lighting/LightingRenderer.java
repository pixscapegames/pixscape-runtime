package games.pixscape.runtime.render.lighting;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.ShaderOrigin;
import games.pixscape.runtime.render.WorldLightComposition;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.ShaderRegistry;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Common Runtime/Studio lighting consumer. GPU preparation is confined to the frame boundary. */
public final class LightingRenderer {
    private ShaderProgram receiver,mappedReceiver,firstShadow,secondShadow,accumulate;
    private LightingBatch batch;
    private int shadowColorSlots=4;
    private FrameBuffer scratch,atlas,secondAtlas;
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
    private final int[] savedScissorBounds=new int[4];
    private final LightingInfluenceBounds influenceBounds=new LightingInfluenceBounds();
    private final Matrix4 projection=new Matrix4(),view=new Matrix4(),matrix=new Matrix4();
    private final Vector3 eye=new Vector3(),target=new Vector3(),up=new Vector3();
    private static final float[] DIRECTIONS={1,0,0,-1,0,0,0,1,0,0,-1,0,0,0,1,0,0,-1};
    private static final float[] UPS={0,-1,0,0,-1,0,0,0,1,0,0,-1,0,-1,0,0,-1,0};
    public long shadowCpuNs,receiverCpuNs,accumulationCpuNs;
    public int selectedCasters,shadowDraws,receiverDraws;
    public long shadowUploadedBytes,shadowPreparationCpuNs;
    public int shadowUploads;
    /** Cost specific to the shared d2 replay; d1 remains included in {@link #shadowCpuNs}. */
    public long twoDepthShadowCpuNs,twoDepthShadowUploadedBytes;
    public int twoDepthShadowDraws,twoDepthShadowUploads;
    /** Deferred GPU elapsed time for the d2 replay; -1 means unsupported or not available yet. */
    public long twoDepthShadowGpuNs=-1;
    private int[] twoDepthGpuQueries;
    private boolean[] twoDepthGpuPending;
    private int twoDepthGpuQueryIndex;
    private final IntBuffer queryResult=BufferUtils.newIntBuffer(1);
    private final games.pixscape.runtime.system.LightingGeometrySystem.ShadowPreparation shadowPreparation=this::prepareFrame;
    private int preparedCasterCount,preparedCasterRevision;
    private final LightingFrame.Light diagnosticLight=new LightingFrame.Light();
    {diagnosticLight.radius=1;diagnosticLight.falloff=1;diagnosticLight.resolution=512;}
    private final LightingSurface missing=new LightingSurface(new float[]{
            0,0,0,0,0,0,0,0, 0,0,0,0,1,0,0,0, 0,0,0,1,1,0,0,0,
            0,0,0,0,0,0,0,0, 0,0,0,1,1,0,0,0, 0,0,0,1,0,0,0,0});

    public void prepare(World world,WorldLightComposition composition) { prepare(world,composition,false); }
    /** Prepares only the existing shadow producer; no historical receiver targets/programs. */
    public void prepareShadows(World world) { prepare(world,null,true); }
    private void prepare(World world,WorldLightComposition composition,boolean shadowsOnly) {
        // Admit pending component additions without running gameplay or rendering systems.
        if(world.getInvocationStrategy() instanceof games.pixscape.runtime.loading.SceneLoadingInvocationStrategy)
            ((games.pixscape.runtime.loading.SceneLoadingInvocationStrategy)world.getInvocationStrategy()).synchronizeEntitySubscriptions();
        saved.clear();Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,saved);int framebuffer=saved.get(0);
        saved.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,saved);for(int i=0;i<4;i++)savedViewport[i]=saved.get(i);
        try { prepareResources(world,composition,shadowsOnly); }
        catch(RuntimeException | Error failure){dispose();throw failure;}
        finally{Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,framebuffer);
            Gdx.gl.glViewport(savedViewport[0],savedViewport[1],savedViewport[2],savedViewport[3]);}
    }
    private void prepareResources(World world,WorldLightComposition composition,boolean shadowsOnly) {
        if(preparedWorld!=world){
            preparedWorld=world;
            games.pixscape.runtime.system.LightingGeometrySystem geometry=world.getSystem(games.pixscape.runtime.system.LightingGeometrySystem.class);
            if(geometry!=null)geometry.setShadowPreparation(shadowPreparation);
            lightSubscription=world.getAspectSubscriptionManager().get(Aspect.one(PointLightComponent.class,ConeLightComponent.class));
            pointMapper=world.getMapper(PointLightComponent.class);coneMapper=world.getMapper(ConeLightComponent.class);
        }
        if(Gdx.gl30==null)throw new IllegalStateException("2.5D lighting requires GL3/GLES3/WebGL2 float render targets");
        prepareShadowProgram();
        prepareTwoDepthGpuTimer();
        if(receiver==null && !shadowsOnly){
            String version=Gdx.app.getType()==Application.ApplicationType.Desktop?"#version 330 core\n":
                    "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n";
            String attributes="in vec3 a_position;in vec2 a_uv;in vec3 a_normal;in vec4 a_color;in vec4 a_flags;in vec2 a_screen;in float a_exclusionId;";
            String vary="out vec3 p;out vec2 uv;out vec3 n;out vec4 color;flat out vec4 flags;flat out float exclusionId;";
            String vertex=version+attributes+vary+"in vec3 a_curveNormal;out vec3 curveNormal;uniform mat4 u_matrix;void main(){curveNormal=a_curveNormal;p=a_position;uv=a_uv;n=a_normal;color=a_color;flags=a_flags;exclusionId=a_exclusionId;gl_Position=u_matrix*vec4(a_screen,0.,1.);}";
            String sample="uniform sampler2D u_tex0,u_tex1,u_tex2,u_tex3;vec4 image(){if(flags.x<.5)return texture(u_tex0,uv);if(flags.x<1.5)return texture(u_tex1,uv);if(flags.x<2.5)return texture(u_tex2,uv);return texture(u_tex3,uv);}";
            String inputs="in vec3 p;in vec2 uv;in vec3 n;in vec4 color;flat in vec4 flags;flat in float exclusionId;";
            receiver=compile(vertex,version+inputs+sample+MACRO_NORMAL+RECEIVER);
            String normalVertex=vertex.replace("void main(){", "in vec3 a_normalUV;in vec4 a_normalBasis;out vec2 normalUV;flat out float normalSlot;flat out vec4 normalBasis;void main(){normalUV=a_normalUV.xy;normalSlot=a_normalUV.z;normalBasis=a_normalBasis;");
            mappedReceiver=compile(normalVertex,version+inputs+sample+MACRO_NORMAL+NORMAL_SAMPLE+RECEIVER
                    .replace("vec3 normal=facade;", "vec3 normal=lightingNormal(facade);")
                    .replace("(n*.5+.5)*alpha", "(lightingNormal(normalLength>.0001?n/normalLength:vec3(0))*.5+.5)*alpha"));
            accumulate=compile(version+"in vec3 a_position;in vec2 a_texCoord0;out vec2 uv;void main(){uv=a_texCoord0;gl_Position=vec4(a_position,1.);}",
                    version+"in vec2 uv;uniform sampler2D u_original,u_contribution;out vec4 result;void main(){vec3 base=texture(u_original,uv).rgb,c=texture(u_contribution,uv).rgb;result=vec4(c/max(base,vec3(1./65536.)),0.);}");
            receiver.bind();samplers(receiver);
            mappedReceiver.bind();samplers(mappedReceiver);
            for(int i=0;i<4;i++)mappedReceiver.setUniformi("u_normal"+i,i+5);
            screen=new Mesh(true,4,6,VertexAttribute.Position(),VertexAttribute.TexCoords(0));
            screen.setVertices(new float[]{-1,-1,0,0,0,-1,1,0,0,1,1,1,0,1,1,1,-1,0,1,0});screen.setIndices(new short[]{0,1,2,2,3,0});
        }
        if(!shadowsOnly){
            int w=composition.targetWidth(),h=composition.targetHeight();
            if(scratch==null || width!=w || height!=h){FrameBuffer next=floatTarget(w,h,false);if(scratch!=null)scratch.dispose();scratch=next;width=w;height=h;}
        }
        int required=0;
        IntBag entities=lightSubscription.getEntities();
        for(int i=0;i<entities.size();i++){
            int e=entities.get(i);PointLightComponent p=pointMapper.getSafe(e,null);
            ConeLightComponent c=coneMapper.getSafe(e,null);
            if(p!=null && p.enabled && p.shadowQuality!=0)required=Math.max(required,p.shadowResolution);
            if(c!=null && c.enabled && c.shadowQuality!=0)required=Math.max(required,c.shadowResolution);
        }
        if(required!=0 && required!=256 && required!=512 && required!=1024)throw new IllegalArgumentException("Unsupported shadow resolution");
        requireTwoDepthAttachments();
        if(required!=resolution){
            FrameBuffer next=required==0?null:twoDepthFirstTarget(required*3,required*2);
            FrameBuffer nextSecond=null;
            try{
                if(required!=0)nextSecond=floatTarget(required*3,required*2,true);
            } catch(RuntimeException | Error failure){
                if(next!=null)next.dispose();if(nextSecond!=null)nextSecond.dispose();throw failure;
            }
            if(atlas!=null)atlas.dispose();if(secondAtlas!=null)secondAtlas.dispose();
            atlas=next;secondAtlas=nextSecond;resolution=required;
        }
    }
    private void prepareShadowProgram() {
        if(firstShadow!=null && secondShadow!=null)return;
        if(firstShadow==null){
            saved.clear();Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_IMAGE_UNITS,saved);
            shadowColorSlots=shadowColorSlotsFor(saved.get(0));
        }
        String version=Gdx.app.getType()==Application.ApplicationType.Desktop?"#version 330 core\n":
                "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n";
        String attributes="in vec3 a_position;in vec2 a_uv;in vec3 a_normal;in vec4 a_color;in vec4 a_flags;in vec2 a_screen;in float a_exclusionId;";
        String vary="out vec3 p;out vec2 uv;out vec3 n;out vec4 color;flat out vec4 flags;flat out float exclusionId;";
        String inputs="in vec3 p;in vec2 uv;in vec3 n;in vec4 color;flat in vec4 flags;flat in float exclusionId;";
        String sample=shadowImageSource(shadowColorSlots)+actorShadowMaskSource();
        String vertex=version+attributes+vary+"uniform mat4 u_matrix;void main(){p=a_position;uv=a_uv;n=a_normal;color=a_color;flags=a_flags;exclusionId=a_exclusionId;gl_Position=u_matrix*vec4(p,1.);}";
        // exclusionId is the cold shadow-group key supplied by LightingBatch, not a
        // geometric support ID. Both passes and both receiver paths use that same key.
        // Discard every layer of id1's group before depth-testing d2: an exterior
        // occultant behind several connected wall supports must remain available.
        if(firstShadow==null){
            firstShadow=compile(vertex,version+inputs+sample+"uniform vec3 u_light;uniform float u_range;layout(location=0)out float distanceValue;layout(location=1)out float casterIdentity;void main(){shadowMask();distanceValue=length(p-u_light)/u_range;casterIdentity=exclusionId;}");
            firstShadow.bind();samplers(firstShadow,shadowColorSlots);batch=new LightingBatch(shadowColorSlots);
        }
        if(secondShadow==null){
            secondShadow=compile(vertex,version+inputs+sample+"uniform vec3 u_light;uniform float u_range;uniform sampler2D u_firstIdentity;out float distanceValue;void main(){shadowMask();float id1=texelFetch(u_firstIdentity,ivec2(gl_FragCoord.xy),0).r;if(id1>.5&&id1==exclusionId)discard;distanceValue=length(p-u_light)/u_range;}");
            secondShadow.bind();samplers(secondShadow,shadowColorSlots);secondShadow.setUniformi("u_firstIdentity",shadowColorSlots);
        }
    }
    // Reserve one fragment texture unit for id1 in the second depth pass.
    static int shadowColorSlotsFor(int fragmentUnits){return fragmentUnits>=9?8:4;}
    static String shadowImageSource(int slots){
        StringBuilder source=new StringBuilder("uniform sampler2D ");
        for(int i=0;i<slots;i++){if(i>0)source.append(',');source.append("u_tex").append(i);}
        source.append(";vec4 imageAt(vec2 uv){");
        for(int i=0;i<slots-1;i++)source.append("if(flags.x<").append(i).append(".5)return texture(u_tex").append(i).append(",uv);");
        return source.append("return texture(u_tex").append(slots-1).append(",uv);}vec4 image(){return imageAt(uv);}").toString();
    }
    private static String actorShadowMaskSource(){
        return "void shadowMask(){if(flags.y<9.){if(image().a*color.a<flags.w)discard;return;}"
                +"if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1))))discard;"
                +"vec2 localUV=uv;"
                +"float a=imageAt(vec2(mix(color.r,color.b,localUV.x),mix(color.g,flags.z,localUV.y))).a*color.a;"
                +"if(a<flags.w)discard;}";
    }
    /** Borrows the current atlas; its lifetime belongs exclusively to this producer. */
    public Texture shadowTexture() { return atlas==null?null:atlas.getColorBufferTexture(); }
    /** Forward uses the same completed textures and never constructs a receiver-specific atlas. */
    public Texture shadowIdentityTexture(){
        return atlas==null?null:atlas.getTextureAttachments().get(1);
    }
    public Texture secondShadowTexture(){
        return secondAtlas==null?null:secondAtlas.getColorBufferTexture();
    }
    public float atlasScale(LightingFrame.Light light) {return resolution==0?1f:(float)light.resolution/resolution;}
    /** After geometry publication, before queue submission. No GPU changes when capacity is stable. */
    public void prepareFrame(LightingFrame frame){
        long start=System.nanoTime();int required=0;
        for(int i=0;i<frame.lights.size;i++){
            LightingFrame.Light light=frame.lights.get(i);if(light.quality==0)continue;
            frame.casterIndex.query(frame,light,256,frame.staticCasterCount);
            required=Math.max(required,batch.requiredReplayBatches(frame,frame.casterIndex.candidates));
        }
        boolean sceneChanged=preparedCasterRevision!=frame.staticCasterRevision||frame.casters.size<preparedCasterCount;
        if(required>batch.replayCapacity()||sceneChanged&&required<batch.replayCapacity())batch.prepareReplayCapacity(required);
        preparedCasterCount=frame.casters.size;preparedCasterRevision=frame.staticCasterRevision;
        shadowPreparationCpuNs=System.nanoTime()-start;
    }
    public long shadowReplayBytes(){return batch==null?0:batch.replayBytes();}
    public void renderShadows(LightingFrame frame,RenderStats stats) {
        shadowUploads=0;shadowUploadedBytes=0;if(batch!=null){batch.uploads=0;batch.uploadedBytes=0;}
        shadowCpuNs=receiverCpuNs=accumulationCpuNs=twoDepthShadowCpuNs=twoDepthShadowUploadedBytes=0;selectedCasters=shadowDraws=receiverDraws=twoDepthShadowDraws=twoDepthShadowUploads=0;
        if(frame.lights.size==0 || frame.lights.first().quality==0)return;
        long start=System.nanoTime();int draws=stats.drawCalls;
        if(frame.timer!=null)frame.timer.begin(LightingPassTimer.SHADOW);
        shadows(frame,frame.lights.first(),stats);
        if(frame.timer!=null)frame.timer.end(LightingPassTimer.SHADOW);
        shadowCpuNs=System.nanoTime()-start;shadowDraws=stats.drawCalls-draws;
        stats.shadowDraws+=shadowDraws;
        shadowUploads=batch.uploads;shadowUploadedBytes=batch.uploadedBytes;
    }
    private static ShaderProgram compile(String v,String f){ShaderProgram s=new ShaderProgram(v,f);if(!s.isCompiled()){String log=s.getLog();s.dispose();throw new IllegalStateException("2.5D lighting shader: "+log);}return s;}
    private static void samplers(ShaderProgram s){samplers(s,4);}
    private static void samplers(ShaderProgram s,int slots){for(int i=0;i<slots;i++)s.setUniformi("u_tex"+i,i);}
    private static FrameBuffer floatTarget(int w,int h,boolean depth){
        GLFrameBuffer.FrameBufferBuilder b=new GLFrameBuffer.FrameBufferBuilder(w,h);
        b.addFloatAttachment(depth?GL30.GL_R32F:GL30.GL_RGBA16F,depth?GL30.GL_RED:GL20.GL_RGBA,GL20.GL_FLOAT,true);
        if(depth)b.addDepthRenderBuffer(GL30.GL_DEPTH_COMPONENT24);
        FrameBuffer f=b.build();f.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
        return f;
    }
    private static FrameBuffer twoDepthFirstTarget(int w,int h){
        GLFrameBuffer.FrameBufferBuilder b=new GLFrameBuffer.FrameBufferBuilder(w,h);
        b.addFloatAttachment(GL30.GL_R32F,GL30.GL_RED,GL20.GL_FLOAT,true);
        b.addFloatAttachment(GL30.GL_R32F,GL30.GL_RED,GL20.GL_FLOAT,true);
        b.addDepthRenderBuffer(GL30.GL_DEPTH_COMPONENT24);
        FrameBuffer f=b.build();
        for(Texture t:f.getTextureAttachments())t.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
        return f;
    }
    private static void requireTwoDepthAttachments(){
        IntBuffer limit=BufferUtils.newIntBuffer(1);Gdx.gl.glGetIntegerv(GL30.GL_MAX_DRAW_BUFFERS,limit);
        if(limit.get(0)<2)throw new LightingAdmissionException("Two-depth self-shadow exclusion requires two float draw buffers");
    }
    /** Four delayed queries avoid a CPU/GPU synchronization point in Preview. */
    private void prepareTwoDepthGpuTimer(){
        if(twoDepthGpuQueries!=null||!Gdx.graphics.supportsExtension("GL_ARB_timer_query"))return;
        twoDepthGpuQueries=new int[4];twoDepthGpuPending=new boolean[4];Gdx.gl30.glGenQueries(twoDepthGpuQueries.length,twoDepthGpuQueries,0);
    }
    private boolean beginTwoDepthGpuTimer(){
        if(twoDepthGpuQueries==null)return false;
        int query=twoDepthGpuQueryIndex;
        if(twoDepthGpuPending[query]){
            queryResult.clear();Gdx.gl30.glGetQueryObjectuiv(twoDepthGpuQueries[query],GL30.GL_QUERY_RESULT_AVAILABLE,queryResult);
            if(queryResult.get(0)==0)return false;
            queryResult.clear();Gdx.gl30.glGetQueryObjectuiv(twoDepthGpuQueries[query],GL30.GL_QUERY_RESULT,queryResult);
            twoDepthShadowGpuNs=((long)queryResult.get(0))&0xffffffffL;twoDepthGpuPending[query]=false;
        }
        Gdx.gl30.glBeginQuery(0x88BF,twoDepthGpuQueries[query]);return true; // GL_TIME_ELAPSED
    }
    private void endTwoDepthGpuTimer(){Gdx.gl30.glEndQuery(0x88BF);twoDepthGpuPending[twoDepthGpuQueryIndex]=true;twoDepthGpuQueryIndex=(twoDepthGpuQueryIndex+1)%twoDepthGpuQueries.length;}
    public void render(FrameRenderQueue queue,LayerStateSOA layers,OrthographicCamera camera,WorldLightComposition composition,RenderStats stats){
        LightingFrame frame=queue.lighting;shadowCpuNs=receiverCpuNs=accumulationCpuNs=twoDepthShadowCpuNs=twoDepthShadowUploadedBytes=0;selectedCasters=shadowDraws=receiverDraws=twoDepthShadowDraws=twoDepthShadowUploads=0;
        composition.beginField();
        // Clear every frame, including empty frames, before skipping work without consumers.
        if(queue.size==0 && frame.diagnostic==0)return;
        boolean mapped=false;
        for(int i=0;i<queue.size && !mapped;i++){
            LightingSurface surface=queue.sourceDomain[i]==FrameRenderQueue.SOURCE_TILED?frame.tiles.get(queue.sourceSlot[i]):frame.entities.get(queue.sourceEntity[i]);
            mapped=surface!=null && surface.normalMap!=null;
        }
        int lightCount=frame.diagnostic==0?frame.lights.size:1;
        boolean clipped=frame.diagnostic==0 && lightCount!=0;
        if(clipped){LightingGlState.readScissor(saved,savedScissorBounds);influenceBounds.prepare(queue,camera);}
        try {
        for(int li=0;li<lightCount;li++){
            LightingFrame.Light l=frame.diagnostic==0?frame.lights.get(li):diagnosticLight;long start=System.nanoTime();int draws=stats.drawCalls;
            // Reject influence, not the source icon. Off-camera casters are still queried for retained lights.
            if(clipped && !influenceBounds.compute(l,width,height))continue;
            if(frame.timer!=null)frame.timer.begin(LightingPassTimer.SHADOW);
            if(l.quality!=0) shadows(frame,l,stats);
            if(frame.timer!=null)frame.timer.end(LightingPassTimer.SHADOW);
            shadowDraws+=stats.drawCalls-draws;stats.shadowDraws+=stats.drawCalls-draws;shadowCpuNs+=System.nanoTime()-start;
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,scratch.getFramebufferHandle());Gdx.gl.glViewport(0,0,width,height);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_CULL_FACE);Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            if(clipped)scissorInfluence();
            zero.clear();Gdx.gl30.glClearBufferfv(GL30.GL_COLOR,0,zero);
            start=System.nanoTime();draws=stats.drawCalls;
            if(frame.timer!=null)frame.timer.begin(LightingPassTimer.RECEIVERS);
            ShaderProgram lightReceiver=mapped?mappedReceiver:receiver;
            boolean receiverPrepared=false;
            for(int i=0;i<queue.size;i++){
                if(queue.light[i]!=0 || queue.textureHandle[i]==0)continue;
                int layer=queue.layerIndex[i];if(layers.maxLayerIndex()>=0 && (layer<0 || layer>layers.maxLayerIndex() || !layers.enabled[layer]))continue;
                int blend=queue.blend[i];if(blend==3 || blend==4)continue;
                LightingSurface s=queue.sourceDomain[i]==FrameRenderQueue.SOURCE_TILED?frame.tiles.get(queue.sourceSlot[i]):frame.entities.get(queue.sourceEntity[i]);
                if(s==null || frame.diagnostic==0 && !s.receiveLight){
                    // Unlit ordinary images still mask light with their actual four-corner coverage.
                    missing.imageQuad=s==null?queue.sourceDomain[i]==FrameRenderQueue.SOURCE_ECS:s.imageQuad;
                    s=missing;}
                if(!receiverPrepared){
                    receiverUniforms(lightReceiver,l,frame,camera);
                    bindReceiverShadowTextures(l,composition);
                    if(mapped)batch.beginMapped(lightReceiver,stats);else batch.begin(lightReceiver,stats);
                    receiverPrepared=true;
                }
                quad[0]=queue.x1[i];quad[1]=queue.y1[i];quad[2]=queue.x2[i];quad[3]=queue.y2[i];
                quad[4]=queue.x3[i];quad[5]=queue.y3[i];quad[6]=queue.x4[i];quad[7]=queue.y4[i];
                boolean supported=ShaderRegistry.getOrigin(queue.shader[i])==ShaderOrigin.CORE && queue.repeatFlags[i]==0;
                if(!supported && frame.diagnostic==0)throw new IllegalArgumentException(
                        "2.5D lighting requires standard coverage and color; unsupported shader/repeat at source entity "+queue.sourceEntity[i]);
                batch.draw(s,queue.textureHandle[i],queue.u1[i],queue.v1[i],queue.u2[i],queue.v2[i],queue.colorPacked[i],blend,quad,0,0,false,supported);
            }
            batch.flush();receiverDraws+=stats.drawCalls-draws;stats.receiverDraws+=stats.drawCalls-draws;receiverCpuNs+=System.nanoTime()-start;
            if(frame.timer!=null)frame.timer.end(LightingPassTimer.RECEIVERS);
            if(frame.timer!=null)frame.timer.begin(LightingPassTimer.ACCUMULATION);
            start=System.nanoTime();composition.resumeField();Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_ONE,GL20.GL_ONE);
            if(clipped)scissorInfluence();
            composition.originalTexture().bind(0);scratch.getColorBufferTexture().bind(1);
            accumulate.bind();accumulate.setUniformi("u_original",0);accumulate.setUniformi("u_contribution",1);screen.render(accumulate,GL20.GL_TRIANGLES);
            stats.drawCalls++;stats.accumulationDraws++;stats.drawnIndices+=6;stats.textureBinds+=2;accumulationCpuNs+=System.nanoTime()-start;
            if(frame.timer!=null)frame.timer.end(LightingPassTimer.ACCUMULATION);
        }
        } finally {
            if(clipped){
                Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                Gdx.gl.glScissor(savedScissorBounds[0],savedScissorBounds[1],savedScissorBounds[2],savedScissorBounds[3]);
            }
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }
    private void scissorInfluence(){
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(influenceBounds.x,influenceBounds.y,influenceBounds.width,influenceBounds.height);
    }
    private void receiverUniforms(ShaderProgram shader,LightingFrame.Light l,LightingFrame frame,OrthographicCamera camera){
        shader.bind();shader.setUniformMatrix("u_matrix",camera.combined);shader.setUniformf("u_light",l.x,l.y,l.z);
        shader.setUniformf("u_color",l.r*l.intensity,l.g*l.intensity,l.b*l.intensity);
        shader.setUniformf("u_range",l.radius);shader.setUniformf("u_falloff",l.falloff);
        shader.setUniformi("u_diagnostic",frame.diagnostic);shader.setUniformf("u_cone",l.cone?1:0,l.cosOuter,l.cosInner);
        shader.setUniformf("u_direction",l.dx,l.dy,l.dz);
        shader.setUniformi("u_quality",l.quality);shader.setUniformf("u_resolution",l.resolution);
        shader.setUniformf("u_atlasScale",resolution==0?1f:(float)l.resolution/resolution);shader.setUniformi("u_shadow",4);
        shader.setUniformi("u_shadowIdentity",9);shader.setUniformi("u_shadowSecond",10);
    }

    /** Binds only completed immutable targets; second pass never reads its own framebuffer. */
    private void bindReceiverShadowTextures(LightingFrame.Light l,WorldLightComposition composition){
        if(l.quality==0){composition.originalTexture().bind(4);composition.originalTexture().bind(9);composition.originalTexture().bind(10);return;}
        FrameBuffer primary=atlas;primary.getColorBufferTexture().bind(4);
        atlas.getTextureAttachments().get(1).bind(9);secondAtlas.getColorBufferTexture().bind(10);
    }

    private void shadows(LightingFrame frame,LightingFrame.Light l,RenderStats stats){
        if(atlas==null || l.resolution>resolution)
            throw new IllegalStateException("Shadow quality/resolution changed after frame preparation; prepare targets before submission");
        frame.casterIndex.query(frame,l,256,frame.staticCasterCount);
        selectedCasters+=frame.casterIndex.candidates.size;
        shadowMap(frame,l,stats,atlas,frame.casterIndex.candidates,firstShadow,true);
        long start=System.nanoTime(),bytes=batch.uploadedBytes;int draws=stats.drawCalls,uploads=batch.uploads;boolean gpuTimer=beginTwoDepthGpuTimer();secondShadowMap(frame,l,stats);if(gpuTimer)endTwoDepthGpuTimer();
        twoDepthShadowCpuNs+=System.nanoTime()-start;twoDepthShadowDraws+=stats.drawCalls-draws;
        twoDepthShadowUploads+=batch.uploads-uploads;twoDepthShadowUploadedBytes+=batch.uploadedBytes-bytes;
    }
    private void shadowMap(LightingFrame frame,LightingFrame.Light l,RenderStats stats,FrameBuffer output,com.badlogic.gdx.utils.IntArray candidates,ShaderProgram program,boolean identityTarget){
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,output.getFramebufferHandle());
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glDisable(GL20.GL_BLEND);Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);Gdx.gl.glDepthMask(true);Gdx.gl.glDepthFunc(GL20.GL_LESS);
        zero.clear();zero.put(0,1);Gdx.gl30.glClearBufferfv(GL30.GL_COLOR,0,zero);
        if(identityTarget){zero.clear();zero.put(0,0);Gdx.gl30.glClearBufferfv(GL30.GL_COLOR,1,zero);}
        zero.clear();zero.put(0,1);Gdx.gl30.glClearBufferfv(GL30.GL_DEPTH,0,zero);zero.put(0,0);
        program.bind();program.setUniformf("u_light",l.x,l.y,l.z);program.setUniformf("u_range",l.radius);
        projection.setToProjection(.05f,l.radius+256,90,1);eye.set(l.x,l.y,l.z);
        for(int face=0;face<6;face++){
            Gdx.gl.glViewport((face%3)*l.resolution,(face/3)*l.resolution,l.resolution,l.resolution);
            target.set(eye).add(DIRECTIONS[face*3],DIRECTIONS[face*3+1],DIRECTIONS[face*3+2]);
            up.set(UPS[face*3],UPS[face*3+1],UPS[face*3+2]);view.setToLookAt(eye,target,up);matrix.set(projection).mul(view);
            program.bind();program.setUniformMatrix("u_matrix",matrix);
            if(face>0){batch.replay();continue;}
            batch.begin(program,stats,true);
            for(int i=0;i<candidates.size;i++){
                LightingFrame.Caster c=frame.casters.get(candidates.get(i));
                if(c.surface.shadowVertices!=null?c.surface.shadowVertexCount==0:c.surface.vertices.length==0)continue;
                batch.draw(c.surface,c.texture,c.u1,c.v1,c.u2,c.v2,Color.WHITE.toFloatBits(),0,null,0,0,true,true);
            }
            batch.flush();
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }
    private void secondShadowMap(LightingFrame frame,LightingFrame.Light l,RenderStats stats){
        if(secondAtlas==null)throw new IllegalStateException("Two-depth target was not prepared");
        atlas.getTextureAttachments().get(1).bind(shadowColorSlots);
        shadowMap(frame,l,stats,secondAtlas,frame.casterIndex.candidates,secondShadow,false);
    }
    public void abort(){if(batch!=null)batch.abort();Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);}
    public long textureBytes(){
        long pixels=(long)resolution*resolution*6;
        // A has complete+decor (2 × [R32F + depth]); B has [d1,id1,depth] + d2 + depth + decor.
        return (long)width*height*8+pixels*20;
    }
    public void dispose(){if(batch!=null)batch.dispose();if(receiver!=null)receiver.dispose();
        if(mappedReceiver!=null)mappedReceiver.dispose();mappedReceiver=null;
        if(twoDepthGpuQueries!=null)Gdx.gl30.glDeleteQueries(twoDepthGpuQueries.length,twoDepthGpuQueries,0);twoDepthGpuQueries=null;twoDepthGpuPending=null;twoDepthGpuQueryIndex=0;twoDepthShadowGpuNs=-1;
        if(firstShadow!=null)firstShadow.dispose();if(secondShadow!=null)secondShadow.dispose();if(accumulate!=null)accumulate.dispose();if(screen!=null)screen.dispose();if(scratch!=null)scratch.dispose();if(atlas!=null)atlas.dispose();if(secondAtlas!=null)secondAtlas.dispose();
        batch=null;receiver=firstShadow=secondShadow=accumulate=null;screen=null;scratch=atlas=secondAtlas=null;width=height=resolution=0;}

    /** Laigter sprite normal, +Y up, +Z toward camera; one image basis across all geometric faces. */
    static final String NORMAL_SAMPLE =
        "in vec2 normalUV;flat in float normalSlot;flat in vec4 normalBasis;uniform sampler2D u_normal0,u_normal1,u_normal2,u_normal3;"+
        "vec3 lightingNormal(vec3 geometric){if(normalSlot<-.5)return geometric;vec3 s;if(normalSlot<.5)s=texture(u_normal0,normalUV).rgb;else if(normalSlot<1.5)s=texture(u_normal1,normalUV).rgb;else if(normalSlot<2.5)s=texture(u_normal2,normalUV).rgb;else s=texture(u_normal3,normalUV).rgb;s=normalize(s*2.-1.);vec2 xy=normalBasis.xy*s.x+normalBasis.zw*s.y;return normalize(xy.x*vec3(.7071067812,-.7071067812,0)+xy.y*vec3(.4082482905,.4082482905,.8164965809)+s.z*vec3(-.5773502692,-.5773502692,.5773502692));}";

    static final String MACRO_NORMAL="in vec3 curveNormal;vec3 macroNormal(vec3 plane){return dot(curveNormal,curveNormal)>.0001?normalize(curveNormal):plane;}";
    // Relief keeps its existing horizon. The macro guard additionally rejects back-facing
    // one-sided decor; it is fully open by three degrees and never changes shadow bias/depth.
    // Reuse the macro normal and skip shadow lookups only when this guard guarantees zero
    // direct. Diagnostic modes still evaluate visibility independently of orientation.
    // The factor covers the entire direct value, including its 10% floor. Ambient/original color and actors are independent.
    // Sprite-oriented tiles (-2) use the normal-map reference axis without facade/horizon gating.
    // Geometric normals still govern shadow bias and PCF depth; visibility/exclusions are unchanged.
    static final String RECEIVER =
        "uniform vec3 u_light,u_color,u_direction,u_cone;uniform float u_range,u_falloff;uniform int u_diagnostic;out vec4 result;uniform float u_resolution,u_atlasScale;uniform int u_quality;uniform sampler2D u_shadow,u_shadowIdentity,u_shadowSecond;"+
        "vec2 shadowUV(vec3 d){vec3 a=abs(d);vec2 q;int f;if(a.x>=a.y&&a.x>=a.z){if(d.x>0.){f=0;q=vec2(-d.z,-d.y)/a.x;}else{f=1;q=vec2(d.z,-d.y)/a.x;}}else if(a.y>=a.z){if(d.y>0.){f=2;q=vec2(d.x,d.z)/a.y;}else{f=3;q=vec2(d.x,-d.z)/a.y;}}else{if(d.z>0.){f=4;q=vec2(d.x,-d.y)/a.z;}else{f=5;q=vec2(-d.x,-d.y)/a.z;}}q=clamp(q*.5+.5,vec2(.5/u_resolution),vec2(1.-.5/u_resolution));return u_atlasScale*(q+vec2(float(f%3),float(f/3)))/vec2(3.,2.);}"+
        "float visible(vec3 d,float distance,float bias){vec2 uv=shadowUV(d);float first=texture(u_shadow,uv).r*u_range;float id=texture(u_shadowIdentity,uv).r;float depth=exclusionId>.5&&id==exclusionId?texture(u_shadowSecond,uv).r*u_range:first;return distance-bias<=depth?1.:0.;}"+
        "void main(){vec4 texel=image();if(flags.w>=0.&&texel.a<flags.w)discard;vec4 t=texel*color;if(flags.w!= -2.&&t.a<=0.)discard;float alpha=flags.w== -2.||flags.w>=0.?1.:t.a;vec3 outputColor=t.rgb;"+
        "float value=0.;vec3 delta=p-u_light;float distance=length(delta);float normalLength=length(n);if(flags.y>.5&&normalLength>.0001){vec3 geometric=n/normalLength;vec3 facade=flags.z< -1.5?vec3(-.5773502692,-.5773502692,.5773502692):macroNormal(geometric);vec3 normal=facade;float cosine=dot(normal,-delta/max(distance,.0001));if(flags.z>.5)cosine=abs(cosine);float facadeGate=flags.z==0.?smoothstep(0.,.052335956,dot(facade,distance>0.?-delta/distance:vec3(0))):1.;float visibility=1.;float c=clamp(abs(dot(geometric,-delta/max(distance,.0001))),.1,1.);float bias=2.304+1.5*distance/u_resolution*sqrt(max(0.,1.-c*c))/c;"+
        "if(u_quality==1&&facadeGate>0.)visibility=visible(delta,distance,bias);if(u_quality==2&&facadeGate>0.){vec3 direction=normalize(delta),reference=abs(direction.z)<.9?vec3(0,0,1):vec3(0,1,0);vec3 a=normalize(cross(direction,reference)),b=cross(direction,a);visibility=0.;for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){vec3 tap=normalize(direction+2./u_resolution*(float(x)*a+float(y)*b));float den=dot(tap,geometric);float dist=abs(den)>.0001?dot(delta,geometric)/den:distance;visibility+=visible(tap,max(.001,dist),bias);}visibility/=9.;}"+
        "float cone=1.;if(u_cone.x>.5){float angle=dot(normalize(delta),u_direction);cone=u_cone.z<=u_cone.y+.00001?step(u_cone.y,angle):smoothstep(u_cone.y,u_cone.z,angle);}value=pow(max(0.,1.-distance/u_range),max(.001,u_falloff))*max(.1,cosine)*cone*visibility;if(flags.z==0.){vec3 toLight=distance>0.?-delta/distance:vec3(0);float cG=dot(normal,toLight);value*=smoothstep(.052335956,.087155743,max(0.,cG))*facadeGate;}}"+
        "result=vec4(outputColor*u_color*value*(flags.w< -2.5?1.:alpha),alpha);if(u_diagnostic==1)result=vec4(fract(abs(p)/256.)*alpha,alpha);if(u_diagnostic==2)result=vec4((n*.5+.5)*alpha,alpha);if(u_diagnostic==3)result=vec4((normalLength<.0001?vec3(1,0,1):abs(flags.y)>2.5?vec3(1,.2,.1):abs(flags.y)>1.5?vec3(.8,.6,.1):vec3(.1,.8,.4))*alpha,alpha);}";
}
