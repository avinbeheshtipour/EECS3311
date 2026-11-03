package com.studyagent;

/**
 * A two-sided study card that tracks how well the user knows it, so review
 * sessions can show weaker cards more often.
 */
public class Flashcard {

    private final String front;
    private final String back;
    private int timesCorrect;
    private int timesSeen;

    public Flashcard(String front, String back) {
        this.front = front;
        this.back = back;
    }

    public String front() {
        return front;
    }

    public String back() {
        return back;
    }

    public int timesCorrect() {
        return timesCorrect;
    }

    public int timesSeen() {
        return timesSeen;
    }

    /** Records one review of this card. */
    public void recordAnswer(boolean correct) {
        timesSeen++;
        if (correct) {
            timesCorrect++;
        }
    }

    /**
     * Estimated chance the user knows this card, from 0 to 1. Smoothed so an
     * unseen card scores 0.5 and a single answer doesn't swing it to 0 or 1.
     */
    public double masteryScore() {
        return (timesCorrect + 1.0) / (timesSeen + 2.0);
    }

    public boolean isLearned() {
        return timesCorrect > 0;
    }
}
