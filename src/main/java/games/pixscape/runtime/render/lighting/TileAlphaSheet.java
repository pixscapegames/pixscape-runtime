package games.pixscape.runtime.render.lighting;

/** Prepared alpha carrier shared by projection and reception.
 * A single optional scratch array suffices: each light completes shadows and reception before the next one.
 * Construction and part publication are cold; light updates only rewrite the existing vertices.
 */
public final class TileAlphaSheet {
    public static final class Part {
        public final int first, end;
        public final float x,y,z,nx,ny,nz,height,halfThickness;
        public Part(int first,int end,float x,float y,float z,float nx,float ny,float nz,float height,float halfThickness) {
            this.first=first;this.end=end;this.x=x;this.y=y;this.z=z;
            this.nx=nx;this.ny=ny;this.nz=nz;this.height=height;this.halfThickness=halfThickness;
        }
    }
    public final float[] calibrated;
    public final Part[] parts;
    private final float[] inclined;
    private final boolean progressive;
    private final float[] coefficients;
    private float singleCoefficient=Float.NaN;
    private boolean displaced;
    /** Null means the whole image is admitted. Otherwise one bit per triangle preserves partial V2 fallback. */
    private final boolean[] admitted;
    /** Cold V2 face attribution, two floats per triangle: side (1=-X,2=+X,3=-Y,4=+Y) and physical plane.
     * Null/zero keeps the existing receiver contract for unknown, horizontal and mixed supports. */
    private final float[] receiverFaces;
    private float previousX=Float.NaN,previousY,previousZ;
    public int transformations;
    public final float maximumScreenX,maximumScreenY;

