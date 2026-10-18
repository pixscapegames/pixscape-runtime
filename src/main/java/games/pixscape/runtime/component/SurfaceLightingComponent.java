package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import games.pixscape.runtime.render.lighting.LocalSurfaceDescription;

/** Explicit sprite sheet placement; independent of Spatial actor sorting height. */
public final class SurfaceLightingComponent extends PooledComponent {
    public boolean receiveLight = true;
    public boolean shadowCaster = false;
    public float altitude = 0;
    /** Stable foot in the complete, untrimmed image, bottom-left UV convention. */
    public float anchorU = .5f, anchorV = 0;
    /** Horizontal sheet direction in common XYZ, never turned towards a light. */
    public float directionX = 1, directionY = -1;
    public float alphaThreshold = .5f;
    public LocalSurfaceDescription description;
    /** Optional static-image caster profile; null preserves the existing support. */
    public games.pixscape.runtime.render.lighting.ActorShadowProfile actorShadow;
    /** Per actor, animation asset ID then clip name. Never keyed by frame or atlas page. */
    public com.badlogic.gdx.utils.IntMap<com.badlogic.gdx.utils.ObjectMap<String,games.pixscape.runtime.render.lighting.ActorShadowProfile>> actorShadowClips;
    public games.pixscape.runtime.render.lighting.ActorShadowProfile shadowProfile(int assetId,String clip) {
        if(clip==null)return actorShadow;
        com.badlogic.gdx.utils.ObjectMap<String,games.pixscape.runtime.render.lighting.ActorShadowProfile> clips=actorShadowClips==null?null:actorShadowClips.get(assetId);
        return clips==null?null:clips.get(clip);
    }
    public void copyActorShadowsTo(SurfaceLightingComponent target) {
        target.actorShadow=actorShadow==null?null:actorShadow.copy();target.actorShadowClips=null;
        if(actorShadowClips!=null){
            target.actorShadowClips=new com.badlogic.gdx.utils.IntMap<>();
            for(com.badlogic.gdx.utils.IntMap.Entry<com.badlogic.gdx.utils.ObjectMap<String,games.pixscape.runtime.render.lighting.ActorShadowProfile>> asset:actorShadowClips){
                com.badlogic.gdx.utils.ObjectMap<String,games.pixscape.runtime.render.lighting.ActorShadowProfile> clips=new com.badlogic.gdx.utils.ObjectMap<>();
                for(com.badlogic.gdx.utils.ObjectMap.Entry<String,games.pixscape.runtime.render.lighting.ActorShadowProfile> clip:asset.value)clips.put(clip.key,clip.value.copy());
                target.actorShadowClips.put(asset.key,clips);
            }
        }
    }
    public SurfaceLightingComponent copy() {
        SurfaceLightingComponent c=new SurfaceLightingComponent();c.receiveLight=receiveLight;c.shadowCaster=shadowCaster;
        c.altitude=altitude;c.anchorU=anchorU;c.anchorV=anchorV;c.directionX=directionX;c.directionY=directionY;
        c.alphaThreshold=alphaThreshold;c.description=description==null?null:description.copy();copyActorShadowsTo(c);return c;
    }
    @Override protected void reset() {
        receiveLight = true; shadowCaster = false; altitude = 0;
        anchorU = .5f; anchorV = 0; directionX = 1; directionY = -1;
        alphaThreshold = .5f; description = null;actorShadow=null;actorShadowClips=null;
    }
}
