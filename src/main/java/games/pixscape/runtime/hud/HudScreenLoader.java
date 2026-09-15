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
        String logicalId = HudScreenAssetId.normalize(screenId);
        HudScreenAsset asset = assetLoader.load(runtimeProjectDir, logicalId);
        String documentId = HudResourceId.normalizeOptional(asset.documentId, "HUD document");
        if (documentId == null) {
            return new ActiveHudScreen(logicalId, asset, null, null, null);
        }

        FileHandle documentFile = runtimeProjectDir.child(documentId);
        HudDocumentV1 document = documentCodec.read(documentFile);
        HudValidationResult structural = validator.validate(document);
        requireValid(HudDocumentValidationException.Phase.STRUCTURAL, documentId, structural);
        HudResourceRequirements requirements =
                HudResourceRequirements.from(structural.validatedDocument());

        HudResources resources = null;
        HudSession session = null;
        try {
            resources = HudResources.prepare(asset, runtimeProjectDir, requirements);
            HudValidationResult resourceAware = validator.validate(document, resources);
            requireValid(HudDocumentValidationException.Phase.RESOURCE_AWARE,
                    documentId, resourceAware);
            MaterializedHud hud = materializer.materialize(
                    resourceAware.validatedDocument(), (HudVisualResources) resources);
            session = HudSession.create(asset, resources, hudShader);
            session.install(hud);
            return new ActiveHudScreen(logicalId, asset, resources, hud, session);
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = null;
            if (session != null) {
                try {
                    session.dispose();
                } catch (RuntimeException disposalFailure) {
                    cleanupFailure = disposalFailure;
                }
            }
            if (resources != null) {
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

    private static void requireValid(HudDocumentValidationException.Phase phase,
                                     String documentId, HudValidationResult result) {
        if (!result.isValid()) {
            throw new HudDocumentValidationException(phase, documentId, result);
        }
    }
}
