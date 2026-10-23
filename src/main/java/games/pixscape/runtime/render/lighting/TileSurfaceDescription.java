package games.pixscape.runtime.render.lighting;

/** Map-local shared asset description; XYZ origin is the map cell's projected spatial anchor. */
public final class TileSurfaceDescription {
    public int assetId;
    public LocalSurfaceDescription geometry;
    public RoundedWallDescription rounded;
    public void validate(){
        if(assetId<=0 || (geometry==null)==(rounded==null))
            throw new IllegalArgumentException("Tile surface requires a positive asset ID and exactly one geometry description");
        if(geometry!=null)geometry.validate();else rounded.validate();
    }
    public TileSurfaceDescription copy(){TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=assetId;
        d.geometry=geometry==null?null:geometry.copy();d.rounded=rounded==null?null:rounded.copy();return d;}
}
