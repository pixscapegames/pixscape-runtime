package games.pixscape.runtime.service;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.IntMap;
import java.util.IdentityHashMap;

/** Scene-local linear RGBA8 pages. Prepared before publication and owned with the color atlas. */
public final class NormalMapAtlas {
    public static final String SUFFIX = ".normals";
    // Overlapping AssetManager leases can expose the same page to two generations.
    private static final IdentityHashMap<Texture,Integer> pageBindings = new IdentityHashMap<Texture,Integer>();
    private final TextureAtlas atlas;
    private final IntMap<NormalMapBinding> bindings = new IntMap<NormalMapBinding>();
    private long bytes;
    private boolean disposed;
    private final boolean ownsPages;

    public NormalMapAtlas(String tag, FileHandle file, TextureAtlas colors) {
        this(tag, new TextureAtlas(file), colors);
    }
    /** Takes ownership of pages uploaded from a CPU-prepared candidate. */
    public NormalMapAtlas(String tag, TextureAtlas preparedAtlas, TextureAtlas colors) {
        this(tag,preparedAtlas,colors,true);
    }
    public NormalMapAtlas(String tag,TextureAtlas preparedAtlas,TextureAtlas colors,boolean ownsPages) {
        atlas = preparedAtlas;
        this.ownsPages=ownsPages;
        for(Texture texture:atlas.getTextures()) {
            Integer count=pageBindings.get(texture);
            pageBindings.put(texture,count==null?1:count+1);
        }
        try {
            AtlasAssetIndex normalIndex = AtlasAssetIndexBuilder.build(tag + SUFFIX, atlas);
            AtlasAssetIndex colorIndex = colors == null ? null : AtlasAssetIndexBuilder.build(tag, colors);
            for (Texture texture : atlas.getTextures()) {
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
                bytes += (long)texture.getWidth() * texture.getHeight() * 4;
            }
            for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
                int marker = region.name.lastIndexOf("__a");
                if (marker < 0) throw new IllegalArgumentException("Normal region lacks asset identity: " + region.name);
                int id = Integer.parseInt(region.name.substring(marker + 3));
                AtlasAssetBinding color = colorIndex == null ? null : colorIndex.get(id);
                if (colorIndex != null && color == null) throw new IllegalArgumentException("Normal has no scene color binding: asset " + id);
                if (bindings.containsKey(id)) throw new IllegalArgumentException("Duplicate normal asset: " + id);
                TextureAtlas.AtlasRegion n = normalIndex.get(id).firstRegion(), c;
                if (color != null) c = color.firstRegion();
                else {
                    // Standalone authoring draws the original full color image, without trim.
                    c = new TextureAtlas.AtlasRegion(n.getTexture(),0,0,n.originalWidth,n.originalHeight);
                    c.originalWidth=n.originalWidth;c.originalHeight=n.originalHeight;
                }
                if (n.originalWidth != c.originalWidth || n.originalHeight != c.originalHeight)
                    throw new IllegalArgumentException("Normal/color original dimensions differ: asset " + id);
                bindings.put(id, new NormalMapBinding(c, n));
            }
        } catch (RuntimeException failure) { dispose(); throw failure; }
    }
    public NormalMapBinding resolve(int assetId) { return bindings.get(assetId); }
    /** Cold dependency discovery from declared IDs present in the acquired color atlas. */
    public static boolean requiresNormals(TextureAtlas colors,games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles profiles) {
        if(profiles.size()==0)return false;
        for(TextureAtlas.AtlasRegion region:colors.getRegions()) {
            if(region.name==null)continue;
            int marker=region.name.lastIndexOf("__a");
            if(marker>=0 && profiles.normalMapSourceForTileAsset(Integer.parseInt(region.name.substring(marker+3)))!=null)return true;
        }
        return false;
    }
    public static void validateDeclarations(String tag,TextureAtlas colors,NormalMapAtlas normals,
            games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles profiles,FileHandle project) {
        boolean declared=false;
        for(games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile profile:profiles.profiles())
            if(profile.normalMapSources.size>0){declared=true;break;}
        if(!declared)return;
        AtlasAssetIndex index=AtlasAssetIndexBuilder.build(tag,colors);
        for(games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile profile:profiles.profiles())
            for(IntMap.Entry<String> entry:profile.normalMapSources)
                if(index.get(entry.key)!=null && (normals==null || normals.resolve(entry.key)==null))
                    throw new IllegalArgumentException("Declared normal resource is unavailable: asset "+entry.key+" in scene "+tag);
    }
    public long textureBytes() { return bytes; }
    public void dispose() {
        if (disposed) return;
        disposed = true;
        for(Texture texture:atlas.getTextures()) {
            Integer count=pageBindings.get(texture);
            if(count!=null && count>1)pageBindings.put(texture,count-1);
            else {pageBindings.remove(texture);TextureRegistry.release(texture);}
        }
        if(ownsPages)atlas.dispose();bindings.clear();bytes=0;
    }
}
