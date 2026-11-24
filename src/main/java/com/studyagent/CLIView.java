package com.studyagent;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Command-line view. Reads commands from the keyboard, a task file or standard input and
 * sends every request through {@link StudyController}; it only displays the responses.
 * <p>
 * Interactive mode (a terminal and no -input/-output) shows the banner, help and prompts.
 * Non-interactive mode shows none of them, prints each command as "> command" before its
 * output, prints flashcards instead of reviewing them, and remembers whether any command failed.
 */
public class CLIView {

    private static final String HELP = """
            Type a question and press Enter to ask it, or use one of these commands:
              ask <question>          Ask a question; answers draw on any uploaded material
              upload <file>           Add a PDF or TXT file of course material for answers to draw on
              summarize <file>        Summarize a PDF or TXT file
              quiz <topic>            Take a 5-question multiple-choice quiz
              flashcard <topic>       Review 10 flashcards; missed cards come back until you get them
              plan <topic> <days>     Get a day-by-day study plan, e.g. plan recursion 7
              graph <topic>           Show how the concepts in a topic connect
              progress                Show your quiz scores and flashcard accuracy
              recommend               Get a study suggestion for your weakest topic
              export [md|pdf] [file]  Save your latest study plan or progress summary
              weaktopics              List topics by accuracy, weakest first
              adaptive <topic>        Quiz with difficulty auto-chosen from your progress
              history                 Show the questions you've asked this session
              help                    Show this list of commands
              quit                    Exit
            Every command also works with a leading slash, e.g. /quiz recursion.
            """;

    private final StudyController controller;
    private final BufferedReader in;
    private final PrintStream out;
    private final boolean interactive;
    /** Set when any command fails; non-interactive runs then exit with code 1. */
    private boolean failed;
    /** Set when "quit" is read at the explain prompt of a non-interactive run. */
    private boolean quitRequested;

    /** An interactive view: banner, help and prompts, as typed at a terminal. */
    public CLIView(StudyController controller, BufferedReader in, PrintStream out) {
        this(controller, in, out, true);
    }

    public CLIView(StudyController controller, BufferedReader in, PrintStream out, boolean interactive) {
        this.controller = controller;
        this.in = in;
        this.out = out;
        this.interactive = interactive;
    }

