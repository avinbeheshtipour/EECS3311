package com.studyagent;

import java.util.ArrayList;
import java.util.List;

/**
 * F12 - Q&A history. Keeps the questions asked this session with their
 * answers, oldest first, up to the most recent {@value #MAX_ENTRIES}.
 */
public class ConversationMemory {

    static final int MAX_ENTRIES = 100;

    private final List<Exchange> history = new ArrayList<>();

    public void add(String q, String a) {
        history.add(new Exchange(q, a));
        if (history.size() > MAX_ENTRIES) {
            history.remove(0);
        }
    }

    public List<Exchange> getHistory() {
        return List.copyOf(history);
    }

    /** One question and the answer it received. */
    public record Exchange(String question, String answer) {
    }
}
