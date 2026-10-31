package com.studyagent;

import java.util.List;
import java.util.Locale;

/**
 * A multiple-choice quiz. Each entry in {@code questions} holds the question
 * text followed by its options ("A) ...", "B) ...") on separate lines, and the
 * matching entry in {@code correctAnswers} is the correct option letter.
 */
public class Quiz {

    private final String topic;
    private final List<String> questions;
    private final List<String> correctAnswers;
    private int userScore;

    public Quiz(String topic, List<String> questions, List<String> correctAnswers) {
        if (questions.size() != correctAnswers.size()) {
            throw new IllegalArgumentException("Each question needs exactly one correct answer.");
        }
        this.topic = topic;
        this.questions = List.copyOf(questions);
        this.correctAnswers = List.copyOf(correctAnswers);
    }

    public String topic() {
        return topic;
    }

    public List<String> questions() {
        return questions;
    }

    public List<String> correctAnswers() {
        return correctAnswers;
    }

    public int userScore() {
        return userScore;
    }

    public int size() {
        return questions.size();
    }

    public boolean isCorrect(int index, String answer) {
        return answer != null && answer.trim().toUpperCase(Locale.ROOT).equals(correctAnswers.get(index));
    }

    /**
     * Scores the given answers (option letters, in question order), stores the
     * result as the user's score and returns it. Missing or blank answers count as wrong.
     */
    public int grade(List<String> answers) {
        int score = 0;
        for (int i = 0; i < size(); i++) {
            if (i < answers.size() && isCorrect(i, answers.get(i))) {
                score++;
            }
        }
        userScore = score;
        return score;
    }
}
