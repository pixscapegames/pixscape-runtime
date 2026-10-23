package games.pixscape.runtime.render.lighting;

/**
 * Experimental cold compiler for one connected quarter-wall support. Not a persisted
 * asset format or an automatic tile rule. Callers own placement, bindings and replacement
 * of the matching rectangular caster portions. Ends stay open to the connected walls.
 */
public final class RoundedWallSupport {
    public final float[] outerXY, innerXY;
    public final LightingSurface receiver, texturedCaster, bottomCaster;
    public final int segments;

    /** Intrinsic tangent directions; shared by occurrences of one published asset/profile version. */
    public static final class Profile {
        final int segments; final double[] cos,sin; final double denominator;
        final float[] explicit;
        public Profile(int segments){
            if(segments<3||segments>8)throw new IllegalArgumentException("Quarter wall requires 3..8 facets");
            this.segments=segments;explicit=null;cos=new double[segments];sin=new double[segments];
            double step=Math.PI*.5/(segments-1);denominator=Math.cos(step*.5);
            for(int i=1;i<segments;i++){double angle=Math.PI+(i-.5)*step;cos[i]=Math.cos(angle);sin[i]=Math.sin(angle);}
        }
        public Profile(RoundedWallDescription description) {
            description.validate();
            if(description.profileXY==null) {
                Profile initial=new Profile(6);segments=initial.segments;cos=initial.cos;sin=initial.sin;
                denominator=initial.denominator;explicit=null;
            } else {
                explicit=LocalSurfaceDescription.copy(description.profileXY);segments=explicit.length/2-1;
                cos=null;sin=null;denominator=1;
            }
        }
        /** Detached editable coordinates; reading does not convert authored legacy data. */
        public float[] vertices() {return explicit==null?profile(0,0,1,this):LocalSurfaceDescription.copy(explicit);}
    }

