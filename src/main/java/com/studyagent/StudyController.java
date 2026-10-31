// Controller in MVC: decouples View from Model.
package com.studyagent;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

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
}
