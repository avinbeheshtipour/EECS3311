// Builder: constructs Prompt step-by-step before handing to LLMFacade.
package com.studyagent;

/**
 * An immutable request to the LLM. Create one with {@link #builder()}:
 *
 * <pre>
 * Prompt p = Prompt.builder()
 *         .topic("Recursion")
 *         .context("Lecture notes on the call stack")
 *         .difficulty("beginner")
 *         .format("bullet points")
 *         .build();
 * </pre>
 */
public final class Prompt {

    private final String topic;
    private final String context;
    private final String difficulty;
    private final String format;

    private Prompt(PromptBuilder builder) {
        this.topic = builder.topic;
        this.context = builder.context;
        this.difficulty = builder.difficulty;
        this.format = builder.format;
    }

    public static PromptBuilder builder() {
        return new PromptBuilder();
    }

    public String topic() {
        return topic;
    }

    public String context() {
        return context;
    }

    public String difficulty() {
        return difficulty;
    }

    public String format() {
        return format;
    }

    /** Renders the prompt as the text sent to the model; unset optional fields are left out. */
    public String toText() {
        StringBuilder text = new StringBuilder("Topic: ").append(topic);
        if (context != null) {
            text.append("\n\nUse the following context:\n").append(context);
        }
        if (difficulty != null) {
            text.append("\n\nPitch the answer at a ").append(difficulty).append(" level.");
        }
        if (format != null) {
            text.append("\n\nFormat the answer as: ").append(format).append('.');
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return toText();
    }

    public static class PromptBuilder {

        private String topic;
        private String context;
        private String difficulty;
        private String format;

        private PromptBuilder() {
        }

        public PromptBuilder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public PromptBuilder context(String context) {
            this.context = blankToNull(context);
            return this;
        }

        public PromptBuilder difficulty(String difficulty) {
            this.difficulty = blankToNull(difficulty);
            return this;
        }

        public PromptBuilder format(String format) {
            this.format = blankToNull(format);
            return this;
        }

        /** @throws IllegalStateException if no topic was set */
        public Prompt build() {
            if (topic == null || topic.isBlank()) {
                throw new IllegalStateException("A Prompt requires a topic.");
            }
            topic = topic.trim();
            return new Prompt(this);
        }

        private static String blankToNull(String value) {
            return (value == null || value.isBlank()) ? null : value.trim();
        }
    }
}
