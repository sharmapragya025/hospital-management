package hospital;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Reads settings from environment variables, falling back to a local .env file. */
final class Env {
    private static final Map<String, String> FILE = new HashMap<>();

    static {
        try {
            for (String line : Files.readAllLines(Path.of(".env"))) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq < 1) continue;
                FILE.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim().replaceAll("^\"|\"$", ""));
            }
        } catch (Exception ignored) {
            // No .env file: environment variables only.
        }
    }

    static String get(String key) {
        String v = System.getenv(key);
        return v != null && !v.isBlank() ? v : FILE.get(key);
    }

    static String get(String key, String fallback) {
        String v = get(key);
        return v == null ? fallback : v;
    }
}