    public static void main(String[] args) {
        CommandLineOptions options;
        try {
            options = CommandLineOptions.parse(args);
        } catch (CommandLineOptions.UsageException e) {
            System.err.println("Error: " + e.getMessage());
            if (e.showUsage()) {
                System.err.println();
                System.err.print(CommandLineOptions.USAGE);
            }
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.print(CommandLineOptions.USAGE);
            return;
        }
        if (options.model() != null) {
            StudyController.useModel(options.model());
        }
        if (options.gui()) {
            MainGUI.main(new String[0]);
            return;
        }

        StudyController controller;
        try {
            controller = new StudyController(new StudyService());
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        if (!options.nonInteractive() && attachedToTerminal()) {
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            new CLIView(controller, stdin, System.out).run();
            return;
        }
        System.exit(runTask(controller, options));
    }

    /**
     * Runs the commands from -input (or standard input) without prompts and returns the exit
     * code: 0 if every command succeeded, 1 if any failed, 2 if a file couldn't be opened.
     */
    private static int runTask(StudyController controller, CommandLineOptions options) {
        BufferedReader in;
        try {
            in = options.readsStdin()
                    ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                    : Files.newBufferedReader(Path.of(options.input()), StandardCharsets.UTF_8);
        } catch (IOException | InvalidPathException e) {
            System.err.println("Error: cannot read the input file " + options.input() + " (" + fileProblem(e) + ").");
            return 2;
        }

        PrintStream out = System.out;
        if (options.output() != null) {
            try {
                out = new PrintStream(Files.newOutputStream(Path.of(options.output())), true, StandardCharsets.UTF_8);
            } catch (IOException | InvalidPathException e) {
                System.err.println("Error: cannot write the output file " + options.output() + " (" + fileProblem(e) + ").");
                return 2;
            }
        }

        CLIView view = new CLIView(controller, in, out, false);
        view.run();
        out.flush();
        if (out != System.out) {
            out.close();
        }
        return view.hasFailures() ? 1 : 0;
    }

    /** A short reason why a task or output file couldn't be opened. */
    private static String fileProblem(Exception e) {
        if (e instanceof java.nio.file.NoSuchFileException) {
            return "file or folder not found";
        }
        if (e instanceof java.nio.file.AccessDeniedException) {
            return "permission denied";
        }
        return describe(e);
    }

    /**
     * True when the program runs in a real terminal. Java 22 and later always return a Console,
     * so its isTerminal() is checked too (called reflectively because the code targets Java 17).
     */
    static boolean attachedToTerminal() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        try {
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (ReflectiveOperationException e) {
            return true; // Java 17-21: a console is only returned for a terminal
        }
    }

    public void run() {
        if (interactive) {
            out.println("AI Study Agent");
            out.println(HELP);
        }

        try {
            String line;
            while (!quitRequested) {
                if (interactive) {
                    out.print("> ");
                    out.flush();
                }
                if ((line = in.readLine()) == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!interactive) {
                    out.println("> " + line);
                }
                if (isQuit(line)) {
                    break;
                }
                try {
                    handle(line);
                } catch (RuntimeException e) {
                    // The controller turns expected problems into error responses; this is a last
                    // safety net so an unexpected bug never ends the session.
                    fail("Something went wrong (" + describe(e) + "). Please try again.");
                }
            }
        } catch (IOException e) {
            out.println("Could not read your input: " + e.getMessage());
            failed = true;
        }
        if (interactive) {
            out.println("Good luck with your studies!");
        }
    }

    /** True if any command in this session failed. */
    public boolean hasFailures() {
        return failed;
    }

    /** Prints an error and records the failure. */
    private void fail(String message) {
        out.println("Error: " + message);
        failed = true;
    }

    private void handle(String line) throws IOException {
        String command = line.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);

        if (line.equalsIgnoreCase("help") || line.equals("/help") || line.equals("?")) {
            out.println(HELP);
        } else if (command.equals("ask") || command.equals("/ask")) {
            printIfArgument(line, "Thinking...");
            show(controller.askQuestion(argument(line)));
        } else if (isFileCommand(line, "upload")) {
            printIfArgument(line, "Uploading...");
            show(controller.uploadFile(argument(line)));
        } else if (isFileCommand(line, "summarize")) {
            printIfArgument(line, "Summarizing...");
            show(controller.summarizeFile(argument(line), (done, total) -> {
                if (total > 1) {
                    out.println("  section " + done + " of " + total + " done");
                }
            }));
        } else if (command.equals("quiz") || command.equals("/quiz")) {
            runQuiz(argument(line));
        } else if (command.matches("/?flashcards?")) {
            runFlashcards(argument(line));
        } else if (command.equals("plan") || command.equals("/plan")) {
            showStudyPlan(argument(line));
        } else if (command.equals("graph") || command.equals("/graph")) {
            showGraph(argument(line));
        } else if (line.equalsIgnoreCase("weaktopics") || line.equals("/weaktopics")) {
            show(controller.weakTopics());
        } else if (command.equals("adaptive") || command.equals("/adaptive")) {
            runAdaptiveQuiz(argument(line));
        } else if (line.equalsIgnoreCase("history") || line.equals("/history")) {
            showHistory();
        } else if (line.equalsIgnoreCase("progress") || line.equals("/progress")) {
            show(controller.progress());
        } else if (line.equalsIgnoreCase("recommend") || line.equals("/recommend")) {
            out.println("Thinking...");
            show(controller.recommend());
        } else if (isExportCommand(line)) {
            export(argument(line));
        } else if (line.startsWith("/")) {
            out.println("Unknown command " + command + ". Type help to see the commands.");
            failed = true;
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
        takeQuiz(controller.generateQuiz(topic));
    }

    /** F12 - Runs a quiz whose difficulty is chosen from recent performance. */
    private void runAdaptiveQuiz(String topic) throws IOException {
        if (!topic.isEmpty()) {
            out.println("Choosing difficulty from your progress and generating quiz...");
        }
        takeQuiz(controller.generateAdaptiveQuiz(topic));
    }

    /** Asks each question of a generated quiz, grades it, then offers explanations (F10). */
    private void takeQuiz(StudyController.QuizResponse response) throws IOException {
        if (!response.success()) {
            fail(response.message());
            return;
        }

        Quiz quiz = response.quiz();
        out.println(interactive
                ? response.message() + " Answer with A-D, press Enter to skip, or type quit to stop."
                : response.message());
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < quiz.size(); i++) {
            out.println("\nQuestion " + (i + 1) + " of " + quiz.size() + ": " + quiz.questions().get(i));
            String answer = readAnswer();
            if (answer == null) {
                out.println("Quiz cancelled.");
                if (!interactive) {
                    failed = true; // the task file ended or said quit before all answers were given
                }
                return;
            }
            answers.add(answer);
        }
        show(controller.submitQuiz(quiz, answers));
        offerExplanations(quiz);
    }