    public TileAlphaSheet(float[] calibrated,Part[] parts,TileShadowMode mode) {
        this(calibrated,parts,mode,null);
    }
    public TileAlphaSheet(float[] calibrated,Part[] parts,TileShadowMode mode,boolean[] admitted) {
        this(calibrated,parts,mode,admitted,null);
    }
    TileAlphaSheet(float[] calibrated,Part[] parts,TileShadowMode mode,boolean[] admitted,float[] receiverFaces) {
        if(calibrated==null || calibrated.length%24!=0 || parts==null)
            throw new IllegalArgumentException("Alpha sheet needs complete triangles and prepared support parts");
        this.calibrated=calibrated;this.parts=parts;this.admitted=admitted;this.receiverFaces=receiverFaces;
        if(receiverFaces!=null&&receiverFaces.length!=calibrated.length/12)throw new IllegalArgumentException("One physical face attribution is required per triangle");
        if(receiverFaces!=null)for(int i=0;i<receiverFaces.length;i+=2)if(!finite(receiverFaces[i+1])||receiverFaces[i]<0||receiverFaces[i]>4||receiverFaces[i]!=(int)receiverFaces[i])throw new IllegalArgumentException("Invalid physical receiver face");
        if(admitted!=null&&admitted.length!=calibrated.length/24)throw new IllegalArgumentException("One admission bit is required per triangle");
        for(float v:calibrated)if(!finite(v))throw new IllegalArgumentException("Nonfinite alpha sheet vertex");
        int end=0;
        boolean movable=false;
        for(int i=0;i<parts.length;i++){
            Part p=parts[i];
            if(p.first<end || p.first%24!=0 || p.end%24!=0 || p.end>calibrated.length || p.end<=p.first
                    || !finite(p.x)||!finite(p.y)||!finite(p.z)||!finite(p.nx)||!finite(p.ny)||!finite(p.nz)
                    || !finite(p.height)||!finite(p.halfThickness)||p.height<0 || p.halfThickness<0
                    || Math.abs(p.nx*p.nx+p.ny*p.ny+p.nz*p.nz-1)>.0001f)
                throw new IllegalArgumentException("Alpha support parts must be ordered, disjoint triangle ranges");
            end=p.end;
            movable|=p.height>0&&p.halfThickness>0&&(p.nx!=0||p.ny!=0);
        }
        progressive=mode==TileShadowMode.V3_PROGRESSIVE&&movable;
        inclined=progressive?new float[calibrated.length]:null;
        coefficients=progressive&&parts.length>1?new float[parts.length]:null;
        if(coefficients!=null)for(int i=0;i<coefficients.length;i++)coefficients[i]=Float.NaN;
        float sx=0,sy=0;
        if(progressive)for(Part p:parts)if(p.height>0)for(int i=p.first;i<p.end;i+=8){
            float d=p.halfThickness/p.height*(calibrated[i+2]-p.z);
            sx=Math.max(sx,Math.abs((p.nx-p.ny)*d));sy=Math.max(sy,Math.abs((p.nx+p.ny)*d*.5f));
        }
        maximumScreenX=sx;maximumScreenY=sy;
        if(mode==TileShadowMode.V3_FIXED&&movable){transform(calibrated,false);transformations++;}
    }
    public boolean admits(int floatOffset){return admitted==null||admitted[floatOffset/24];}
    public float receiverSide(int floatOffset){return receiverFaces==null?0:receiverFaces[floatOffset/24*2];}
    public float receiverPlane(int floatOffset){return receiverFaces==null?0:receiverFaces[floatOffset/24*2+1];}
    private static boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
    /** One light is replayed completely before the next; no per-light geometry buffer or identity map. */
    public float[] forLight(LightingFrame.Light light) {
        if(inclined==null)return calibrated;
        if(previousX==light.x && previousY==light.y && previousZ==light.z)return displaced?inclined:calibrated;
        previousX=light.x;previousY=light.y;previousZ=light.z;
        boolean changed=false,nonzero=false;
        for(int i=0;i<parts.length;i++){
            float k=coefficient(parts[i],light);float old=coefficients==null?singleCoefficient:coefficients[i];
            changed|=old!=k;nonzero|=k!=0;
            if(coefficients==null)singleCoefficient=k;else coefficients[i]=k;
        }
        if(!changed)return displaced?inclined:calibrated;
        displaced=nonzero;
        if(!displaced)return calibrated;
        transformations++;
        for(int i=0;i<calibrated.length;i++)inclined[i]=calibrated[i];
        transform(inclined,true);
        return inclined;
    }
    private static float coefficient(Part p,LightingFrame.Light light){
        if(p.height==0||p.halfThickness==0||p.nx==0&&p.ny==0)return 0;
        float x=light.x-p.x,y=light.y-p.y,z=light.z-(p.z+p.height*.5f);
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        float incidence=length<.000001f?1:Math.abs(p.nx*x+p.ny*y+p.nz*z)/length;
        float t=Math.max(0,Math.min(1,1-incidence/.20791169f));
        return p.halfThickness/p.height*t*t*(3-2*t);
    }
    private void transform(float[] target,boolean cached){
        for(int part=0;part<parts.length;part++){
            Part p=parts[part];
            if(p.height==0 || p.halfThickness==0 || p.nx==0 && p.ny==0)continue;
            float k=cached?(coefficients==null?singleCoefficient:coefficients[part]):p.halfThickness/p.height;
            if(k==0)continue;
            // Signed height and one fixed direction form an affine shear, with determinant one.
            float nx=p.nx,ny=p.ny,nz=p.nz-k*(p.nx*p.nx+p.ny*p.ny);
            float n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=n;ny/=n;nz/=n;
            for(int i=p.first;i<p.end;i+=8){
                float d=k*(calibrated[i+2]-p.z);
                target[i]+=p.nx*d;target[i+1]+=p.ny*d;
                target[i+5]=nx;target[i+6]=ny;target[i+7]=nz;
            }
        }
    }
    /** Cold conservative bounds for all coefficients in 0..1, including transparent source padding. */
    public void bounds(LightingFrame.Caster caster) {
        if(!progressive)return;
        for(int j=0;j<parts.length;j++){
            Part p=parts[j];if(p.height==0)continue;
            for(int i=p.first;i<p.end;i+=8){
                float d=p.halfThickness/p.height*(calibrated[i+2]-p.z);
                float x=calibrated[i]+p.nx*d,y=calibrated[i+1]+p.ny*d;
                caster.minX=Math.min(caster.minX,x);caster.maxX=Math.max(caster.maxX,x);
                caster.minY=Math.min(caster.minY,y);caster.maxY=Math.max(caster.maxY,y);
            }
        }
    }
}
