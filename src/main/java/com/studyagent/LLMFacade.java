// Facade: single entry point hiding GeminiProxy and RAGEngine complexity.
package com.studyagent;

import java.io.IOException;

/**
 * The only class the rest of the application needs to talk to the AI
 * subsystem. Callers build a {@link Prompt} and get back the model's answer,
 * without knowing about caching, retries or the Gemini API.
 */
public class LLMFacade {

    private final GeminiProxy proxy;

    public LLMFacade() {
        this.proxy = new GeminiProxy();
    }

    public String ask(Prompt p) throws IOException, InterruptedException {
        if (p == null) {
            throw new IllegalArgumentException("Prompt must not be null.");
        }
        return proxy.generate(p.toText());
    }
}
