package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.lighting.*;

/** Publishes source geometry before submission, using complete source domains rather than visible queue. */
public final class LightingGeometrySystem extends BaseSystem {
    public interface ShadowPreparation { void prepare(LightingFrame frame); }
    @com.artemis.annotations.SkipWire private ShadowPreparation shadowPreparation;
    /** Frame-publication resource boundary; never called by render submission. */
    public void setShadowPreparation(ShadowPreparation preparation){shadowPreparation=preparation;}
    private final DynamicEntityRenderState dynamic;
    private final TiledMapRenderState tiled;
    private final LightingFrame frame;
    private final LayerStateSOA layers;
    private ComponentMapper<VisibilityComponent> visibility;
    private ComponentMapper<SurfaceLightingComponent> surfaces;
    private ComponentMapper<PointLightComponent> points;
    private ComponentMapper<ConeLightComponent> cones;
    private ComponentMapper<DimensionsComponent> dimensions;
    private ComponentMapper<AssetRefComponent> assetRefs;
    private ComponentMapper<games.pixscape.runtime.component.TiledAnimationComponent> animations;
    private ComponentMapper<games.pixscape.runtime.component.AnimationComponent> actorAnimations;
    private ComponentMapper<games.pixscape.runtime.component.spatial.SpatialHeightComponent> heights;
    private ComponentMapper<games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent> footprints;
    private ComponentMapper<games.pixscape.runtime.component.TransformComponent> transforms;
    private final float[] rectangleAnchor=new float[3],rectangleUV=new float[2];
    @com.artemis.annotations.SkipWire private games.pixscape.runtime.service.AtlasRuntimeService atlasService;
    @com.artemis.annotations.SkipWire private GameObjectCompositionSystem composition;
    @com.artemis.annotations.SkipWire private games.pixscape.runtime.service.NormalMapAtlas authoringNormals;
    @com.artemis.annotations.SkipWire private Runnable authoringNormalCleanup;
    /** Cold isolated authoring-context publication; ownership ends with this World. */
    public void publishAuthoringNormals(games.pixscape.runtime.service.NormalMapAtlas normals) {
        publishAuthoringNormals(normals,null);
    }
    public void publishAuthoringNormals(games.pixscape.runtime.service.NormalMapAtlas normals,Runnable cleanup) {
        disposeAuthoringNormals();authoringNormals=normals;authoringNormalCleanup=cleanup;
    }
    private void disposeAuthoringNormals(){
        if(authoringNormals!=null)authoringNormals.dispose();authoringNormals=null;
        if(authoringNormalCleanup!=null)authoringNormalCleanup.run();authoringNormalCleanup=null;
    }
    @Override protected void dispose(){disposeAuthoringNormals();}
    @Override protected void initialize(){
        composition=world.getSystem(GameObjectCompositionSystem.class);
        world.getAspectSubscriptionManager().get(com.artemis.Aspect.all()).addSubscriptionListener(
            new com.artemis.EntitySubscription.SubscriptionListener(){
                public void inserted(com.artemis.utils.IntBag entities){}
                public void removed(com.artemis.utils.IntBag entities){
                    for(int i=0;i<entities.size();i++){int e=entities.get(i);
                        frame.entities.remove(e);localDescriptions.remove(e);lights.remove(e);spriteCasters.remove(e);
                        shadowExclusionIdentities.removeEntity(e);}
                    staticRevision=Integer.MIN_VALUE;
                }
            });
    }
    private final IntMap<LocalSurfaceDescription> localDescriptions=new IntMap<LocalSurfaceDescription>();
    private final IntMap<LightingFrame.Light> lights = new IntMap<LightingFrame.Light>();
    private final IntMap<LightingFrame.Caster> spriteCasters = new IntMap<LightingFrame.Caster>();
    private final IntMap<LightingFrame.Caster> staticCasters = new IntMap<LightingFrame.Caster>();
    private final ShadowExclusionIdentities shadowExclusionIdentities = new ShadowExclusionIdentities();
    private final float[] corners = new float[8];
    private int staticRevision=Integer.MIN_VALUE;
    public LightingGeometrySystem(DynamicEntityRenderState dynamic, TiledMapRenderState tiled, FrameRenderQueue queue,LayerStateSOA layers) {
        this.dynamic=dynamic;this.tiled=tiled;frame=queue.lighting;this.layers=layers;
    }
    public LightingGeometrySystem(DynamicEntityRenderState dynamic,TiledMapRenderState tiled,FrameRenderQueue queue,
            LayerStateSOA layers,games.pixscape.runtime.service.AtlasRuntimeService atlasService){
        this(dynamic,tiled,queue,layers);this.atlasService=atlasService;
    }
    /** Cold full-domain admission, including images outside the camera. */
    public void validateForwardAvailability(games.pixscape.runtime.service.AtlasRuntimeService.TextureArrayBundle bundle) {
        ForwardAdmission.validateDomains(dynamic,tiled,bundle);
    }
    /** Cold scene preparation: reserve actor storage without advancing animation or processing World. */
    public void prepareRuntimeAvailability() {
        for(int slot=0;slot<dynamic.activeCount;slot++){
            int e=dynamic.renderSlotToEntityId[slot];if(e<0 || dynamic.light[slot]!=0)continue;
            SurfaceLightingComponent a=surfaces.getSafe(e,null);
            games.pixscape.runtime.component.spatial.SpatialHeightComponent h=heights.getSafe(e,null);
            if(h!=null){if(h.rectangles==null)throw new LightingAdmissionException("Spatial actor "+e+" has no rectangles");h.rectangles.validate();}
            if((a==null || a.description==null) && frame.entities.get(e)==null){
                LightingSurface s=new LightingSurface(new float[48]);
                s.twoSided=true;s.approximate=true;s.forwardTopology=ForwardTopology.NATIVE;frame.entities.put(e,s);
            } else if(a!=null && a.description!=null && localDescriptions.get(e)!=a.description) {
                LightingSurface s=LocalSurfaceCompiler.compile(a.description,0,0,0);
                if(s.receiverVertices!=null){s.localReceiverVertices=s.receiverVertices;s.receiverVertices=LocalSurfaceDescription.copy(s.localReceiverVertices);}
                localDescriptions.put(e,a.description);frame.entities.put(e,s);
            }
            shadowExclusionIdentities.idForActor(e);
            LightingSurface prepared=frame.entities.get(e);
            if(h!=null && prepared!=null && prepared.actorShadowRectangle==null)prepared.actorShadowRectangle=new ActorShadowRectangleCaster();
        }
    }
    @Override protected void processSystem() {
        long started=System.nanoTime();
        frame.lights.clear();frame.tiles=tiled.lightingSurfaces;
        if(staticRevision!=tiled.lightingRevision){
        frame.casters.clear();
        // Registry survives map rebuilds; dynamic identities must never alias new supports.
        int serial=0;
        for (IntMap.Entry<MapLightingGeometry> entry : tiled.lightingMaps) {
            if(!world.getEntityManager().isActive(entry.key)) continue;
            for(int i=0;i<entry.value.casters.size;i++) {
                LightingSurface surface=entry.value.casters.get(i);
                surface.selfShadowExclusionId=surface.roundedIdentity
                        ?shadowExclusionIdentities.idForCorner(entry.key,surface.roundedCellKey):0;
                LightingFrame.Caster c=staticCasters.get(serial);
                if(c==null){c=new LightingFrame.Caster();staticCasters.put(serial,c);}
                serial++;
                if(c.surface!=surface){c.surface=surface;c.bounds();}
                c.tiledRef=-1;c.texture=games.pixscape.runtime.service.TextureRegistry.WHITE_HANDLE;c.u1=c.v1=0;c.u2=c.v2=1;frame.casters.add(c);
            }
        }
        for(IntMap.Entry<LightingSurface> entry:tiled.lightingSurfaces){
            LightingSurface surface=entry.value;
            if(!surface.shadowCaster || !world.getEntityManager().isActive(surface.ownerEntity) || !tiled.enabled[entry.key])continue;
            if(surface.receiverBlockIds!=null){
                surface.receiverExclusionIds=new int[surface.receiverBlockIds.length];
                for(int i=0;i<surface.receiverBlockIds.length;i++)
                    surface.receiverExclusionIds[i]=shadowExclusionIdentities.idFor(surface.ownerEntity,surface.receiverBlockIds[i]);
            }
            int exclusionId=surface.roundedIdentity?shadowExclusionIdentities.idForCorner(surface.ownerEntity,surface.roundedCellKey)
                    :surface.pocSourceBlockId<=0?ShadowExclusionIdentities.INVALID
                    :shadowExclusionIdentities.idFor(surface.ownerEntity,surface.pocSourceBlockId);
            surface.selfShadowExclusionId=exclusionId;
            if(surface.casterSurface!=null)surface.casterSurface.selfShadowExclusionId=exclusionId;
            int contributions=surface.casterContributions==null?1:surface.casterContributions.size;
            for(int contribution=0;contribution<contributions;contribution++){
            LightingFrame.Caster c=staticCasters.get(serial);
            if(c==null){c=new LightingFrame.Caster();staticCasters.put(serial,c);}serial++;
            LightingSurface caster=surface.casterContributions!=null?surface.casterContributions.get(contribution)
                    :surface.casterSurface==null?surface:surface.casterSurface;
            caster.selfShadowExclusionId=caster.roundedIdentity
                    ?shadowExclusionIdentities.idForCorner(surface.ownerEntity,caster.roundedCellKey)
                    :shadowExclusionIdentities.idFor(surface.ownerEntity,caster.pocSourceBlockId);
            if(c.surface!=caster){c.surface=caster;c.bounds();}
            int ref=entry.key;c.tiledRef=ref;c.texture=tiled.textureHandle[ref];c.u1=tiled.u1[ref];c.v1=tiled.v1[ref];c.u2=tiled.u2[ref];c.v2=tiled.v2[ref];
            frame.casters.add(c);
            }
        }
        WallShadowGroups shadowGroups=new WallShadowGroups();
        for(IntMap.Entry<MapLightingGeometry> entry:tiled.lightingMaps)
            if(world.getEntityManager().isActive(entry.key))entry.value.registerShadowGroups(entry.key,shadowExclusionIdentities,shadowGroups);
        shadowGroups.connectWalls();
        for(IntMap.Entry<LightingSurface> entry:tiled.lightingSurfaces)shadowGroups.apply(entry.value);
        for(LightingFrame.Caster caster:frame.casters)shadowGroups.apply(caster.surface);
        frame.staticCasterCount=frame.casters.size;
        for(com.badlogic.gdx.utils.IntMap.Entries<LightingFrame.Caster> entries=staticCasters.entries();entries.hasNext();)
            if(entries.next().key>=serial)entries.remove();
        frame.staticCasterRevision=tiled.lightingRevision;
        frame.casterIndex.rebuild(frame,frame.staticCasterCount);staticRevision=tiled.lightingRevision;
        }else frame.casters.truncate(frame.staticCasterCount);
        // Animated map images reuse geometry but publish the current texture frame.
        for(int i=0;i<frame.staticCasterCount;i++){
            LightingFrame.Caster c=frame.casters.get(i);int ref=c.tiledRef;if(ref<0)continue;
            c.texture=tiled.textureHandle[ref];c.u1=tiled.u1[ref];c.v1=tiled.v1[ref];c.u2=tiled.u2[ref];c.v2=tiled.v2[ref];
        }
        for(int slot=0;slot<dynamic.activeCount;slot++) {
            int e=dynamic.renderSlotToEntityId[slot];
            if(e<0 || !dynamic.enabled[slot])continue;
            if(dynamic.light[slot]!=0){publishLight(e,slot);continue;}
            SurfaceLightingComponent authored=surfaces.getSafe(e,null);
            LightingSurface surface=frame.entities.get(e);
            LocalSurfaceDescription description=authored==null?null:authored.description;
            if(description!=localDescriptions.get(e)){
                surface=description==null?null:LocalSurfaceCompiler.compile(description,0,0,0);
                if(surface!=null && surface.receiverVertices!=null){
                    surface.localReceiverVertices=surface.receiverVertices;
                    surface.receiverVertices=LocalSurfaceDescription.copy(surface.localReceiverVertices);
                }
                localDescriptions.put(e,description);if(surface!=null)frame.entities.put(e,surface);
            }
            if(surface==null){surface=new LightingSurface(new float[48]);surface.twoSided=true;
                surface.approximate=true;frame.entities.put(e,surface);}
            surface.receiveLight=(authored==null || authored.receiveLight) && (description==null || description.receiveLight);
            surface.shadowCaster=authored!=null && authored.shadowCaster && (description==null || description.shadowCaster);
            surface.alphaThreshold=dynamic.blend[slot]==0?0:dynamic.blend[slot]==7?.5f:authored==null?.5f:authored.alphaThreshold;
            corners[0]=dynamic.x1[slot];corners[1]=dynamic.y1[slot];corners[2]=dynamic.x2[slot];corners[3]=dynamic.y2[slot];
            corners[4]=dynamic.x3[slot];corners[5]=dynamic.y3[slot];corners[6]=dynamic.x4[slot];corners[7]=dynamic.y4[slot];
            surface.actor=true;
            surface.selfShadowExclusionId=shadowExclusionIdentities.idForActor(e);
            surface.shadowGroupId=surface.selfShadowExclusionId;
            surface.imageQuad=description==null;
            if(description==null)surface.forwardTopology=ForwardTopology.NATIVE;
            if(description==null)sheet(surface,corners,authored);
            else local(surface,corners,authored,dimensions.getSafe(e,null));
            AssetRefComponent ref=assetRefs.getSafe(e,null);
            publishActorShadowRectangle(e,surface,ref);
            games.pixscape.runtime.component.TiledAnimationComponent animation=animations.getSafe(e,null);
            int visualId=animation!=null && animation.appliedFrameAssetId>0?animation.appliedFrameAssetId:ref==null?0:ref.assetId;
            surface.normalMap=atlasService==null || ref==null || visualId<=0?null:atlasService.resolveNormal(visualId,ref.atlasTag);
            if(surface.normalMap==null && authoringNormals!=null)surface.normalMap=authoringNormals.resolve(visualId);
            if(surface.shadowCaster){
                LightingFrame.Caster c=spriteCasters.get(e);
                if(c==null){c=new LightingFrame.Caster();spriteCasters.put(e,c);}
                c.surface=surface;c.texture=dynamic.textureHandle[slot];
                c.u1=dynamic.u1[slot];c.v1=dynamic.v1[slot];c.u2=dynamic.u2[slot];c.v2=dynamic.v2[slot];
                c.bounds();frame.casters.add(c);
            }
        }
        if(shadowPreparation!=null)shadowPreparation.prepare(frame);
        frame.preparationCpuNs=System.nanoTime()-started;
    }
    private void publishActorShadowRectangle(int e,LightingSurface surface,AssetRefComponent ref){
        surface.shadowVertices=null;surface.shadowVertexCount=0;
        games.pixscape.runtime.component.spatial.SpatialHeightComponent h=heights.getSafe(e,null);
        if(h==null)return; // Non-spatial sprites keep their native surface, not a legacy actor profile.
        games.pixscape.runtime.component.AnimationComponent animation=actorAnimations.getSafe(e,null);
        games.pixscape.runtime.spatial.ActorSpatialRectangleData rectangle=h.rectangles==null?null:h.rectangles.get(
                ref==null?0:ref.assetId,animation==null?null:animation.currentClip);
        if(rectangle==null)throw new LightingAdmissionException("Spatial actor "+e+" has no rectangle for its active representation");
        games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent foot=footprints.getSafe(e,null);
        games.pixscape.runtime.component.TransformComponent t=transforms.getSafe(e,null);
        if(foot==null||!foot.valid||t==null)throw new LightingAdmissionException("Spatial actor "+e+" has no valid footprint anchor");
        GameObjectHierarchySystem hierarchy=world.getSystem(GameObjectHierarchySystem.class);
        games.pixscape.runtime.hierarchy.WorldTransformState state=hierarchy==null?null:hierarchy.worldTransforms();
        boolean resolved=state!=null&&state.isResolved(e);
        games.pixscape.runtime.spatial.ActorSpatialRectanglePlacement.anchor(resolved?state.x[e]:t.x,resolved?state.y[e]:t.y,
                resolved?state.rotationRad[e]:t.rotationRad,foot.localOffsetXPx,foot.localOffsetYPx,h.altitude,rectangleAnchor);
        if(surface.actorShadowRectangle==null)surface.actorShadowRectangle=new ActorShadowRectangleCaster();
        surface.actorShadowRectangle.update(rectangle,rectangleAnchor[0],rectangleAnchor[1],rectangleAnchor[2],corners);
        surface.shadowVertices=surface.actorShadowRectangle.vertices;surface.shadowVertexCount=surface.actorShadowRectangle.vertexCount;
        if(surface.imageQuad){
            // Receiver stays on the footprint anchor; editing the caster must not move received lighting.
            float x=rectangleAnchor[0],y=rectangleAnchor[1],z=rectangleAnchor[2];
            SilhouetteImageMapping.imageUV(corners,LightingCoordinates.screenX(x,y),LightingCoordinates.screenY(x,y,z),rectangleUV,0);
            SpriteSurfaceSupport.sheet(surface.vertices,corners,rectangleUV[0],rectangleUV[1],z,1,-1);
        }
    }
    private void publishLight(int e,int slot){
        VisibilityComponent visible=visibility.getSafe(e,null);
        if(composition!=null && e<composition.state().hierarchyVisible.length && !composition.state().hierarchyVisible[e])return;
        int layer=dynamic.layerIndex[slot];
        if(visible!=null && !visible.visible || layers.maxLayerIndex()>=0 && (layer<0 || layer>layers.maxLayerIndex() || !layers.enabled[layer]))return;
        PointLightComponent p=points.getSafe(e,null);ConeLightComponent c=cones.getSafe(e,null);
        if(p==null && c==null || p!=null && !p.enabled || c!=null && !c.enabled)return;
        if(games.pixscape.runtime.service.ShaderRegistry.getOrigin(dynamic.shader[slot])!=games.pixscape.runtime.render.ShaderOrigin.CORE)
            throw new IllegalArgumentException("2.5D lighting requires a standard point/cone source; custom light shader at entity "+e);
        LightingFrame.Light l=lights.get(e);if(l==null){l=new LightingFrame.Light();lights.put(e,l);}
        float sx=(dynamic.x1[slot]+dynamic.x3[slot])*.5f,sy=(dynamic.y1[slot]+dynamic.y3[slot])*.5f;
        l.x=LightingCoordinates.worldX(sx,sy,0);l.y=LightingCoordinates.worldY(sx,sy,0);
        l.z=p!=null?p.height:c.height;l.r=p!=null?p.r:c.r;l.g=p!=null?p.g:c.g;l.b=p!=null?p.b:c.b;
        l.intensity=p!=null?p.intensity:c.intensity;l.radius=p!=null?p.radius:c.radius;
        l.falloff=p!=null?p.falloff:c.falloff;l.quality=p!=null?p.shadowQuality:c.shadowQuality;
        l.resolution=p!=null?p.shadowResolution:c.shadowResolution;l.cone=c!=null;
        if(l.quality<0 || l.quality>2 || l.resolution!=256 && l.resolution!=512 && l.resolution!=1024)
            throw new IllegalArgumentException("Light shadow quality/resolution must be 0..2 and 256/512/1024");
        if(l.radius<=0 || Float.isNaN(l.radius) || Float.isInfinite(l.radius) || Float.isNaN(l.z) || Float.isInfinite(l.z))
            throw new IllegalArgumentException("Light requires finite source height and positive radius");
        finite(l.r);finite(l.g);finite(l.b);finite(l.intensity);finite(l.falloff);
        if(l.intensity<0 || l.falloff<=0)throw new IllegalArgumentException("Light intensity must be nonnegative and falloff positive");
        if(c!=null){
            finite(c.coneAngleDeg);finite(c.softness);finite(c.inclinationDeg);
            if(c.inclinationDeg<ConeLightComponent.MIN_INCLINATION_DEG || c.inclinationDeg>ConeLightComponent.MAX_INCLINATION_DEG)
                throw new IllegalArgumentException("Cone inclination must be within -90..90 degrees");
            if(c.coneAngleDeg<=0 || c.coneAngleDeg>360)throw new IllegalArgumentException("Cone opening must be within (0,360]");
            // Preserve the authored screen azimuth, including the hierarchy's effective rotation.
            float angle=(float)Math.atan2(dynamic.y4[slot]-dynamic.y1[slot],dynamic.x4[slot]-dynamic.x1[slot]);
            float x=MathUtils.cos(angle),y=MathUtils.sin(angle);
            l.dx=y+x*.5f;l.dy=y-x*.5f;l.dz=0;
            float len=(float)Math.sqrt(l.dx*l.dx+l.dy*l.dy);l.dx/=len;l.dy/=len;
            if(c.inclinationDeg!=0f){
                float horizontal=Math.abs(c.inclinationDeg)==90f?0f:MathUtils.cosDeg(c.inclinationDeg);
                l.dx*=horizontal;l.dy*=horizontal;l.dz=MathUtils.sinDeg(c.inclinationDeg);
            }
            l.cosOuter=MathUtils.cos(c.coneAngleDeg*.5f*MathUtils.degreesToRadians);
            l.cosInner=MathUtils.cos(c.coneAngleDeg*.5f*(1-MathUtils.clamp(c.softness,0,1))*MathUtils.degreesToRadians);
        }
        frame.lights.add(l);
    }
    private static void finite(float value){if(Float.isNaN(value)||Float.isInfinite(value))throw new IllegalArgumentException("Light parameters must be finite");}
    private static void sheet(LightingSurface s,float[] q,SurfaceLightingComponent a){
        sheet(s.vertices,q,a);
    }
    private static void sheet(float[] vertices,float[] q,SurfaceLightingComponent a){
        SpriteSurfaceSupport.sheet(vertices,q,a==null?.5f:a.anchorU,a==null?0:a.anchorV,a==null?0:a.altitude,
                a==null?1:a.directionX,a==null?-1:a.directionY);
    }
    private static void local(LightingSurface s,float[] q,SurfaceLightingComponent a,DimensionsComponent dim){
        if(dim==null || dim.width<=0 || dim.height<=0)throw new IllegalArgumentException("Local surface requires positive image dimensions");
        float sx=q[0]+a.anchorU*(q[6]-q[0])+a.anchorV*(q[2]-q[0]);
        float sy=q[1]+a.anchorU*(q[7]-q[1])+a.anchorV*(q[3]-q[1]);
        float ox=LightingCoordinates.worldX(sx,sy,a.altitude),oy=LightingCoordinates.worldY(sx,sy,a.altitude);
        float ax=(q[6]-q[0])/dim.width,ay=(q[7]-q[1])/dim.width;
        float bx=(q[2]-q[0])/dim.height,by=(q[3]-q[1])/dim.height,zscale=(float)Math.sqrt(bx*bx+by*by);
        float g00=.5f*ax+ay+.25f*bx+.5f*by,g01=-.5f*ax-ay+.25f*bx+.5f*by,g02=.5f*bx+by-zscale;
        float g10=-.5f*ax+ay-.25f*bx+.5f*by,g11=.5f*ax-ay-.25f*bx+.5f*by,g12=-.5f*bx+by-zscale;
        float determinant=g00*g11-g01*g10;
        if(Math.abs(determinant)<.000001f || zscale<.000001f)throw new IllegalArgumentException("Local surface transform is singular");
        LocalSurfaceDescription d=a.description;
        for(int i=0;i<d.triangles.length;i++){
            int k=d.triangles[i],o=i*8;float x=d.xyz[k*3],y=d.xyz[k*3+1],z=d.xyz[k*3+2];
            if(Math.abs(x-y-dim.width*(d.uv[k*2]-a.anchorU))>.02f
                    || Math.abs((x+y)*.5f+z-dim.height*(d.uv[k*2+1]-a.anchorV))>.02f)
                throw new IllegalArgumentException("Local sprite XYZ/UV registration does not match its native image");
            s.vertices[o]=ox+g00*x+g01*y+g02*z;s.vertices[o+1]=oy+g10*x+g11*y+g12*z;s.vertices[o+2]=a.altitude+z*zscale;
            float nx=(g11*d.normals[k*3]-g10*d.normals[k*3+1])/determinant;
            float ny=(-g01*d.normals[k*3]+g00*d.normals[k*3+1])/determinant;
            float nz=(d.normals[k*3+2]-g02*nx-g12*ny)/zscale,len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            s.vertices[o+5]=nx/len;s.vertices[o+6]=ny/len;s.vertices[o+7]=nz/len;
        }
        // The UV topology and extrapolation are compiled once with the local description.
        // Apply only the existing affine transform here; no facet search or allocation per frame.
        float[] source=s.localReceiverVertices,target=s.receiverVertices;
        if(source!=null)for(int o=0;o<source.length;o+=8){
            float x=source[o],y=source[o+1],z=source[o+2];
            target[o]=ox+g00*x+g01*y+g02*z;target[o+1]=oy+g10*x+g11*y+g12*z;target[o+2]=a.altitude+z*zscale;
            float nx=(g11*source[o+5]-g10*source[o+6])/determinant;
            float ny=(-g01*source[o+5]+g00*source[o+6])/determinant;
            float nz=(source[o+7]-g02*nx-g12*ny)/zscale,len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            target[o+5]=nx/len;target[o+6]=ny/len;target[o+7]=nz/len;
        }
    }
}
