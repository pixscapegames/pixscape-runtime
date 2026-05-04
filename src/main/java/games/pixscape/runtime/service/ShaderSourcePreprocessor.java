package games.pixscape.runtime.service;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * Lightweight GLSL source preprocessor.
 *
 * Supports:
 *   #include "file.glsl"
 *
 * Resolution order:
 *   1. relative to the current shader file directory
 *   2. relative to shader-local includes/ directory
 *   3. relative to shared includes directory, if provided
 *
 * GWT-compatible:
 *   - uses LibGDX FileHandle
 *   - no java.io.File
 *   - no NIO
 *   - no reflection
 */
public final class ShaderSourcePreprocessor {

    private static final String INCLUDE_DIRECTIVE = "#include";

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

        return preprocessFile(shaderFile, shaderFile.parent(), shaderFile.parent().child("includes"), sharedIncludesDir,
                includeStack, includeTrace);
    }

    public static String preprocess(String source, FileHandle baseDir, FileHandle sharedIncludesDir) {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }

        FileHandle localIncludesDir = baseDir != null ? baseDir.child("includes") : null;

        ObjectSet<String> includeStack = new ObjectSet<>();
        Array<String> includeTrace = new Array<>();

        return preprocessSource(source, baseDir, localIncludesDir, sharedIncludesDir, "<memory>",
                includeStack, includeTrace);
    }

    private static String preprocessFile(FileHandle file,
                                         FileHandle baseDir,
                                         FileHandle localIncludesDir,
                                         FileHandle sharedIncludesDir,
                                         ObjectSet<String> includeStack,
                                         Array<String> includeTrace) {
        String key = normalizePath(file);

        if (includeStack.contains(key)) {
            throw new IllegalStateException("Shader include cycle detected:\n" + buildCycleMessage(includeTrace, key));
        }

        includeStack.add(key);
        includeTrace.add(key);

        String source = file.readString("UTF-8");

        String result = preprocessSource(
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

    private static String preprocessSource(String source,
                                           FileHandle baseDir,
                                           FileHandle localIncludesDir,
                                           FileHandle sharedIncludesDir,
                                           String sourceName,
                                           ObjectSet<String> includeStack,
                                           Array<String> includeTrace) {
        String[] lines = source.split("\\r?\\n", -1);
        StringBuilder out = new StringBuilder(source.length() + 256);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (!trimmed.startsWith(INCLUDE_DIRECTIVE)) {
                out.append(line).append('\n');
                continue;
            }

            String includeName = parseIncludeName(trimmed);
            if (includeName == null || includeName.isEmpty()) {
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
                    includeTrace
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
        if (!after.isEmpty() && !after.startsWith("//")) {
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
        if (includeName == null || includeName.isBlank()) return true;

        String p = includeName.replace('\\', '/');

        return p.startsWith("/")
                || p.contains("://")
                || p.equals("..")
                || p.startsWith("../")
                || p.endsWith("/..")
                || p.contains("/../");
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