package com.studyagent;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * F09 - Recommendations. Finds the topic with the weakest results in
 * {@link ProgressRepository} and asks the LLM how to study it.
 */
public class RecommendationService {

    /** Quiz results store only the number correct; every generated quiz asks for 5 questions. */
    static final int QUESTIONS_PER_QUIZ = 5;

    private final ProgressRepository progress;
    private final LLMFacade llm;

    public RecommendationService(ProgressRepository progress, LLMFacade llm) {
        this.progress = progress;
        this.llm = llm;
    }

    /**
     * Returns the weakest topic with a suggested study approach for it.
     *
     * @throws IllegalStateException if there are no quiz or flashcard results yet
     */
    public String recommend() throws IOException, InterruptedException {
        TopicStats weakest = findWeakestTopic();
        if (weakest == null) {
            throw new IllegalStateException(
                    "No progress yet. Take a quiz or review flashcards so there's something to base a recommendation on.");
        }

        Prompt prompt = Prompt.builder()
                .topic(weakest.topic)
                .context("The student's results on this topic so far:\n" + weakest.details()
                        + "\nOverall: " + weakest.percent() + " correct. This is their weakest topic.")
                .format("a focused study approach for this weak topic: one sentence on what the results suggest,"
                        + " then 3 to 5 numbered, concrete steps for the next study session (they can use this app's"
                        + " quiz, flashcard and plan commands), with no other introduction or closing text")
                .build();

        return "Weakest topic: " + weakest.topic + " (" + weakest.percent() + " correct)\n\n" + llm.ask(prompt);
    }

    /** The topic with the lowest share of correct answers across quizzes and flashcards, or null if none. */
    TopicStats findWeakestTopic() {
        Map<String, TopicStats> byTopic = new LinkedHashMap<>();
        for (ProgressRepository.QuizResult result : progress.quizResults()) {
            TopicStats stats = statsFor(byTopic, result.topic());
            stats.correct += Math.min(result.score(), QUESTIONS_PER_QUIZ);
            stats.total += QUESTIONS_PER_QUIZ;
            stats.details.append("- Quiz on ").append(result.date()).append(": ")
                    .append(result.score()).append('/').append(QUESTIONS_PER_QUIZ).append(" correct\n");
        }
        for (ProgressRepository.FlashcardResult result : progress.flashcardResults()) {
            TopicStats stats = statsFor(byTopic, result.topic());
            stats.correct += result.correct();
            stats.total += result.total();
            stats.details.append("- Flashcard session: ").append(result.correct()).append('/')
                    .append(result.total()).append(" correct\n");
        }

        TopicStats weakest = null;
        for (TopicStats stats : byTopic.values()) {
            if (stats.total > 0 && (weakest == null || stats.accuracy() < weakest.accuracy())) {
                weakest = stats;
            }
        }
        return weakest;
    }


    // ── F11 ─────────────────────────────────────────────────────────────────────

    /** Returns every tracked topic mapped to its accuracy percentage string, weakest first. */
    public java.util.LinkedHashMap<String, String> getAllTopicStats() {
        Map<String, TopicStats> byTopic = new LinkedHashMap<>();
        for (ProgressRepository.QuizResult result : progress.quizResults()) {
            TopicStats stats = statsFor(byTopic, result.topic());
            stats.correct += Math.min(result.score(), QUESTIONS_PER_QUIZ);
            stats.total += QUESTIONS_PER_QUIZ;
        }
        for (ProgressRepository.FlashcardResult result : progress.flashcardResults()) {
            TopicStats stats = statsFor(byTopic, result.topic());
            stats.correct += result.correct();
            stats.total += result.total();
        }
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        byTopic.entrySet().stream()
                .filter(e -> e.getValue().total > 0)
                .sorted(java.util.Comparator.comparingDouble(e -> e.getValue().accuracy()))
                .forEach(e -> out.put(e.getValue().topic, e.getValue().percent()));
        return out;
    }

    // ── F12 ─────────────────────────────────────────────────────────────────────

    /**
     * Chooses quiz difficulty based on overall accuracy across all topics.
     * Below 50 % → Easy, 50–80 % → Medium, above 80 % → Hard.
     * Returns "Medium" when there is no progress data yet.
     */
    public String adaptiveDifficulty() {
        Map<String, TopicStats> byTopic = new LinkedHashMap<>();
        for (ProgressRepository.QuizResult result : progress.quizResults()) {
            TopicStats stats = statsFor(byTopic, result.topic());
            stats.correct += Math.min(result.score(), QUESTIONS_PER_QUIZ);
            stats.total += QUESTIONS_PER_QUIZ;
        }
        for (ProgressRepository.FlashcardResult result : progress.flashcardResults()) {
            TopicStats stats = statsFor(byTopic, result.topic());
            stats.correct += result.correct();
            stats.total += result.total();
        }
        int totalCorrect = 0, totalAnswered = 0;
        for (TopicStats stats : byTopic.values()) {
            totalCorrect += stats.correct;
            totalAnswered += stats.total;
        }
        if (totalAnswered == 0) return "Medium";
        double accuracy = (double) totalCorrect / totalAnswered;
        if (accuracy < 0.50) return "Easy";
        if (accuracy <= 0.80) return "Medium";
        return "Hard";
    }

    /** Groups results by topic, ignoring case and surrounding spaces. */
    private static TopicStats statsFor(Map<String, TopicStats> byTopic, String topic) {
        return byTopic.computeIfAbsent(topic.trim().toLowerCase(Locale.ROOT), key -> new TopicStats(topic.trim()));
    }

    static final class TopicStats {
        final String topic;
        int correct;
        int total;
        final StringBuilder details = new StringBuilder();

        TopicStats(String topic) {
            this.topic = topic;
        }

        double accuracy() {
            return (double) correct / total;
        }

        String percent() {
            return Math.round(100 * accuracy()) + "%";
        }

        String details() {
            return details.toString();
        }
    }
}
