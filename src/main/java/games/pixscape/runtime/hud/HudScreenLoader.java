package games.pixscape.runtime.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudValidationResult;

/** {@code INTERNAL} synchronous candidate builder for project-authored HUD screens. */
public final class HudScreenLoader {
    private final FileHandle runtimeProjectDir;
    private final ShaderProgram hudShader;
    private final HudScreenAssetLoader assetLoader = new HudScreenAssetLoader();
    private final HudDocumentCodec documentCodec = new HudDocumentCodec();
    private final HudDocumentValidator validator = new HudDocumentValidator();
    private final HudMaterializer materializer = new HudMaterializer();

    public HudScreenLoader(FileHandle runtimeProjectDir, ShaderProgram hudShader) {
        if (runtimeProjectDir == null) {
            throw new IllegalArgumentException("Runtime project directory is required.");
        }
        this.runtimeProjectDir = runtimeProjectDir;
        this.hudShader = hudShader;
    }

    /** Builds a detached ownership candidate; it does not mutate any active-screen state. */
    public ActiveHudScreen load(String screenId) {
        return load(screenId, null, ActiveHudScreen.ResourceOwnership.OWNED);
    }

    /**
     * Builds a detached screen borrowing an already prepared environment. The caller retains
     * ownership on success and failure and must keep it open until every borrowing session ends.
     * Does not load graphics resources or require exact environment membership/atlas identity.
     */
    public ActiveHudScreen loadBorrowing(String screenId, HudResources environment) {
        if (environment == null) throw new IllegalArgumentException("Borrowed HudResources is required.");
        environment.requireOpen();
        return load(screenId, environment, ActiveHudScreen.ResourceOwnership.BORROWED);
    }

    private ActiveHudScreen load(String screenId, HudResources environment,
                                 ActiveHudScreen.ResourceOwnership ownership) {
        String logicalId = HudScreenAssetId.normalize(screenId);
        HudScreenAsset asset = assetLoader.load(runtimeProjectDir, logicalId);
        String documentId = asset.documentId;

        FileHandle documentFile = runtimeProjectDir.child(documentId);
        HudDocumentV1 document = documentCodec.read(documentFile);
        HudValidationResult structural = validator.validate(document);
        requireValid(HudDocumentValidationException.Phase.STRUCTURAL, documentId, structural);
        HudResourceRequirements requirements =
                HudResourceRequirements.from(structural.validatedDocument());

        HudResources resources = environment;
        HudSession session = null;
        try {
            if (ownership == ActiveHudScreen.ResourceOwnership.OWNED) {
                resources = HudResources.prepareStandalone(asset, runtimeProjectDir, requirements);
            }
            HudSelectedResources selected = resources.select(
                    requirements.requiresSkin() ? requireSkinId(asset, logicalId) : null);
            if (!selected.satisfies(requirements)) {
                throw new IllegalArgumentException("HUD environment does not satisfy resource categories for "
                        + logicalId + " (Skin " + selected.skinId() + ").");
            }
            if (ownership == ActiveHudScreen.ResourceOwnership.OWNED) {
                String profileId = HudTextureProfile.normalizeIdOrDefault(asset.textureProfileId);
                if (!resources.textureProfile().id().equals(profileId)) {
                    throw new IllegalArgumentException("HUD environment texture profile does not match "
                            + logicalId + ": expected " + profileId + ".");
                }
            }
            HudValidationResult resourceAware = validator.validate(document, selected);
            requireValid(HudDocumentValidationException.Phase.RESOURCE_AWARE,
                    documentId, resourceAware);
            MaterializedHud hud = materializer.materialize(
                    resourceAware.validatedDocument(), selected);
            session = HudSession.create(asset, resources, hudShader);
            session.install(hud);
            return new ActiveHudScreen(logicalId, asset, resources, hud, session, ownership);
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = null;
            if (session != null) {
                try {
                    session.dispose();
                } catch (RuntimeException disposalFailure) {
                    cleanupFailure = disposalFailure;
                }
            }
            if (ownership == ActiveHudScreen.ResourceOwnership.OWNED && resources != null) {
                try {
                    resources.dispose();
                } catch (RuntimeException disposalFailure) {
                    if (cleanupFailure == null) cleanupFailure = disposalFailure;
                }
            }
            if (cleanupFailure != null && Gdx.app != null) {
                Gdx.app.error("PixscapeHud", "HUD candidate cleanup failed for "
                        + logicalId + ".", cleanupFailure);
            }
            throw failure;
        }
    }

    private static String requireSkinId(HudScreenAsset asset, String logicalId) {
        String id = HudResourceId.normalizeOptional(asset.skinId, "Skin");
        if (id == null) throw new IllegalArgumentException("HUD " + logicalId + " requires skinId.");
        return id;
    }

    private static void requireValid(HudDocumentValidationException.Phase phase,
                                     String documentId, HudValidationResult result) {
        if (!result.isValid()) {
            throw new HudDocumentValidationException(phase, documentId, result);
        }
    }
}
