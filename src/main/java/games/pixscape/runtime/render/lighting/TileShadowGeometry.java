package games.pixscape.runtime.render.lighting;

/** Prepared, single open image sheet for an explicitly selected decor tile.
 * No pixel access: the actor's bounded carrier topology pins the contact directrix.
 * The physical support plane and half thickness are supplied by cold volume preparation.
 */
public final class TileShadowGeometry {
    public final float[] calibrated, compensated;
    private final float[] weights;
    public final int vertexCount;
    public final float contactX,contactY,contactZ,normalX,normalY,normalZ,halfThickness,volumeHeight;
    /** Experimental angular window, independent of asset identity and volume thickness. */
    public static final float BLIND_ANGLE_DEGREES=12;
    private static final float BLIND_SINE=(float)Math.sin(Math.toRadians(BLIND_ANGLE_DEGREES));
    public boolean compensationEnabled=true;
    public float lastWeight;
    private final com.badlogic.gdx.utils.ObjectMap<LightingFrame.Light, LightSheet> lightSheets =
            new com.badlogic.gdx.utils.ObjectMap<LightingFrame.Light, LightSheet>();
    private final LightSheet fallback;
    /** Diagnostic counters: no camera or shading-only light change invalidates world vertices. */
    public int transformations;

    private static final class LightSheet {
        final float[] caster, receiver;
        float weight = Float.NaN, receiverWeight=Float.NaN, x,y,z;
        boolean valid, enabled;
        LightSheet(float[] caster) { this.caster=caster; receiver=new float[caster.length]; }
    }