    /**
     * F10 - Lets the user type "explain N" for any question. Enter or quit goes back to the
     * main prompt; any other input is treated as a normal command so it isn't lost.
     */
    private void offerExplanations(Quiz quiz) throws IOException {
        if (interactive) {
            out.println("Type 'explain N' (e.g. explain 2) to see why an answer is correct, or press Enter to continue.");
        }
        while (true) {
            if (interactive) {
                out.print("explain> ");
                out.flush();
            }
            String line = in.readLine();
            if (line == null || line.isBlank()) {
                return;
            }
            if (!interactive) {
                out.println("> " + line.trim());
            }
            if (isQuit(line.trim())) {
                quitRequested = !interactive; // in a task file, quit ends the run
                return;
            }
            String[] parts = line.trim().split("\\s+", 2);
            if (!parts[0].equalsIgnoreCase("explain")) {
                handle(line.trim());
                return;
            }
            if (parts.length < 2 || !parts[1].trim().matches("\\d{1,3}")) {
                out.println("Usage: explain N, where N is a question number from 1 to " + quiz.size() + ".");
                failed = true;
                continue;
            }
            out.println("Explaining...");
            show(controller.explainAnswer(quiz, Integer.parseInt(parts[1].trim()) - 1));
        }
    }

    /** Reviews a generated deck card by card until every card has been answered correctly. */
    private void runFlashcards(String topic) throws IOException {
        if (!topic.isEmpty()) {
            out.println("Generating flashcards...");
        }
        StudyController.FlashcardResponse response = controller.generateFlashcards(topic);
        if (!response.success()) {
            fail(response.message());
            return;
        }

        List<Flashcard> deck = response.cards();
        if (!interactive) {
            // A task file can't know how many lines a review takes, so the cards are only printed.
            out.println(response.message());
            for (int i = 0; i < deck.size(); i++) {
                out.println("\n" + (i + 1) + ". Front: " + deck.get(i).front());
                out.println("   Back:  " + deck.get(i).back());
            }
            out.println();
            return;
        }
        out.println(response.message() + " Press Enter to flip each card, then answer y or n."
                + " Type quit to stop.");

        Flashcard card = null;
        while ((card = controller.nextFlashcard(deck, card)) != null) {
            out.println("\nFront: " + card.front());
            out.print("(press Enter to flip) ");
            out.flush();
            String line = in.readLine();
            if (line == null || isQuit(line.trim())) {
                break;
            }
            out.println("Back:  " + card.back());

            Boolean correct = readYesNo();
            if (correct == null) {
                break;
            }
            controller.recordFlashcardAnswer(card, correct);
        }
        show(controller.finishFlashcards(topic, deck));
    }

    /** Handles "plan <topic> <days>": the last word is the number of days, the rest is the topic. */
    private void showStudyPlan(String arguments) {
        int lastSpace = arguments.lastIndexOf(' ');
        String daysText = lastSpace < 0 ? arguments : arguments.substring(lastSpace + 1);
        String topic = lastSpace < 0 ? "" : arguments.substring(0, lastSpace).trim();
        if (topic.isEmpty() || !daysText.matches("\\d{1,4}")) {
            out.println("Usage: plan <topic> <days>, e.g. plan recursion 7");
            failed = true;
            return;
        }

        out.println("Generating study plan...");
        StudyController.PlanResponse response = controller.generateStudyPlan(topic, Integer.parseInt(daysText));
        if (!response.success()) {
            fail(response.message());
            return;
        }

        out.println("\n" + response.message());
        for (String milestone : response.plan().milestones()) {
            out.println("  " + milestone);
        }
        out.println();
    }

    private void showGraph(String topic) {
        if (!topic.isEmpty()) {
            out.println("Building concept graph...");
        }
        StudyController.GraphResponse response = controller.buildGraph(topic);
        if (!response.success()) {
            fail(response.message());
            return;
        }

        out.println("\n" + response.message());
        response.graph().forEach((concept, related) ->
                out.println("  " + concept + " -> " + (related.isEmpty() ? "(none)" : String.join(", ", related))));
        out.println();
    }

