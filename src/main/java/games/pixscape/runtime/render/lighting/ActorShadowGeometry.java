package games.pixscape.runtime.render.lighting;

/** One open alpha sheet. All storage is owned by the prepared actor, never by a light. */
public final class ActorShadowGeometry {
    /** Includes spare capacity for exact boundary degeneracies; no buffer grows during a frame. */
    public static final int VERTICES=48;
    public final float[] calibrated=new float[VERTICES*8];
    public final float[] compensated=new float[VERTICES*8];
    private final float[] displacement=new float[VERTICES];
    private final float[] uv=new float[VERTICES*3],polygonA=new float[24],polygonB=new float[24];
    private float previousA=Float.NaN,previousB=Float.NaN,previousC=Float.NaN;
    private boolean vertical;
    private float heightA,heightB,heightC;
    // Normals of the two original calibrated triangles, before any light deformation.
    private float supportAX,supportAY,supportAZ,supportBX,supportBY,supportBZ;
    private boolean supportValid;
    public int vertexCount;
    public boolean active;
    public float contactX,contactY,contactZ,width,normalX,normalY,thickness;

    public void calibrate(float[] q,ActorShadowProfile profile,float altitude,boolean flipX,boolean flipY) {
        active=profile!=null;
        if(!active)return;
        float lu=flipX?1-profile.leftU:profile.leftU,lv=flipY?1-profile.leftV:profile.leftV;
        float ru=flipX?1-profile.rightU:profile.rightU,rv=flipY?1-profile.rightV:profile.rightV;
        float du=ru-lu,dv=rv-lv;
        // The line is independent of endpoint span. Dominant-axis normalization preserves every
        // original edge-height profile exactly and remains bounded for vertical directors.
        float scale=Math.max(Math.abs(du),Math.abs(dv));
        if(scale<.000001f){active=false;return;} // Defensive fallback for an unvalidated gameplay mutation.
        if(du<0 || du==0 && dv>0)scale=-scale;
        float a=-dv/scale,b=du/scale,c=-(a*lu+b*lv);
        if(a!=previousA || b!=previousB || c!=previousC){
            vertical=Math.abs(a)>Math.abs(b);topology(a,b,c);
            previousA=a;previousB=b;previousC=c;
        }
        thickness=profile.thickness;
        float lx=ActorShadowImageMapping.x(q,lu,lv),ly=ActorShadowImageMapping.y(q,lu,lv);
        float rx=ActorShadowImageMapping.x(q,ru,rv),ry=ActorShadowImageMapping.y(q,ru,rv);
        float dx=LightingCoordinates.worldX(rx,ry,altitude)-LightingCoordinates.worldX(lx,ly,altitude);
        float dy=LightingCoordinates.worldY(rx,ry,altitude)-LightingCoordinates.worldY(lx,ly,altitude);
        if(scale<0){dx=-dx;dy=-dy;}
        float footLength=(float)Math.sqrt(dx*dx+dy*dy);
        normalX=footLength>0?-dy/footLength:1;normalY=footLength>0?dx/footLength:0;
        // Reference width is independent of the authored foot slope.
        float ax=q[6]-q[0],ay=q[7]-q[1],wx=ay+ax*.5f,wy=ay-ax*.5f;
        width=(float)Math.sqrt(wx*wx+wy*wy);
        if(footLength<.000001f)thickness=0;
        contactX=LightingCoordinates.worldX((lx+rx)*.5f,(ly+ry)*.5f,altitude);
        contactY=LightingCoordinates.worldY((lx+rx)*.5f,(ly+ry)*.5f,altitude);contactZ=altitude;
        float bx=q[2]-q[0],by=q[3]-q[1],height=(float)Math.sqrt(bx*bx+by*by);
        heightA=height*a;heightB=height*b;heightC=height*c;
        supportValid=true;
        supportNormal(q[4]-q[2],q[5]-q[3],q[2]-q[0],q[3]-q[1],height*a,height*b,false);
        supportNormal(q[6]-q[0],q[7]-q[1],q[4]-q[6],q[5]-q[7],height*a,height*b,true);
        for(int i=0;i<vertexCount;i++){
            float u=uv[i*3],v=uv[i*3+1];
            vertex(i*8,q,u,v,a,b,c,height,altitude,uv[i*3+2]!=0);
        }
    }
    /** Uncompensated receiver, same original six UV corners and diagonal as the displayed image.
     * The placement anchor remains in Q; the foot directrix alone defines the reference altitude.
     * Light-dependent bowing never enters this buffer. Missing profiles retain the existing sheet.
     */
    public void writeReceiver(float[] output,float[] q){
        if(!active)return;
        for(int i=0;i<6;i++){
            int corner=SpriteSurfaceSupport.corner(i),o=i*8;
            float u=corner>=2?1:0,v=corner==1 || corner==2?1:0;
            float z=contactZ+heightA*u+heightB*v+heightC;
            output[o]=LightingCoordinates.worldX(q[corner*2],q[corner*2+1],z);
            output[o+1]=LightingCoordinates.worldY(q[corner*2],q[corner*2+1],z);output[o+2]=z;
            output[o+3]=u;output[o+4]=v;
            output[o+5]=i<3?supportAX:supportBX;output[o+6]=i<3?supportAY:supportBY;output[o+7]=i<3?supportAZ:supportBZ;
        }
    }
    /** Constant-size clipping, rebuilt only for an explicit profile/flip change, with reserved scratch. */
    private void topology(float a,float b,float c){
        vertexCount=0;
        // Preserve the color quad's original diagonal, including independently moved corners.
        for(int triangle=0;triangle<2;triangle++)for(int column=0;column<2;column++)for(int band=0;band<2;band++){
            polygonA[0]=0;polygonA[1]=0;polygonA[2]=0;
            polygonA[3]=triangle==0?0:1;polygonA[4]=1;polygonA[5]=0;
            polygonA[6]=1;polygonA[7]=triangle==0?1:0;polygonA[8]=0;
            int sign=column==0?-1:1;
            int count=clip(polygonA,3,polygonB,vertical?0:sign,vertical?sign:0,-sign*.5f,false);
            sign=band==0?-1:1;
            count=clip(polygonB,count,polygonA,sign*a,sign*b,sign*c,true);
            for(int k=1;k<count-1;k++){
                float area=(polygonA[k*3]-polygonA[0])*(polygonA[(k+1)*3+1]-polygonA[1])
                        -(polygonA[(k+1)*3]-polygonA[0])*(polygonA[k*3+1]-polygonA[1]);
                if(area==0)continue;
                append(polygonA,0);append(polygonA,k*3);append(polygonA,(k+1)*3);
            }
        }
    }
    private void append(float[] source,int offset){
        int o=vertexCount++*3;uv[o]=source[offset];uv[o+1]=source[offset+1];uv[o+2]=source[offset+2];
    }
    static int clip(float[] input,int count,float[] output,float a,float b,float c,boolean foot){
        int n=0;
        for(int i=0;i<count;i++){
            int p=i*3,q=((i+1)%count)*3;float x=input[p],y=input[p+1],xx=input[q],yy=input[q+1];
            float d=a*x+b*y+c,e=a*xx+b*yy+c;
            if(d>=0){int o=n++*3;output[o]=x;output[o+1]=y;output[o+2]=foot && d==0?1:input[p+2];}
            if((d<0)!=(e<0)){
                float t=d/(d-e);int o=n++*3;float u=x+t*(xx-x);
                output[o]=u;output[o+1]=y+t*(yy-y);output[o+2]=foot?1:0;
            }
        }
        return n;
    }
    private void vertex(int o,float[] q,float u,float v,float a,float b,float c,float height,float altitude,boolean contact) {
        float x=ActorShadowImageMapping.x(q,u,v),y=ActorShadowImageMapping.y(q,u,v);
        float z=contact?altitude:altitude+(a*u+b*v+c)*height;
        calibrated[o]=LightingCoordinates.worldX(x,y,z);calibrated[o+1]=LightingCoordinates.worldY(x,y,z);calibrated[o+2]=z;
        calibrated[o+3]=u;calibrated[o+4]=v;calibrated[o+5]=normalX;calibrated[o+6]=normalY;calibrated[o+7]=0;
        // Use the stable image axis (coefficient magnitude >= 1), never divide by a small slope.
        float coordinate=vertical?u:v,foot=vertical?-(b*v+c)/a:-(a*u+c)/b;
        float h=contact || coordinate==foot?0:coordinate<foot?(foot==0?0:(foot-coordinate)/foot)
                :(foot==1?0:(coordinate-foot)/(1-foot));
        displacement[o/8]=(1-Math.abs(2*(vertical?v:u)-1))*h;
    }
    private void supportNormal(float ux,float uy,float vx,float vy,float uz,float vz,boolean second){
        float x=uy-uz+ux*.5f,y=uy-uz-ux*.5f;
        float xx=vy-vz+vx*.5f,yy=vy-vz-vx*.5f;
        float nx=y*vz-uz*yy,ny=uz*xx-x*vz,nz=x*yy-y*xx;
        float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length<=.000001f){supportValid=false;return;}
        if(second){supportBX=nx/length;supportBY=ny/length;supportBZ=nz/length;}
        else{supportAX=nx/length;supportAY=ny/length;supportAZ=nz/length;}
    }
    /** Maximum depth of the single open alpha sheet. At .5 it equals the full image width.
     * Source-independent depth avoids a second collapse when the light crosses the support.
     * The original height weight pins the entire foot directrix, not just its endpoints.
     */
    public float maximumDisplacement() { return supportValid?2*thickness*width:0; }
    public float amplitude(LightingFrame.Light light) { return maximumDisplacement(); }
    /** Called once per selected actor/light, before replaying the six unchanged shadow views.
     * Every light sees the same bent sheet; only the ordinary perspective projection changes.
     */
    public float[] forLight(LightingFrame.Light light) {
        if(thickness==0)return calibrated;
        float a=amplitude(light);
        if(a==0)return calibrated;
        for(int i=0;i<vertexCount;i++) {
            float d=a*displacement[i];int o=i*8;
            // Explicit copies avoid the temporary JS slice used by GWT's arraycopy emulation.
            for(int k=0;k<8;k++)compensated[o+k]=calibrated[o+k];
            compensated[o]=calibrated[o]+normalX*d;compensated[o+1]=calibrated[o+1]+normalY*d;
        }
        return compensated;
    }
}
