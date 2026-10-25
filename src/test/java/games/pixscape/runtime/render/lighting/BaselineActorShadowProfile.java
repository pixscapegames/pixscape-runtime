// Test-only baseline copied from the starting Git commit, for bounded before/after comparisons.
package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** Authored caster calibration in the reference image, before atlas mapping and flips. */
public final class BaselineActorShadowProfile implements Json.Serializable {
    public float leftU, leftV, rightU=1, rightV, thickness;

    public BaselineActorShadowProfile() {}
    public BaselineActorShadowProfile(float leftV, float rightV, float thickness) {
        this(0,leftV,1,rightV,thickness);
    }
    public BaselineActorShadowProfile(float leftU,float leftV,float rightU,float rightV,float thickness) {
        this.leftU=leftU;this.leftV=leftV;this.rightU=rightU;this.rightV=rightV;this.thickness=thickness;validate();
    }
    public void validate() {
        if (!(leftU>=0 && leftU<=1 && leftV>=0 && leftV<=1 && rightU>=0 && rightU<=1
                && rightV>=0 && rightV<=1 && thickness>=0 && thickness<=.5f))
            throw new IllegalArgumentException("Actor foot endpoints must be in 0..1 and thickness in 0..0.5");
        if (!distinct(leftU,leftV,rightU,rightV))
            throw new IllegalArgumentException("Actor foot endpoints must be distinct");
    }
    public static boolean distinct(float u,float v,float otherU,float otherV) {
        float x=u-otherU,y=v-otherV;return x*x+y*y>=.0000000001f;
    }
    public BaselineActorShadowProfile copy() { return new BaselineActorShadowProfile(leftU,leftV,rightU,rightV,thickness); }
    @Override public void write(Json json) {
        json.writeValue("version",2);json.writeValue("leftU",leftU);json.writeValue("leftV",leftV);
        json.writeValue("rightU",rightU);json.writeValue("rightV",rightV);json.writeValue("thickness",thickness);
    }
    @Override public void read(Json json,JsonValue data) {
        int version=data.getInt("version",1);
        if(version!=1 && version!=2)throw new IllegalArgumentException("Unsupported actor shadow profile version: "+version);
        // Explicit migration of the original two edge heights. The full alpha domain is unchanged.
        leftU=version==1?0:data.getFloat("leftU");rightU=version==1?1:data.getFloat("rightU");
        leftV=data.getFloat("leftV",0);rightV=data.getFloat("rightV",0);thickness=data.getFloat("thickness",0);validate();
    }
}
