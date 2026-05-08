package games.pixscape.runtime.service;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * Lightweight GLSL source preprocessor.
 * <p>
 * Supports:
 * #include "file.glsl"
 * <p>
 * Resolution order:
 * 1. relative to the current shader file directory
 * 2. relative to shader-local includes/ directory
 * 3. relative to shared includes directory, if provided
 * <p>
 * GWT-compatible:
 * - uses LibGDX FileHandle
 * - no java.io.File
 * - no NIO
 * - no reflection
 */
public final class ShaderSourcePreprocessor {

    private static boolean isBlank(String s) {
        if (s == null || s.length() == 0) return true;

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static final String INCLUDE_DIRECTIVE = "#include";
    private static final String VERSION_DIRECTIVE = "#version";

    private ShaderSourcePreprocessor() {
    }

    public static String preprocess(FileHandle shaderFile) {
        return preprocess(shaderFile, null);
    }

    public static String preprocess(FileHandle shaderFile, FileHandle sharedIncludesDir) {
        if (shaderFile == null) {
            throw new IllegalArgumentException("shaderFile cannot be null");
        }
        if (!shaderFile.exists()) {
            throw new IllegalArgumentException("Shader file does not exist: " + shaderFile.path());
        }

        ObjectSet<String> includeStack = new ObjectSet<>();
        Array<String> includeTrace = new Array<>();

        return preprocessFile(
                shaderFile,
                shaderFile.parent(),
                shaderFile.parent().child("includes"),
                sharedIncludesDir,
                includeStack,
                includeTrace,
                true
        );
    }

    public static String preprocess(String source, FileHandle baseDir, FileHandle sharedIncludesDir) {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }

        FileHandle localIncludesDir = baseDir != null ? baseDir.child("includes") : null;

        ObjectSet<String> includeStack = new ObjectSet<>();
        Array<String> includeTrace = new Array<>();

        return preprocessRootSource(
                source,
                baseDir,
                localIncludesDir,
                sharedIncludesDir,
                "<memory>",
                includeStack,
                includeTrace
        );
    }

    private static String preprocessFile(FileHandle file,
                                         FileHandle baseDir,
                                         FileHandle localIncludesDir,
                                         FileHandle sharedIncludesDir,
                                         ObjectSet<String> includeStack,
                                         Array<String> includeTrace,
                                         boolean root) {
        String key = normalizePath(file);

        if (includeStack.contains(key)) {
            throw new IllegalStateException("Shader include cycle detected:\n" + buildCycleMessage(includeTrace, key));
        }

        includeStack.add(key);
        includeTrace.add(key);

        String source = file.readString("UTF-8");

        String result = root
                ? preprocessRootSource(
                source,
                file.parent(),
                localIncludesDir,
                sharedIncludesDir,
                file.path(),
                includeStack,
                includeTrace
        )
                : preprocessSource(
                source,
                file.parent(),
                localIncludesDir,
                sharedIncludesDir,
                file.path(),
                includeStack,
                includeTrace
        );

        includeTrace.pop();
        includeStack.remove(key);

        return result;
    }

    /**
     * Root shader preprocessing.
     *
     * Guarantees that #version, when present, is emitted as the very first
     * line of the final shader source. This is required by GLSL ES/WebGL.
     */
    private static String preprocessRootSource(String source,
                                               FileHandle baseDir,
                                               FileHandle localIncludesDir,
                                               FileHandle sharedIncludesDir,
                                               String sourceName,
                                               ObjectSet<String> includeStack,
                                               Array<String> includeTrace) {
        String normalized = stripBom(source);

        String[] lines = normalized.split("\\r?\\n", -1);

        String versionLine = null;
        StringBuilder body = new StringBuilder(normalized.length());

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (versionLine == null && trimmed.startsWith(VERSION_DIRECTIVE)) {
                versionLine = trimmed;
                continue;
            }

            body.append(line).append('\n');
        }

        String processedBody = preprocessSource(
                body.toString(),
                baseDir,
                localIncludesDir,
                sharedIncludesDir,
                sourceName,
                includeStack,
                includeTrace
        );

