package games.pixscape.runtime.particle;

import com.badlogic.gdx.files.FileHandle;

/** Authoritative normalization and resolution for Runtime particle effect files. */
public final class ParticleEffectPath {

    private ParticleEffectPath() {
    }

    public static String normalize(String effectPathOrName) {
        if (effectPathOrName == null) {
            throw new IllegalArgumentException("Particle effect path must not be null.");
        }
        String normalized = effectPathOrName.trim().replace('\\', '/');
        if (normalized.length() == 0) {
            throw new IllegalArgumentException("Particle effect path must not be blank.");
        }
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized.endsWith(".p") ? normalized : normalized + ".p";
    }

    public static FileHandle resolve(FileHandle effectsRoot, String effectPathOrName) {
        if (effectsRoot == null) {
            throw new IllegalArgumentException("Particle effects root must not be null.");
        }
        return effectsRoot.child(normalize(effectPathOrName));
    }
}
