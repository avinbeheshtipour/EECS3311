package com.studyagent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * F01 - Basic AI Q&A. Answers a student's question, grounding the answer in
 * any course material that has been added with {@link #addMaterial(String)}.
 */
public class StudyService {

    static final int MAX_PLAN_DAYS = 365;

    private final RAGEngine ragEngine;
    private final KnowledgeRepository knowledgeRepository;
    private final LLMFacade llm;
    private final KnowledgeGraph knowledgeGraph;
    private final ConversationMemory memory = new ConversationMemory();

    public StudyService() {
        this(new RAGEngine(), new LLMFacade());
    }

    public StudyService(RAGEngine ragEngine, LLMFacade llm) {
        this.ragEngine = ragEngine;
        this.knowledgeRepository = new KnowledgeRepository(ragEngine);
        this.llm = llm;
        this.knowledgeGraph = new KnowledgeGraph(llm);
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

        String answer = llm.ask(prompt);
        memory.add(input.trim(), answer);
        return answer;
    }

    /** F12 - Every question asked this session with its answer, oldest first. */
    public List<ConversationMemory.Exchange> getHistory() {
        return memory.getHistory();
    }

    /** F11 - Builds a graph of the concepts in a topic and how they relate. */
    public Map<String, List<String>> buildGraph(String topic) throws IOException, InterruptedException {
        return knowledgeGraph.buildGraph(topic);
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

    /** F04 - Generates a deck of flashcards, drawing on uploaded material when relevant. */
    public List<Flashcard> generateFlashcards(String topic) throws IOException, InterruptedException {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Please give a flashcard topic, e.g. flashcard recursion");
        }

        Prompt prompt = Prompt.builder()
                .topic(topic)
                .context(relevantContext(topic))
                .format(LearningActivityFactory.FLASHCARD_FORMAT)
                .build();

        return LearningActivityFactory.createFlashcard(topic.trim(), llm.ask(prompt));
    }

    /** F05 - Breaks a topic into day-by-day milestones, drawing on uploaded material when relevant. */
    public StudyPlan generateStudyPlan(String topic, int days) throws IOException, InterruptedException {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Please give a topic, e.g. plan recursion 7");
        }
        if (days < 1 || days > MAX_PLAN_DAYS) {
            throw new IllegalArgumentException("A study plan must be between 1 and " + MAX_PLAN_DAYS + " days.");
        }

        Prompt prompt = Prompt.builder()
                .topic(topic)
                .context(relevantContext(topic))
                .format(LearningActivityFactory.studyPlanFormat(days))
                .build();

        return LearningActivityFactory.createStudyPlan(topic.trim(), llm.ask(prompt));
    }

    /**
     * F04 - Spaced repetition: picks the next card to review, or null once every card
     * has been answered correctly. Unlearned cards come first, weakest (lowest
     * mastery score) first, and the card just shown is not repeated straight away.
     */
    public Flashcard nextFlashcard(List<Flashcard> deck, Flashcard previous) {
        Flashcard next = null;
        for (Flashcard card : deck) {
            if (card.isLearned() || (card == previous && countUnlearned(deck) > 1)) {
                continue;
            }
            if (next == null || card.masteryScore() < next.masteryScore()) {
                next = card;
            }
        }
        return next;
    }

    private static int countUnlearned(List<Flashcard> deck) {
        int count = 0;
        for (Flashcard card : deck) {
            if (!card.isLearned()) {
                count++;
            }
        }
        return count;
    }

    /** The best-matching uploaded chunks joined into one context block, or null if none match. */
    private String relevantContext(String query) {
        List<String> relevant = ragEngine.retrieve(query);
        return relevant.isEmpty() ? null : String.join("\n\n---\n\n", relevant);
    }
}
