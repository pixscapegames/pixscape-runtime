package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.NormalMapBinding;
import games.pixscape.runtime.service.TextureRegistry;

import java.nio.IntBuffer;

/** Ordered one-light renderer. Units: color array 0; existing material table reserves 1;
 * one borrowed normal page 2; borrowed d1/id1/d2 shadow atlases 3/4/5. Nine attributes,
 * eleven varying declarations (the extra identity is flat),
 * eight sampler uniforms (the four normal samplers alias unit 2),
 * no texture uploads or topology work. */
public class ForwardLightingRenderer {
    static final int VERTEX_STRIDE=26;
    static final int UNMAPPED_VERTEX_STRIDE=19;
    static final int COLOR_OFFSET=11;
    private static final int CAPACITY=12288;
    private static final int SCREEN_OFFSET=0,UV_OFFSET=2,PHYSICAL_OFFSET=4,NORMAL_OFFSET=8;
    private static final int MATERIAL_OFFSET=12,NORMAL_UV_OFFSET=16,NORMAL_BASIS_OFFSET=19,CURVE_NORMAL_OFFSET=23;
    private float[] data;
    private final float[] quad=new float[8];
    private boolean useNormalMaps=true,preparedNormalMaps=true;
    private int vertexStride=VERTEX_STRIDE,curveNormalOffset=CURVE_NORMAL_OFFSET;
    private final ForwardWorldTarget target=new ForwardWorldTarget();
    private ShaderProgram shader;
    private ShaderProgram receiverShader;
    private Mesh mesh;
    private World admittedWorld;
    private AtlasRuntimeService.TextureArrayBundle bundle;
    private int vertices,blend=-1,normalHandle=-1;
    private RenderStats stats;
    public int worldDraws,presentationDraws,normalFlushes,blendFlushes,capacityFlushes,endFlushes;
    public long uploadedBytes;
    private boolean roundedLightingEnabled=true;
    public void setRoundedLightingEnabled(boolean enabled){roundedLightingEnabled=enabled;}
    public boolean roundedLightingEnabled(){return roundedLightingEnabled;}
    /** Variant resources are rebuilt only by the existing preparation boundary. */
    public void setUseNormalMaps(boolean enabled){useNormalMaps=enabled;}

