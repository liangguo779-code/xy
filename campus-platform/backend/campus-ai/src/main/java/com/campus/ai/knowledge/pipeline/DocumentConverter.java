package com.campus.ai.knowledge.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Converts a non-Markdown knowledge file into Markdown-shaped text. Mirrors the Python
 * service's {@code rag.converter.convert_to_markdown} fallback path:
 * <ul>
 *   <li>{@code .md} and {@code .txt} are returned as-is (UTF-8 text).</li>
 *   <li>{@code .pdf} is read with Apache PDFBox and wrapped with {@code ## 第 N 页} headings.</li>
 *   <li>{@code .docx} is read with Apache POI and {@code Heading 1/2/3} styles are mapped to
 *       {@code # / ## / ###} (Word style IDs are 1/2/3 respectively).</li>
 * </ul>
 */
@Slf4j
@Component
public class DocumentConverter {

    public String toMarkdown(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".md") || name.endsWith(".txt")) {
            return Files.readString(file);
        }
        if (name.endsWith(".pdf")) {
            return readPdf(file);
        }
        if (name.endsWith(".docx")) {
            return readDocx(file);
        }
        if (name.endsWith(".doc")) {
            // Legacy binary .doc: not supported by POI-XWPF. Surface a clear error so the admin
            // sees a helpful message rather than an empty chunk.
            throw new IOException("Legacy .doc format is not supported; please convert to .docx first: " + file);
        }
        throw new IOException("Unsupported file type: " + file);
    }

    private String readPdf(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Split per-page and add a heading so the section splitter can pick up boundaries.
            int total = doc.getNumberOfPages();
            StringBuilder out = new StringBuilder();
            for (int p = 1; p <= total; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                out.append("## 第 ").append(p).append(" 页\n\n");
                out.append(stripper.getText(doc));
                out.append("\n\n");
            }
            return out.toString();
        }
    }

    private String readDocx(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder out = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String styleId = p.getStyle();
                String prefix = switch (styleId) {
                    case "1" -> "# ";
                    case "2" -> "## ";
                    case "3" -> "### ";
                    default -> "";
                };
                out.append(prefix).append(p.getText()).append("\n");
            }
            return out.toString();
        }
    }
}
