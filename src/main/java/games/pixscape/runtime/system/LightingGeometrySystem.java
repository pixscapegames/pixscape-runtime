package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.lighting.*;

/** Publishes source geometry before submission, using complete source domains rather than visible queue. */
public final class LightingGeometrySystem extends BaseSystem {
    private final DynamicEntityRenderState dynamic;
    private final TiledMapRenderState tiled;
    private final LightingFrame frame;
    private final LayerStateSOA layers;
    private ComponentMapper<VisibilityComponent> visibility;
    private ComponentMapper<SurfaceLightingComponent> surfaces;
    private ComponentMapper<PointLightComponent> points;
    private ComponentMapper<ConeLightComponent> cones;
    private ComponentMapper<DimensionsComponent> dimensions;
    @com.artemis.annotations.SkipWire private GameObjectCompositionSystem composition;
    @Override protected void initialize(){
        composition=world.getSystem(GameObjectCompositionSystem.class);
        world.getAspectSubscriptionManager().get(com.artemis.Aspect.all()).addSubscriptionListener(
            new com.artemis.EntitySubscription.SubscriptionListener(){
                public void inserted(com.artemis.utils.IntBag entities){}
                public void removed(com.artemis.utils.IntBag entities){
                    for(int i=0;i<entities.size();i++){int e=entities.get(i);
                        frame.entities.remove(e);localDescriptions.remove(e);lights.remove(e);spriteCasters.remove(e);}
                    staticRevision=Integer.MIN_VALUE;
                }
            });
    }
    private final IntMap<LocalSurfaceDescription> localDescriptions=new IntMap<LocalSurfaceDescription>();
    private final IntMap<LightingFrame.Light> lights = new IntMap<LightingFrame.Light>();
    private final IntMap<LightingFrame.Caster> spriteCasters = new IntMap<LightingFrame.Caster>();
    private final IntMap<LightingFrame.Caster> staticCasters = new IntMap<LightingFrame.Caster>();
    private final float[] corners = new float[8];
    private int staticRevision=Integer.MIN_VALUE;
    public LightingGeometrySystem(DynamicEntityRenderState dynamic, TiledMapRenderState tiled, FrameRenderQueue queue,LayerStateSOA layers) {
        this.dynamic=dynamic;this.tiled=tiled;frame=queue.lighting;this.layers=layers;
    }
    @Override protected void processSystem() {
        long started=System.nanoTime();
        frame.lights.clear();frame.tiles=tiled.lightingSurfaces;
        if(staticRevision!=tiled.lightingRevision){
        frame.casters.clear();
        int serial=0;
        for (IntMap.Entry<MapLightingGeometry> entry : tiled.lightingMaps) {
            if(!world.getEntityManager().isActive(entry.key)) continue;
            for(int i=0;i<entry.value.casters.size;i++) {
                LightingSurface surface=entry.value.casters.get(i);
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
            LightingFrame.Caster c=staticCasters.get(serial);
            if(c==null){c=new LightingFrame.Caster();staticCasters.put(serial,c);}serial++;
            if(c.surface!=surface){c.surface=surface;c.bounds();}
            int ref=entry.key;c.tiledRef=ref;c.texture=tiled.textureHandle[ref];c.u1=tiled.u1[ref];c.v1=tiled.v1[ref];c.u2=tiled.u2[ref];c.v2=tiled.v2[ref];
            frame.casters.add(c);
        }
        frame.staticCasterCount=frame.casters.size;
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
                localDescriptions.put(e,description);if(surface!=null)frame.entities.put(e,surface);
            }
            if(surface==null){surface=new LightingSurface(new float[48]);surface.twoSided=true;
                surface.approximate=true;frame.entities.put(e,surface);}
            surface.receiveLight=(authored==null || authored.receiveLight) && (description==null || description.receiveLight);
            surface.shadowCaster=authored!=null && authored.shadowCaster && (description==null || description.shadowCaster);
            surface.alphaThreshold=dynamic.blend[slot]==0?0:dynamic.blend[slot]==7?.5f:authored==null?.5f:authored.alphaThreshold;
            corners[0]=dynamic.x1[slot];corners[1]=dynamic.y1[slot];corners[2]=dynamic.x2[slot];corners[3]=dynamic.y2[slot];
            corners[4]=dynamic.x3[slot];corners[5]=dynamic.y3[slot];corners[6]=dynamic.x4[slot];corners[7]=dynamic.y4[slot];
            if(description==null)sheet(surface,corners,authored);
            else local(surface,corners,authored,dimensions.getSafe(e,null));
            if(surface.shadowCaster){
                LightingFrame.Caster c=spriteCasters.get(e);
                if(c==null){c=new LightingFrame.Caster();spriteCasters.put(e,c);}
                c.surface=surface;c.texture=dynamic.textureHandle[slot];
                c.u1=dynamic.u1[slot];c.v1=dynamic.v1[slot];c.u2=dynamic.u2[slot];c.v2=dynamic.v2[slot];
                c.bounds();frame.casters.add(c);
            }
        }
        frame.preparationCpuNs=System.nanoTime()-started;
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
            finite(c.coneAngleDeg);finite(c.softness);
            if(c.coneAngleDeg<=0 || c.coneAngleDeg>360)throw new IllegalArgumentException("Cone opening must be within (0,360]");
            // Existing cone direction is an angle in the drawn XY plane, with no new inclination.
            // Resolved quad direction includes the hierarchy's effective rotation.
            float angle=(float)Math.atan2(dynamic.y4[slot]-dynamic.y1[slot],dynamic.x4[slot]-dynamic.x1[slot]);
            float x=MathUtils.cos(angle),y=MathUtils.sin(angle);
            l.dx=y+x*.5f;l.dy=y-x*.5f;l.dz=0;
            float len=(float)Math.sqrt(l.dx*l.dx+l.dy*l.dy);l.dx/=len;l.dy/=len;
            l.cosOuter=MathUtils.cos(c.coneAngleDeg*.5f*MathUtils.degreesToRadians);
            l.cosInner=MathUtils.cos(c.coneAngleDeg*.5f*(1-MathUtils.clamp(c.softness,0,1))*MathUtils.degreesToRadians);
        }
        frame.lights.add(l);
    }
    private static void finite(float value){if(Float.isNaN(value)||Float.isInfinite(value))throw new IllegalArgumentException("Light parameters must be finite");}
    private static void sheet(LightingSurface s,float[] q,SurfaceLightingComponent a){
        float u=a==null?.5f:a.anchorU,v=a==null?0:a.anchorV,z=a==null?0:a.altitude;
        float sx=q[0]+u*(q[6]-q[0])+v*(q[2]-q[0]),sy=q[1]+u*(q[7]-q[1])+v*(q[3]-q[1]);
        float x=LightingCoordinates.worldX(sx,sy,z),y=LightingCoordinates.worldY(sx,sy,z);
        float dx=a==null?1:a.directionX,dy=a==null?-1:a.directionY,den=dx-dy;
        if(Math.abs(den)<.000001f)throw new IllegalArgumentException("Sheet direction projects to zero width");
        float nx=-dy,ny=dx,len=(float)Math.sqrt(nx*nx+ny*ny);nx/=len;ny/=len;
        for(int i=0;i<6;i++){
            int corner=i==0||i==3?0:i==1?1:i==2||i==4?2:3,o=i*8;
            float t=(q[corner*2]-sx)/den,px=x+dx*t,py=y+dy*t;
            s.vertices[o]=px;s.vertices[o+1]=py;s.vertices[o+2]=q[corner*2+1]-(px+py)*.5f;
            s.vertices[o+3]=corner>=2?1:0;s.vertices[o+4]=corner==1||corner==2?1:0;
            s.vertices[o+5]=nx;s.vertices[o+6]=ny;s.vertices[o+7]=0;
        }
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
    }
}
