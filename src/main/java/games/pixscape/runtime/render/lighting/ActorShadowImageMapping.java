package games.pixscape.runtime.render.lighting;

/** The rendered BL/TL/TR, BL/TR/BR mapping, shared by caster preparation and canvas handles. */
public final class ActorShadowImageMapping {
    private ActorShadowImageMapping() {}
    public static float x(float[] q,float u,float v) {
        return u<=v?q[0]+(v-u)*(q[2]-q[0])+u*(q[4]-q[0]):q[0]+(u-v)*(q[6]-q[0])+v*(q[4]-q[0]);
    }
    public static float y(float[] q,float u,float v) {
        return u<=v?q[1]+(v-u)*(q[3]-q[1])+u*(q[5]-q[1]):q[1]+(u-v)*(q[7]-q[1])+v*(q[5]-q[1]);
    }
    /** Inverts either triangle; outside drags clamp to the nearest image edge. No slope division. */
    public static boolean inverse(float[] q,float x,float y,float[] uv) {
        boolean valid=false;
        for(int t=0;t<2;t++) {
            int b=t==0?2:4,c=t==0?4:6;
            float bx=q[b]-q[0],by=q[b+1]-q[1],cx=q[c]-q[0],cy=q[c+1]-q[1];
            float den=bx*cy-by*cx,scale=Math.abs(bx*cy)+Math.abs(by*cx);
            if(Math.abs(den)<=Math.max(.000000000001f,scale*.0000001f))continue;
            valid=true;
            float px=x-q[0],py=y-q[1],s=(px*cy-py*cx)/den,r=(bx*py-by*px)/den;
            if(s>=0 && r>=0 && s+r<=1) {
                uv[0]=t==0?r:s+r;uv[1]=t==0?s+r:s;return true;
            }
        }
        if(!valid)return false;
        float best=Float.POSITIVE_INFINITY;
        for(int edge=0;edge<4;edge++) {
            int a=edge*2,b=(edge+1)%4*2;float dx=q[b]-q[a],dy=q[b+1]-q[a+1],den=dx*dx+dy*dy;
            float t=den>0?Math.max(0,Math.min(1,((x-q[a])*dx+(y-q[a+1])*dy)/den)):0;
            float ex=x-q[a]-t*dx,ey=y-q[a+1]-t*dy,d=ex*ex+ey*ey;
            if(d<best){best=d;uv[0]=edge==0?0:edge==1?t:edge==2?1:1-t;uv[1]=edge==0?t:edge==1?1:edge==2?1-t:0;}
        }
        return true;
    }
}
