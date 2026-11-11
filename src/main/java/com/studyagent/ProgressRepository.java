package com.studyagent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * F07 - Progress tracking. Records quiz scores and flashcard accuracy for
 * this session, oldest first.
 */
public class ProgressRepository {

    private final List<QuizResult> quizResults = new ArrayList<>();
    private final List<FlashcardResult> flashcardResults = new ArrayList<>();

    /** One completed quiz: how many of its questions were answered correctly, and when. */
    public record QuizResult(String topic, int score, int total, LocalDate date) {
    }

    /** One flashcard session: correct answers out of total cards reviewed. */
    public record FlashcardResult(String topic, int correct, int total) {
    }

    public void saveQuizResult(String topic, int score, int total) {
        if (total <= 0) {
            return;
        }
        quizResults.add(new QuizResult(topic, Math.min(score, total), total, LocalDate.now()));
    }

    public void saveFlashcardResult(String topic, int correct, int total) {
        if (total <= 0) {
            return;
        }
        flashcardResults.add(new FlashcardResult(topic, correct, total));
    }

    public List<QuizResult> quizResults() {
        return List.copyOf(quizResults);
    }

    public List<FlashcardResult> flashcardResults() {
        return List.copyOf(flashcardResults);
    }

    /** All past results as display text, with an average for quizzes and overall flashcard accuracy. */
    public String getProgressSummary() {
        if (quizResults.isEmpty() && flashcardResults.isEmpty()) {
            return "No progress yet. Take a quiz or review flashcards to start tracking.";
        }

        StringBuilder summary = new StringBuilder();
        if (!quizResults.isEmpty()) {
            summary.append("Quiz scores:\n");
            int totalScore = 0;
            int totalQuestions = 0;
            for (QuizResult result : quizResults) {
                summary.append("  ").append(result.date()).append("  ").append(result.topic())
                        .append(": ").append(result.score()).append('/').append(result.total())
                        .append(" correct (").append(percent(result.score(), result.total())).append(")\n");
                totalScore += result.score();
                totalQuestions += result.total();
            }
            summary.append("  Overall: ").append(percent(totalScore, totalQuestions)).append('\n');
        }

        if (!flashcardResults.isEmpty()) {
            if (summary.length() > 0) {
                summary.append('\n');
            }
            summary.append("Flashcard accuracy:\n");
            int correct = 0;
            int total = 0;
            for (FlashcardResult result : flashcardResults) {
                summary.append("  ").append(result.topic()).append(": ")
                        .append(result.correct()).append('/').append(result.total())
                        .append(" correct (").append(percent(result.correct(), result.total())).append(")\n");
                correct += result.correct();
                total += result.total();
            }
            summary.append("  Overall: ").append(percent(correct, total)).append('\n');
        }
        return summary.toString().stripTrailing();
    }

    private static String percent(int correct, int total) {
        return Math.round(100.0 * correct / total) + "%";
    }
}
