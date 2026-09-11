package games.pixscape.runtime.hud;

/** Normalization for project-relative physical resources declared by a HUD screen. */
final class HudResourceId {

    private HudResourceId() {
    }

    static String normalizeOptional(String value, String resourceKind) {
        if (value == null || value.trim().length() == 0) return null;

        String normalized = value.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
            throw invalid(value, resourceKind);
        }

        int segmentStart = 0;
        for (int i = 0; i <= normalized.length(); i++) {
            if (i != normalized.length() && normalized.charAt(i) != '/') continue;
            String segment = normalized.substring(segmentStart, i);
            if (segment.length() == 0 || ".".equals(segment) || "..".equals(segment)) {
                throw invalid(value, resourceKind);
            }
            segmentStart = i + 1;
        }
        return normalized;
    }

    private static IllegalArgumentException invalid(String value, String resourceKind) {
        return new IllegalArgumentException("HUD " + resourceKind
                + " ID must be a normalized project-relative path: " + value + ".");
    }
}
