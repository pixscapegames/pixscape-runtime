package games.pixscape.runtime.render.lighting;

/** Recoverable authored-data/resource refusal, not a renderer programming or GPU failure. */
public final class LightingAdmissionException extends IllegalArgumentException {
    public LightingAdmissionException(String message){super(message);}
    public LightingAdmissionException(String message, Throwable cause){super(message,cause);}
}
