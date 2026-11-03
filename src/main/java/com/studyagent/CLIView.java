package com.studyagent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Command-line view. Reads input from stdin and sends every request through
 * {@link StudyController}; it only displays the responses.
 */
public class CLIView {

    private static final String HELP = """
            Type a question and press Enter to ask it.
            Commands:
              upload <file>      Add a PDF or TXT file of course material for answers to draw on
              quiz <topic>       Take a 5-question multiple-choice quiz
              flashcard <topic>  Review 10 flashcards; missed cards come back until you get them
              /help              Show this help
              /quit              Exit
            """;

    private final StudyController controller;
    private final BufferedReader in;
    private final PrintStream out;

    public CLIView(StudyController controller, BufferedReader in, PrintStream out) {
        this.controller = controller;
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) {
        StudyController controller;
        try {
            controller = new StudyController(new StudyService());
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new CLIView(controller, stdin, System.out).run();
    }

    public void run() {
        out.println("AI Study Agent");
        out.println(HELP);

        try {
            String line;
            while (true) {
                out.print("> ");
                out.flush();
                if ((line = in.readLine()) == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.equals("/quit") || line.equals("/exit")) {
                    break;
                }
                handle(line);
            }
        } catch (IOException e) {
            out.println("Input error: " + e.getMessage());
        }
        out.println("Good luck with your studies!");
    }

    private void handle(String line) throws IOException {
        String command = line.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);

        if (line.equals("/help")) {
            out.println(HELP);
        } else if (isUploadCommand(line)) {
            out.println("Uploading...");
            show(controller.uploadFile(argument(line)));
        } else if (command.equals("quiz") || command.equals("/quiz")) {
            runQuiz(argument(line));
        } else if (command.matches("/?flashcards?")) {
            runFlashcards(argument(line));
        } else if (line.startsWith("/")) {
            out.println("Unknown command. Type /help to see the commands.");
        } else {
            out.println("Thinking...");
            show(controller.askQuestion(line));
        }
    }

    /** Generates a quiz, asks each question in turn, then shows the score and corrections. */
    private void runQuiz(String topic) throws IOException {
        if (!topic.isEmpty()) {
            out.println("Generating quiz...");
        }
        StudyController.QuizResponse response = controller.generateQuiz(topic);
        if (!response.success()) {
            out.println("Error: " + response.message());
            return;
        }

        Quiz quiz = response.quiz();
        out.println(response.message() + " Answer with A-D, or press Enter to skip.");
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < quiz.size(); i++) {
            out.println("\nQuestion " + (i + 1) + " of " + quiz.size() + ": " + quiz.questions().get(i));
            String answer = readAnswer();
            if (answer == null) {
                out.println("Quiz cancelled.");
                return;
            }
            answers.add(answer);
        }
        show(controller.submitQuiz(quiz, answers));
    }

    /** Reviews a generated deck card by card until every card has been answered correctly. */
    private void runFlashcards(String topic) throws IOException {
        if (!topic.isEmpty()) {
            out.println("Generating flashcards...");
        }
        StudyController.FlashcardResponse response = controller.generateFlashcards(topic);
        if (!response.success()) {
            out.println("Error: " + response.message());
            return;
        }

        List<Flashcard> deck = response.cards();
        out.println(response.message() + " Press Enter to flip each card, then answer y or n."
                + " Type /quit to stop.");

        Flashcard card = null;
        while ((card = controller.nextFlashcard(deck, card)) != null) {
            out.println("\nFront: " + card.front());
            out.print("(press Enter to flip) ");
            out.flush();
            String line = in.readLine();
            if (line == null || line.trim().equals("/quit")) {
                break;
            }
            out.println("Back:  " + card.back());

            Boolean correct = readYesNo();
            if (correct == null) {
                break;
            }
            controller.recordFlashcardAnswer(card, correct);
        }
        show(controller.flashcardSummary(deck));
    }

    /** Returns true for y, false for n, or null if input ended or the user typed /quit. */
    private Boolean readYesNo() throws IOException {
        while (true) {
            out.print("Did you get it? (y/n): ");
            out.flush();
            String line = in.readLine();
            if (line == null || line.trim().equals("/quit")) {
                return null;
            }
            String answer = line.trim().toLowerCase(Locale.ROOT);
            if (answer.equals("y") || answer.equals("yes")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("no")) {
                return false;
            }
            out.println("Please answer y or n.");
        }
    }

    /** Returns A-D, "" if skipped, or null if input ended or the user typed /quit. */
    private String readAnswer() throws IOException {
        while (true) {
            out.print("Your answer: ");
            out.flush();
            String line = in.readLine();
            if (line == null || line.trim().equals("/quit")) {
                return null;
            }
            String answer = line.trim().toUpperCase(Locale.ROOT);
            if (answer.isEmpty() || answer.matches("[A-D]")) {
                return answer;
            }
            out.println("Please enter A, B, C or D (or press Enter to skip).");
        }
    }

    /** Everything after the command word, e.g. "recursion" from "quiz recursion". */
    private static String argument(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? "" : line.substring(space + 1).trim();
    }

    /**
     * Matches "/upload ..." always, but "upload ..." only when it ends in a file
     * extension, so a question such as "upload speed vs download speed?" is still asked.
     */
    private static boolean isUploadCommand(String line) {
        if (line.equals("/upload") || line.startsWith("/upload ")) {
            return true;
        }
        if (!line.toLowerCase(Locale.ROOT).startsWith("upload ")) {
            return false;
        }
        String arg = line.substring("upload ".length()).trim().replaceAll("^[\"']|[\"']$", "");
        return arg.matches(".*\\.[A-Za-z0-9]{1,5}");
    }

    private void show(StudyController.Response response) {
        out.println(response.success() ? "\n" + response.message() + "\n" : "Error: " + response.message());
    }
}
