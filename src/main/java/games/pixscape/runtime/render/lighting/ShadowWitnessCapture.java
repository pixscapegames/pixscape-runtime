package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.FloatBuffer;
import java.io.DataOutputStream;
import java.util.zip.GZIPOutputStream;

/** Explicit one-shot readback, disabled without a diagnostic request. Never a measurement path. */
final class ShadowWitnessCapture {
    private String lastRequest;
    private final com.badlogic.gdx.utils.IntSet captured=new com.badlogic.gdx.utils.IntSet();
    private FloatBuffer values;
    void capture(LightingFrame.Light light,FrameBuffer d1,FrameBuffer d2,FrameBuffer receiver,int resolution,int lightIndex) {
        String request=System.getProperty("pixscape.poc.witnessReadback");
        if(request==null)return;
        if(!request.equals(lastRequest)){captured.clear();lastRequest=request;}
        if(!captured.add(lightIndex))return;
        java.nio.IntBuffer state=BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);int previous=state.get(0);
        try {
            String stem=request+"-index-"+lightIndex+"-light-"+light.x+"-"+light.y+"-"+light.z;
            if(request.endsWith("mode-0")&&light.quality!=0&&d1!=null&&d2!=null){
                read(stem+"-d1.gz",d1,0,GL30.GL_RED,1,light,resolution);
                read(stem+"-id1.gz",d1,1,GL30.GL_RED,1,light,resolution);
                read(stem+"-d2.gz",d2,0,GL30.GL_RED,1,light,resolution);
            }
            if(receiver!=null&&(request.endsWith("mode-5")||request.endsWith("mode-6")||request.endsWith("mode-7")))
                read(stem+"-receiver.gz",receiver,0,GL20.GL_RGBA,4,light,resolution);
        } catch(RuntimeException failure) {
            // A requested diagnostic must not abort the interactive renderer.
            try {Gdx.files.absolute(request+"-FAILED.txt").writeString(failure.toString(),false,"UTF-8");}
            catch(RuntimeException writeFailure){failure.addSuppressed(writeFailure);}
            Gdx.app.error("ShadowWitnessCapture","Readback incomplet",failure);
        } finally {Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,previous);values=null;}
    }
    private void read(String path,FrameBuffer target,int attachment,int format,int channels,LightingFrame.Light l,int resolution){
        int w=target.getWidth(),h=target.getHeight();
        if(values==null||values.capacity()<w*h*channels)values=BufferUtils.newFloatBuffer(w*h*channels);
        values.clear();values.limit(w*h*channels);
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,target.getFramebufferHandle());
        Gdx.gl30.glReadBuffer(GL20.GL_COLOR_ATTACHMENT0+attachment);
        try {
            Gdx.gl.glReadPixels(0,0,w,h,format,GL20.GL_FLOAT,values);
            int error=Gdx.gl.glGetError();if(error!=GL20.GL_NO_ERROR)throw new IllegalStateException("Witness readback GL error "+error);
            try(DataOutputStream out=new DataOutputStream(new java.io.BufferedOutputStream(new GZIPOutputStream(Gdx.files.absolute(path).write(false)),65536))){
                out.writeInt(w);out.writeInt(h);out.writeInt(channels);out.writeInt(resolution);
                out.writeFloat(l.x);out.writeFloat(l.y);out.writeFloat(l.z);out.writeFloat(l.radius);
                for(int i=0;i<w*h*channels;i++)out.writeFloat(values.get(i));
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        } finally {Gdx.gl30.glReadBuffer(GL20.GL_COLOR_ATTACHMENT0);}
    }
}
