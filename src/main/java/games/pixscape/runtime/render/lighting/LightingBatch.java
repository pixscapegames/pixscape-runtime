package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.Gdx;
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
    private final Mesh mesh;
    private int vertices,textures,blend=-1;
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
    void begin(ShaderProgram shader,RenderStats stats){this.shader=shader;this.stats=stats;vertices=textures=0;blend=-1;}
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
            System.arraycopy(source,i,data,o,3);
            float u=source[i+3],v=source[i+4];data[o+3]=u1+u*(u2-u1);data[o+4]=v2+v*(v1-v2);
            System.arraycopy(source,i+5,data,o+5,3);
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
        for(int i=0;i<textures;i++){Texture t=TextureRegistry.getByHandle(handles[i]);
            if(t==null)throw new IllegalStateException("Lighting texture handle is not registered: "+handles[i]);t.bind(i);}
        // All active sampler uniforms need complete, non-attachment textures on WebGL.
        // A preceding accumulation leaves the scratch attachment bound on unit 1.
        Texture first=TextureRegistry.getByHandle(handles[0]);
        for(int i=textures;i<4;i++)first.bind(i);
        mesh.setVertices(data,0,vertices*STRIDE);mesh.render(shader,GL20.GL_TRIANGLES,0,vertices);
        if(stats!=null){stats.drawCalls++;stats.flushes++;stats.textureBinds+=4;}
        vertices=0;textures=0;
    }
    void abort(){vertices=0;textures=0;blend=-1;}
    void dispose(){mesh.dispose();}
}
