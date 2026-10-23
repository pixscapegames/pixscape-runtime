package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.NumberUtils;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.render.batch.performance.RenderStats;

/** Ordered triangle batching with four page bindings. User parameter tables are untouched. */
final class LightingBatch {
    private static final int SHADOW_STRIDE=18, BASE_STRIDE=21, MAPPED_STRIDE=28, CAPACITY=12288;
    private int stride=BASE_STRIDE;
    private final float[] data=new float[CAPACITY*MAPPED_STRIDE];
    private final int[] handles=new int[4];
    private final int[] planningHandles=new int[4];
    private static final class ReplayBatch {
        final Mesh mesh;
        final int[] handles=new int[4];
        int vertices,textures;
        ReplayBatch(){mesh=baseMesh(false);}
    }
    private final com.badlogic.gdx.utils.Array<ReplayBatch> replayBatches=new com.badlogic.gdx.utils.Array<ReplayBatch>();
    private int replayCount;
    long uploadedBytes;
    int uploads;
    private final int[] normalHandles=new int[4], boundNormals=new int[4];
    private int normalTextures;
    private boolean mapped;
    private final Mesh mesh, mappedMesh;
    // GWT emulates tiny array copies with temporary JS arrays; keep native copies unchanged.
    private final boolean htmlVertexCopies=Gdx.app.getType()==Application.ApplicationType.WebGL;
    private int vertices,textures,blend=-1;
    private boolean retainReplay;
    private ShaderProgram shader;
    private RenderStats stats;
    LightingBatch(){
        mesh=baseMesh(true);
        mappedMesh=new Mesh(false,CAPACITY,0,new VertexAttribute(VertexAttributes.Usage.Position,3,"a_position"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,2,"a_uv"),
                new VertexAttribute(VertexAttributes.Usage.Normal,3,"a_normal"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked,4,"a_color"),
                new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_flags"),
                new VertexAttribute(VertexAttributes.Usage.Generic,2,"a_screen"),
                new VertexAttribute(VertexAttributes.Usage.Generic,3,"a_normalUV"),
                new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_normalBasis"),
                new VertexAttribute(VertexAttributes.Usage.Generic,3,"a_curveNormal"));
    }
    private static Mesh baseMesh(boolean receiver){
        VertexAttribute[] attributes={
            new VertexAttribute(VertexAttributes.Usage.Position,3,"a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,2,"a_uv"),
            new VertexAttribute(VertexAttributes.Usage.Normal,3,"a_normal"),
            new VertexAttribute(VertexAttributes.Usage.ColorUnpacked,4,"a_color"),
            new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_flags"),
            new VertexAttribute(VertexAttributes.Usage.Generic,2,"a_screen"),
            new VertexAttribute(VertexAttributes.Usage.Generic,3,"a_curveNormal")};
        return new Mesh(false,CAPACITY,0,receiver?attributes:java.util.Arrays.copyOf(attributes,attributes.length-1));}
    /** Explicit frame preparation, before submission. Release excess scene capacity here only. */
    void prepareReplayCapacity(int count){
        abort();
        while(replayBatches.size<count)replayBatches.add(new ReplayBatch());
        while(replayBatches.size>count)replayBatches.pop().mesh.dispose();
        replayBatches.shrink();
    }
    int replayCapacity(){return replayBatches.size;}
    long replayBytes(){return (long)replayBatches.size*CAPACITY*SHADOW_STRIDE*4;}
    void begin(ShaderProgram shader,RenderStats stats){begin(shader,stats,false);}
    void begin(ShaderProgram shader,RenderStats stats,boolean retainReplay){
        this.shader=shader;this.stats=stats;this.retainReplay=retainReplay;vertices=textures=0;blend=-1;replayCount=0;
        mapped=false;stride=retainReplay?SHADOW_STRIDE:BASE_STRIDE;normalTextures=0;
    }
    void beginMapped(ShaderProgram shader,RenderStats stats){
        begin(shader,stats,false);mapped=true;stride=MAPPED_STRIDE;
        for(int i=0;i<4;i++)boundNormals[i]=0;
    }
    /** Allocation-free exact batch plan, using the actor topology count (light deformation keeps it). */
    int requiredReplayBatches(LightingFrame frame,com.badlogic.gdx.utils.IntArray candidates,boolean decorOnly){
        int total=0,textureCount=0,batches=0;
        for(int i=0;i<candidates.size;i++){
            LightingFrame.Caster caster=frame.casters.get(candidates.get(i));
            if(decorOnly && caster!=null && caster.surface!=null && caster.surface.actor)continue;
            if(caster==null || caster.surface==null || caster.surface.vertices==null || caster.surface.vertices.length%8!=0 ||
                    caster.texture==0 || TextureRegistry.getByHandle(caster.texture)==null)
                throw new LightingAdmissionException("Shadow caster has no valid prepared geometry/texture");
            int count=caster.surface.actorShadow!=null&&caster.surface.actorShadow.active
                    ?caster.surface.actorShadow.vertexCount:caster.surface.vertices.length/8;
            if(count==0)continue;
            boolean found=false;
            for(int j=0;j<textureCount;j++)if(planningHandles[j]==caster.texture){found=true;break;}
            if(!found){if(textureCount==4){if(total>0)batches++;total=textureCount=0;}planningHandles[textureCount++]=caster.texture;}
            while(count>0){
                if(total==CAPACITY){batches++;total=0;textureCount=1;planningHandles[0]=caster.texture;}
                int n=Math.min(count,CAPACITY-total);total+=n;count-=n;
            }
        }
        return batches+(total==0?0:1);
    }
    void replay(){
        shader.bind();
        for(int i=0;i<replayCount;i++){
            ReplayBatch b=replayBatches.get(i);bindTextures(b.handles,b.textures);
            b.mesh.render(shader,GL20.GL_TRIANGLES,0,b.vertices);
            if(stats!=null){stats.drawCalls++;stats.replayDraws++;stats.drawnVertices+=b.vertices;stats.textureBinds+=4;}
        }
    }
    void draw(LightingSurface surface,int texture,float u1,float v1,float u2,float v2,float color,
              int blendMode,float[] quad,float offsetX,float offsetY,boolean shadow,boolean supported){
        if(blend!=blendMode){flush();blend=blendMode;
            if(shadow)Gdx.gl.glDisable(GL20.GL_BLEND);
            else{Gdx.gl.glEnable(GL20.GL_BLEND);
                boolean alpha=blend==1||blend==2;Gdx.gl.glBlendFuncSeparate(alpha?GL20.GL_ONE:GL20.GL_ONE,
                        alpha?GL20.GL_ONE_MINUS_SRC_ALPHA:GL20.GL_ZERO,GL20.GL_ONE,alpha?GL20.GL_ONE_MINUS_SRC_ALPHA:GL20.GL_ZERO);}
        }
        int slot=-1;for(int i=0;i<textures;i++)if(handles[i]==texture){slot=i;break;}
        if(slot<0){if(textures==4)flush();slot=textures;handles[textures++]=texture;}
        games.pixscape.runtime.service.NormalMapBinding normal=shadow?null:surface.normalMap;
        int normalSlot=-1;
        if(mapped && normal!=null){
            for(int i=0;i<normalTextures;i++)if(normalHandles[i]==normal.textureHandle){normalSlot=i;break;}
            if(normalSlot<0){
                if(normalTextures==4){flush();normalTextures=0;textures=1;handles[0]=texture;slot=0;}
                normalSlot=normalTextures;normalHandles[normalTextures++]=normal.textureHandle;
            }
        }
        float ex=1,ey=0,ux=0,uy=1;
        if(normalSlot>=0 && quad!=null){
            float ax=(quad[6]-quad[0])/normal.colorWidth,ay=(quad[7]-quad[1])/normal.colorWidth;
            float bx=(quad[2]-quad[0])/normal.colorHeight,by=(quad[3]-quad[1])/normal.colorHeight;
            float determinant=ax*by-ay*bx,depthScale=(float)Math.sqrt(bx*bx+by*by);
            // Normal covectors use the inverse transpose of the whole image transform.
            // Image-up sets depth scale, matching LocalSurface transforms; uniform scale cancels.
            if(Math.abs(determinant)>.000001f){float scale=depthScale/determinant;
                ex=by*scale;ey=-bx*scale;ux=-ay*scale;uy=ax*scale;}
        }
        int packed=NumberUtils.floatToIntColor(color);
        float r=(packed&255)/255f,g=((packed>>>8)&255)/255f,b=((packed>>>16)&255)/255f,a=((packed>>>24)&255)/255f;
        float[] source=shadow && surface.shadowVertices!=null?surface.shadowVertices:!shadow && surface.receiverVertices!=null?surface.receiverVertices:surface.vertices;
        int sourceLength=shadow && surface.shadowVertices!=null?surface.shadowVertexCount*8:source.length;
        for(int i=0;i<sourceLength;i+=8){
            if(vertices==CAPACITY){flush();textures=1;handles[0]=texture;slot=0;}
            int o=vertices++*stride;
            if(htmlVertexCopies){data[o]=source[i];data[o+1]=source[i+1];data[o+2]=source[i+2];}
            else System.arraycopy(source,i,data,o,3);
            float u=source[i+3],v=source[i+4];data[o+3]=u1+u*(u2-u1);data[o+4]=v2+v*(v1-v2);
            if(mapped){
            data[o+18]=normalSlot<0?0:normal.u0+normal.ux*u+normal.uy*v;
            data[o+19]=normalSlot<0?0:normal.v0+normal.vx*u+normal.vy*v;
            data[o+20]=normalSlot;data[o+21]=ex;data[o+22]=ey;data[o+23]=ux;data[o+24]=uy;
            }
            if(htmlVertexCopies){data[o+5]=source[i+5];data[o+6]=source[i+6];data[o+7]=source[i+7];}
            else System.arraycopy(source,i+5,data,o+5,3);
            // Shadow replay keeps its original compact layout: it never consumes facade normals.
            if(!retainReplay){
                int macro=o+(mapped?25:18),normalIndex=i/8*3;
                float[] curve=shadow?null:surface.curveNormals;
                data[macro]=curve==null?source[i+5]:curve[normalIndex];
                data[macro+1]=curve==null?source[i+6]:curve[normalIndex+1];
                data[macro+2]=curve==null?source[i+7]:curve[normalIndex+2];
            }
            data[o+8]=r;data[o+9]=g;data[o+10]=b;data[o+11]=a;
            boolean completed=!shadow && surface.receiverVertices!=null && surface.vertices[i+5]==0 && surface.vertices[i+6]==0 && surface.vertices[i+7]==0;
            data[o+12]=slot;data[o+13]=(completed?3:surface.approximate?2:1)*(supported&&surface.receiveLight?1:-1);
            // Receiver side/provenance: 0 = one-sided decor, 1 = two-sided, -1 = one-sided actor.
            // Keep shadow flags unchanged; ordinary local actor meshes may also be one-sided.
            data[o+14]=surface.twoSided?1:!shadow&&surface.actor?-1:0;data[o+15]=shadow?surface.alphaThreshold:blendMode==7?.5f:blendMode==0?-2:blendMode==2?-3:-1;
            // Simple sheets share the color triangles BL-TL-TR / BL-TR-BR, including a freely moved TR.
            // Local descriptions and tile receivers keep their existing UV topology and placement.
            if(quad!=null && surface.imageQuad){int c=SpriteSurfaceSupport.corner(i/8)*2;
                data[o+16]=quad[c];data[o+17]=quad[c+1];}
            else if(quad!=null){data[o+16]=quad[0]+u*(quad[6]-quad[0])+v*(quad[2]-quad[0]);
                data[o+17]=quad[1]+u*(quad[7]-quad[1])+v*(quad[3]-quad[1]);}
            else{data[o+16]=LightingCoordinates.screenX(source[i],source[i+1])+offsetX;
                data[o+17]=LightingCoordinates.screenY(source[i],source[i+1],source[i+2])+offsetY;}
        }
    }
    void flush(){
        if(vertices==0)return;
        shader.bind();
        bindTextures(handles,textures);
        if(mapped)bindNormalTextures();
        Mesh active=mapped?mappedMesh:mesh;
        if(retainReplay){
            if(mapped||replayCount>=replayBatches.size)throw new IllegalStateException("Shadow replay capacity must be prepared before submission");
            ReplayBatch b=replayBatches.get(replayCount++);active=b.mesh;
            b.vertices=vertices;b.textures=textures;System.arraycopy(handles,0,b.handles,0,textures);
        }
        active.setVertices(data,0,vertices*stride);active.render(shader,GL20.GL_TRIANGLES,0,vertices);
        uploads++;uploadedBytes+=(long)vertices*stride*4;
        if(stats!=null){stats.drawCalls++;stats.flushes++;stats.textureBinds+=4;
            stats.bufferUploads++;stats.bufferUploadBytes+=(long)vertices*stride*4;
            stats.uploadedVertices+=vertices;stats.drawnVertices+=vertices;}
        vertices=0;textures=0;
    }
    private static void bindTextures(int[] source,int count){
        for(int i=0;i<count;i++){Texture t=TextureRegistry.getByHandle(source[i]);
            if(t==null)throw new IllegalStateException("Lighting texture handle is not registered: "+source[i]);t.bind(i);}
        // All active sampler uniforms need complete, non-attachment textures on WebGL.
        // A preceding accumulation leaves the scratch attachment bound on unit 1.
        Texture first=TextureRegistry.getByHandle(source[0]);
        if(first==null)throw new IllegalStateException("Lighting texture handle is not registered: "+source[0]);
        for(int i=count;i<4;i++)first.bind(i);
    }
    private void bindNormalTextures(){
        for(int i=0;i<4;i++){
            int handle=normalTextures==0?TextureRegistry.WHITE_HANDLE:normalHandles[i<normalTextures?i:0];
            if(boundNormals[i]==handle)continue;
            Texture texture=TextureRegistry.getByHandle(handle);
            if(texture==null || texture.getTextureObjectHandle()==0)
                throw new IllegalStateException("Normal page texture is not live: "+handle);
            texture.bind(i+5);boundNormals[i]=handle;if(stats!=null)stats.textureBinds++;
        }
    }
    void abort(){vertices=textures=replayCount=0;blend=-1;retainReplay=false;}
    void dispose(){abort();for(ReplayBatch b:replayBatches)b.mesh.dispose();replayBatches.clear();mesh.dispose();mappedMesh.dispose();}
}