    public TileShadowGeometry(float[] quad,float cx,float cy,float cz,float nx,float ny,float nz,float halfThickness,float volumeHeight) {
        if(quad==null || quad.length!=8 || !finite(cx)||!finite(cy)||!finite(cz)||!finite(halfThickness)||halfThickness<0||!finite(volumeHeight)||volumeHeight<0)
            throw new IllegalArgumentException("Tile sheet needs a finite image quad, contact and nonnegative half thickness");
        float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(!finite(length)||length<.000001f)throw new IllegalArgumentException("Tile sheet has no support normal");
        nx/=length;ny/=length;nz/=length;
        float denominator=nz-nx-ny;
        if(Math.abs(denominator)<.00001f)throw new IllegalArgumentException("Tile support is edge-on to the image projection");
        contactX=cx;contactY=cy;contactZ=cz;normalX=nx;normalY=ny;normalZ=nz;this.halfThickness=halfThickness;this.volumeHeight=volumeHeight;
        for(float v:quad)if(!finite(v))throw new IllegalArgumentException("Nonfinite tile image placement");
        float[] cornerZ=new float[4];
        for(int i=0;i<4;i++)cornerZ[i]=height(quad[i*2],quad[i*2+1],cx,cy,cz,nx,ny,nz,denominator);
        // A non-affine image cannot share a single physical contact line in this bounded POC.
        if(Math.abs(cornerZ[0]+cornerZ[2]-cornerZ[1]-cornerZ[3])>.01f)
            throw new IllegalArgumentException("Tile sheet requires an affine placed image");
        float a=cornerZ[3]-cornerZ[0],b=cornerZ[1]-cornerZ[0],c=cornerZ[0]-cz;
        boolean horizontal=Math.abs(a)+Math.abs(b)<.00001f;
        float[] source;int count;
        if(horizontal){
            count=6;source=new float[48];
            for(int i=0;i<6;i++){int k=SpriteSurfaceSupport.corner(i);source[i*8+3]=k>=2?1:0;source[i*8+4]=k==1||k==2?1:0;}
        }else{
            float[] intersections=new float[8];int contacts=0;
            float[] us={0,0,1,1},vs={0,1,1,0};
            for(int i=0;i<4;i++){
                int j=(i+1)%4;float d=a*us[i]+b*vs[i]+c,e=a*us[j]+b*vs[j]+c;
                if(Math.abs(d)<.0001f)contacts=point(intersections,contacts,us[i],vs[i]);
                if(d*e<0){float t=d/(d-e);contacts=point(intersections,contacts,us[i]+t*(us[j]-us[i]),vs[i]+t*(vs[j]-vs[i]));}
            }
            if(contacts!=2)throw new IllegalArgumentException("Volume contact does not define one line through the image");
            if(volumeHeight<=0)throw new IllegalArgumentException("Vertical tile support needs a positive volume height");
            source=topology(a,b,c,volumeHeight);count=source.length/8;
        }
        vertexCount=count;
        calibrated=new float[vertexCount*8];compensated=new float[calibrated.length];weights=new float[vertexCount];
        fallback=new LightSheet(compensated);
        boolean vertical=Math.abs(a)>Math.abs(b);
        for(int i=0;i<vertexCount;i++){
            int o=i*8;float u=source[o+3],v=source[o+4];
            float sx=ActorShadowImageMapping.x(quad,u,v),sy=ActorShadowImageMapping.y(quad,u,v);
            float z=horizontal?cz:height(sx,sy,cx,cy,cz,nx,ny,nz,denominator);
            boolean contact=Math.abs(z-cz)<.002f;if(contact)z=cz;
            calibrated[o]=LightingCoordinates.worldX(sx,sy,z);calibrated[o+1]=LightingCoordinates.worldY(sx,sy,z);calibrated[o+2]=z;
            calibrated[o+3]=u;calibrated[o+4]=v;calibrated[o+5]=nx;calibrated[o+6]=ny;calibrated[o+7]=nz;
            if(!horizontal&&!contact){
                float h=volumeHeight==0?0:Math.abs(z-cz)/volumeHeight;
                weights[i]=(1-Math.abs(2*(vertical?v:u)-1))*Math.max(0,Math.min(1,h));
            }
        }
    }
    /** Fixed physical-height knots keep a padded source canvas from weakening the actual object.
     * These cuts only carry a piecewise linear transform, independently of the alpha complexity.
     */
    private static float[] topology(float a,float b,float c,float h){
        java.util.ArrayList<Float> out=new java.util.ArrayList<Float>();
        boolean vertical=Math.abs(a)>Math.abs(b);float[] p=new float[24],q=new float[24];
        for(int column=0;column<2;column++)for(int band=0;band<4;band++){
            float[] rectangle={0,0,0,0,1,0,1,1,0,1,0,0};System.arraycopy(rectangle,0,p,0,12);
            int sign=column==0?-1:1;
            int n=ActorShadowGeometry.clip(p,4,q,vertical?0:sign,vertical?sign:0,-sign*.5f,false);
            if(band!=0){float lower=band==1?-h:band==2?0:h;n=ActorShadowGeometry.clip(q,n,p,a,b,c-lower,false);}
            else{System.arraycopy(q,0,p,0,n*3);}
            if(band!=3){float upper=band==0?-h:band==1?0:h;n=ActorShadowGeometry.clip(p,n,q,-a,-b,upper-c,false);}
            else{System.arraycopy(p,0,q,0,n*3);}
            for(int k=1;k<n-1;k++){
                float area=(q[k*3]-q[0])*(q[(k+1)*3+1]-q[1])-(q[(k+1)*3]-q[0])*(q[k*3+1]-q[1]);if(Math.abs(area)<.00000001f)continue;
                for(int t=0;t<3;t++){int at=t==0?0:t==1?k*3:(k+1)*3;for(int d=0;d<8;d++)out.add(d==3?q[at]:d==4?q[at+1]:0f);}
            }
        }
        if(out.size()>96*8)throw new IllegalArgumentException("Tile transform exceeded its constant carrier budget");
        float[] result=new float[out.size()];for(int i=0;i<result.length;i++)result[i]=out.get(i);return result;
    }
    private static float height(float sx,float sy,float cx,float cy,float cz,float nx,float ny,float nz,float denominator){
        return (float)(((double)nx*cx+(double)ny*cy+(double)nz*cz-(nx+ny)*(double)sy-(nx-ny)*(double)sx*.5)/denominator);
    }
    private static int point(float[] points,int count,float u,float v){
        for(int i=0;i<count;i++)if(Math.abs(points[i*2]-u)+Math.abs(points[i*2+1]-v)<.00001f)return count;
        if(count==4)throw new IllegalArgumentException("Ambiguous tile contact");points[count*2]=u;points[count*2+1]=v;return count+1;
    }
    private static boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
    float displacementWeight(int vertex){return weights[vertex];}
    public float weight(LightingFrame.Light light){
        float x=light.x-contactX,y=light.y-contactY,z=light.z-(contactZ+volumeHeight*.5f);
        float length=(float)Math.sqrt(x*x+y*y+z*z);if(length<.00001f)return 0;
        float cosine=Math.abs(x*normalX+y*normalY+z*normalZ)/length;
        float t=Math.max(0,Math.min(1,1-cosine/BLIND_SINE));return t*t*(3-2*t);
    }
    /** Only light coefficients and the reserved carrier vertices change; alpha stays in its source page. */
    public float[] forLight(LightingFrame.Light light){
        LightSheet sheet=lightSheets.get(light);
        if(sheet==null)sheet=fallback;
        update(light,sheet);
        return sheet.weight==0||halfThickness==0?calibrated:sheet.caster;
    }
    /** Cold registration, before READY; light identities are the frame publication's stable objects. */
    public void prepareLight(LightingFrame.Light light) {
        if(!lightSheets.containsKey(light))lightSheets.put(light,new LightSheet(new float[calibrated.length]));
        receiverForLight(light);
    }
    /** Same XYZ, UV and triangle interpolation as the caster; actual facet normals for PCF/ramp.
     * Screen placement still comes exclusively from the original image quad in LightingBatch.
     */
    public float[] receiverForLight(LightingFrame.Light light) {
        LightSheet sheet=lightSheets.get(light);
        if(sheet==null)sheet=fallback;
        update(light,sheet);
        if(sheet.receiverWeight!=sheet.weight)prepareReceiver(sheet);
        return sheet.receiver;
    }
    private void update(LightingFrame.Light light,LightSheet sheet) {
        if(sheet.valid && sheet.x==light.x && sheet.y==light.y && sheet.z==light.z && sheet.enabled==compensationEnabled){lastWeight=sheet.weight;return;}
        sheet.valid=true;sheet.x=light.x;sheet.y=light.y;sheet.z=light.z;sheet.enabled=compensationEnabled;
        lastWeight=compensationEnabled?weight(light):0;
        if(sheet.weight==lastWeight)return;
        sheet.weight=lastWeight;transformations++;
        for(int i=0;i<vertexCount;i++){
            int o=i*8;float d=halfThickness*lastWeight*weights[i];
            for(int k=0;k<8;k++)sheet.caster[o+k]=calibrated[o+k];
            sheet.caster[o]+=normalX*d;sheet.caster[o+1]+=normalY*d;sheet.caster[o+2]+=normalZ*d;
        }
    }
    private void prepareReceiver(LightSheet sheet) {
        sheet.receiverWeight=sheet.weight;
        float[] source=sheet.weight==0||halfThickness==0?calibrated:sheet.caster;
        System.arraycopy(source,0,sheet.receiver,0,source.length);
        for(int o=0;o<source.length;o+=24){
            float ax=source[o+8]-source[o],ay=source[o+9]-source[o+1],az=source[o+10]-source[o+2];
            float bx=source[o+16]-source[o],by=source[o+17]-source[o+1],bz=source[o+18]-source[o+2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<.000001f){nx=normalX;ny=normalY;nz=normalZ;}
            else{float sign=nx*normalX+ny*normalY+nz*normalZ<0?-1:1;nx*=sign/length;ny*=sign/length;nz*=sign/length;}
            for(int j=o;j<o+24;j+=8){sheet.receiver[j+5]=nx;sheet.receiver[j+6]=ny;sheet.receiver[j+7]=nz;}
        }
    }
    /** Expand the prepared static bounds for every permitted light, before the spatial index publishes. */
    public void bounds(LightingFrame.Caster caster){
        float dx=Math.abs(normalX*halfThickness),dy=Math.abs(normalY*halfThickness),dz=Math.abs(normalZ*halfThickness);
        caster.minX-=dx;caster.maxX+=dx;caster.minY-=dy;caster.maxY+=dy;caster.minZ-=dz;caster.maxZ+=dz;
    }
}
