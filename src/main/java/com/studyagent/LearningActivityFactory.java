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

    /** The layout {@link #createFlashcard} expects; include it in the prompt that generates the cards. */
    public static final String FLASHCARD_FORMAT = """
            exactly 10 flashcards with no introduction or closing text, each in this layout, \
            with a blank line between cards:
            Q: <term or question for the front>
            A: <short answer for the back>""";

    // "Q: ...", "1. Q: ...", "Question 2: ...", "Front - ..."
    private static final Pattern CARD_FRONT = Pattern.compile(
            "^(?:\\d+\\s*[.)]\\s*)?(?:q(?:uestion)?|front)\\s*\\d*\\s*[:\\-]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    // "A: ...", "Answer: ...", "Back - ..."
    private static final Pattern CARD_BACK = Pattern.compile(
            "^(?:a(?:nswer)?|back)\\s*\\d*\\s*[:\\-]\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    // "Day 1: ...", "Days 2-3: ...", "1. Day 4 to 5 - ...", "Day 6 – ..."
    private static final Pattern MILESTONE = Pattern.compile(
            "^(?:\\d+\\s*[.)]\\s*)?days?\\s+(\\d+)(?:\\s*(?:-|–|—|to)\\s*(\\d+))?\\s*[:\\-–—]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private LearningActivityFactory() {
    }

    /** The layout {@link #createStudyPlan} expects for a plan lasting the given number of days. */
    public static String studyPlanFormat(int days) {
        int maxMilestones = Math.min(days, 10);
        String count = maxMilestones <= 3 ? "exactly " + maxMilestones : "between 3 and " + maxMilestones;
        return "a study plan covering days 1 to " + days + " with " + count + " milestones, in order,"
                + " with no introduction or closing text, one milestone per line in this layout:\n"
                + "Day <n>: <what to study and practise>\n"
                + "or, for a milestone spanning several days:\n"
                + "Days <first>-<last>: <what to study and practise>";
    }

    /**
     * Parses day-labelled milestones in the {@link #studyPlanFormat} layout. The
     * plan's duration is the last day any milestone covers.
     *
     * @throws IllegalArgumentException if no milestone can be found
     */
    public static StudyPlan createStudyPlan(String topic, String llmOutput) {
        List<String> milestones = new ArrayList<>();
        int lastDay = 0;
        boolean milestoneClosed = false;

        for (String rawLine : (llmOutput == null ? "" : llmOutput).split("\\R")) {
            String line = clean(rawLine);
            if (line.isEmpty()) {
                // A blank line ends the milestone, so closing text isn't glued onto it.
                milestoneClosed = true;
                continue;
            }

            Matcher m = MILESTONE.matcher(line);
            if (m.matches()) {
                int first = Integer.parseInt(m.group(1));
                int last = m.group(2) == null ? first : Math.max(first, Integer.parseInt(m.group(2)));
                String label = first == last ? "Day " + first : "Days " + first + "-" + last;
                milestones.add(label + ": " + m.group(3).trim());
                lastDay = Math.max(lastDay, last);
                milestoneClosed = false;
            } else if (!milestones.isEmpty() && !milestoneClosed) {
                // Sub-bullets and wrapped lines belong to the milestone above them.
                int index = milestones.size() - 1;
                String previous = milestones.get(index);
                milestones.set(index, previous + (previous.endsWith(":") ? " " : "; ") + line);
            }
        }

        if (milestones.isEmpty()) {
            throw new IllegalArgumentException("Could not find a day-by-day plan for " + topic
                    + " in the AI's reply. Please try again.");
        }
        return new StudyPlan(topic, milestones, lastDay);
    }

    /**
     * Parses "Q: ... / A: ..." pairs in the {@link #FLASHCARD_FORMAT} layout into cards.
     * A card missing either side is skipped.
     *
     * @throws IllegalArgumentException if no complete card can be found
     */
    public static List<Flashcard> createFlashcard(String topic, String llmOutput) {
        List<Flashcard> cards = new ArrayList<>();
        StringBuilder front = null;
        StringBuilder back = null;
        boolean cardClosed = false;

        for (String rawLine : (llmOutput == null ? "" : llmOutput).split("\\R")) {
            String line = clean(rawLine);
            if (line.isEmpty()) {
                // A blank line after the back ends the card, so closing text isn't glued onto it.
                cardClosed = back != null;
                continue;
            }

            Matcher m;
            if ((m = CARD_FRONT.matcher(line)).matches()) {
                addCardIfComplete(front, back, cards);
                front = new StringBuilder(m.group(1).trim());
                back = null;
                cardClosed = false;
            } else if (front != null && back == null && (m = CARD_BACK.matcher(line)).matches()) {
                back = new StringBuilder(m.group(1).trim());
            } else if (front != null && !cardClosed) {
                // A wrapped line continues whichever side came just before it.
                (back != null ? back : front).append(' ').append(line);
            }
        }
        addCardIfComplete(front, back, cards);

        if (cards.isEmpty()) {
            throw new IllegalArgumentException("Could not find any flashcards on " + topic
                    + " in the AI's reply. Please try again.");
        }
        return cards;
    }

    private static void addCardIfComplete(StringBuilder front, StringBuilder back, List<Flashcard> cards) {
        if (front != null && back != null) {
            cards.add(new Flashcard(front.toString(), back.toString()));
        }
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
