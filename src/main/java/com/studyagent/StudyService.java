package com.studyagent;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
    private final SummarizationService summarizationService;
    private final ConversationMemory memory = new ConversationMemory();
    private final ProgressRepository progress = new ProgressRepository();
    private final ExportService exportService = new ExportService();
    private Exportable lastExportable;

    public StudyService() {
        this(new RAGEngine(), new LLMFacade());
    }

    public StudyService(RAGEngine ragEngine, LLMFacade llm) {
        this.ragEngine = ragEngine;
        this.knowledgeRepository = new KnowledgeRepository(ragEngine);
        this.llm = llm;
        this.knowledgeGraph = new KnowledgeGraph(llm);
        this.summarizationService = new SummarizationService(knowledgeRepository, llm);
    }

    /** Indexes notes, slides or readings so later questions can draw on them. */
    public void addMaterial(String text) {
        ragEngine.index(text);
    }

    /** F02 - Parses a PDF or TXT file into chunks and indexes them; returns the new chunks. */
    public List<Chunk> uploadFile(Path file) throws IOException {
        return knowledgeRepository.upload(file);
    }

    /** F06 - Summarizes a PDF or TXT file chunk by chunk; the file is not added to the knowledge base. */
    public SummarizationService.DocumentSummary summarize(Path file, SummarizationService.ProgressListener progress)
            throws IOException, InterruptedException {
        return summarizationService.summarize(file, progress);
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

    /** F07 - Records a graded quiz. */
    public void saveQuizResult(Quiz quiz) {
        progress.saveQuizResult(quiz.topic(), quiz.userScore());
    }

    /** F07 - Records a flashcard session; sessions where no card was reviewed are ignored. */
    public void saveFlashcardResult(String topic, List<Flashcard> deck) {
        int correct = 0;
        int total = 0;
        for (Flashcard card : deck) {
            correct += card.timesCorrect();
            total += card.timesSeen();
        }
        progress.saveFlashcardResult(topic, correct, total);
    }

    /** F07 - All past quiz scores and flashcard results as display text. */
    public String getProgressSummary() {
        String summary = progress.getProgressSummary();
        lastExportable = progressAsMarkdown(summary);
        return summary;
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

        StudyPlan plan = LearningActivityFactory.createStudyPlan(topic.trim(), llm.ask(prompt));
        lastExportable = planAsMarkdown(plan);
        return plan;
    }

    /** F08 - What {@link #exportLast} would export, e.g. "study plan for recursion", or null if nothing yet. */
    public String lastExportableDescription() {
        return lastExportable == null ? null : lastExportable.description();
    }

    /**
     * F08 - Exports the most recent study plan or progress summary, whichever came last.
     *
     * @param pdf      true for PDF, false for Markdown
     * @param filename where to write; null or blank uses a name based on the content
     * @throws IllegalStateException if there is nothing to export yet
     */
    public Path exportLast(boolean pdf, String filename) throws IOException {
        if (lastExportable == null) {
            throw new IllegalStateException("Nothing to export yet. Generate a study plan or view your progress first.");
        }
        String name = (filename == null || filename.isBlank()) ? lastExportable.defaultFilename() : filename;
        return pdf
                ? exportService.exportToPDF(lastExportable.markdown(), name)
                : exportService.exportToMarkdown(lastExportable.markdown(), name);
    }

    private static Exportable planAsMarkdown(StudyPlan plan) {
        StringBuilder markdown = new StringBuilder("# Study plan: ").append(plan.topic()).append("\n\n")
                .append(plan.durationDays()).append(plan.durationDays() == 1 ? " day" : " days").append("\n\n");
        for (String milestone : plan.milestones()) {
            markdown.append("- ").append(milestone).append('\n');
        }
        return new Exportable("study plan for " + plan.topic(), markdown.toString(),
                "study-plan-" + slug(plan.topic()));
    }

    /** Turns the progress summary's "Heading:" lines into Markdown headings and its indented lines into bullets. */
    private static Exportable progressAsMarkdown(String summary) {
        StringBuilder markdown = new StringBuilder("# Study progress\n\n");
        for (String line : summary.split("\\R")) {
            if (line.startsWith("  ")) {
                markdown.append("- ").append(line.strip()).append('\n');
            } else if (line.endsWith(":")) {
                markdown.append("## ").append(line, 0, line.length() - 1).append("\n\n");
            } else if (line.isBlank()) {
                markdown.append('\n');
            } else {
                markdown.append(line).append('\n');
            }
        }
        return new Exportable("progress summary", markdown.toString(), "progress-" + LocalDate.now());
    }

    private static String slug(String text) {
        String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "topic" : slug;
    }

    /** Content ready to export, with a short description and a file name (without extension). */
    private record Exportable(String description, String markdown, String defaultFilename) {
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
