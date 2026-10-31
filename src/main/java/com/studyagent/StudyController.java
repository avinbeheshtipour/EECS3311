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
}
