// Factory: creates Quiz, Flashcard, StudyPlan from LLM output.
package com.studyagent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw LLM text into learning-activity objects, so the rest of the app
 * never parses model output itself.
 */
public final class LearningActivityFactory {

    /** The layout {@link #createQuiz} expects; include it in the prompt that generates the quiz. */
    public static final String QUIZ_FORMAT = """
            exactly 5 multiple-choice questions numbered 1 to 5, with no introduction or closing text, \
            each in this layout:
            1. <question>
            A) <option>
            B) <option>
            C) <option>
            D) <option>
            Answer: <correct letter>""";

    // "1. ...", "1) ...", "Q1: ...", "Question 1. ..."
    private static final Pattern QUESTION =
            Pattern.compile("^(?:q(?:uestion)?\\s*)?\\d+\\s*[.):]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    // "A) ...", "a. ...", "(B) ...", "C: ..."
    private static final Pattern OPTION =
            Pattern.compile("^\\(?([a-d])\\s*[).:]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    // "Answer: B", "Correct answer - (C) Paris"
    private static final Pattern ANSWER =
            Pattern.compile("^(?:correct\\s+)?answer\\s*[:\\-]?\\s*\\(?([a-d])\\b.*$", Pattern.CASE_INSENSITIVE);

    private LearningActivityFactory() {
    }

    /**
     * Parses numbered multiple-choice questions in the {@link #QUIZ_FORMAT} layout.
     * Questions without an answer or with fewer than two options are skipped.
     *
     * @throws IllegalArgumentException if no complete question can be found
     */
    public static Quiz createQuiz(String topic, String llmOutput) {
        List<String> questions = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        QuestionDraft current = null;

        for (String rawLine : (llmOutput == null ? "" : llmOutput).split("\\R")) {
            String line = clean(rawLine);
            if (line.isEmpty()) {
                continue;
            }

            Matcher m;
            if (current != null && (m = ANSWER.matcher(line)).matches()) {
                current.answer = m.group(1).toUpperCase(Locale.ROOT);
            } else if (current != null && current.answer == null && (m = OPTION.matcher(line)).matches()) {
                current.options.add(m.group(1).toUpperCase(Locale.ROOT) + ") " + m.group(2).trim());
            } else if ((m = QUESTION.matcher(line)).matches()) {
                addIfComplete(current, questions, answers);
                current = new QuestionDraft(m.group(1).trim());
            } else if (current != null && current.answer == null) {
                // A wrapped line continues whatever came just before it.
                if (current.options.isEmpty()) {
                    current.text.append(' ').append(line);
                } else {
                    int last = current.options.size() - 1;
                    current.options.set(last, current.options.get(last) + " " + line);
                }
            }
        }
        addIfComplete(current, questions, answers);

        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Could not find any quiz questions in the AI's reply. Please try again.");
        }
        return new Quiz(topic, questions, answers);
    }

    private static void addIfComplete(QuestionDraft draft, List<String> questions, List<String> answers) {
        if (draft == null || draft.answer == null || draft.options.size() < 2) {
            return;
        }
        boolean answerIsAnOption = draft.options.stream().anyMatch(o -> o.startsWith(draft.answer + ")"));
        if (answerIsAnOption) {
            questions.add(draft.text + "\n" + String.join("\n", draft.options));
            answers.add(draft.answer);
        }
    }

    /** Strips Markdown bullets and bold markers that models often add. */
    private static String clean(String line) {
        return line.replace("**", "").replace("__", "").strip().replaceFirst("^[-*•]\\s+", "");
    }

    private static final class QuestionDraft {
        final StringBuilder text;
        final List<String> options = new ArrayList<>();
        String answer;

        QuestionDraft(String text) {
            this.text = new StringBuilder(text);
        }
    }
}
