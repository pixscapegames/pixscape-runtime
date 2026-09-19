package games.pixscape.runtime.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.runtime.loading.FileAvailabilityService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/** INTERNAL, GL-free staged file discovery. The Scene plan, not this helper, owns resources. */
public final class SceneHudFiles {
    private final FileAvailabilityService availability;
    private final FileHandle project;
    private final String atlasId;
    private final Map<String, Screen> screens = new LinkedHashMap<String, Screen>();
    private final Set<String> files = new LinkedHashSet<String>();
    private final Map<String, Boolean> skins = new LinkedHashMap<String, Boolean>();
    private final Set<Integer> bitmapFontAssetIds = new LinkedHashSet<Integer>();
    private boolean atlasExpanded;

    public SceneHudFiles(FileAvailabilityService availability, FileHandle project,
                         String atlasId, List<String> roots) {
        this.availability = availability;
        this.project = project;
        this.atlasId = atlasId;
        for (String root : roots) {
            String id = HudScreenAssetId.normalize(root);
            if (!screens.containsKey(id)) screens.put(id, new Screen(id));
        }
        // An empty union has no Scene environment and no derived atlas request.
        if (!screens.isEmpty()) {
            try {
                request(atlasId);
                for (Screen screen : screens.values()) request(screen.assetId);
            } catch (RuntimeException failure) {
                try {
                    release();
                } catch (RuntimeException cleanupFailure) {
                    if (Gdx.app != null) Gdx.app.error("PixscapeHud", "HUD file acquisition cleanup failed.", cleanupFailure);
                }
                throw failure;
            }
        }
    }

    public void advance() {
        if (screens.isEmpty()) return;
        if (!atlasExpanded && available(atlasId)) {
            FileHandle descriptor = file(atlasId);
            TextureAtlas.TextureAtlasData data = new TextureAtlas.TextureAtlasData(
                    descriptor, descriptor.parent(), false);
            if (data.getPages().size == 0) {
                throw new IllegalArgumentException("Scene HUD atlas has no pages: " + descriptor.path());
            }
            for (TextureAtlas.TextureAtlasData.Page page : data.getPages()) {
                request(HudResourceId.resolveRelative(atlasId, page.name));
            }
            atlasExpanded = true;
        }
        for (Screen screen : screens.values()) {
            if (screen.asset == null && available(screen.assetId)) {
                screen.asset = new HudScreenAssetLoader().load(project, screen.id);
                screen.documentId = screen.asset.documentId;
                request(screen.documentId);
            }
            if (!screen.expanded && screen.documentId != null && available(screen.documentId)) {
                HudValidationResult validation = new HudDocumentValidator().validate(
                        new HudDocumentCodec().read(file(screen.documentId)));
                if (!validation.isValid()) {
                    throw new HudDocumentValidationException(
                            HudDocumentValidationException.Phase.STRUCTURAL, screen.documentId, validation);
                }
                HudResourceRequirements requirements = HudResourceRequirements.from(validation.validatedDocument());
                for (Integer fontAssetId : requirements.bitmapFontAssetIds()) {
                    if (bitmapFontAssetIds.add(fontAssetId)) {
                        request(HudBitmapFontResource.descriptorId(fontAssetId));
                    }
                }
                if (requirements.requiresSkin()) {
                    String skinId = HudResourceId.normalizeOptional(screen.asset.skinId, "skinId");
                    if (skinId == null) throw new IllegalArgumentException("Scene HUD " + screen.id + " requires skinId.");
                    if (!skins.containsKey(skinId)) {
                        skins.put(skinId, false);
                        request(skinId);
                    }
                }
                screen.expanded = true;
            }
        }
        for (Map.Entry<String, Boolean> skin : skins.entrySet()) {
            if (skin.getValue() || !available(skin.getKey())) continue;
            JsonValue json = new JsonReader().parse(file(skin.getKey()));
            if (!json.isObject()) throw new IllegalArgumentException("Scene HUD Skin must be an object: " + skin.getKey());
            for (JsonValue section = json.child; section != null; section = section.next) {
                if (!"BitmapFont".equals(section.name)
                        && !"com.badlogic.gdx.graphics.g2d.BitmapFont".equals(section.name)) continue;
                if (!section.isObject()) throw new IllegalArgumentException("Invalid BitmapFont section: " + skin.getKey());
                for (JsonValue font = section.child; font != null; font = font.next) {
                    JsonValue descriptor = font.isObject() ? font.get("file") : null;
                    if (descriptor == null || !descriptor.isString() || descriptor.asString().trim().length() == 0) {
                        throw new IllegalArgumentException("Missing BitmapFont descriptor in " + skin.getKey() + ": " + font.name);
                    }
                    request(HudResourceId.resolveRelative(skin.getKey(), descriptor.asString()));
                }
            }
            skin.setValue(true);
        }
    }

    public boolean isComplete() {
        if (screens.isEmpty()) return true;
        if (!atlasExpanded) return false;
        for (Screen screen : screens.values()) if (!screen.expanded) return false;
        for (Boolean expanded : skins.values()) if (!expanded) return false;
        return completeFileCount() == files.size();
    }

    public List<String> skinIds() {
        List<String> ids = new ArrayList<String>(skins.keySet());
        Collections.sort(ids);
        return ids;
    }

    public Set<Integer> bitmapFontAssetIds() {
        return Collections.unmodifiableSet(bitmapFontAssetIds);
    }

    public int fileCount() { return files.size(); }
    public int completeFileCount() {
        int count = 0;
        for (String id : files) if (available(id)) count++;
        return count;
    }

    public void release() {
        RuntimeException failure = null;
        for (String id : files) {
            try {
                availability.releaseFile(project.child(id).path());
            } catch (RuntimeException cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
            }
        }
        files.clear();
        if (failure != null) throw failure;
    }

    private void request(String id) {
        if (!files.contains(id)) {
            availability.requestFile(project.child(id).path());
            files.add(id);
        }
    }
    private boolean available(String id) {
        return id != null && availability.isFileAvailable(project.child(id).path());
    }
    private FileHandle file(String id) { return availability.file(project.child(id).path()); }

    private static final class Screen {
        final String id;
        final String assetId;
        HudScreenAsset asset;
        String documentId;
        boolean expanded;
        Screen(String id) {
            this.id = id;
            assetId = id + HudScreenAsset.EXTENSION;
        }
    }
}
