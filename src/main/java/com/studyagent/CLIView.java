package com.studyagent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Command-line view. Reads input from stdin and sends every request through
 * {@link StudyController}; it only displays the responses.
 */
public class CLIView {

    private static final String HELP = """
            Type a question and press Enter to ask it.
            Commands:
              upload <file>  Add a PDF or TXT file of course material for answers to draw on
              /help          Show this help
              /quit          Exit
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

    private void handle(String line) {
        if (line.equals("/help")) {
            out.println(HELP);
        } else if (isUploadCommand(line)) {
            out.println("Uploading...");
            show(controller.uploadFile(line.substring(line.indexOf("upload") + "upload".length())));
        } else if (line.startsWith("/")) {
            out.println("Unknown command. Type /help to see the commands.");
        } else {
            out.println("Thinking...");
            show(controller.askQuestion(line));
        }
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
