package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;

/** One HDR world attachment, presented without tone mapping. Owns no atlas resources. */
final class ForwardWorldTarget {
    private FrameBuffer target;
    FrameBuffer diagnosticBuffer(){return target;}
    private Mesh quad;
    private ShaderProgram present;
    private final IntBuffer integers=BufferUtils.newIntBuffer(4);
    private final FloatBuffer zero=BufferUtils.newFloatBuffer(4);
    private final int[] viewport=new int[4],scissorBox=new int[4];
    private int framebuffer,src,dst,srcAlpha,dstAlpha,equation,equationAlpha;
    private int depthFunction,depthWrite;
    private boolean scissor,blend,depth,cull,active;
    int width,height;

    void prepare() {
        if(Gdx.gl30==null)throw new IllegalStateException("Forward lighting requires GL3/GLES3/WebGL2 with blendable RGBA16F");
        int w=readViewport(2),h=readViewport(3);
        if(target!=null && width==w && height==h)return;
        save();
        try {
            if(present==null){
                Gdx.graphics.supportsExtension("EXT_color_buffer_float");Gdx.graphics.supportsExtension("EXT_color_buffer_half_float");
                String version=version();
                present=new ShaderProgram(version+"in vec3 a_position;in vec2 a_texCoord0;out vec2 uv;void main(){uv=a_texCoord0;gl_Position=vec4(a_position,1.);}",
                        version+"in vec2 uv;uniform sampler2D u_world;out vec4 result;void main(){result=texture(u_world,uv);}");
                if(!present.isCompiled())throw new IllegalStateException(present.getLog());
                quad=new Mesh(true,4,6,VertexAttribute.Position(),VertexAttribute.TexCoords(0));
                quad.setVertices(new float[]{-1,-1,0,0,0,-1,1,0,0,1,1,1,0,1,1,1,-1,0,1,0});
                quad.setIndices(new short[]{0,1,2,2,3,0});
            }
            GLFrameBuffer.FrameBufferBuilder b=new GLFrameBuffer.FrameBufferBuilder(w,h);
            b.addFloatAttachment(GL30.GL_RGBA16F,GL20.GL_RGBA,GL20.GL_FLOAT,true);
            FrameBuffer next=b.build();next.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
            if(target!=null)target.dispose();target=next;width=w;height=h;
        } finally {restore();}
    }
    static String version() {return Gdx.app.getType()==Application.ApplicationType.Desktop?"#version 330 core\n":"#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler2DArray;\n";}
    private int read(int name){integers.clear();Gdx.gl.glGetIntegerv(name,integers);return integers.get(0);}
    private int readViewport(int offset){integers.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,integers);return Math.max(1,integers.get(offset));}
    private void save(){
        framebuffer=read(GL20.GL_FRAMEBUFFER_BINDING);
        integers.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,integers);for(int i=0;i<4;i++)viewport[i]=integers.get(i);
        LightingGlState.readScissor(integers,scissorBox);
        scissor=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);blend=Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        depth=Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);cull=Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
        depthFunction=read(GL20.GL_DEPTH_FUNC);depthWrite=LightingGlState.readDepthWriteMask(integers);
        src=read(GL20.GL_BLEND_SRC_RGB);dst=read(GL20.GL_BLEND_DST_RGB);srcAlpha=read(GL20.GL_BLEND_SRC_ALPHA);dstAlpha=read(GL20.GL_BLEND_DST_ALPHA);
        equation=read(GL20.GL_BLEND_EQUATION_RGB);equationAlpha=read(GL20.GL_BLEND_EQUATION_ALPHA);
    }
    void begin(){if(target==null)throw new IllegalStateException("Forward target must be prepared before submission");save();active=true;resume();zero.clear();Gdx.gl30.glClearBufferfv(GL30.GL_COLOR,0,zero);}
    void resume(){Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,target.getFramebufferHandle());Gdx.gl.glViewport(0,0,width,height);Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_CULL_FACE);}
    void present(){
        restoreTarget();Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);Gdx.gl.glBlendFunc(GL20.GL_ONE,GL20.GL_ONE_MINUS_SRC_ALPHA);
        target.getColorBufferTexture().bind(0);present.bind();present.setUniformi("u_world",0);quad.render(present,GL20.GL_TRIANGLES);
    }
    private void restoreTarget(){Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,framebuffer);Gdx.gl.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);Gdx.gl.glScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);enable(GL20.GL_SCISSOR_TEST,scissor);}
    private static void enable(int flag,boolean on){if(on)Gdx.gl.glEnable(flag);else Gdx.gl.glDisable(flag);}
    private void restore(){restoreTarget();Gdx.gl.glBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);Gdx.gl.glBlendEquationSeparate(equation,equationAlpha);enable(GL20.GL_BLEND,blend);enable(GL20.GL_DEPTH_TEST,depth);Gdx.gl.glDepthFunc(depthFunction);Gdx.gl.glDepthMask(depthWrite!=0);enable(GL20.GL_CULL_FACE,cull);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);}
    void end(){if(active){restore();active=false;}}
    long textureBytes(){return target==null?0:(long)width*height*8;}
    void dispose(){end();if(target!=null)target.dispose();if(quad!=null)quad.dispose();if(present!=null)present.dispose();target=null;quad=null;present=null;width=height=0;}
}
