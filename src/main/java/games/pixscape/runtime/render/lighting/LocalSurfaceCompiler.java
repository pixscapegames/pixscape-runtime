package games.pixscape.runtime.render.lighting;

/** Cold immutable local expansion; placements and atlas bindings remain separate. */
public final class LocalSurfaceCompiler {
    private LocalSurfaceCompiler(){}
    public static LightingSurface compile(LocalSurfaceDescription d,float x,float y,float z) {
        d.validate();float[] vertices=new float[d.triangles.length*8];
        for(int i=0;i<d.triangles.length;i++){
            int index=d.triangles[i],o=i*8;
            vertices[o]=d.xyz[index*3]+x;vertices[o+1]=d.xyz[index*3+1]+y;vertices[o+2]=d.xyz[index*3+2]+z;
            vertices[o+3]=d.uv[index*2];vertices[o+4]=d.uv[index*2+1];
            System.arraycopy(d.normals,index*3,vertices,o+5,3);
        }
        LightingSurface s=new LightingSurface(MapLightingGeometry.completeLocal(vertices));s.receiveLight=d.receiveLight;
        s.shadowCaster=d.shadowCaster;s.twoSided=d.twoSided;return s;
    }
}
