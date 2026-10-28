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

    /** Resolve a file-relative reference within the project, without filesystem canonicalization. */
    static String resolveRelative(String owner, String reference) {
        String path = reference.replace('\\', '/');
        if (path.startsWith("/") || path.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Scene HUD reference must be project-relative: " + reference);
        }
        java.util.List<String> segments = new java.util.ArrayList<String>();
        String joined = owner.substring(0, owner.lastIndexOf('/') + 1) + path;
        for (String segment : joined.split("/")) {
            if (segment.length() == 0 || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                if (segments.isEmpty()) throw new IllegalArgumentException("Scene HUD reference escapes project: " + reference);
                segments.remove(segments.size() - 1);
            } else segments.add(segment);
        }
        StringBuilder result = new StringBuilder();
        for (String segment : segments) {
            if (result.length() > 0) result.append('/');
            result.append(segment);
        }
        String id = normalizeOptional(result.toString(), "Scene HUD file");
        if (id == null) throw new IllegalArgumentException("Scene HUD reference must identify a file: " + reference);
        return id;
    }

    private static IllegalArgumentException invalid(String value, String resourceKind) {
        return new IllegalArgumentException("HUD " + resourceKind
                + " ID must be a normalized project-relative path: " + value + ".");
    }
}
