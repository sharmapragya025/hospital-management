package hospital;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calls Google's Gemini API (free tier works). The API key stays on the server. */
final class Gemini {
    private final String key;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    Gemini(String key, String model) {
        this.key = key;
        this.model = model;
    }

    boolean enabled() { return key != null && !key.isBlank(); }

    String ask(String prompt, boolean asJson) throws IOException, InterruptedException {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("temperature", 0.3);
        if (asJson) config.put("responseMimeType", "application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", config);

        HttpRequest req = HttpRequest.newBuilder(URI.create(
                        Env.get("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta") + "/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(Json.str(body)))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            String b = res.body();
            throw new IOException("Gemini returned HTTP " + res.statusCode() + ": " + b.substring(0, Math.min(300, b.length())));
        }
        try {
            List<?> candidates = (List<?>) Json.obj(res.body()).get("candidates");
            Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            StringBuilder text = new StringBuilder();
            for (Object part : (List<?>) content.get("parts")) {
                Object t = ((Map<?, ?>) part).get("text");
                if (t != null) text.append(t);
            }
            return text.toString().trim();
        } catch (RuntimeException e) {
            throw new IOException("Unexpected response from Gemini");
        }
    }
}
