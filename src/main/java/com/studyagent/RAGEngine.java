package com.studyagent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A lightweight retrieval engine: study material is split into chunks, and
 * the chunks sharing the most keywords with a query are returned as context.
 */
public class RAGEngine {

    private static final int MAX_CHUNK_CHARS = 500;
    private static final int TOP_K = 3;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from",
            "how", "in", "is", "it", "me", "of", "on", "or", "that", "the", "this", "to", "was",
            "what", "when", "where", "which", "who", "why", "with", "you", "your");

    private final List<String> chunks = new ArrayList<>();

    /** Splits text into chunks of whole sentences, each at most about 500 characters. */
    public void index(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        for (String paragraph : text.split("\\R\\s*\\R")) {
            StringBuilder current = new StringBuilder();
            for (String sentence : paragraph.trim().split("(?<=[.!?])\\s+")) {
                if (current.length() > 0 && current.length() + sentence.length() + 1 > MAX_CHUNK_CHARS) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                if (current.length() > 0) {
                    current.append(' ');
                }
                current.append(sentence.replaceAll("\\s+", " "));
            }
            if (current.length() > 0) {
                chunks.add(current.toString());
            }
        }
    }

    /** Stores an already-split chunk as-is, without re-splitting it. */
    public void index(Chunk chunk) {
        if (chunk != null && chunk.content() != null && !chunk.content().isBlank()) {
            chunks.add(chunk.content());
        }
    }

    /** Returns up to 3 chunks ranked by how many query keywords they contain; empty if none match. */
    public List<String> retrieve(String query) {
        Set<String> keywords = keywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            int score = score(chunks.get(i), keywords);
            if (score > 0) {
                scored.add(new ScoredChunk(i, score));
            }
        }

        // Highest score first; ties keep document order.
        scored.sort(Comparator.comparingInt(ScoredChunk::score).reversed()
                .thenComparingInt(ScoredChunk::position));

        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_K, scored.size()); i++) {
            results.add(chunks.get(scored.get(i).position()));
        }
        return results;
    }

    public List<String> chunks() {
        return List.copyOf(chunks);
    }

    public void clear() {
        chunks.clear();
    }

    /** Each distinct keyword present counts 10, plus 1 per occurrence, so breadth beats repetition. */
    private static int score(String chunk, Set<String> keywords) {
        List<String> words = tokenize(chunk);
        int score = 0;
        for (String keyword : keywords) {
            int occurrences = 0;
            for (String word : words) {
                if (word.equals(keyword)) {
                    occurrences++;
                }
            }
            if (occurrences > 0) {
                score += 10 + occurrences;
            }
        }
        return score;
    }

    private static Set<String> keywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String word : tokenize(text)) {
            if (word.length() > 1 && !STOP_WORDS.contains(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    private static List<String> tokenize(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    private record ScoredChunk(int position, int score) {
    }
}
