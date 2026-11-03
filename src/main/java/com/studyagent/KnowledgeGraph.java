package com.studyagent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F11 - Topic graph. Asks the LLM how the concepts in a topic relate and stores
 * the answer as an undirected adjacency list: each concept maps to the
 * concepts connected to it.
 */
public class KnowledgeGraph {

    static final String GRAPH_FORMAT = """
            the 5 to 8 most important concepts in this topic and how they connect, with no introduction \
            or closing text, one line per concept in this layout:
            <concept>: <related concept>, <related concept>, ...
            The first line must be for the topic itself. Keep every concept name short (1 to 4 words) \
            and spell each concept the same way everywhere it appears""";

    // "Recursion: base case, call stack", "1. **Base case**: recursion"
    private static final Pattern LINE = Pattern.compile("^(?:\\d+\\s*[.)]\\s*)?([^:]{1,60}):\\s*(.+)$");

    private final LLMFacade llm;
    private final Map<String, List<String>> adjacency = new LinkedHashMap<>();

    public KnowledgeGraph(LLMFacade llm) {
        this.llm = llm;
    }

    /**
     * Replaces the graph with one for the given topic and returns it.
     *
     * @throws IllegalArgumentException if the reply contains no connections
     */
    public Map<String, List<String>> buildGraph(String topic) throws IOException, InterruptedException {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Please give a topic, e.g. graph recursion");
        }

        Prompt prompt = Prompt.builder()
                .topic(topic)
                .format(GRAPH_FORMAT)
                .build();
        String reply = llm.ask(prompt);

        adjacency.clear();
        addNode(topic.trim());
        parse(reply);
        if (edgeCount() == 0) {
            adjacency.clear();
            throw new IllegalArgumentException("Could not find any related concepts for " + topic.trim()
                    + " in the AI's reply. Please try again.");
        }
        return getGraph();
    }

    /** A read-only view of the adjacency list, in the order concepts were first mentioned. */
    public Map<String, List<String>> getGraph() {
        Map<String, List<String>> view = new LinkedHashMap<>();
        adjacency.forEach((concept, related) -> view.put(concept, Collections.unmodifiableList(related)));
        return Collections.unmodifiableMap(view);
    }

    public int edgeCount() {
        int ends = 0;
        for (List<String> related : adjacency.values()) {
            ends += related.size();
        }
        return ends / 2;
    }

    private void parse(String reply) {
        for (String rawLine : (reply == null ? "" : reply).split("\\R")) {
            String line = rawLine.replace("**", "").replace("__", "").strip().replaceFirst("^[-*•]\\s+", "");
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String concept = conceptName(m.group(1));
            for (String related : m.group(2).split("[,;]")) {
                addEdge(concept, conceptName(related));
            }
        }
    }

    private void addEdge(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return;
        }
        String from = addNode(a);
        String to = addNode(b);
        if (from.equals(to)) {
            return;
        }
        if (!adjacency.get(from).contains(to)) {
            adjacency.get(from).add(to);
            adjacency.get(to).add(from);
        }
    }

    /** Adds the concept if new and returns the name it is stored under; matching ignores case. */
    private String addNode(String concept) {
        for (String existing : adjacency.keySet()) {
            if (existing.equalsIgnoreCase(concept)) {
                return existing;
            }
        }
        adjacency.put(concept, new ArrayList<>());
        return concept;
    }

    /** Tidies a concept name; "none" (as in "Base case: none") becomes empty and is ignored. */
    private static String conceptName(String text) {
        String name = text.strip().replaceAll("\\.+$", "").replaceAll("\\s+", " ");
        return name.toLowerCase(Locale.ROOT).equals("none") ? "" : name;
    }
}
