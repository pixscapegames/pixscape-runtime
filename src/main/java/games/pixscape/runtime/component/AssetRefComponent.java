// games/pixscape/studio/runtime/component/AssetRefComponent.java
package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Logical sprite asset reference shared by studio and runtime.
 */
public final class AssetRefComponent extends PooledComponent {

    public int assetId = -1;

    /** Scene atlas tag, ex: "MainScene". */
    public String atlasTag = "main";

    @Override
    protected void reset() {
        assetId = -1;
        atlasTag = "main";
    }
}