    /** Tangent end facets exactly match -X and -Y walls; no image analysis is performed. */
    public RoundedWallSupport(float cx, float cy, float radius, float thickness,
                              float base, float height, int segments, float[] quad) {
        this(cx,cy,radius,thickness,base,height,new Profile(segments),quad);
    }
    public RoundedWallSupport(float cx,float cy,float radius,float thickness,float base,float height,Profile profile,float[] quad){
        int segments=profile.segments;
        if (!finite(cx) || !finite(cy) || !finite(radius) || !finite(thickness)
                || !finite(base) || !finite(height) || radius <= 0 || thickness <= 0
                || thickness >= radius || height <= 0 || segments < 3 || segments > 8
                || quad == null || quad.length != 8)
            throw new IllegalArgumentException("Quarter wall requires finite dimensions, 3..8 facets and native quad");
        for (int i=0;i<quad.length;i++) if (!finite(quad[i]))
            throw new IllegalArgumentException("Quarter wall quad must be finite");
        float ax=quad[6]-quad[0],ay=quad[7]-quad[1],bx=quad[2]-quad[0],by=quad[3]-quad[1];
        if (Math.abs(ax*by-ay*bx)<.000001f)
            throw new IllegalArgumentException("Quarter wall requires a nondegenerate native quad");
        if(ax<=0 || by<=0 || ay!=0 || bx!=0 || quad[4]!=quad[6] || quad[5]!=quad[3])
            throw new IllegalArgumentException("Experimental quarter wall requires an untransformed native quad");
        this.segments=segments;
        outerXY=profile(cx,cy,radius,profile);
        innerXY=profile.explicit==null?profile(cx,cy,radius-thickness,profile):offset(outerXY,thickness);
        float[] front=new float[segments*48],back=new float[segments*48];
        float[] top=new float[segments*48],bottom=new float[segments*48];
        for(int i=0;i<segments;i++) {
            int j=i+1;float x0=outerXY[i*2],y0=outerXY[i*2+1],x1=outerXY[j*2],y1=outerXY[j*2+1];
            float ix0=innerXY[i*2],iy0=innerXY[i*2+1],ix1=innerXY[j*2],iy1=innerXY[j*2+1];
            float dx=x1-x0,dy=y1-y0,len=(float)Math.sqrt(dx*dx+dy*dy),nx=-dy/len,ny=dx/len;
            if(nx*(x0-cx)+ny*(y0-cy)<0){nx=-nx;ny=-ny;}
            float[][] f={{x0,y0,base},{x1,y1,base},{x1,y1,base+height},{x0,y0,base+height}};
            float[][] b={{ix0,iy0,base},{ix1,iy1,base},{ix1,iy1,base+height},{ix0,iy0,base+height}};
            float[][] t={{x0,y0,base+height},{x1,y1,base+height},{ix1,iy1,base+height},{ix0,iy0,base+height}};
            float[][] d={{x0,y0,base},{x1,y1,base},{ix1,iy1,base},{ix0,iy0,base}};
            emit(front,i*48,f,f,nx,ny,0,quad);
            // Material UV is transported through thickness, not projected from the hidden back.
            emit(back,i*48,b,f,-nx,-ny,0,quad);
            emit(top,i*48,t,t,0,0,1,quad);
            emit(bottom,i*48,d,d,0,0,-1,quad);
        }
        validateCoverage(front,top,outerXY,innerXY,quad);
        // The silhouette is x-monotone. Complete only its upper and lower strips,
        // rather than successively subtracting every triangle and fragmenting empty UV space.
        receiver=new LightingSurface(join(front,top,coverage(outerXY,innerXY,base,height,quad)));
        receiver.receiverVertices=MapLightingGeometry.completeReceiverVertices(receiver.vertices,quad,base);
        receiver.forwardTopology=ForwardTopology.compile(receiver,quad);
        float[] physical=receiver.receiverVertices==null?receiver.vertices:receiver.receiverVertices;
        receiver.curveNormals=new float[physical.length/8*3];
        for(int i=0;i<physical.length;i+=8){
            int o=i/8*3;
            if(Math.abs(physical[i+7])>.5f){receiver.curveNormals[o+2]=physical[i+7];continue;}
            if(profile.explicit!=null) {smoothNormal(outerXY,physical[i],physical[i+1],receiver.curveNormals,o);continue;}
            float x=physical[i]-cx,y=physical[i+1]-cy;
            // Exact terminal orientations, including duplicated vertices on the shared vertical edges.
            if(x>=0){receiver.curveNormals[o+1]=-1;continue;}
            if(y>=0){receiver.curveNormals[o]=-1;continue;}
            float length=(float)Math.sqrt(x*x+y*y);
            receiver.curveNormals[o]=x/length;receiver.curveNormals[o+1]=y/length;
        }
        receiver.approximate=true;
        ForwardTopology.prepareImmutableAttributes(receiver);
        texturedCaster=new LightingSurface(join(front,back,top));
        texturedCaster.shadowCaster=true;texturedCaster.receiveLight=false;
        receiver.shadowCaster=true;receiver.casterSurface=texturedCaster;
        // This POC has no base opening. The bottom is solid; callers bind the existing white texture.
        bottomCaster=new LightingSurface(bottom);bottomCaster.shadowCaster=true;bottomCaster.receiveLight=false;
    }