    private void showHistory() {
        List<ConversationMemory.Exchange> history = controller.history();
        if (history.isEmpty()) {
            out.println("No questions asked yet this session.");
            return;
        }
        for (int i = 0; i < history.size(); i++) {
            ConversationMemory.Exchange exchange = history.get(i);
            out.println("\n" + (i + 1) + ". Q: " + exchange.question());
            out.println("   A: " + exchange.answer().replace("\n", "\n      "));
        }
        out.println();
    }

    /** Returns true for y, false for n, or null if input ended or the user typed quit. */
    private Boolean readYesNo() throws IOException {
        while (true) {
            out.print("Did you get it? (y/n): ");
            out.flush();
            String line = in.readLine();
            if (line == null || isQuit(line.trim())) {
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

    /**
     * Returns A-D, "" if skipped, or null if input ended or the user typed quit. In a task file
     * each answer is one line; an invalid line is reported and counted as skipped, so the
     * following lines are still read as the next answers.
     */
    private String readAnswer() throws IOException {
        while (true) {
            if (interactive) {
                out.print("Your answer: ");
                out.flush();
            }
            String line = in.readLine();
            if (line == null) {
                return null;
            }
            if (!interactive) {
                out.println("> " + (line.isBlank() ? "(skipped)" : line.trim()));
            }
            if (isQuit(line.trim())) {
                return null;
            }
            String answer = line.trim().toUpperCase(Locale.ROOT);
            if (answer.isEmpty() || answer.matches("[A-D]")) {
                return answer;
            }
            if (!interactive) {
                fail("\"" + line.trim() + "\" is not an answer (use A, B, C, D or an empty line); counted as skipped.");
                return "";
            }
            out.println("Please enter A, B, C or D (or press Enter to skip).");
        }
    }

    /**
     * Handles "export [md|pdf] [file]". A lone file name ending in .md or .pdf also
     * sets the format, e.g. "export plan.pdf".
     */
    private void export(String arguments) {
        String[] parts = arguments.split("\\s+", 2);
        String lower = arguments.toLowerCase(Locale.ROOT);
        if (arguments.isEmpty()) {
            show(controller.export("md", ""));
        } else if (parts[0].matches("(?i)md|markdown|pdf")) {
            show(controller.export(parts[0], parts.length > 1 ? parts[1] : ""));
        } else if (lower.endsWith(".pdf") || lower.endsWith(".md")) {
            show(controller.export(lower.endsWith(".pdf") ? "pdf" : "md", arguments));
        } else {
            show(controller.export(parts[0], parts.length > 1 ? parts[1] : ""));
        }
    }

    /**
     * Matches "/export ..." always, but bare "export" only on its own, with a single
     * word, or followed by md/pdf, so a question such as "export controls in the US?"
     * is still asked.
     */
    private static boolean isExportCommand(String line) {
        if (line.equals("/export") || line.startsWith("/export ")) {
            return true;
        }
        return line.matches("(?i)export(\\s+\\S+|\\s+(md|markdown|pdf)\\s+.*)?");
    }

    /** Prints a "working..." message, unless the command is missing its argument and will just show an error. */
    private void printIfArgument(String line, String message) {
        if (!argument(line).isEmpty()) {
            out.println(message);
        }
    }

    /** Everything after the command word, e.g. "recursion" from "quiz recursion". */
    private static String argument(String line) {
        String[] parts = line.trim().split("\\s+", 2);
        return parts.length < 2 ? "" : parts[1].trim();
    }

    private static boolean isQuit(String line) {
        return line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")
                || line.equals("/quit") || line.equals("/exit");
    }

    /** A short description of an unexpected error, for messages shown to the user. */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    /**
     * Matches "/name ..." and a bare "name" (which then shows usage), but "name ..." only
     * when it ends in a file extension, so questions such as "upload speed vs download
     * speed?" or "summarize the French Revolution" are still asked.
     */
    private static boolean isFileCommand(String line, String name) {
        if (line.equals("/" + name) || line.startsWith("/" + name + " ") || line.equalsIgnoreCase(name)) {
            return true;
        }
        if (!line.toLowerCase(Locale.ROOT).startsWith(name + " ")) {
            return false;
        }
        String arg = line.substring(name.length() + 1).trim().replaceAll("^[\"']|[\"']$", "");
        return arg.matches(".*\\.[A-Za-z0-9]{1,5}");
    }

    private void show(StudyController.Response response) {
        out.println(response.success() ? "\n" + response.message() + "\n" : "Error: " + response.message());
        if (!response.success()) {
            failed = true;
        }
    }
}
