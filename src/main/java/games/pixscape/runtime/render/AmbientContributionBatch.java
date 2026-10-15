package games.pixscape.runtime.render;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.NumberUtils;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.TextureRegistry;

/** Visual-only premultiplied contribution/mask batching. No particle lighting geometry. */
final class AmbientContributionBatch implements Disposable {
    private static final int CAPACITY=2048, STRIDE=10;
    private final int[] handles=new int[4];
    private Mesh mesh;
    private ShaderProgram shader;
    private float[] data;
    private int quads,textures;

    void prepare() {
        if(mesh!=null)return;
        try {
            data=new float[CAPACITY*4*STRIDE];
            mesh=new Mesh(false,CAPACITY*4,CAPACITY*6,
                    new VertexAttribute(VertexAttributes.Usage.Position,2,"a_position"),
                    new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,2,"a_uv"),
                    new VertexAttribute(VertexAttributes.Usage.ColorUnpacked,4,"a_color"),
                    new VertexAttribute(VertexAttributes.Usage.Generic,2,"a_flags"));
            short[] indices=new short[CAPACITY*6];
            for(int n=0;n<CAPACITY;n++){int o=n*6,v=n*4;
                indices[o]=(short)v;indices[o+1]=(short)(v+1);indices[o+2]=(short)(v+2);
                indices[o+3]=(short)(v+2);indices[o+4]=(short)(v+3);indices[o+5]=(short)v;}
            mesh.setIndices(indices);
            String version=Gdx.app.getType()==Application.ApplicationType.Desktop?"#version 330 core\n":
                    "#version 300 es\nprecision highp float;\nprecision highp sampler2D;\n";
            shader=new ShaderProgram(version+
                    "in vec2 a_position,a_uv;in vec4 a_color;in vec2 a_flags;out vec2 uv;out vec4 color;flat out vec2 flags;uniform mat4 u_matrix;"+
                    "void main(){uv=a_uv;color=a_color;flags=a_flags;gl_Position=u_matrix*vec4(a_position,0,1);}",
                    version+"in vec2 uv;in vec4 color;flat in vec2 flags;uniform sampler2D u_tex0,u_tex1,u_tex2,u_tex3;out vec4 result;"+
                    "void main(){vec4 texel;if(flags.x<.5)texel=texture(u_tex0,uv);else if(flags.x<1.5)texel=texture(u_tex1,uv);"+
                    "else if(flags.x<2.5)texel=texture(u_tex2,uv);else texel=texture(u_tex3,uv);float blend=flags.y;"+
                    "if(blend>6.5&&texel.a<.5)discard;vec4 c=texel*color;float alpha=(blend<.5||blend>6.5)?1.:c.a;"+
                    "vec3 rgb=(blend>.5&&blend<1.5||blend>3.5&&blend<4.5)?c.rgb*c.a:c.rgb;"+
                    "if(blend>2.5&&blend<4.5)alpha=0.;result=vec4(rgb,alpha);}");
            if(!shader.isCompiled())throw new IllegalStateException("Ambient contribution shader: "+shader.getLog());
            shader.bind();for(int i=0;i<4;i++)shader.setUniformi("u_tex"+i,i);
        } catch(RuntimeException | Error failure) {dispose();throw failure;}
    }

    void begin(OrthographicCamera camera) {
        if(mesh==null)throw new IllegalStateException("Prepare ambient contribution resources before submission");
        abort();shader.bind();shader.setUniformMatrix("u_matrix",camera.combined);
        // Managed shader recompilation after a context change resets uniform values.
        shader.setUniformi("u_tex0",0);shader.setUniformi("u_tex1",1);
        shader.setUniformi("u_tex2",2);shader.setUniformi("u_tex3",3);bindBlend();
    }
    private static void bindBlend(){
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        Gdx.gl.glBlendFunc(GL20.GL_ONE,GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    void draw(FrameRenderQueue q,int i,boolean contribute,RenderStats stats) {
        if(quads==CAPACITY)flush(stats);
        int handle=q.textureHandle[i],slot=-1;
        for(int t=0;t<textures;t++)if(handles[t]==handle){slot=t;break;}
        if(slot<0){if(textures==4)flush(stats);slot=textures;handles[textures++]=handle;}
        int rgba=NumberUtils.floatToIntColor(q.colorPacked[i]);float keep=contribute?1:0;
        float r=(rgba&255)/255f*keep,g=((rgba>>>8)&255)/255f*keep,b=((rgba>>>16)&255)/255f*keep,a=((rgba>>>24)&255)/255f;
        int o=quads++*4*STRIDE,blend=q.blend[i];
        vertex(o,q.x1[i],q.y1[i],q.u1[i],q.v2[i],r,g,b,a,slot,blend);
        vertex(o+STRIDE,q.x2[i],q.y2[i],q.u1[i],q.v1[i],r,g,b,a,slot,blend);
        vertex(o+2*STRIDE,q.x3[i],q.y3[i],q.u2[i],q.v1[i],r,g,b,a,slot,blend);
        vertex(o+3*STRIDE,q.x4[i],q.y4[i],q.u2[i],q.v2[i],r,g,b,a,slot,blend);
    }
    private void vertex(int o,float x,float y,float u,float v,float r,float g,float b,float a,int slot,int blend){
        data[o]=x;data[o+1]=y;data[o+2]=u;data[o+3]=v;data[o+4]=r;data[o+5]=g;
        data[o+6]=b;data[o+7]=a;data[o+8]=slot;data[o+9]=blend;
    }
    void flush(RenderStats stats) {
        if(quads==0)return;
        shader.bind();bindBlend();
        for(int t=0;t<4;t++){
            Texture texture=TextureRegistry.getByHandle(handles[t<textures?t:0]);
            if(texture==null)throw new IllegalStateException("Ambient contribution texture is not registered");
            texture.bind(t);
        }
        mesh.setVertices(data,0,quads*4*STRIDE);mesh.render(shader,GL20.GL_TRIANGLES,0,quads*6);
        if(stats!=null){stats.drawCalls++;stats.flushes++;stats.textureBinds+=4;stats.shaderBinds++;}
        abort();
    }
    void abort(){quads=textures=0;}
    @Override public void dispose(){abort();if(mesh!=null)mesh.dispose();if(shader!=null)shader.dispose();mesh=null;shader=null;data=null;}
}
