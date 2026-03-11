package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Décrit un émetteur de particules basé sur un fichier .p LibGDX.
 * Ce composant est sérialisable (uniquement des types simples).
 * Le runtime se charge de créer les ParticleEffect réels à partir de ça.
 */
public final class ParticleEmitterComponent extends PooledComponent {

    /** Chemin relatif vers le fichier .p (ex: "particles/fire.p"). */
    public String effectPath;

    /** Tag d'atlas à utiliser pour les sprites de particules (ex: "MainScene" ou "Particles"). */
    public String atlasTag;

    /** L’émetteur suit l’entité (Transform) ou non. */
    public boolean localSpace = true;

    /** Démarrer automatiquement à l'apparition de l'entité. */
    public boolean autoStart = true;

    /** Boucler indéfiniment. Si false : joue une fois puis reste complet. */
    public boolean looping = true;

    public boolean paused = false;
    public boolean playRequested = false;
    public boolean restartRequested = false;

    public ParticleEmitterComponent() {
    }

    @Override
    protected void reset() {
        effectPath = "";
        atlasTag = "";
        localSpace = true;
        looping = true;
        autoStart = true;
        paused = false;
        playRequested = false;
        restartRequested = false;
    }

    public ParticleEmitterComponent(String effectPath, String atlasTag) {
        this.effectPath = effectPath;
        this.atlasTag = atlasTag;
    }
}
