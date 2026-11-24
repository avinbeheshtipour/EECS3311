package com.studyagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the standalone CLI's option parser. They need no API key or network. */
class CommandLineOptionsTest {

    @Test
    void noOptionsMeansInteractiveGemini() throws Exception {
        CommandLineOptions options = CommandLineOptions.parse();

        assertNull(options.input());
        assertNull(options.output());
        assertNull(options.model());
        assertEquals("gemini", options.llmApi());
        assertFalse(options.gui());
        assertFalse(options.help());
        assertFalse(options.nonInteractive());
    }

    @Test
    void readsEveryOption() throws Exception {
        CommandLineOptions options = CommandLineOptions.parse(
                "-LLMAPI", "gemini", "-input", "tasks/sample-task.txt", "-output", "result.txt",
                "-model", "gemini-3.5-flash-lite");

        assertEquals("tasks/sample-task.txt", options.input());
        assertEquals("result.txt", options.output());
        assertEquals("gemini-3.5-flash-lite", options.model());
        assertEquals("gemini", options.llmApi());
        assertTrue(options.nonInteractive());
        assertFalse(options.readsStdin());
    }

    @Test
    void dashAsInputMeansStandardInput() throws Exception {
        CommandLineOptions options = CommandLineOptions.parse("-input", "-");

        assertTrue(options.readsStdin());
        assertTrue(options.nonInteractive());
    }

    @Test
    void optionNamesAreNotCaseSensitive() throws Exception {
        CommandLineOptions options = CommandLineOptions.parse("-llmapi", "Gemini", "-GUI");

        assertEquals("gemini", options.llmApi());
        assertTrue(options.gui());
    }

    @Test
    void helpAndGuiFlags() throws Exception {
        assertTrue(CommandLineOptions.parse("-help").help());
        assertTrue(CommandLineOptions.parse("--help").help());
        assertTrue(CommandLineOptions.parse("-gui", "-model", "gemini-3.8-flash").gui());
    }

    @Test
    void unsupportedLlmApiIsRejectedWithoutUsage() {
        CommandLineOptions.UsageException e = assertThrows(CommandLineOptions.UsageException.class,
                () -> CommandLineOptions.parse("-LLMAPI", "openai"));

        assertTrue(e.getMessage().contains("openai"));
        assertFalse(e.showUsage());
    }

    @Test
    void unknownOptionShowsUsage() {
        CommandLineOptions.UsageException e = assertThrows(CommandLineOptions.UsageException.class,
                () -> CommandLineOptions.parse("-verbose"));

        assertTrue(e.getMessage().contains("-verbose"));
        assertTrue(e.showUsage());
    }

    @Test
    void plainWordIsAnUnknownOption() {
        assertThrows(CommandLineOptions.UsageException.class, () -> CommandLineOptions.parse("task.txt"));
    }

    @Test
    void missingValueIsRejected() {
        assertThrows(CommandLineOptions.UsageException.class, () -> CommandLineOptions.parse("-input"));
        assertThrows(CommandLineOptions.UsageException.class, () -> CommandLineOptions.parse("-output", "-gui"));
        assertThrows(CommandLineOptions.UsageException.class, () -> CommandLineOptions.parse("-model"));
    }

    @Test
    void repeatedOptionIsRejected() {
        assertThrows(CommandLineOptions.UsageException.class,
                () -> CommandLineOptions.parse("-input", "a.txt", "-input", "b.txt"));
    }

    @Test
    void invalidModelNameIsRejected() {
        assertThrows(CommandLineOptions.UsageException.class,
                () -> CommandLineOptions.parse("-model", "gemini/../../x"));
    }

    @Test
    void guiCannotBeCombinedWithATask() {
        assertThrows(CommandLineOptions.UsageException.class,
                () -> CommandLineOptions.parse("-gui", "-input", "tasks/sample-task.txt"));
    }
}