    public void prepare(World world,AtlasRuntimeService.TextureArrayBundle published) {
        if(world.getInvocationStrategy() instanceof games.pixscape.runtime.loading.SceneLoadingInvocationStrategy)
            ((games.pixscape.runtime.loading.SceneLoadingInvocationStrategy)world.getInvocationStrategy()).synchronizeEntitySubscriptions();
        if(admittedWorld!=world){
            admitWorld(world,published);
            admittedWorld=world;
        }
        bundle=published;
        if(shader!=null && preparedNormalMaps!=useNormalMaps){
            mesh.dispose();shader.dispose();mesh=null;shader=null;
        }
        if(shader==null){
            preparedNormalMaps=useNormalMaps;
            vertexStride=useNormalMaps?VERTEX_STRIDE:UNMAPPED_VERTEX_STRIDE;
            data=new float[CAPACITY*vertexStride];
            curveNormalOffset=useNormalMaps?CURVE_NORMAL_OFFSET:16;
            IntBuffer caps=BufferUtils.newIntBuffer(1);
            check(caps,GL20.GL_MAX_VERTEX_ATTRIBS,useNormalMaps?9:7,"vertex attributes");
            check(caps,GL20.GL_MAX_TEXTURE_IMAGE_UNITS,6,"fragment samplers");
            check(caps,GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS,6,"combined samplers");
            boolean desktop=Gdx.app.getType()==com.badlogic.gdx.Application.ApplicationType.Desktop;
            check(caps,desktop?0x8B4B:GL20.GL_MAX_VARYING_VECTORS,desktop?40:10,"varying capacity");
            shader=new ShaderProgram(vertexSource(ForwardWorldTarget.version(),useNormalMaps),fragmentSource(ForwardWorldTarget.version(),useNormalMaps));
            if(!shader.isCompiled())throw new IllegalStateException("Forward lighting shader: "+shader.getLog());
            com.badlogic.gdx.utils.Array<VertexAttribute> attributes=new com.badlogic.gdx.utils.Array<VertexAttribute>();
            attributes.addAll(
                    new VertexAttribute(VertexAttributes.Usage.Position,2,"a_screen"),
                    new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,2,"a_uv"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_physical"),
                    new VertexAttribute(VertexAttributes.Usage.Normal,3,"a_normal"),
                    new VertexAttribute(VertexAttributes.Usage.ColorPacked,4,"a_color"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_material"));
            if(useNormalMaps)attributes.addAll(
                    new VertexAttribute(VertexAttributes.Usage.Generic,3,"a_normalUV"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_normalBasis"));
            attributes.add(new VertexAttribute(VertexAttributes.Usage.Generic,3,"a_curveNormal"));
            mesh=new Mesh(false,CAPACITY,0,attributes.toArray(VertexAttribute.class));
            shader.bind();shader.setUniformi("u_array",0);shader.setUniformi("u_shadow",3);shader.setUniformi("u_shadowIdentity",4);shader.setUniformi("u_shadowSecond",5);
            if(useNormalMaps)for(int i=0;i<4;i++)shader.setUniformi("u_normal"+i,2);
        }
        target.prepare();
    }
    private static void check(IntBuffer caps,int name,int minimum,String label){caps.clear();Gdx.gl.glGetIntegerv(name,caps);if(caps.get(0)<minimum)throw new IllegalStateException("Forward lighting requires "+minimum+" "+label+", available "+caps.get(0));}
    /** Resource admission belongs to the renderer's owner; Runtime admits only packed resources. */
    protected void admitWorld(World world,AtlasRuntimeService.TextureArrayBundle published) {
        ForwardAdmission.validateWorld(world,published);
        games.pixscape.runtime.system.LightingGeometrySystem geometry=world.getSystem(games.pixscape.runtime.system.LightingGeometrySystem.class);
        if(geometry!=null)geometry.validateForwardAvailability(published);
    }
    protected void admitQueue(FrameRenderQueue queue,AtlasRuntimeService.TextureArrayBundle published,boolean normalMaps) {
        ForwardAdmission.validateQueue(queue,published,normalMaps);
    }
    public static String vertexSource(String version){return vertexSource(version,true);}
    public static String vertexSource(String version,boolean useNormalMaps){return LightingShaderSources.forwardVertex(version,useNormalMaps);}
    /** Explicit forward composition with its own alpha, ambient and direct-light result. */
    public static String fragmentSource(String version){return LightingShaderSources.forwardReceiver(version);}
    public static String fragmentSource(String version,boolean useNormalMaps){return LightingShaderSources.forwardReceiver(version,useNormalMaps);}
    public void render(FrameRenderQueue queue,LayerStateSOA layers,OrthographicCamera camera,
                       LightingRenderer shadows,float ambientR,float ambientG,float ambientB,RenderStats stats) {
        admitQueue(queue,bundle,preparedNormalMaps);
        this.stats=stats;worldDraws=presentationDraws=normalFlushes=blendFlushes=capacityFlushes=endFlushes=0;uploadedBytes=0;
        vertices=0;blend=normalHandle=-1;
        target.begin();
        try {
            shadows.renderShadows(queue.lighting,stats);target.resume();
            if(queue.lighting.timer!=null)queue.lighting.timer.begin(LightingPassTimer.RECEIVERS);
            receiverShader=shader;
            bindReceiverUniforms(queue,camera,shadows,ambientR,ambientG,ambientB);
            bundle.textureArray.bind(0);stats.textureBinds++;
            Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
            renderEntries(queue,layers,camera,shadows,ambientR,ambientG,ambientB);
            if(vertices!=0)endFlushes++;flush();
            if(queue.lighting.timer!=null)queue.lighting.timer.end(LightingPassTimer.RECEIVERS);
            if(queue.lighting.timer!=null)queue.lighting.timer.begin(4);
            target.present();stats.drawCalls++;stats.presentationDraws++;stats.drawnIndices+=6;stats.textureBinds++;stats.shaderBinds++;presentationDraws=1;
            stats.framebufferBinds+=3;stats.framebufferSwitches+=3;
            if(queue.lighting.timer!=null)queue.lighting.timer.end(4);
        } finally {vertices=0;shadows.abort();target.end();}
    }
    /** Ordered receiver submission inside one shadow pass and one final presentation. */
    protected void renderEntries(FrameRenderQueue queue,LayerStateSOA layers,OrthographicCamera camera,
                                 LightingRenderer shadows,float ambientR,float ambientG,float ambientB) {
        for(int i=0;i<queue.size;i++){
            if(queue.light[i]!=0)continue;
            int layer=queue.layerIndex[i];if(layers.maxLayerIndex()>=0&&(layer<0||layer>layers.maxLayerIndex()||!layers.enabled[layer]))continue;
            LightingSurface surface=queue.sourceDomain[i]==FrameRenderQueue.SOURCE_TILED?queue.lighting.tiles.get(queue.sourceSlot[i]):queue.lighting.entities.get(queue.sourceEntity[i]);
            draw(queue,i,surface,shadows,bundle.handle2layer.get(queue.textureHandle[i],-1));
        }
    }
    /** Borrows a prepared receiver shader; switching never clears or presents the target. */
    protected final void useReceiverShader(ShaderProgram next,FrameRenderQueue queue,OrthographicCamera camera,
                                           LightingRenderer shadows,float ambientR,float ambientG,float ambientB) {
        if(receiverShader==next)return;
        flush();receiverShader=next;
        bindReceiverUniforms(queue,camera,shadows,ambientR,ambientG,ambientB);
        stats.shaderBinds++;
    }
    protected final ShaderProgram packedReceiverShader(){return shader;}
    private void bindReceiverUniforms(FrameRenderQueue queue,OrthographicCamera camera,LightingRenderer shadows,
                                      float ambientR,float ambientG,float ambientB){
        receiverShader.bind();receiverShader.setUniformMatrix("u_matrix",camera.combined);
        receiverShader.setUniformf("u_ambient",ambientR,ambientG,ambientB);
        lightUniforms(queue.lighting,shadows);
    }
    private void lightUniforms(LightingFrame frame,LightingRenderer shadows){
        ShaderProgram shader=receiverShader;
        LightingFrame.Light l=frame.lights.size==0?null:frame.lights.first();
        shader.setUniformf("u_light",l==null?0:l.x,l==null?0:l.y,l==null?0:l.z);
        shader.setUniformf("u_color",l==null?0:l.r*l.intensity,l==null?0:l.g*l.intensity,l==null?0:l.b*l.intensity);
        shader.setUniformf("u_range",l==null?1:l.radius);shader.setUniformf("u_falloff",l==null?1:l.falloff);
        shader.setUniformf("u_direction",l==null?1:l.dx,l==null?0:l.dy,l==null?0:l.dz);
        shader.setUniformf("u_cone",l!=null&&l.cone?1:0,l==null?0:l.cosOuter,l==null?0:l.cosInner);
        shader.setUniformi("u_quality",l==null?0:l.quality);shader.setUniformf("u_resolution",l==null?512:l.resolution);
        shader.setUniformf("u_atlasScale",l==null?1:shadows.atlasScale(l));
    }
    protected final void draw(FrameRenderQueue q,int i,LightingSurface s,LightingRenderer shadows,int layer){
        NormalMapBinding normal=!preparedNormalMaps || s==null?null:s.normalMap;
        // Unmapped vertices never sample u_normal. Keep the current page so they
        // can share a draw with mapped vertices; still initialize a complete sampler
        // after each render reset, including frames beginning with unmapped sprites.
        int page=normal==null?(normalHandle<0?TextureRegistry.WHITE_HANDLE:normalHandle):normal.textureHandle;
        if(normalHandle<0){
            Texture atlas=q.lighting.lights.size==0||q.lighting.lights.first().quality==0?null:shadows.shadowTexture();
            (atlas==null?TextureRegistry.getByHandle(TextureRegistry.WHITE_HANDLE):atlas).bind(3);
            Texture identity=shadows.shadowIdentityTexture(),second=shadows.secondShadowTexture();
            (identity==null?TextureRegistry.getByHandle(TextureRegistry.WHITE_HANDLE):identity).bind(4);
            (second==null?TextureRegistry.getByHandle(TextureRegistry.WHITE_HANDLE):second).bind(5);stats.textureBinds+=3;
        }
        if(preparedNormalMaps && normalHandle!=page){if(vertices!=0)normalFlushes++;flush();TextureRegistry.getByHandle(page).bind(2);stats.textureBinds++;}
        normalHandle=page;
        int mode=q.blend[i];
        if(blend!=mode){if(vertices!=0)blendFlushes++;flush();blend=mode;
            boolean additive=mode==3||mode==4;
            int src=mode==1||mode==4?GL20.GL_SRC_ALPHA:GL20.GL_ONE;
            int dst=additive?GL20.GL_ONE:mode==0||mode==7?GL20.GL_ZERO:GL20.GL_ONE_MINUS_SRC_ALPHA;
            Gdx.gl.glBlendFuncSeparate(src,dst,additive?GL20.GL_ZERO:GL20.GL_ONE,additive?GL20.GL_ONE:GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        quad[0]=q.x1[i];quad[1]=q.y1[i];quad[2]=q.x2[i];quad[3]=q.y2[i];quad[4]=q.x3[i];quad[5]=q.y3[i];quad[6]=q.x4[i];quad[7]=q.y4[i];
        float ex=1,ey=0,ux=0,uy=1;
        if(normal!=null){float ax=(quad[6]-quad[0])/normal.colorWidth,ay=(quad[7]-quad[1])/normal.colorWidth,bx=(quad[2]-quad[0])/normal.colorHeight,by=(quad[3]-quad[1])/normal.colorHeight;
            float det=ax*by-ay*bx;if(Math.abs(det)>.000001f){float scale=(float)Math.sqrt(bx*bx+by*by)/det;ex=by*scale;ey=-bx*scale;ux=-ay*scale;uy=ax*scale;}}
        ForwardTopology topology=s==null?ForwardTopology.NATIVE:s.forwardTopology;
        float[] physical=s==null?null:s.receiverVertices==null?s.vertices:s.receiverVertices;
        float[] curve=s!=null&&roundedLightingEnabled?s.curveNormals:null;
        float[] immutable=s==null?null:s.forwardAttributes;
        for(int k=0;k<topology.vertexCount;k++){
            if(vertices==CAPACITY){
                capacityFlushes++;flush();
            }int out=vertices++*vertexStride,w=k*5;
            int triangle=(int)topology.weights[w],source=s==null?-1:topology.source[k];
            float pa=topology.weights[w+1],pb=topology.weights[w+2],pc=1-pa-pb;
            float ca=topology.weights[w+3],cb=topology.weights[w+4],cc=1-ca-cb;
            int c0=ForwardTopology.CORNERS[triangle*3],c1=ForwardTopology.CORNERS[triangle*3+1],c2=ForwardTopology.CORNERS[triangle*3+2];
            data[out+SCREEN_OFFSET]=ca*quad[c0*2]+cb*quad[c1*2]+cc*quad[c2*2];data[out+SCREEN_OFFSET+1]=ca*quad[c0*2+1]+cb*quad[c1*2+1]+cc*quad[c2*2+1];
            float u=ca*(c0>=2?1:0)+cb*(c1>=2?1:0)+cc*(c2>=2?1:0),v=ca*(c0==1||c0==2?1:0)+cb*(c1==1||c1==2?1:0)+cc*(c2==1||c2==2?1:0);
            int cached=k*11;
            data[out+UV_OFFSET]=q.u1[i]+u*(q.u2[i]-q.u1[i]);data[out+UV_OFFSET+1]=q.v2[i]+v*(q.v1[i]-q.v2[i]);
            for(int c=0;c<3;c++){data[out+PHYSICAL_OFFSET+c]=source<0?0:immutable!=null?immutable[cached+c]:pa*physical[source+c]+pb*physical[source+8+c]+pc*physical[source+16+c];data[out+NORMAL_OFFSET+c]=source<0?0:immutable!=null?immutable[cached+3+c]:pa*physical[source+5+c]+pb*physical[source+13+c]+pc*physical[source+21+c];}
            data[out+PHYSICAL_OFFSET+3]=s==null?0:s.receiverShadowGroup(source);
            data[out+COLOR_OFFSET]=q.colorPacked[i];
            data[out+MATERIAL_OFFSET]=layer;data[out+MATERIAL_OFFSET+1]=mode+(q.ignoreAmbientLighting[i]?16:0);data[out+MATERIAL_OFFSET+2]=s!=null&&s.receiveLight&&source>=0&&mode!=3&&mode!=4?1:-1;data[out+MATERIAL_OFFSET+3]=s==null?0:s.receiverLightingMode(source);
            if(preparedNormalMaps){
                float pu=source<0?u:immutable!=null?immutable[cached+6]:pa*physical[source+3]+pb*physical[source+11]+pc*physical[source+19],pv=source<0?v:immutable!=null?immutable[cached+7]:pa*physical[source+4]+pb*physical[source+12]+pc*physical[source+20];
                data[out+NORMAL_UV_OFFSET]=normal==null?0:normal.u0+normal.ux*pu+normal.uy*pv;data[out+NORMAL_UV_OFFSET+1]=normal==null?0:normal.v0+normal.vx*pu+normal.vy*pv;data[out+NORMAL_UV_OFFSET+2]=normal==null?-1:0;
                data[out+NORMAL_BASIS_OFFSET]=ex;data[out+NORMAL_BASIS_OFFSET+1]=ey;data[out+NORMAL_BASIS_OFFSET+2]=ux;data[out+NORMAL_BASIS_OFFSET+3]=uy;
            }
            int csource=source/8*3;
            for(int c=0;c<3;c++)data[out+curveNormalOffset+c]=curve==null||source<0?0:immutable!=null?immutable[cached+8+c]:pa*curve[csource+c]+pb*curve[csource+3+c]+pc*curve[csource+6+c];
        }
        stats.drawnQuads++;
    }
    protected final void flush(){if(vertices==0)return;receiverShader.bind();mesh.setVertices(data,0,vertices*vertexStride);mesh.render(receiverShader,GL20.GL_TRIANGLES,0,vertices);uploadedBytes+=(long)vertices*vertexStride*4;worldDraws++;stats.worldDraws++;stats.drawCalls++;stats.flushes++;stats.flushedVertices+=vertices;
        stats.bufferUploads++;stats.bufferUploadBytes+=(long)vertices*vertexStride*4;stats.uploadedVertices+=vertices;stats.drawnVertices+=vertices;vertices=0;}
    public long textureBytes(){return target.textureBytes();}
    public int targetWidth(){return target.width;}
    public int targetHeight(){return target.height;}
    public void dispose(){target.dispose();if(mesh!=null)mesh.dispose();if(shader!=null)shader.dispose();mesh=null;shader=null;receiverShader=null;bundle=null;admittedWorld=null;}
}
