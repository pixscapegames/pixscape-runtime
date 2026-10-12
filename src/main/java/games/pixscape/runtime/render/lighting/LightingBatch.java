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
    private static final int STRIDE=18, CAPACITY=12288;
    private final float[] data=new float[CAPACITY*STRIDE];
    private final int[] handles=new int[4];
    private final int[] replayHandles=new int[4];
    private final Mesh mesh;
    // GWT emulates tiny array copies with temporary JS arrays; keep native copies unchanged.
    private final boolean htmlVertexCopies=Gdx.app.getType()==Application.ApplicationType.WebGL;
    private int vertices,textures,blend=-1,replayVertices,replayTextures;
    private boolean retainReplay;
    private ShaderProgram shader;
    private RenderStats stats;
    LightingBatch(){
        mesh=new Mesh(false,CAPACITY,0,new VertexAttribute(VertexAttributes.Usage.Position,3,"a_position"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,2,"a_uv"),
                new VertexAttribute(VertexAttributes.Usage.Normal,3,"a_normal"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked,4,"a_color"),
                new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_flags"),
                new VertexAttribute(VertexAttributes.Usage.Generic,2,"a_screen"));
    }
    void begin(ShaderProgram shader,RenderStats stats){begin(shader,stats,false);}
    void begin(ShaderProgram shader,RenderStats stats,boolean retainReplay){
        this.shader=shader;this.stats=stats;this.retainReplay=retainReplay;vertices=textures=0;blend=-1;replayVertices=replayTextures=0;
    }
    /** Checks whether one complete shadow batch can be prepared before any face submits it. */
    boolean canReplay(LightingFrame frame){
        int total=0,textureCount=0;
        for(int i=0;i<frame.casterIndex.candidates.size;i++){
            LightingFrame.Caster caster=frame.casters.get(frame.casterIndex.candidates.get(i));
            if(caster==null || caster.surface==null || caster.surface.vertices==null || caster.surface.vertices.length%8!=0 ||
                    caster.texture==0 || TextureRegistry.getByHandle(caster.texture)==null)return false;
            int count=caster.surface.vertices.length/8;
            if(count>CAPACITY-total)return false;
            total+=count;
            boolean found=false;
            for(int j=0;j<textureCount;j++)if(replayHandles[j]==caster.texture){found=true;break;}
            if(!found){if(textureCount==4)return false;replayHandles[textureCount++]=caster.texture;}
        }
        return total!=0;
    }
    void replay(){
        if(replayVertices==0 || replayTextures==0)throw new IllegalStateException("Lighting replay has no prepared shadow batch");
        shader.bind();
        bindTextures(replayHandles,replayTextures);
        mesh.render(shader,GL20.GL_TRIANGLES,0,replayVertices);
        if(stats!=null){stats.drawCalls++;stats.flushes++;stats.textureBinds+=4;}
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
        int packed=NumberUtils.floatToIntColor(color);
        float r=(packed&255)/255f,g=((packed>>>8)&255)/255f,b=((packed>>>16)&255)/255f,a=((packed>>>24)&255)/255f;
        float[] source=surface.vertices;
        for(int i=0;i<source.length;i+=8){
            if(vertices==CAPACITY){flush();textures=1;handles[0]=texture;slot=0;}
            int o=vertices++*STRIDE;
            if(htmlVertexCopies){data[o]=source[i];data[o+1]=source[i+1];data[o+2]=source[i+2];}
            else System.arraycopy(source,i,data,o,3);
            float u=source[i+3],v=source[i+4];data[o+3]=u1+u*(u2-u1);data[o+4]=v2+v*(v1-v2);
            if(htmlVertexCopies){data[o+5]=source[i+5];data[o+6]=source[i+6];data[o+7]=source[i+7];}
            else System.arraycopy(source,i+5,data,o+5,3);
            data[o+8]=r;data[o+9]=g;data[o+10]=b;data[o+11]=a;
            data[o+12]=slot;data[o+13]=(surface.approximate?2:1)*(supported&&surface.receiveLight?1:-1);
            data[o+14]=surface.twoSided?1:0;data[o+15]=shadow?surface.alphaThreshold:blendMode==7?.5f:blendMode==0?-2:blendMode==2?-3:-1;
            if(quad!=null){data[o+16]=quad[0]+u*(quad[6]-quad[0])+v*(quad[2]-quad[0]);
                data[o+17]=quad[1]+u*(quad[7]-quad[1])+v*(quad[3]-quad[1]);}
            else{data[o+16]=LightingCoordinates.screenX(source[i],source[i+1])+offsetX;
                data[o+17]=LightingCoordinates.screenY(source[i],source[i+1],source[i+2])+offsetY;}
        }
    }
    void flush(){
        if(vertices==0)return;
        shader.bind();
        bindTextures(handles,textures);
        mesh.setVertices(data,0,vertices*STRIDE);mesh.render(shader,GL20.GL_TRIANGLES,0,vertices);
        if(retainReplay){replayVertices=vertices;replayTextures=textures;System.arraycopy(handles,0,replayHandles,0,textures);}
        if(stats!=null){stats.drawCalls++;stats.flushes++;stats.textureBinds+=4;}
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
    void abort(){vertices=textures=replayVertices=replayTextures=0;blend=-1;retainReplay=false;}
    void dispose(){mesh.dispose();}
}
