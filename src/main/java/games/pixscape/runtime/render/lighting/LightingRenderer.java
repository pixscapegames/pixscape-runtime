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
    private boolean useNormalMaps = true,preparedNormalMaps = true;
    /** The legacy unmapped receiver already preserves geometric and curved normals. */
    public void setUseNormalMaps(boolean enabled) { useNormalMaps = enabled; }
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
        batch.prepareNormalMaps(useNormalMaps && !shadowsOnly);
        prepareTwoDepthGpuTimer();
        if(receiver!=null && preparedNormalMaps!=useNormalMaps){
            receiver.dispose();receiver=null;
            if(mappedReceiver!=null)mappedReceiver.dispose();mappedReceiver=null;
            accumulate.dispose();accumulate=null;screen.dispose();screen=null;
        }
        if(receiver==null && !shadowsOnly){
            preparedNormalMaps=useNormalMaps;
            String version=Gdx.app.getType()==Application.ApplicationType.Desktop?"#version 330 core\n":
                    "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n";
            String attributes="in vec3 a_position;in vec2 a_uv;in vec3 a_normal;in vec4 a_color;in vec4 a_flags;in vec2 a_screen;in float a_exclusionId;";
            String vary="out vec3 p;out vec2 uv;out vec3 n;out vec4 color;flat out vec4 flags;flat out float exclusionId;";
            String vertex=version+attributes+vary+"in vec3 a_curveNormal;out vec3 curveNormal;uniform mat4 u_matrix;void main(){curveNormal=a_curveNormal;p=a_position;uv=a_uv;n=a_normal;color=a_color;flags=a_flags;exclusionId=a_exclusionId;gl_Position=u_matrix*vec4(a_screen,0.,1.);}";
            String sample="uniform sampler2D u_tex0,u_tex1,u_tex2,u_tex3;vec4 image(){if(flags.x<.5)return texture(u_tex0,uv);if(flags.x<1.5)return texture(u_tex1,uv);if(flags.x<2.5)return texture(u_tex2,uv);return texture(u_tex3,uv);}";
            String inputs="in vec3 p;in vec2 uv;in vec3 n;in vec4 color;flat in vec4 flags;flat in float exclusionId;";
            receiver=compile(vertex,LightingShaderSources.legacyReceiver(version,inputs,sample));
            String normalVertex=version+attributes+vary+"in vec3 a_curveNormal;out vec3 curveNormal;uniform mat4 u_matrix;"+
                    "in vec3 a_normalUV;in vec4 a_normalBasis;out vec2 normalUV;flat out float normalSlot;flat out vec4 normalBasis;"+
                    "void main(){normalUV=a_normalUV.xy;normalSlot=a_normalUV.z;normalBasis=a_normalBasis;curveNormal=a_curveNormal;p=a_position;uv=a_uv;n=a_normal;color=a_color;flags=a_flags;exclusionId=a_exclusionId;gl_Position=u_matrix*vec4(a_screen,0.,1.);}";
            if(useNormalMaps)mappedReceiver=compile(normalVertex,LightingShaderSources.legacyMappedReceiver(version,inputs,sample));
            accumulate=compile(version+"in vec3 a_position;in vec2 a_texCoord0;out vec2 uv;void main(){uv=a_texCoord0;gl_Position=vec4(a_position,1.);}",
                    version+"in vec2 uv;uniform sampler2D u_original,u_contribution;out vec4 result;void main(){vec3 base=texture(u_original,uv).rgb,c=texture(u_contribution,uv).rgb;result=vec4(c/max(base,vec3(1./65536.)),0.);}");
            receiver.bind();samplers(receiver);
            if(useNormalMaps){
                mappedReceiver.bind();samplers(mappedReceiver);
                for(int i=0;i<4;i++)mappedReceiver.setUniformi("u_normal"+i,i+5);
            }
            screen=new Mesh(true,4,6,VertexAttribute.Position(),VertexAttribute.TexCoords(0));
            screen.setVertices(new float[]{-1,-1,0,0,0,-1,1,0,0,1,1,1,0,1,1,1,-1,0,1,0});screen.setIndices(new short[]{0,1,2,2,3,0});
        }
        if(!shadowsOnly){
            int w=composition.targetWidth(),h=composition.targetHeight();
            if(scratch==null || width!=w || height!=h){FrameBuffer next=floatTarget(w,h,false);if(scratch!=null)scratch.dispose();scratch=next;width=w;height=h;}
        }
        // Scene preparation includes authored disabled lights so later activation needs no target rebuild.
        int required=0;
        IntBag entities=lightSubscription.getEntities();
        for(int i=0;i<entities.size();i++){
            int e=entities.get(i);PointLightComponent p=pointMapper.getSafe(e,null);
            ConeLightComponent c=coneMapper.getSafe(e,null);
            if(p!=null && p.shadowQuality!=0)required=Math.max(required,p.shadowResolution);
            if(c!=null && c.shadowQuality!=0)required=Math.max(required,c.shadowResolution);
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
        String attributes="in vec3 a_position;in vec2 a_uv;in vec4 a_color;in vec4 a_flags;in float a_exclusionId;";
        String vary="out vec3 p;out vec2 uv;out vec4 color;flat out vec4 flags;flat out float exclusionId;";
        String inputs="in vec3 p;in vec2 uv;in vec4 color;flat in vec4 flags;flat in float exclusionId;";
        String sample=shadowImageSource(shadowColorSlots)+actorShadowMaskSource();
        String vertex=version+attributes+vary+"uniform mat4 u_matrix;void main(){p=a_position;uv=a_uv;color=a_color;flags=a_flags;exclusionId=a_exclusionId;gl_Position=u_matrix*vec4(p,1.);}";
        // exclusionId is the cold shadow-group key supplied by LightingBatch, not a
        // geometric support ID. Both passes and both receiver paths use that same key.
        // Discard every layer of id1's group before depth-testing d2: an exterior
        // occultant behind several connected wall supports must remain available.
        if(firstShadow==null){
            firstShadow=compile(vertex,version+inputs+sample+"uniform vec3 u_light;uniform float u_range;layout(location=0)out float distanceValue;layout(location=1)out float casterIdentity;void main(){shadowMask();distanceValue=length(p-u_light)/u_range;casterIdentity=exclusionId;}");
            firstShadow.bind();samplers(firstShadow,shadowColorSlots);batch=new LightingBatch(shadowColorSlots,false);
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
    /** Indexed capacity check only; never prepares or resizes GPU resources. */
    public boolean hasPreparedShadowResolution(int requested) {
        return atlas != null && secondAtlas != null && requested > 0 && requested <= resolution;
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
        for(int i=0;useNormalMaps && i<queue.size && !mapped;i++){
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
        long start=System.nanoTime(),bytes=batch.uploadedBytes;int draws=stats.drawCalls,uploads=batch.uploads;boolean gpuTimer=beginTwoDepthGpuTimer();secondShadowMap(l,stats);if(gpuTimer)endTwoDepthGpuTimer();
        twoDepthShadowCpuNs+=System.nanoTime()-start;twoDepthShadowDraws+=stats.drawCalls-draws;
        twoDepthShadowUploads+=batch.uploads-uploads;twoDepthShadowUploadedBytes+=batch.uploadedBytes-bytes;
    }
    private void shadowMap(LightingFrame frame,LightingFrame.Light l,RenderStats stats,FrameBuffer output,com.badlogic.gdx.utils.IntArray candidates,ShaderProgram program,boolean identityTarget){
        prepareShadowTarget(output,identityTarget);
        program.bind();program.setUniformf("u_light",l.x,l.y,l.z);program.setUniformf("u_range",l.radius);
        projection.setToProjection(.05f,l.radius+256,90,1);eye.set(l.x,l.y,l.z);
        for(int face=0;face<6;face++){
            shadowView(l,program,face);
            if(face>0){batch.replay();continue;}
            batch.begin(program,stats,true);
            for(int i=0;i<candidates.size;i++){
                LightingFrame.Caster c=frame.casters.get(candidates.get(i));
                if(c.surface.shadowVertices!=null?c.surface.shadowVertexCount==0:c.surface.vertices.length==0)continue;
                if(c.surface.actorShadowRectangle!=null && c.surface.shadowVertices==c.surface.actorShadowRectangle.vertices)
                    c.surface.actorShadowRectangle.faceLight(l.x,l.y);
                batch.draw(c.surface,c.texture,c.u1,c.v1,c.u2,c.v2,Color.WHITE.toFloatBits(),0,null,0,0,true,true);
            }
            batch.flush();
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }
    private void prepareShadowTarget(FrameBuffer output,boolean identityTarget){
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,output.getFramebufferHandle());
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glDisable(GL20.GL_BLEND);Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);Gdx.gl.glDepthMask(true);Gdx.gl.glDepthFunc(GL20.GL_LESS);
        zero.clear();zero.put(0,1);Gdx.gl30.glClearBufferfv(GL30.GL_COLOR,0,zero);
        if(identityTarget){zero.clear();zero.put(0,0);Gdx.gl30.glClearBufferfv(GL30.GL_COLOR,1,zero);}
        zero.clear();zero.put(0,1);Gdx.gl30.glClearBufferfv(GL30.GL_DEPTH,0,zero);zero.put(0,0);
    }
    private void shadowView(LightingFrame.Light l,ShaderProgram program,int face){
        Gdx.gl.glViewport((face%3)*l.resolution,(face/3)*l.resolution,l.resolution,l.resolution);
        target.set(eye).add(DIRECTIONS[face*3],DIRECTIONS[face*3+1],DIRECTIONS[face*3+2]);
        up.set(UPS[face*3],UPS[face*3+1],UPS[face*3+2]);view.setToLookAt(eye,target,up);matrix.set(projection).mul(view);
        program.bind();program.setUniformMatrix("u_matrix",matrix);
    }
    private void secondShadowMap(LightingFrame.Light l,RenderStats stats){
        if(secondAtlas==null)throw new IllegalStateException("Two-depth target was not prepared");
        atlas.getTextureAttachments().get(1).bind(shadowColorSlots);
        prepareShadowTarget(secondAtlas,false);
        secondShadow.bind();secondShadow.setUniformf("u_light",l.x,l.y,l.z);secondShadow.setUniformf("u_range",l.radius);
        projection.setToProjection(.05f,l.radius+256,90,1);eye.set(l.x,l.y,l.z);
        for(int face=0;face<6;face++){shadowView(l,secondShadow,face);batch.replay(secondShadow,stats);}
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
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

}
