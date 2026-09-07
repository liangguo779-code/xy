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
 * 将非 Markdown 的知识文件转换为 Markdown 格式文本。对应 Python 服务的
 * {@code rag.converter.convert_to_markdown} 兜底路径：
 * <ul>
 *   <li>{@code .md} 和 {@code .txt} 原样返回（UTF-8 文本）。</li>
 *   <li>{@code .pdf} 使用 Apache PDFBox 读取，用 {@code ## 第 N 页} 标题包裹。</li>
 *   <li>{@code .docx} 使用 Apache POI 读取，{@code Heading 1/2/3} 样式映射为
 *       {@code # / ## / ###}（Word 样式 ID 分别为 1/2/3）。</li>
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
            // 旧版二进制 .doc 格式：POI-XWPF 不支持。抛出明确错误，
            // 让管理员看到有用的信息而非空分块。
            throw new IOException("Legacy .doc format is not supported; please convert to .docx first: " + file);
        }
        throw new IOException("Unsupported file type: " + file);
    }

    private String readPdf(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按页切分并添加标题，使 section splitter 能识别边界。
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
