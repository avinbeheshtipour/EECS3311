package com.studyagent;

import java.util.List;

/**
 * A study schedule for one topic. Each milestone is labelled with the day or
 * days it covers, e.g. "Day 1: Read chapter 3" or "Days 2-3: Practice problems".
 */
public class StudyPlan {

    private final String topic;
    private final List<String> milestones;
    private final int durationDays;

    public StudyPlan(String topic, List<String> milestones, int durationDays) {
        if (durationDays < 1) {
            throw new IllegalArgumentException("A study plan must last at least one day.");
        }
        this.topic = topic;
        this.milestones = List.copyOf(milestones);
        this.durationDays = durationDays;
    }

    public String topic() {
        return topic;
    }

    public List<String> milestones() {
        return milestones;
    }

    public int durationDays() {
        return durationDays;
    }
}
