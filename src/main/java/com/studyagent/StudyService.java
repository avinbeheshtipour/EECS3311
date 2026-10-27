package com.studyagent;

import java.io.IOException;
import java.util.List;

/**
 * F01 - Basic AI Q&A. Answers a student's question, grounding the answer in
 * any course material that has been added with {@link #addMaterial(String)}.
 */
public class StudyService {

    private final RAGEngine ragEngine;
    private final LLMFacade llm;

    public StudyService() {
        this(new RAGEngine(), new LLMFacade());
    }

    public StudyService(RAGEngine ragEngine, LLMFacade llm) {
        this.ragEngine = ragEngine;
        this.llm = llm;
    }

    /** Indexes notes, slides or readings so later questions can draw on them. */
    public void addMaterial(String text) {
        ragEngine.index(text);
    }

    public String askQuestion(String input) throws IOException, InterruptedException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Please enter a question.");
        }

        List<String> relevant = ragEngine.retrieve(input);

        Prompt prompt = Prompt.builder()
                .topic(input)
                .context(relevant.isEmpty() ? null : String.join("\n\n---\n\n", relevant))
                .format("a clear, concise answer to the student's question; if context is given,"
                        + " base the answer on it and say when the context does not cover the question")
                .build();

        return llm.ask(prompt);
    }
}
