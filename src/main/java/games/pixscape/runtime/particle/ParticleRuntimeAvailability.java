package games.pixscape.runtime.particle;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.service.AtlasRuntimeService;

/** Owns scene particle templates and pools shared by loading and frame synchronization. */
public final class ParticleRuntimeAvailability {
    private final AtlasRuntimeService atlasRuntimeService;
    private final FileHandle effectsRoot;
    private final ObjectMap<String, ParticleEffectPool> pools = new ObjectMap<>();
    private int fileParseCount;
    private int templateConstructionCount;
    private int poolConstructionCount;
    private int obtainCount;
    private int effectConstructionCount;

    public ParticleRuntimeAvailability(
            AtlasRuntimeService atlasRuntimeService, FileHandle effectsRoot) {
        this.atlasRuntimeService = atlasRuntimeService;
        this.effectsRoot = effectsRoot;
    }

    /** Strictly prepares one known scene dependency. */
    public void prepare(String atlasTag, String effectPath) {
        prepare(atlasTag, effectPath, 1);
    }

    /** Reserves actual effects and their particle storage, not just the pool's backing array. */
    public void prepare(String atlasTag, String effectPath, int initialInstances) {
        if (initialInstances < 0) throw new IllegalArgumentException("Particle reserve must be non-negative.");
        ParticleEffectPool pool = pool(atlasTag, effectPath, initialInstances);
        if (initialInstances > pool.max) throw new IllegalStateException("Particle pool was prepared with a smaller retention limit.");
        pool.fill(Math.max(0, initialInstances - pool.getFree()));
    }

    /** Obtains an instance from a pool prepared before READY or by an explicit authoring rebuild. */
    public ParticleEffectPool.PooledEffect obtain(String atlasTag, String effectPath) {
        String normalizedAtlasTag = requireText(atlasTag, "atlasTag");
        String normalizedEffectPath = ParticleEffectPath.normalize(effectPath);
        ParticleEffectPool prepared = pools.get(key(normalizedAtlasTag, normalizedEffectPath));
        if (prepared == null) {
            throw new IllegalStateException("Particle effect is not prepared: '"
                    + normalizedEffectPath + "' with atlas '" + normalizedAtlasTag
                    + "'. Add it to Runtime Availability before scene loading.");
        }
        ParticleEffectPool.PooledEffect effect = prepared.obtain();
        obtainCount++;
        effect.setEmittersCleanUpBlendFunction(false);
        return effect;
    }

    public boolean isPrepared(String atlasTag, String effectPath) {
        return pools.containsKey(key(atlasTag, effectPath));
    }

    int fileParseCount() {
        return fileParseCount;
    }

    int templateConstructionCount() {
        return templateConstructionCount;
    }

    int poolConstructionCount() {
        return poolConstructionCount;
    }

    int obtainCount() {
        return obtainCount;
    }

    int effectConstructionCount() { return effectConstructionCount; }

    public void clear() {
        for (ParticleEffectPool pool : pools.values()) pool.clear();
        pools.clear();
    }

    private ParticleEffectPool pool(String atlasTag, String effectPath, int initialInstances) {
        String normalizedAtlasTag = requireText(atlasTag, "atlasTag");
        String normalizedEffectPath = ParticleEffectPath.normalize(effectPath);
        String key = key(normalizedAtlasTag, normalizedEffectPath);
        ParticleEffectPool existing = pools.get(key);
        if (existing != null) return existing;

        if (effectsRoot == null) {
            throw new IllegalStateException("Particle effects root is not configured.");
        }
        if (atlasRuntimeService == null) {
            throw new IllegalStateException("Atlas runtime service is not configured.");
        }
        FileHandle effectFile = ParticleEffectPath.resolve(effectsRoot, normalizedEffectPath);
        if (!effectFile.exists()) {
            throw new IllegalStateException("Particle effect file is unavailable: "
                    + effectFile.path());
        }
        TextureAtlas atlas = atlasRuntimeService.getAtlas(normalizedAtlasTag);
        if (atlas == null) {
            throw new IllegalStateException("Particle atlas is unavailable: "
                    + normalizedAtlasTag + " (effect " + normalizedEffectPath + ")");
        }

        ParticleEffect template = new ParticleEffect();
        templateConstructionCount++;
        try {
            fileParseCount++;
            template.load(effectFile, atlas);
            template.setEmittersCleanUpBlendFunction(false);
            // Pool.max bounds retained free effects, not concurrent effects. Exhaustion grows on demand.
            ParticleEffectPool created = new ParticleEffectPool(template, initialInstances, Math.max(16, initialInstances)) {
                @Override protected PooledEffect newObject() {
                    PooledEffect effect = super.newObject();
                    for (ParticleEmitter emitter : effect.getEmitters()) {
                        if (!emitter.getSprites().isEmpty()) emitter.preAllocateParticles();
                    }
                    effectConstructionCount++;
                    return effect;
                }
            };
            poolConstructionCount++;
            pools.put(key, created);
            return created;
        } catch (RuntimeException failure) {
            template.dispose();
            throw new IllegalStateException("Cannot prepare particle effect '"
                    + normalizedEffectPath + "' with atlas '" + normalizedAtlasTag + "'.",
                    failure);
        }
    }

    private static String key(String atlasTag, String effectPath) {
        return requireText(atlasTag, "atlasTag") + "|" + ParticleEffectPath.normalize(effectPath);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(label + " must not be null or blank.");
        }
        return value.trim();
    }
}
