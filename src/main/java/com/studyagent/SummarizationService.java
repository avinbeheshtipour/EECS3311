package com.studyagent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * F06 - Document summarization. Splits a file into chunks, summarizes each
 * chunk in 3 bullet points, then merges those into one final summary.
 */
public class SummarizationService {

    /** Larger documents have adjacent chunks grouped so no more than this many LLM calls are made. */
    static final int MAX_SECTIONS = 30;

    private final KnowledgeRepository knowledgeRepository;
    private final LLMFacade llm;

    public SummarizationService(KnowledgeRepository knowledgeRepository, LLMFacade llm) {
        this.knowledgeRepository = knowledgeRepository;
        this.llm = llm;
    }

    /** Called after each section is summarized, so views can show progress. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int sectionsDone, int totalSections);
    }

    /** The final summary of a file and how many sections it was summarized in. */
    public record DocumentSummary(String source, int sections, String text) {
    }

    public DocumentSummary summarize(Path file) throws IOException, InterruptedException {
        return summarize(file, (done, total) -> { });
    }

    /**
     * @throws IllegalArgumentException if the file is not a .pdf or .txt
     * @throws IOException if the file cannot be read or an LLM request fails
     */
    public DocumentSummary summarize(Path file, ProgressListener progress) throws IOException, InterruptedException {
        List<Chunk> chunks = knowledgeRepository.readChunks(file);
        String source = chunks.get(0).source();
        List<String> sections = groupIntoSections(chunks);

        List<String> sectionSummaries = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            sectionSummaries.add(summarizeSection(sections.get(i)));
            progress.onProgress(i + 1, sections.size());
        }

        String text = sectionSummaries.size() == 1
                ? sectionSummaries.get(0)
                : merge(source, sectionSummaries);
        return new DocumentSummary(source, sections.size(), text);
    }

    private String summarizeSection(String section) throws IOException, InterruptedException {
        Prompt prompt = Prompt.builder()
                .topic("Summarize this in 3 bullet points")
                .context(section)
                .format("exactly 3 bullet points, each starting with \"- \", with no introduction or closing text")
                .build();
        return llm.ask(prompt);
    }

    private String merge(String source, List<String> sectionSummaries) throws IOException, InterruptedException {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sectionSummaries.size(); i++) {
            context.append("Section ").append(i + 1).append(":\n").append(sectionSummaries.get(i)).append("\n\n");
        }

        Prompt prompt = Prompt.builder()
                .topic("Combine these section summaries of " + source + " into one final summary of the whole document")
                .context(context.toString().strip())
                .format("a 2 to 3 sentence overview followed by 5 to 8 bullet points (each starting with \"- \")"
                        + " covering the key ideas in document order, without repeating points"
                        + " and with no other introduction or closing text")
                .build();
        return llm.ask(prompt);
    }

    /** One section per chunk, unless that would exceed {@value #MAX_SECTIONS}; then adjacent chunks are joined. */
    static List<String> groupIntoSections(List<Chunk> chunks) {
        int chunksPerSection = (chunks.size() + MAX_SECTIONS - 1) / MAX_SECTIONS;
        List<String> sections = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += chunksPerSection) {
            StringBuilder section = new StringBuilder();
            for (int i = start; i < Math.min(start + chunksPerSection, chunks.size()); i++) {
                if (section.length() > 0) {
                    section.append(' ');
                }
                section.append(chunks.get(i).content());
            }
            sections.add(section.toString());
        }
        return sections;
    }
}
