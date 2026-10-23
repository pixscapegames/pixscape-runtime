package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.NumberUtils;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.*;
import java.nio.IntBuffer;

/** Ordered one-light renderer. Units: color array 0; existing material table reserves 1;
 * one borrowed normal page 2; borrowed full/decor shadow atlas 3. Nine attributes,
 * ten varying declarations (27 scalar components, budgeted as ten vectors),
 * six active sampler uniforms (the four normal samplers alias unit 2),
 * no texture uploads or topology work. */
public final class ForwardLightingRenderer {
    private static final int STRIDE=28,CAPACITY=12288;
    private final float[] data=new float[CAPACITY*STRIDE],quad=new float[8];
    private final ForwardWorldTarget target=new ForwardWorldTarget();
    private ShaderProgram shader;
    private Mesh mesh;
    private World admittedWorld;
    private AtlasRuntimeService.TextureArrayBundle bundle;
    private int vertices,blend=-1,normalHandle=-1,domain=-1;
    private RenderStats stats;
    public int worldDraws,presentationDraws,normalFlushes,domainFlushes,blendFlushes,capacityFlushes,endFlushes;
    public long uploadedBytes;
    private boolean roundedLightingEnabled=true;
    public void setRoundedLightingEnabled(boolean enabled){roundedLightingEnabled=enabled;}
    public boolean roundedLightingEnabled(){return roundedLightingEnabled;}

