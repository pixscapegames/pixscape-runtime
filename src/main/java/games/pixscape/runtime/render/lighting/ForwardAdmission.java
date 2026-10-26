package games.pixscape.runtime.render.lighting;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.Texture;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;

/** Whole-scene cold admission, then one ordered queue preflight before any GPU draw. */
public final class ForwardAdmission {
    private ForwardAdmission() {}
    public static void validateWorld(World world,AtlasRuntimeService.TextureArrayBundle bundle) {
        validateBundle(bundle);
        IntBag entities=world.getAspectSubscriptionManager().get(Aspect.all(RenderMaterialComponent.class)).getEntities();
        ComponentMapper<RenderMaterialComponent> materials=world.getMapper(RenderMaterialComponent.class);
        ComponentMapper<RenderRepeatComponent> repeats=world.getMapper(RenderRepeatComponent.class);
        ComponentMapper<PointLightComponent> points=world.getMapper(PointLightComponent.class);
        ComponentMapper<ConeLightComponent> cones=world.getMapper(ConeLightComponent.class);
        for(int i=0;i<entities.size();i++) {
            int e=entities.get(i);RenderMaterialComponent m=materials.get(e);
            if(points.has(e)||cones.has(e)) {
                if(ShaderRegistry.getOrigin(m.shaderIdx)!=ShaderOrigin.CORE)throw refused(e,"custom light shader");
                LightCompositionPass.validate(true,m.blendModeId);continue;
            }
            RenderRepeatComponent repeat=repeats.getSafe(e,null);
            if(repeat!=null && (repeat.repeatX||repeat.repeatY))throw refused(e,"repeat");
            validateMaterial(e,m.shaderIdx,m.blendModeId,false);
            if(m.textureHandle!=0)validateTexture(e,m.textureHandle,bundle);
        }
        IntBag lights=world.getAspectSubscriptionManager().get(Aspect.one(PointLightComponent.class,ConeLightComponent.class)).getEntities();
        ComponentMapper<VisibilityComponent> visibility=world.getMapper(VisibilityComponent.class);
        int count=0;
        for(int i=0;i<lights.size();i++){
            int e=lights.get(i);VisibilityComponent v=visibility.getSafe(e,null);
            PointLightComponent p=points.getSafe(e,null);ConeLightComponent c=cones.getSafe(e,null);
            if((v==null||v.visible)&&((p!=null&&p.enabled)||(c!=null&&c.enabled)))if(++count>1)throw refused(e,"second active light");
        }
    }
    public static void validateQueue(FrameRenderQueue q,AtlasRuntimeService.TextureArrayBundle bundle) {
        validateBundle(bundle);
        if(q.lighting.diagnostic!=0)throw new LightingAdmissionException("Forward lighting requires normal rendering; select the reference for historical diagnostics");
        if(q.lighting.lights.size>1)throw new LightingAdmissionException("Forward lighting refuses "+q.lighting.lights.size+" active lights; maximum is one");
        for(int i=0;i<q.size;i++) {
            int e=q.sourceEntity[i];
            if(q.light[i]!=0){if(ShaderRegistry.getOrigin(q.shader[i])!=ShaderOrigin.CORE)throw refused(e,"custom light shader");LightCompositionPass.validate(true,q.blend[i]);continue;}
            if(q.repeatFlags[i]!=0)throw refused(e,"repeat");
            validateMaterial(e,q.shader[i],q.blend[i],q.sourceDomain[i]==FrameRenderQueue.SOURCE_VFX);
            validateTexture(e,q.textureHandle[i],bundle);
            LightingSurface s=q.sourceDomain[i]==FrameRenderQueue.SOURCE_TILED?q.lighting.tiles.get(q.sourceSlot[i]):q.lighting.entities.get(e);
            if(s!=null){
                if(s.forwardTopology==null)throw refused(e,"physical/color topology was not prepared before submission");
                if(s.normalMap!=null){Texture t=TextureRegistry.getByHandle(s.normalMap.textureHandle);
                    if(t==null||t.getTextureObjectHandle()==0)throw refused(e,"normal page is not live");}
            }
        }
    }
    /** Cold full-domain admission includes off-camera images, not just the visible queue. */
    public static void validateDomains(DynamicEntityRenderState dynamic,TiledMapRenderState tiled,AtlasRuntimeService.TextureArrayBundle bundle) {
        validateBundle(bundle);
        for(int slot=0;slot<dynamic.activeCount;slot++){
            int e=dynamic.renderSlotToEntityId[slot];if(e<0||!dynamic.enabled[slot]||dynamic.light[slot]!=0)continue;
            validateMaterial(e,dynamic.shader[slot],dynamic.blend[slot],false);
            if(dynamic.repeatFlags[slot]!=0)throw refused(e,"repeat");
            validateTexture(e,dynamic.textureHandle[slot],bundle);
        }
        for(int ref=0;ref<tiled.getRefCount();ref++){
            if(!tiled.enabled[ref])continue;
            LightingSurface s=tiled.lightingSurfaces.get(ref);int owner=s==null?-1:s.ownerEntity;
            validateMaterial(owner,tiled.shader[ref],tiled.blend[ref],false);
            if(tiled.repeatFlags[ref]!=0)throw refused(owner,"repeat at tiled ref "+ref);
            validateTexture(owner,tiled.textureHandle[ref],bundle);
            if(s!=null&&s.forwardTopology==null)throw refused(owner,"topology missing at tiled ref "+ref);
        }
    }
    private static void validateBundle(AtlasRuntimeService.TextureArrayBundle b){if(b==null||b.textureArray==null||b.textureArray.getTextureObjectHandle()==0||b.handle2layer==null)throw new LightingAdmissionException("Forward lighting requires a live, owner-published color texture array");}
    private static void validateTexture(int e,int handle,AtlasRuntimeService.TextureArrayBundle b){
        int layer=b.handle2layer.get(handle,-1);
        if(handle==0||layer<0||layer>=b.textureArray.getDepth())throw refused(e,"color texture handle "+handle+" is absent from the active bundle");
    }
    private static void validateMaterial(int e,int shader,int blend,boolean vfx){
        boolean standard=shader==ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName())
                ||shader==ShaderRegistry.indexOf(ShaderMode.MULTI_TEXTURE.defaultShaderName())
                ||shader==ShaderRegistry.indexOf(ShaderMode.TEXTURE_2D.defaultShaderName());
        if(!standard||ShaderRegistry.getOrigin(shader)!=ShaderOrigin.CORE||ShaderRegistry.getParameterLayout(shader).size()!=0)throw refused(e,"unsupported material shader "+shader);
        if(blend!=0&&blend!=1&&blend!=2&&blend!=7&&!(vfx&&(blend==3||blend==4)))throw refused(e,"unsupported blend "+blend);
    }
    private static IllegalArgumentException refused(int e,String reason){return new LightingAdmissionException("Forward lighting refused source entity "+e+": "+reason+"; correct the scene or select the reference path explicitly");}
}
