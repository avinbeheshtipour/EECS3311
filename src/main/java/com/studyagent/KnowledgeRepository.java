package com.studyagent;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * F02 - Knowledge base upload. Reads PDF or TXT course material, splits it
 * into chunks of about 200 words, and indexes them in the {@link RAGEngine}.
 */
public class KnowledgeRepository {

    static final int WORDS_PER_CHUNK = 200;

    private final RAGEngine ragEngine;
    private final List<Chunk> chunks = new ArrayList<>();

    public KnowledgeRepository(RAGEngine ragEngine) {
        this.ragEngine = ragEngine;
    }

    /**
     * Parses the file, indexes its chunks and returns them.
     *
     * @throws IllegalArgumentException if the file is not a .pdf or .txt
     * @throws IOException if the file cannot be read or contains no text
     */
    public List<Chunk> upload(Path file) throws IOException {
        List<Chunk> fileChunks = readChunks(file);
        for (Chunk chunk : fileChunks) {
            ragEngine.index(chunk);
        }
        chunks.addAll(fileChunks);
        return fileChunks;
    }

    /**
     * Parses the file into chunks without indexing them.
     *
     * @throws IllegalArgumentException if the file is not a .pdf or .txt
     * @throws IOException if the file cannot be read or contains no text
     */
    public List<Chunk> readChunks(Path file) throws IOException {
        String text = readText(file);
        String source = file.getFileName().toString();
        List<Chunk> fileChunks = split(text, source);
        if (fileChunks.isEmpty()) {
            throw new IOException(source + " contains no readable text"
                    + (isPdf(source) ? " (scanned PDFs are not supported)." : "."));
        }
        return fileChunks;
    }

    public List<Chunk> chunks() {
        return List.copyOf(chunks);
    }

    String readText(Path file) throws IOException {
        if (Files.isDirectory(file)) {
            throw new IOException(file + " is a folder. Choose a PDF or TXT file inside it.");
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("File not found: " + file);
        }
        if (!Files.isReadable(file)) {
            throw new IOException("You don't have permission to read " + file + ".");
        }

        String name = file.getFileName().toString();
        if (isPdf(name)) {
            try (PDDocument document = Loader.loadPDF(file.toFile())) {
                return new PDFTextStripper().getText(document);
            } catch (InvalidPasswordException e) {
                throw new IOException(name + " is password-protected. Remove the password and try again.", e);
            } catch (IOException e) {
                throw new IOException(name + " could not be read as a PDF; it may be damaged or not really a PDF.", e);
            }
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            // Decoding the bytes directly replaces invalid characters instead of failing.
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Unsupported file type: " + name + " (use .pdf or .txt)");
    }

    /** Splits text into consecutive chunks of {@value #WORDS_PER_CHUNK} words; the last may be shorter. */
    static List<Chunk> split(String text, String source) {
        List<Chunk> result = new ArrayList<>();
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return result;
        }

        List<String> words = List.of(trimmed.split("\\s+"));
        for (int start = 0; start < words.size(); start += WORDS_PER_CHUNK) {
            int end = Math.min(start + WORDS_PER_CHUNK, words.size());
            String content = String.join(" ", words.subList(start, end));
            result.add(new Chunk(source + "#" + result.size(), content, source));
        }
        return result;
    }

    private static boolean isPdf(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
}