        if (versionLine == null) {
            return processedBody;
        }

        return versionLine + "\n" + processedBody;
    }

    private static String preprocessSource(String source,
                                           FileHandle baseDir,
                                           FileHandle localIncludesDir,
                                           FileHandle sharedIncludesDir,
                                           String sourceName,
                                           ObjectSet<String> includeStack,
                                           Array<String> includeTrace) {
        String[] lines = stripBom(source).split("\\r?\\n", -1);
        StringBuilder out = new StringBuilder(source.length() + 256);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // #version is only legal at the root and must be first.
            // Includes containing #version are ignored deliberately.
            if (trimmed.startsWith(VERSION_DIRECTIVE)) {
                continue;
            }

            if (!trimmed.startsWith(INCLUDE_DIRECTIVE)) {
                out.append(line).append('\n');
                continue;
            }

            String includeName = parseIncludeName(trimmed);
            if (includeName == null || includeName.length() == 0) {
                throw new IllegalStateException("Invalid shader include at " + sourceName + ":" + (i + 1)
                        + "\nExpected: #include \"file.glsl\"");
            }

            FileHandle includeFile = resolveInclude(includeName, baseDir, localIncludesDir, sharedIncludesDir);

            if (includeFile == null || !includeFile.exists()) {
                throw new IllegalStateException("Shader include not found at " + sourceName + ":" + (i + 1)
                        + "\nInclude: " + includeName
                        + "\nSearched relative to shader file, local includes/, and shared includes/.");
            }

            out.append("// BEGIN include \"").append(includeName).append("\" from ")
                    .append(includeFile.path()).append('\n');

            out.append(preprocessFile(
                    includeFile,
                    includeFile.parent(),
                    localIncludesDir,
                    sharedIncludesDir,
                    includeStack,
                    includeTrace,
                    false
            ));

            out.append("// END include \"").append(includeName).append("\"\n");
        }

        return out.toString();
    }

    private static String parseIncludeName(String trimmedLine) {
        String rest = trimmedLine.substring(INCLUDE_DIRECTIVE.length()).trim();

        if (!rest.startsWith("\"") || rest.length() < 2) {
            return null;
        }

        int end = rest.indexOf('"', 1);
        if (end <= 1) {
            return null;
        }

        String after = rest.substring(end + 1).trim();
        if (after.length() > 0 && !after.startsWith("//")) {
            return null;
        }

        return rest.substring(1, end).trim();
    }

    private static FileHandle resolveInclude(String includeName,
                                             FileHandle baseDir,
                                             FileHandle localIncludesDir,
                                             FileHandle sharedIncludesDir) {
        if (isUnsafeIncludePath(includeName)) {
            throw new IllegalStateException("Unsafe shader include path rejected: " + includeName);
        }

        if (baseDir != null) {
            FileHandle candidate = baseDir.child(includeName);
            if (candidate.exists()) return candidate;
        }

        if (localIncludesDir != null) {
            FileHandle candidate = localIncludesDir.child(includeName);
            if (candidate.exists()) return candidate;
        }

        if (sharedIncludesDir != null) {
            FileHandle candidate = sharedIncludesDir.child(includeName);
            if (candidate.exists()) return candidate;
        }

        return null;
    }

    private static boolean isUnsafeIncludePath(String includeName) {
        if (includeName == null || isBlank(includeName)) return true;

        String p = includeName.replace('\\', '/');

        return p.startsWith("/")
                || p.contains("://")
                || p.equals("..")
                || p.startsWith("../")
                || p.endsWith("/..")
                || p.contains("/../");
    }

    private static String stripBom(String source) {
        if (source != null && source.length() > 0 && source.charAt(0) == '\uFEFF') {
            return source.substring(1);
        }
        return source;
    }

    private static String normalizePath(FileHandle file) {
        if (file == null) return "<null>";
        return file.path().replace('\\', '/');
    }

    private static String buildCycleMessage(Array<String> trace, String repeated) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < trace.size; i++) {
            sb.append("  ").append(trace.get(i)).append('\n');
        }

        sb.append("  ").append(repeated).append("  <-- cycle");

        return sb.toString();
    }
}