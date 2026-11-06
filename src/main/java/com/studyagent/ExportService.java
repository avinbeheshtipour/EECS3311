package com.studyagent;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * F08 - Export. Writes study content to Markdown or PDF files. Content is
 * expected to be Markdown; in PDFs, "#" headings are drawn in bold.
 */
public class ExportService {

    private static final float MARGIN = 54;
    private static final float BODY_SIZE = 11;
    private static final float HEADING_SIZE = 15;
    private static final float LINE_SPACING = 1.35f;

    private final PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font headingFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** Writes the content as-is; ".md" is added to the filename if missing. Returns the file written. */
    public Path exportToMarkdown(String content, String filename) throws IOException {
        Path file = prepare(filename, ".md");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** Lays the content out on Letter pages; ".pdf" is added to the filename if missing. Returns the file written. */
    public Path exportToPDF(String content, String filename) throws IOException {
        Path file = prepare(filename, ".pdf");

        try (PDDocument document = new PDDocument()) {
            PdfWriter writer = new PdfWriter(document);
            try {
                for (String rawLine : content.split("\\R", -1)) {
                    String line = rawLine.replace("\t", "    ").stripTrailing();
                    if (line.isBlank()) {
                        writer.skip(BODY_SIZE * LINE_SPACING * 0.6f);
                    } else if (line.startsWith("#")) {
                        writer.skip(BODY_SIZE * 0.5f);
                        writer.paragraph(line.replaceFirst("^#+\\s*", ""), headingFont, HEADING_SIZE);
                    } else {
                        writer.paragraph(line, bodyFont, BODY_SIZE);
                    }
                }
            } finally {
                writer.close();
            }
            document.save(file.toFile());
        }
        return file;
    }

    private static Path prepare(String filename, String extension) throws IOException {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Please give a file name.");
        }
        String name = filename.trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(extension)) {
            name += extension;
        }
        Path file = Path.of(name).toAbsolutePath();
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        return file;
    }

    /** Draws wrapped lines top to bottom, starting a new page when one fills up. */
    private static final class PdfWriter {

        private final PDDocument document;
        private final float width = PDRectangle.LETTER.getWidth() - 2 * MARGIN;
        private PDPageContentStream stream;
        private float y;

        PdfWriter(PDDocument document) {
            this.document = document;
        }

        void paragraph(String text, PDType1Font font, float size) throws IOException {
            // Keep a list item's continuation lines aligned under its text.
            String indent = text.replaceFirst("^(\\s*(?:[-*]|\\d+[.)])?\\s*).*$", "$1");
            float indentWidth = width(font, size, indent);
            boolean first = true;
            for (String line : wrap(encodable(font, text.substring(indent.length())), font, size, width - indentWidth)) {
                write(first ? encodable(font, indent) + line : line, first ? 0 : indentWidth, font, size);
                first = false;
            }
        }

        void skip(float height) {
            y -= height;
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
            } else {
                document.addPage(new PDPage(PDRectangle.LETTER));
            }
        }

        private void write(String line, float x, PDType1Font font, float size) throws IOException {
            float leading = size * LINE_SPACING;
            if (stream == null || y - leading < MARGIN) {
                newPage();
            }
            y -= leading;
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN + x, y);
            stream.showText(line);
            stream.endText();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        /** Word-wraps to the given width, breaking words that are too long to fit on a line. */
        private static List<String> wrap(String text, PDType1Font font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.trim().split("\\s+")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (width(font, size, candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                while (width(font, size, word) > maxWidth) {
                    int fit = 1;
                    while (fit < word.length() && width(font, size, word.substring(0, fit + 1)) <= maxWidth) {
                        fit++;
                    }
                    lines.add(word.substring(0, fit));
                    word = word.substring(fit);
                }
                line.append(word);
            }
            if (line.length() > 0 || lines.isEmpty()) {
                lines.add(line.toString());
            }
            return lines;
        }

        private static float width(PDType1Font font, float size, String text) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }

        /** The standard PDF fonts only cover Western European characters; others become "?". */
        private static String encodable(PDType1Font font, String text) {
            StringBuilder result = new StringBuilder(text.length());
            text.codePoints().forEach(cp -> {
                String ch = new String(Character.toChars(cp));
                switch (ch) {
                    case "→" -> result.append("->");
                    case "←" -> result.append("<-");
                    case "≤" -> result.append("<=");
                    case "≥" -> result.append(">=");
                    default -> {
                        if (Character.isISOControl(cp)) {
                            return;
                        }
                        try {
                            font.encode(ch);
                            result.append(ch);
                        } catch (IOException | IllegalArgumentException e) {
                            result.append('?');
                        }
                    }
                }
            });
            return result.toString();
        }
    }
}
