package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

/** Draw-ready lighting publication, built before submission; independent of camera caster culling. */
public final class LightingFrame {
    public static final class Light {
        public float x,y,z,r,g,b,intensity,radius,falloff,dx,dy,dz,cosOuter,cosInner;
        public int quality,resolution;
        public boolean cone;
    }
    public static final class Caster {
        public LightingSurface surface;
        public int texture;
        public int tiledRef=-1;
        public float u1,v1,u2,v2;
        public float minX,minY,minZ,maxX,maxY,maxZ;
        public void bounds() {
            minX=minY=minZ=Float.POSITIVE_INFINITY;maxX=maxY=maxZ=Float.NEGATIVE_INFINITY;
            float[] v=surface.vertices;
            for(int i=0;i<v.length;i+=8){if(v[i+5]==0 && v[i+6]==0 && v[i+7]==0)continue;
                minX=Math.min(minX,v[i]);maxX=Math.max(maxX,v[i]);
                minY=Math.min(minY,v[i+1]);maxY=Math.max(maxY,v[i+1]);minZ=Math.min(minZ,v[i+2]);maxZ=Math.max(maxZ,v[i+2]);}
        }
        public boolean affects(Light l,float margin) {
            float x=Math.max(minX,Math.min(maxX,l.x))-l.x,y=Math.max(minY,Math.min(maxY,l.y))-l.y,
                    z=Math.max(minZ,Math.min(maxZ,l.z))-l.z,r=l.radius+margin;
            return x*x+y*y+z*z<=r*r;
        }
    }
    public final Array<Light> lights=new Array<Light>();
    public final Array<Caster> casters=new Array<Caster>();
    public final CasterIndex casterIndex=new CasterIndex();
    public int staticCasterCount;
    public IntMap<LightingSurface> tiles=new IntMap<LightingSurface>();
    public final IntMap<LightingSurface> entities=new IntMap<LightingSurface>();
    /** Off by default: 1 positions, 2 normals, 3 approximated/missing coverage. */
    public int diagnostic;
    public LightingPassTimer timer;
    public long preparationCpuNs;
}
