package com.studyagent;

import java.io.IOException;

/**
 * Subject interface for the Proxy pattern: both the real {@link GeminiClient} and
 * its stand-in {@link GeminiProxy} implement it, so callers can use either one.
 */
public interface LLMClient {

    /** Sends one prompt to the model and returns its reply text. */
    String generate(String prompt) throws IOException, InterruptedException;
}
