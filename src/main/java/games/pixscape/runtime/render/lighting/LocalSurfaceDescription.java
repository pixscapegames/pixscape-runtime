package games.pixscape.runtime.render.lighting;

/** Authored replacement geometry. UV coordinates are fractions of the untrimmed image. */
public final class LocalSurfaceDescription {
    public float[] xyz = new float[0];
    public float[] uv = new float[0];
    public float[] normals = new float[0];
    public int[] triangles = new int[0];
    public boolean receiveLight = true;
    public boolean shadowCaster = false;
    public boolean twoSided = false;
    public LocalSurfaceDescription copy() {
        LocalSurfaceDescription c=new LocalSurfaceDescription();c.xyz=copy(xyz);c.uv=copy(uv);
        c.normals=copy(normals);c.triangles=new int[triangles.length];
        System.arraycopy(triangles,0,c.triangles,0,triangles.length);c.receiveLight=receiveLight;
        c.shadowCaster=shadowCaster;c.twoSided=twoSided;return c;
    }
    public static float[] copy(float[] source) {
        float[] result=new float[source.length];System.arraycopy(source,0,result,0,source.length);return result;
    }

    public void validate() {
        int count = xyz.length / 3;
        if (count < 3 || xyz.length != count * 3 || uv.length != count * 2
                || normals.length != count * 3 || triangles.length == 0 || triangles.length % 3 != 0)
            throw new IllegalArgumentException("Surface requires aligned XYZ, UV, normals and triangle indices");
        for (int i = 0; i < xyz.length; i++) finite(xyz[i]);
        for (int i = 0; i < uv.length; i++) finite(uv[i]);
        for (int i = 0; i < uv.length; i++) if(uv[i]<0 || uv[i]>1)
            throw new IllegalArgumentException("Local surface UV must be within the untrimmed image");
        for (int i = 0; i < normals.length; i++) finite(normals[i]);
        for (int i = 0; i < count; i++) {
            float x = normals[i * 3], y = normals[i * 3 + 1], z = normals[i * 3 + 2];
            if (x * x + y * y + z * z < .000001f)
                throw new IllegalArgumentException("Surface normal must be nonzero");
        }
        for (int i = 0; i < triangles.length; i++) if (triangles[i] < 0 || triangles[i] >= count)
            throw new IllegalArgumentException("Surface triangle index outside vertex array");
    }
    private static void finite(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            throw new IllegalArgumentException("Surface values must be finite");
    }
}
