// Proxy: adds retry and caching before delegating to GeminiClient.
package com.studyagent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Stands in for {@link GeminiClient}: repeated prompts are answered from a
 * cache, and failed requests are retried up to {@value #MAX_ATTEMPTS} times.
 */
public class GeminiProxy {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final GeminiClient client;
    private final Map<String, String> cache = new HashMap<>();

    public GeminiProxy() {
        this.client = GeminiClient.getInstance();
    }

    public String generate(String prompt) throws IOException, InterruptedException {
        synchronized (cache) {
            String cached = cache.get(prompt);
            if (cached != null) {
                return cached;
            }
        }

        String reply = generateWithRetry(prompt);

        synchronized (cache) {
            cache.put(prompt, reply);
        }
        return reply;
    }

    public void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

    private String generateWithRetry(String prompt) throws IOException, InterruptedException {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; ; attempt++) {
            try {
                return client.generate(prompt);
            } catch (IOException e) {
                boolean retryable = !(e instanceof GeminiClient.ApiException apiError) || apiError.isRetryable();
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                System.err.println("Gemini request failed (attempt " + attempt + " of " + MAX_ATTEMPTS
                        + "), retrying in " + backoffMs + " ms: " + e.getMessage());
                Thread.sleep(backoffMs);
                backoffMs *= 2;
            }
        }
    }
}