    /** Admission proof for this restricted strip construction, not an area-only heuristic.
     * Both boundaries must be monotone, span the native width, and all physical faces
     * must remain inside the image without folded triangles. The shared strip edges
     * then partition the image from v=0 to v=1 without gaps or overlaps. */
    private static void validateCoverage(float[] front,float[] top,float[] outer,float[] inner,float[] q){
        float width=q[6]-q[0];
        float left=(outer[0]-outer[1]-q[0])/width;
        int last=outer.length-2;
        float right=(outer[last]-outer[last+1]-q[0])/width;
        if(Math.abs(left)>1e-6f||Math.abs(right-1)>1e-6f)
            throw new IllegalArgumentException("Rounded support must span the native image width");
        for(float[] boundary:new float[][]{outer,inner})for(int i=2;i<boundary.length;i+=2)
            if(boundary[i]-boundary[i+1]<=boundary[i-2]-boundary[i-1])
                throw new IllegalArgumentException("Rounded support projection must be strictly monotone");
        for(float[] face:new float[][]{front,top}){
            for(int i=0;i<face.length;i+=8)
                if(face[i+3]<0||face[i+3]>1||face[i+4]<0||face[i+4]>1)
                    throw new IllegalArgumentException("Rounded support UV lies outside its native image");
            for(int i=0;i<face.length;i+=24){
                double ax=(double)face[i+11]-face[i+3],ay=(double)face[i+12]-face[i+4];
                double bx=(double)face[i+19]-face[i+3],by=(double)face[i+20]-face[i+4];
                if(ax*by-ay*bx<0)throw new IllegalArgumentException("Rounded support has a folded UV face");
            }
        }
    }

