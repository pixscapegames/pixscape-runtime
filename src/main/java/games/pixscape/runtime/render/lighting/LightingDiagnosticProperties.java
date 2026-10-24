package games.pixscape.runtime.render.lighting;

/** Desktop-only opt-in diagnostics; WebGL uses the normal rendering defaults. */
final class LightingDiagnosticProperties {
    private LightingDiagnosticProperties() {}
    static String get(String name) { return System.getProperty(name); }
}
