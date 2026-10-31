package com.studyagent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * F01 - Basic AI Q&A. Answers a student's question, grounding the answer in
 * any course material that has been added with {@link #addMaterial(String)}.
 */
public class StudyService {

    private final RAGEngine ragEngine;
    private final KnowledgeRepository knowledgeRepository;
    private final LLMFacade llm;

    public StudyService() {
        this(new RAGEngine(), new LLMFacade());
    }

    public StudyService(RAGEngine ragEngine, LLMFacade llm) {
        this.ragEngine = ragEngine;
        this.knowledgeRepository = new KnowledgeRepository(ragEngine);
        this.llm = llm;
    }

    /** Indexes notes, slides or readings so later questions can draw on them. */
    public void addMaterial(String text) {
        ragEngine.index(text);
    }

    /** F02 - Parses a PDF or TXT file into chunks and indexes them; returns the new chunks. */
    public List<Chunk> uploadFile(Path file) throws IOException {
        return knowledgeRepository.upload(file);
    }

    public String askQuestion(String input) throws IOException, InterruptedException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Please enter a question.");
        }

        Prompt prompt = Prompt.builder()
                .topic(input)
                .context(relevantContext(input))
                .format("a clear, concise answer to the student's question; if context is given,"
                        + " base the answer on it and say when the context does not cover the question")
                .build();

        return llm.ask(prompt);
    }

    /** F03 - Generates a 5-question multiple-choice quiz, drawing on uploaded material when relevant. */
    public Quiz generateQuiz(String topic) throws IOException, InterruptedException {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Please give a quiz topic, e.g. quiz recursion");
        }

        Prompt prompt = Prompt.builder()
                .topic(topic)
                .context(relevantContext(topic))
                .format(LearningActivityFactory.QUIZ_FORMAT)
                .build();

        return LearningActivityFactory.createQuiz(topic.trim(), llm.ask(prompt));
    }

    /** The best-matching uploaded chunks joined into one context block, or null if none match. */
    private String relevantContext(String query) {
        List<String> relevant = ragEngine.retrieve(query);
        return relevant.isEmpty() ? null : String.join("\n\n---\n\n", relevant);
    }
}
