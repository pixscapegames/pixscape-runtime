package games.pixscape.runtime.particle;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/*
 * Adapted from libGDX ParticleEmitter / ParticleEffect internals.
 * Original project: libGDX
 * Original license: Apache License 2.0
 *
 * Modifications:
 * - moved into games.pixscape.runtime.particle
 * - exposed particle arrays for Pixscape SOA runtime extraction
 * - adapted for Pixscape runtime rendering pipeline
 */
public class ParticleEffectPool extends Pool<ParticleEffectPool.PooledEffect> {
    private final ParticleEffect effect;

    public ParticleEffectPool(ParticleEffect effect, int initialCapacity, int max) {
        super(initialCapacity, max);
        this.effect = effect;
    }

    protected PooledEffect newObject() {
        PooledEffect pooledEffect = new PooledEffect(effect);
        pooledEffect.start();
        return pooledEffect;
    }

    public void free(PooledEffect effect) {
        super.free(effect);

        effect.reset(false); // copy parameters exactly to avoid introducing error
        if (effect.xSizeScale != this.effect.xSizeScale || effect.ySizeScale != this.effect.ySizeScale
                || effect.motionScale != this.effect.motionScale) {
            Array<ParticleEmitter> emitters = effect.getEmitters();
            Array<ParticleEmitter> templateEmitters = this.effect.getEmitters();
            for (int i = 0; i < emitters.size; i++) {
                ParticleEmitter emitter = emitters.get(i);
                ParticleEmitter templateEmitter = templateEmitters.get(i);
                emitter.matchSize(templateEmitter);
                emitter.matchMotion(templateEmitter);
            }
            effect.xSizeScale = this.effect.xSizeScale;
            effect.ySizeScale = this.effect.ySizeScale;
            effect.motionScale = this.effect.motionScale;
        }
    }

    public class PooledEffect extends ParticleEffect {
        PooledEffect(ParticleEffect effect) {
            super(effect);
        }

        public void free() {
            ParticleEffectPool.this.free(this);
        }
    }
}