    private static boolean finite(float f){return !Float.isNaN(f)&&!Float.isInfinite(f);}
    private static float[] profile(float cx,float cy,float r,Profile profile){
        int n=profile.segments;
        float[] result=new float[(n+1)*2];
        if(profile.explicit!=null) {
            for(int i=0;i<result.length;i+=2){result[i]=cx+r*profile.explicit[i];result[i+1]=cy+r*profile.explicit[i+1];}
            return result;
        }
        result[0]=cx-r;result[1]=cy;result[n*2]=cx;result[n*2+1]=cy-r;
        for(int i=1;i<n;i++){
            double distance=r/profile.denominator;
            result[i*2]=(float)(cx+distance*profile.cos[i]);
            result[i*2+1]=(float)(cy+distance*profile.sin[i]);
        }
        return result;
    }
    private static float[] offset(float[] p,float thickness) {
        int n=p.length/2;float[] q=new float[p.length];
        for(int v=0;v<n;v++) {
            int a=Math.max(0,v-1)*2,b=Math.min(n-2,v)*2;
            double ax=p[a+2]-p[a],ay=p[a+3]-p[a+1],al=Math.sqrt(ax*ax+ay*ay);
            double bx=p[b+2]-p[b],by=p[b+3]-p[b+1],bl=Math.sqrt(bx*bx+by*by);
            double nx=-ay/al,ny=ax/al,mx=-by/bl,my=bx/bl,den=1+nx*mx+ny*my;
            if(den<1e-6)throw new IllegalArgumentException("Invalid corner thickness offset");
            q[v*2]=(float)(p[v*2]+thickness*(nx+mx)/den);
            q[v*2+1]=(float)(p[v*2+1]+thickness*(ny+my)/den);
        }
        for(int i=2;i<q.length;i+=2) {
            double dx=q[i]-q[i-2],dy=q[i+1]-q[i-1];
            if(dx*(p[i]-p[i-2])+dy*(p[i+1]-p[i-1])<=1e-8 || dx<-.00001 || dy>.00001)
                throw new IllegalArgumentException("Wall thickness collapses or reverses a corner segment");
        }
        return q;
    }
    private static void smoothNormal(float[] p,float x,float y,float[] out,int o) {
        // All facade vertices are on the compiled outer strip (including its UV-completion splits).
        int n=p.length/2;float sx=x-y;
        if(sx<=p[0]-p[1]){out[o]=-1;out[o+1]=0;return;}
        if(sx>=p[p.length-2]-p[p.length-1]){out[o]=0;out[o+1]=-1;return;}
        int segment=0;while(segment<n-2 && sx>p[(segment+1)*2]-p[(segment+1)*2+1])segment++;
        float u0=p[segment*2]-p[segment*2+1],u1=p[segment*2+2]-p[segment*2+3];
        float t=Math.max(0,Math.min(1,(sx-u0)/(u1-u0)));
        double a=normalAngle(p,segment),b=normalAngle(p,segment+1),angle=a+(b-a)*t;
        out[o]=(float)Math.cos(angle);out[o+1]=(float)Math.sin(angle);
    }
    private static double normalAngle(float[] p,int v) {
        int n=p.length/2;if(v==0)return Math.PI;if(v==n-1)return Math.PI*1.5;
        double a=Math.atan2(-(p[v*2]-p[v*2-2]),p[v*2+1]-p[v*2-1]);
        double b=Math.atan2(-(p[v*2+2]-p[v*2]),p[v*2+3]-p[v*2+1]);
        if(a<0)a+=Math.PI*2;if(b<0)b+=Math.PI*2;
        return (a+b)*.5;
    }
    private static void emit(float[] out,int offset,float[][] xyz,float[][] material,
                             float nx,float ny,float nz,float[] q){
        int[] corners={0,1,2,0,2,3};
        float ax=q[6]-q[0],ay=q[7]-q[1],bx=q[2]-q[0],by=q[3]-q[1],det=ax*by-ay*bx;
        for(int k=0;k<6;k++){
            int c=corners[k],o=offset+k*8;float[] p=xyz[c],m=material[c];
            out[o]=p[0];out[o+1]=p[1];out[o+2]=p[2];
            float sx=LightingCoordinates.screenX(m[0],m[1])-q[0],sy=LightingCoordinates.screenY(m[0],m[1],m[2])-q[1];
            out[o+3]=(sx*by-sy*bx)/det;out[o+4]=(ax*sy-ay*sx)/det;
            out[o+5]=nx;out[o+6]=ny;out[o+7]=nz;
        }
    }
    private static float[] join(float[]... parts){
        int n=0;for(int i=0;i<parts.length;i++)n+=parts[i].length;
        float[] result=new float[n];int at=0;
        for(int i=0;i<parts.length;i++){System.arraycopy(parts[i],0,result,at,parts[i].length);at+=parts[i].length;}
        return result;
    }
    private static float[] coverage(float[] outer,float[] inner,float base,float height,float[] q){
        int n=outer.length/2-1;float[] result=new float[(n+n+2)*48];int at=0;
        for(int i=0;i<n;i++){
            float u0=(outer[i*2]-outer[i*2+1]-q[0])/(q[6]-q[0]);
            float u1=(outer[(i+1)*2]-outer[(i+1)*2+1]-q[0])/(q[6]-q[0]);
            float v0=((outer[i*2]+outer[i*2+1])*.5f+base-q[1])/(q[3]-q[1]);
            float v1=((outer[(i+1)*2]+outer[(i+1)*2+1])*.5f+base-q[1])/(q[3]-q[1]);
            empty(result,at,u0,0,u1,0,u1,v1,u0,v0);at+=48;
        }
        float[] upper=new float[(n+3)*2];upper[0]=outer[0];upper[1]=outer[1];
        System.arraycopy(inner,0,upper,2,inner.length);upper[upper.length-2]=outer[outer.length-2];upper[upper.length-1]=outer[outer.length-1];
        for(int i=0;i<n+2;i++){
            float u0=(upper[i*2]-upper[i*2+1]-q[0])/(q[6]-q[0]);
            float u1=(upper[(i+1)*2]-upper[(i+1)*2+1]-q[0])/(q[6]-q[0]);
            float v0=((upper[i*2]+upper[i*2+1])*.5f+base+height-q[1])/(q[3]-q[1]);
            float v1=((upper[(i+1)*2]+upper[(i+1)*2+1])*.5f+base+height-q[1])/(q[3]-q[1]);
            empty(result,at,u0,v0,u1,v1,u1,1,u0,1);at+=48;
        }
        return result;
    }
    private static void empty(float[] out,int offset,float... uv){
        int[] corners={0,1,2,0,2,3};
        for(int i=0;i<6;i++){int c=corners[i]*2,o=offset+i*8;out[o+3]=uv[c];out[o+4]=uv[c+1];}
    }
}
