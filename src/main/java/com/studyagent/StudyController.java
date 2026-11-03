// Controller in MVC: decouples View from Model.
package com.studyagent;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * Receives requests from any view (CLI or GUI), forwards them to
 * {@link StudyService}, and returns a {@link Response} the view can display.
 * Views never call the model directly and never handle model exceptions.
 */
public class StudyController {

    private final StudyService service;

    public StudyController(StudyService service) {
        this.service = service;
    }

    public Response askQuestion(String input) {
        if (input == null || input.isBlank()) {
            return Response.error("Please enter a question.");
        }
        try {
            return Response.ok(service.askQuestion(input.trim()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.error("The request was interrupted.");
        } catch (IOException e) {
            return Response.error("Could not get an answer: " + e.getMessage());
        }
    }

    /** F03 - Generates a multiple-choice quiz on the topic. */
    public QuizResponse generateQuiz(String topic) {
        if (topic == null || topic.isBlank()) {
            return QuizResponse.error("Please give a quiz topic, e.g. quiz recursion");
        }
        try {
            return QuizResponse.ok(service.generateQuiz(topic.trim()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return QuizResponse.error("The request was interrupted.");
        } catch (IllegalArgumentException | IOException e) {
            return QuizResponse.error("Could not generate a quiz: " + e.getMessage());
        }
    }

    /** Grades the answers (option letters, in question order) and reports the score with corrections. */
    public Response submitQuiz(Quiz quiz, List<String> answers) {
        int score = quiz.grade(answers);
        StringBuilder result = new StringBuilder("You scored " + score + "/" + quiz.size() + ".");
        for (int i = 0; i < quiz.size(); i++) {
            String given = i < answers.size() ? answers.get(i) : null;
            if (!quiz.isCorrect(i, given)) {
                result.append("\nQuestion ").append(i + 1).append(": correct answer ")
                        .append(quiz.correctAnswers().get(i))
                        .append(given == null || given.isBlank() ? " (skipped)" : ", you answered " + given);
            }
        }
        return Response.ok(result.toString());
    }

    /** F04 - Generates a deck of flashcards on the topic. */
    public FlashcardResponse generateFlashcards(String topic) {
        if (topic == null || topic.isBlank()) {
            return FlashcardResponse.error("Please give a flashcard topic, e.g. flashcard recursion");
        }
        try {
            return FlashcardResponse.ok(topic.trim(), service.generateFlashcards(topic.trim()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FlashcardResponse.error("The request was interrupted.");
        } catch (IllegalArgumentException | IOException e) {
            return FlashcardResponse.error("Could not generate flashcards: " + e.getMessage());
        }
    }

    /** F05 - Generates a study plan for the topic over the given number of days. */
    public PlanResponse generateStudyPlan(String topic, int days) {
        if (topic == null || topic.isBlank()) {
            return PlanResponse.error("Please give a topic, e.g. plan recursion 7");
        }
        try {
            return PlanResponse.ok(service.generateStudyPlan(topic.trim(), days));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PlanResponse.error("The request was interrupted.");
        } catch (IllegalArgumentException | IOException e) {
            return PlanResponse.error("Could not generate a study plan: " + e.getMessage());
        }
    }

    /** The next card to review, or null when every card has been answered correctly. */
    public Flashcard nextFlashcard(List<Flashcard> deck, Flashcard previous) {
        return service.nextFlashcard(deck, previous);
    }

    public void recordFlashcardAnswer(Flashcard card, boolean correct) {
        card.recordAnswer(correct);
    }

    /** End-of-session summary, listing the cards that took more than one try. */
    public Response flashcardSummary(List<Flashcard> deck) {
        int reviews = 0;
        int learned = 0;
        StringBuilder practice = new StringBuilder();
        for (Flashcard card : deck) {
            reviews += card.timesSeen();
            if (card.isLearned()) {
                learned++;
            }
            if (card.timesSeen() > card.timesCorrect()) {
                practice.append("\n  ").append(card.front()).append(" (")
                        .append(card.timesCorrect()).append('/').append(card.timesSeen()).append(" correct)");
            }
        }

        StringBuilder summary = new StringBuilder("Learned " + learned + "/" + deck.size()
                + " cards in " + reviews + (reviews == 1 ? " review." : " reviews."));
        if (practice.length() > 0) {
            summary.append("\nWorth more practice:").append(practice);
        }
        return Response.ok(summary.toString());
    }

    /** Indexes study material typed or pasted directly into the view. */
    public Response addMaterial(String text) {
        if (text == null || text.isBlank()) {
            return Response.error("There is no material to add.");
        }
        service.addMaterial(text);
        return Response.ok("Material added.");
    }

    /** F02 - Uploads a PDF or TXT file into the knowledge base. */
    public Response uploadFile(String path) {
        if (path == null || path.isBlank()) {
            return Response.error("Please give a file path, e.g. upload notes.pdf");
        }
        Path file;
        try {
            file = Path.of(stripQuotes(path.trim()));
        } catch (InvalidPathException e) {
            return Response.error("Invalid file path: " + path.trim());
        }
        try {
            int count = service.uploadFile(file).size();
            return Response.ok("Uploaded " + file.getFileName() + " (" + count
                    + (count == 1 ? " chunk" : " chunks") + ").");
        } catch (IllegalArgumentException | IOException e) {
            return Response.error("Could not upload " + file.getFileName() + ": " + e.getMessage());
        }
    }

    /** Terminals often wrap dragged-in paths containing spaces in quotes. */
    private static String stripQuotes(String path) {
        if (path.length() >= 2 && (path.startsWith("\"") && path.endsWith("\"")
                || path.startsWith("'") && path.endsWith("'"))) {
            return path.substring(1, path.length() - 1);
        }
        return path;
    }

    /** The outcome of a request: either a message to show, or an error to show. */
    public record Response(boolean success, String message) {

        public static Response ok(String message) {
            return new Response(true, message);
        }

        public static Response error(String message) {
            return new Response(false, message);
        }
    }

    /** The outcome of a quiz request: the quiz on success, or an error message to show. */
    public record QuizResponse(boolean success, String message, Quiz quiz) {

        public static QuizResponse ok(Quiz quiz) {
            return new QuizResponse(true, "Generated a " + quiz.size() + "-question quiz on " + quiz.topic() + ".", quiz);
        }

        public static QuizResponse error(String message) {
            return new QuizResponse(false, message, null);
        }
    }

    /** The outcome of a flashcard request: the deck on success, or an error message to show. */
    public record FlashcardResponse(boolean success, String message, List<Flashcard> cards) {

        public static FlashcardResponse ok(String topic, List<Flashcard> cards) {
            return new FlashcardResponse(true, "Generated " + cards.size()
                    + (cards.size() == 1 ? " flashcard" : " flashcards") + " on " + topic + ".", cards);
        }

        public static FlashcardResponse error(String message) {
            return new FlashcardResponse(false, message, null);
        }
    }

    /** The outcome of a study plan request: the plan on success, or an error message to show. */
    public record PlanResponse(boolean success, String message, StudyPlan plan) {

        public static PlanResponse ok(StudyPlan plan) {
            return new PlanResponse(true, "Study plan for " + plan.topic() + " (" + plan.durationDays()
                    + (plan.durationDays() == 1 ? " day):" : " days):"), plan);
        }

        public static PlanResponse error(String message) {
            return new PlanResponse(false, message, null);
        }
    }
}
