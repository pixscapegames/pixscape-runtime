package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

/** Cold placement shared by map admission and the detached editor preview. */
public final class RoundedWallPlacement {
    public final int xWall,yWall;
    public final float cx,cy,radius,thickness,base,height;
    public RoundedWallPlacement(TiledMapLayerData map,int gx,int gy,RoundedWallDescription d,Array<SpatialBlockData> owners) {
        d.validate();
        if(map.projection!=TiledProjection.ISO || map.tileWidth!=map.tileHeight*2)
            throw new IllegalArgumentException("Corner profiles require a 2:1 ISO map");
        if(owners==null || owners.size!=2)
            throw new IllegalArgumentException("Corner profile requires exactly two explicitly linked walls");
        SpatialBlockData x=null,y=null;
        float startX=gx+(d.centerGX-d.radiusCells),startY=gy+(d.centerGY-d.radiusCells);
        for(SpatialBlockData b:owners) {
            if(b.x!=startX||b.y!=startY||(!b.shadowCaster&&!b.lightOccluder))
                throw new IllegalArgumentException("Wall anchor or caster flags do not match the corner profile");
            if(b.width>=d.radiusCells&&b.depth>0&&b.depth<d.radiusCells)x=b;
            if(b.depth>=d.radiusCells&&b.width>0&&b.width<d.radiusCells)y=b;
        }
        if(x==null||y==null||x==y||x.depth!=y.width||x.altitude!=y.altitude||x.height!=y.height||x.height<=0)
            throw new IllegalArgumentException("Walls must be perpendicular with equal thickness, altitude and height");
        xWall=x.id;yWall=y.id;
        float[] point=new float[3];LightingCoordinates.mapPoint(map,gx+d.centerGX,gy+d.centerGY,x.altitude,point,0);
        cx=point[0];cy=point[1];radius=d.radiusCells*map.tileHeight;thickness=x.depth*map.tileHeight;base=x.altitude;height=x.height;
    }
    public RoundedWallSupport compile(RoundedWallDescription description,float[] nativeQuad) {
        return new RoundedWallSupport(cx,cy,radius,thickness,base,height,new RoundedWallSupport.Profile(description),nativeQuad);
    }
}