    public void prepare(World world,AtlasRuntimeService.TextureArrayBundle published) {
        if(world.getInvocationStrategy() instanceof games.pixscape.runtime.loading.SceneLoadingInvocationStrategy)
            ((games.pixscape.runtime.loading.SceneLoadingInvocationStrategy)world.getInvocationStrategy()).synchronizeEntitySubscriptions();
        if(admittedWorld!=world){
            ForwardAdmission.validateWorld(world,published);
            games.pixscape.runtime.system.LightingGeometrySystem geometry=world.getSystem(games.pixscape.runtime.system.LightingGeometrySystem.class);
            if(geometry!=null)geometry.validateForwardAvailability(published);
            admittedWorld=world;
        }
        bundle=published;
        if(shader==null){
            IntBuffer caps=BufferUtils.newIntBuffer(1);
            check(caps,GL20.GL_MAX_VERTEX_ATTRIBS,9,"vertex attributes");
            check(caps,GL20.GL_MAX_TEXTURE_IMAGE_UNITS,6,"fragment samplers");
            check(caps,GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS,6,"combined samplers");
            boolean desktop=Gdx.app.getType()==com.badlogic.gdx.Application.ApplicationType.Desktop;
            check(caps,desktop?0x8B4B:GL20.GL_MAX_VARYING_VECTORS,desktop?40:10,"varying capacity");
            shader=new ShaderProgram(vertexSource(ForwardWorldTarget.version()),fragmentSource(ForwardWorldTarget.version()));
            if(!shader.isCompiled())throw new IllegalStateException("Forward lighting shader: "+shader.getLog());
            mesh=new Mesh(false,CAPACITY,0,
                    new VertexAttribute(VertexAttributes.Usage.Position,2,"a_screen"),
                    new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,2,"a_uv"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,3,"a_physical"),
                    new VertexAttribute(VertexAttributes.Usage.Normal,3,"a_normal"),
                    new VertexAttribute(VertexAttributes.Usage.ColorUnpacked,4,"a_color"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_material"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,3,"a_normalUV"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_normalBasis"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,3,"a_curveNormal"));
            shader.bind();shader.setUniformi("u_array",0);shader.setUniformi("u_shadow",3);
            for(int i=0;i<4;i++)shader.setUniformi("u_normal"+i,2);
        }
        target.prepare();
    }
    private static void check(IntBuffer caps,int name,int minimum,String label){caps.clear();Gdx.gl.glGetIntegerv(name,caps);if(caps.get(0)<minimum)throw new IllegalStateException("Forward lighting requires "+minimum+" "+label+", available "+caps.get(0));}
    public static String vertexSource(String version){return version+
            "in vec2 a_screen,a_uv;in vec3 a_physical,a_normal,a_curveNormal;in vec4 a_color,a_material;in vec3 a_normalUV;in vec4 a_normalBasis;uniform mat4 u_matrix;"+
            "out vec2 uv;out vec3 p,n;out vec3 curveNormal;out vec4 color;flat out vec4 flags;out vec2 normalUV;flat out float normalSlot;flat out vec4 normalBasis;flat out float materialMode;"+
            "void main(){uv=a_uv;p=a_physical;n=a_normal;curveNormal=a_curveNormal;color=a_color;materialMode=a_material.y;float blend=mod(materialMode,16.);"+
            "flags=vec4(a_material.x,a_material.z,a_material.w,blend==7.?.5:blend==0.?-2.:blend==2.?-3.:-1.);"+
            "normalUV=a_normalUV.xy;normalSlot=a_normalUV.z;normalBasis=a_normalBasis;gl_Position=u_matrix*vec4(a_screen,0.,1.);}";}
    /** Shares the exact reference hard/PCF, geometric bias, horizon and normal-map expressions. */
    public static String fragmentSource(String version){
        String reference=LightingRenderer.RECEIVER;
        String declarations=reference.substring(0,reference.indexOf("void main()"));
        String direct=reference.substring(reference.indexOf("float value=0.;"),reference.indexOf("result=vec4(outputColor"))
                // Range and the existing geometric horizon can make direct light exactly zero.
                // Keep coverage/ambient; only skip work whose final contribution is zero.
                .replace("if(flags.y>.5&&normalLength>.0001)",
                        "if(flags.y>.5&&normalLength>.0001&&distance<u_range&&"+
                        "(flags.z!=0.||dot(macroNormal(n/normalLength),distance>0.?-delta/distance:vec3(0))>.052335956))")
                .replace("vec3 normal=n/normalLength;","vec3 geometric=n/normalLength;vec3 normal=lightingNormal(macroNormal(geometric));")
                .replace("float c=clamp(abs(cosine),","float c=clamp(abs(dot(geometric,-delta/max(distance,.0001))),")
                .replace("dot(tap,normal)","dot(tap,geometric)").replace("dot(delta,normal)","dot(delta,geometric)")
                .replace("float cG=dot(n/normalLength,","float cG=dot(macroNormal(n/normalLength),");
        return version+"in vec2 uv;in vec3 p,n;in vec4 color;flat in vec4 flags;flat in float materialMode;uniform sampler2DArray u_array;uniform vec3 u_ambient;"+
                declarations+LightingRenderer.NORMAL_SAMPLE+
                "in vec3 curveNormal;vec3 macroNormal(vec3 plane){return dot(curveNormal,curveNormal)>.0001?normalize(curveNormal):plane;}"+
                "void main(){vec4 texel=texture(u_array,vec3(uv,flags.x));if(flags.w>=0.&&texel.a<flags.w)discard;vec4 t=texel*color;"+
                // Native premultiplied additive emitters carry emission in RGB with zero coverage.
                // ONE-source blends must retain that RGB even when alpha is zero.
                "if(flags.w!= -2.&&flags.w!= -3.&&mod(materialMode,16.)!=3.&&t.a<=0.)discard;float alpha=flags.w== -2.||flags.w>=0.?1.:t.a;"+
                direct+"vec3 ambient=materialMode>=16.?vec3(1.):u_ambient;result=vec4(t.rgb*(ambient+u_color*value),alpha);}";
    }
    public void render(FrameRenderQueue queue,LayerStateSOA layers,OrthographicCamera camera,
                       LightingRenderer shadows,float ambientR,float ambientG,float ambientB,RenderStats stats) {
        ForwardAdmission.validateQueue(queue,bundle);
        this.stats=stats;worldDraws=presentationDraws=normalFlushes=domainFlushes=blendFlushes=capacityFlushes=endFlushes=0;uploadedBytes=0;
        vertices=0;blend=normalHandle=domain=-1;
        target.begin();
        try {
            shadows.renderShadows(queue.lighting,stats);target.resume();
            if(queue.lighting.timer!=null)queue.lighting.timer.begin(LightingPassTimer.RECEIVERS);
            shader.bind();shader.setUniformMatrix("u_matrix",camera.combined);shader.setUniformf("u_ambient",ambientR,ambientG,ambientB);
            lightUniforms(queue.lighting,shadows);
            bundle.textureArray.bind(0);stats.textureBinds++;
            Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
            for(int i=0;i<queue.size;i++){
                if(queue.light[i]!=0)continue;
                int layer=queue.layerIndex[i];if(layers.maxLayerIndex()>=0&&(layer<0||layer>layers.maxLayerIndex()||!layers.enabled[layer]))continue;
                LightingSurface surface=queue.sourceDomain[i]==FrameRenderQueue.SOURCE_TILED?queue.lighting.tiles.get(queue.sourceSlot[i]):queue.lighting.entities.get(queue.sourceEntity[i]);
                draw(queue,i,surface,shadows);
            }
            if(vertices!=0)endFlushes++;flush();
            if(queue.lighting.timer!=null)queue.lighting.timer.end(LightingPassTimer.RECEIVERS);
            if(queue.lighting.timer!=null)queue.lighting.timer.begin(4);
            target.present();stats.drawCalls++;stats.textureBinds++;stats.shaderBinds++;presentationDraws=1;
            stats.framebufferBinds+=3;stats.framebufferSwitches+=3;
            if(queue.lighting.timer!=null)queue.lighting.timer.end(4);
        } finally {vertices=0;target.end();}
    }
    private void lightUniforms(LightingFrame frame,LightingRenderer shadows){
        LightingFrame.Light l=frame.lights.size==0?null:frame.lights.first();
        shader.setUniformf("u_light",l==null?0:l.x,l==null?0:l.y,l==null?0:l.z);
        shader.setUniformf("u_color",l==null?0:l.r*l.intensity,l==null?0:l.g*l.intensity,l==null?0:l.b*l.intensity);
        shader.setUniformf("u_range",l==null?1:l.radius);shader.setUniformf("u_falloff",l==null?1:l.falloff);
        shader.setUniformf("u_direction",l==null?1:l.dx,l==null?0:l.dy,l==null?0:l.dz);
        shader.setUniformf("u_cone",l!=null&&l.cone?1:0,l==null?0:l.cosOuter,l==null?0:l.cosInner);
        shader.setUniformi("u_quality",l==null?0:l.quality);shader.setUniformf("u_resolution",l==null?512:l.resolution);
        shader.setUniformf("u_atlasScale",l==null?1:shadows.atlasScale(l));
    }
    private void draw(FrameRenderQueue q,int i,LightingSurface s,LightingRenderer shadows){
        int receiverDomain=s!=null&&s.actor?1:0;
        NormalMapBinding normal=s==null?null:s.normalMap;
        int page=normal==null?TextureRegistry.WHITE_HANDLE:normal.textureHandle;
        if(domain!=receiverDomain){if(vertices!=0)domainFlushes++;flush();domain=receiverDomain;
            Texture atlas=q.lighting.lights.size==0||q.lighting.lights.first().quality==0?null:shadows.shadowTexture(domain==1);
            (atlas==null?TextureRegistry.getByHandle(TextureRegistry.WHITE_HANDLE):atlas).bind(3);stats.textureBinds++;
        }
        if(normalHandle!=page){if(vertices!=0)normalFlushes++;flush();normalHandle=page;TextureRegistry.getByHandle(page).bind(2);stats.textureBinds++;}
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
        int packed=NumberUtils.floatToRawIntBits(q.colorPacked[i]);
        float r=(packed&255)/255f,g=((packed>>>8)&255)/255f,b=((packed>>>16)&255)/255f,a=((packed>>>24)&255)/255f;
        ForwardTopology topology=s==null?ForwardTopology.NATIVE:s.forwardTopology;
        float[] physical=s==null?null:s.receiverVertices==null?s.vertices:s.receiverVertices;
        float[] curve=s!=null&&roundedLightingEnabled?s.curveNormals:null;
        int layer=bundle.handle2layer.get(q.textureHandle[i],-1);
        for(int k=0;k<topology.vertexCount;k++){
            if(vertices==CAPACITY){capacityFlushes++;flush();}int out=vertices++*STRIDE,w=k*5;
            int triangle=(int)topology.weights[w],source=s==null?-1:topology.source[k];
            float pa=topology.weights[w+1],pb=topology.weights[w+2],pc=1-pa-pb;
            float ca=topology.weights[w+3],cb=topology.weights[w+4],cc=1-ca-cb;
            int c0=ForwardTopology.CORNERS[triangle*3],c1=ForwardTopology.CORNERS[triangle*3+1],c2=ForwardTopology.CORNERS[triangle*3+2];
            data[out]=ca*quad[c0*2]+cb*quad[c1*2]+cc*quad[c2*2];data[out+1]=ca*quad[c0*2+1]+cb*quad[c1*2+1]+cc*quad[c2*2+1];
            float u=ca*(c0>=2?1:0)+cb*(c1>=2?1:0)+cc*(c2>=2?1:0),v=ca*(c0==1||c0==2?1:0)+cb*(c1==1||c1==2?1:0)+cc*(c2==1||c2==2?1:0);
            float pu=source<0?u:pa*physical[source+3]+pb*physical[source+11]+pc*physical[source+19],pv=source<0?v:pa*physical[source+4]+pb*physical[source+12]+pc*physical[source+20];
            data[out+2]=q.u1[i]+u*(q.u2[i]-q.u1[i]);data[out+3]=q.v2[i]+v*(q.v1[i]-q.v2[i]);
            for(int c=0;c<3;c++){data[out+4+c]=source<0?0:pa*physical[source+c]+pb*physical[source+8+c]+pc*physical[source+16+c];data[out+7+c]=source<0?0:pa*physical[source+5+c]+pb*physical[source+13+c]+pc*physical[source+21+c];}
            data[out+10]=r;data[out+11]=g;data[out+12]=b;data[out+13]=a;
            data[out+14]=layer;data[out+15]=mode+(q.ignoreAmbientLighting[i]?16:0);data[out+16]=s!=null&&s.receiveLight&&source>=0&&mode!=3&&mode!=4?1:-1;data[out+17]=s!=null&&s.twoSided?1:s!=null&&s.actor?-1:0;
            data[out+18]=normal==null?0:normal.u0+normal.ux*pu+normal.uy*pv;data[out+19]=normal==null?0:normal.v0+normal.vx*pu+normal.vy*pv;data[out+20]=normal==null?-1:0;
            data[out+21]=ex;data[out+22]=ey;data[out+23]=ux;data[out+24]=uy;
            int csource=source/8*3;
            for(int c=0;c<3;c++)data[out+25+c]=curve==null||source<0?0:pa*curve[csource+c]+pb*curve[csource+3+c]+pc*curve[csource+6+c];
        }
        stats.drawnQuads++;
    }
    private void flush(){if(vertices==0)return;shader.bind();mesh.setVertices(data,0,vertices*STRIDE);mesh.render(shader,GL20.GL_TRIANGLES,0,vertices);uploadedBytes+=(long)vertices*STRIDE*4;worldDraws++;stats.drawCalls++;stats.flushes++;stats.flushedVertices+=vertices;vertices=0;}
    public long textureBytes(){return target.textureBytes();}
    public int targetWidth(){return target.width;}
    public int targetHeight(){return target.height;}
    public void dispose(){target.dispose();if(mesh!=null)mesh.dispose();if(shader!=null)shader.dispose();mesh=null;shader=null;bundle=null;admittedWorld=null;}
}
