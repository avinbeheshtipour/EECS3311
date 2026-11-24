package com.studyagent;

import java.util.Locale;

/**
 * Options of the standalone command-line agent (view layer). {@link #parse} only reads the
 * arguments and has no side effects, so it can be unit-tested; {@link CLIView#main} acts on
 * the result.
 *
 * @param input  file of commands to run, {@link #STDIN} for standard input, or null for none
 * @param output file to write all output to, or null for the terminal
 * @param model  Gemini model that overrides GEMINI_MODEL, or null to use .env / the default
 * @param llmApi the LLM API to use; always {@link #DEFAULT_LLM_API}, the only one supported
 * @param gui    open the desktop window instead of the command line
 * @param help   print {@link #USAGE} and exit
 */
public record CommandLineOptions(String input, String output, String model, String llmApi,
                                 boolean gui, boolean help) {

    /** The -input value that means "read the commands from standard input". */
    public static final String STDIN = "-";
    public static final String DEFAULT_LLM_API = "gemini";

    public static final String USAGE = """
            Usage: java -jar target/ai-study-agent.jar [options]

            With no options, starts the interactive command-line agent.

            Options:
              -input <file>    Run the commands in <file>, one per line (use - to read them from standard input)
              -output <file>   Write all output to <file> instead of the terminal
              -model <name>    Gemini model to use, e.g. gemini-3.5-flash-lite (overrides GEMINI_MODEL in .env)
              -LLMAPI <name>   LLM API to use; only "gemini" is supported (the default)
              -gui             Open the desktop window instead of the command line
              -help            Show this help and exit

            The API key is read from GEMINI_API_KEY in .env.
            Exit codes: 0 = success, 1 = a command in the task failed, 2 = invalid options or files.
            """;

    /** True when the options ask for a non-interactive run (no prompts, commands echoed). */
    public boolean nonInteractive() {
        return input != null || output != null;
    }

    /** True when the commands come from standard input rather than a file. */
    public boolean readsStdin() {
        return input == null || input.equals(STDIN);
    }

    /**
     * Parses the command-line arguments. Option names are not case-sensitive.
     *
     * @throws UsageException for an unknown option, a missing or repeated value, an unsupported
     *                        LLM API, an invalid model name, or -gui combined with -input/-output
     */
    public static CommandLineOptions parse(String... args) throws UsageException {
        String input = null;
        String output = null;
        String model = null;
        String llmApi = null;
        boolean gui = false;
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            switch (option.toLowerCase(Locale.ROOT)) {
                case "-input" -> input = value(args, ++i, option, input, true);
                case "-output" -> output = value(args, ++i, option, output, false);
                case "-model" -> {
                    model = value(args, ++i, option, model, false);
                    if (!model.matches("[A-Za-z0-9._-]+")) {
                        throw new UsageException("Invalid model name \"" + model + "\".", false);
                    }
                }
                case "-llmapi" -> {
                    String api = value(args, ++i, option, llmApi, false);
                    if (!api.equalsIgnoreCase(DEFAULT_LLM_API)) {
                        throw new UsageException("Unsupported LLM API \"" + api + "\". Only \""
                                + DEFAULT_LLM_API + "\" is supported.", false);
                    }
                    llmApi = DEFAULT_LLM_API;
                }
                case "-gui" -> gui = true;
                case "-help", "--help", "-h" -> help = true;
                default -> throw new UsageException("Unknown option: " + option, true);
            }
        }
        if (gui && (input != null || output != null)) {
            throw new UsageException("-gui cannot be combined with -input or -output.", true);
        }
        return new CommandLineOptions(input, output, model, llmApi == null ? DEFAULT_LLM_API : llmApi, gui, help);
    }

    /** The value after an option; "-" is only accepted for -input, where it means standard input. */
    private static String value(String[] args, int index, String option, String previous, boolean allowStdin)
            throws UsageException {
        if (previous != null) {
            throw new UsageException(option + " was given more than once.", true);
        }
        if (index >= args.length || args[index].isBlank()
                || (args[index].startsWith("-") && !(allowStdin && args[index].equals(STDIN)))) {
            throw new UsageException("Missing value for " + option + ".", true);
        }
        return args[index];
    }

    /** A problem with the command-line arguments; the CLI prints it and exits with code 2. */
    public static final class UsageException extends Exception {

        private static final long serialVersionUID = 1L;

        private final boolean showUsage;

        public UsageException(String message, boolean showUsage) {
            super(message);
            this.showUsage = showUsage;
        }

        /** Whether the full usage text should be printed after the message. */
        public boolean showUsage() {
            return showUsage;
        }
    }
}
