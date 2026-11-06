package net.ai.chatbot.service.aiprocess;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Pulls plain text out of the file types the knowledge-base trainer accepts.
 */
@Component
@Slf4j
public class DocumentTextExtractor {

    private static final int MAX_CHARS = 200_000;

    public String extract(byte[] data, String fileName, String contentType) {
        if (data == null || data.length == 0) {
            return "";
        }
        String name = fileName == null ? "" : fileName.toLowerCase();
        String type = contentType == null ? "" : contentType.toLowerCase();
        try {
            String text;
            if (type.contains("pdf") || name.endsWith(".pdf")) {
                try (PDDocument document = PDDocument.load(data)) {
                    text = new PDFTextStripper().getText(document);
                }
            } else if (type.contains("wordprocessingml") || name.endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(data))) {
                    StringBuilder builder = new StringBuilder();
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        builder.append(paragraph.getText()).append('\n');
                    }
                    text = builder.toString();
                }
            } else if (type.contains("spreadsheet") || name.endsWith(".xlsx")) {
                text = extractSpreadsheet(data);
            } else if (isTextual(type, name)) {
                text = new String(data, StandardCharsets.UTF_8);
                if (type.contains("html") || name.endsWith(".html") || name.endsWith(".htm")) {
                    text = stripHtml(text);
                }
            } else {
                log.info("Skipping unsupported knowledge file '{}' ({})", fileName, contentType);
                return "";
            }
            if (text == null) {
                return "";
            }
            text = text.trim();
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS);
            }
            return text;
        } catch (Exception e) {
            log.warn("Could not extract text from '{}': {}", fileName, e.getMessage());
            return "";
        }
    }

    private static boolean isTextual(String type, String name) {
        return type.startsWith("text/")
                || type.contains("json")
                || type.contains("xml")
                || name.endsWith(".txt")
                || name.endsWith(".md")
                || name.endsWith(".csv")
                || name.endsWith(".json")
                || name.endsWith(".html")
                || name.endsWith(".htm")
                || name.endsWith(".xml");
    }

    private static String extractSpreadsheet(byte[] data) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            for (Sheet sheet : workbook) {
                builder.append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        builder.append(cell.toString()).append('\t');
                    }
                    builder.append('\n');
                }
            }
        }
        return builder.toString();
    }

    private static String stripHtml(String html) {
        return html
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
