// Singleton: ensures one shared API connection to Gemini.
package com.studyagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Single shared client for the Gemini generateContent REST endpoint.
 * Obtain it with {@link #getInstance()}.
 */
public final class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private static GeminiClient instance;

    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    private GeminiClient() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String key = dotenv.get("GEMINI_API_KEY");
        if (key == null || key.isBlank() || key.equals("your_key_here")) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set. Copy .env.example to .env and add your key.");
        }
        String configuredModel = dotenv.get("GEMINI_MODEL");

        this.apiKey = key.trim();
        this.model = (configuredModel == null || configuredModel.isBlank()) ? DEFAULT_MODEL : configuredModel.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public static synchronized GeminiClient getInstance() {
        if (instance == null) {
            instance = new GeminiClient();
        }
        return instance;
    }

    /** Sends a single prompt to Gemini and returns the reply text. */
    public String generate(String prompt) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.putArray("contents").addObject()
                .put("role", "user")
                .putArray("parts").addObject().put("text", prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + model + ":generateContent"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), response.body());
        }
        return extractText(response.body());
    }

    public String model() {
        return model;
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode parts = mapper.readTree(responseBody)
                .path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IOException("Gemini returned no content: " + responseBody);
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            text.append(part.path("text").asText(""));
        }
        return text.toString().trim();
    }

    /** A non-200 response from the Gemini API, carrying the HTTP status code. */
    public static class ApiException extends IOException {

        private static final long serialVersionUID = 1L;

        private final int statusCode;

        public ApiException(int statusCode, String body) {
            super("Gemini API error " + statusCode + ": " + body);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }

        /** Rate limits and server errors may succeed on retry; other client errors will not. */
        public boolean isRetryable() {
            return statusCode == 429 || statusCode >= 500;
        }
    }
}
