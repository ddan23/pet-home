package com.hnist.support;

import com.hnist.common.exception.BusinessException;
import com.hnist.config.HealthReportPdfProperties;
import com.hnist.domain.HealthCitation;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 健康报告 PDF 渲染器：把 AI 生成的报告文本排版为 A4 多页 PDF，返回字节数组。
 * 内容过长自动换行、自动分页；字体缺失或读取失败抛 {@link BusinessException}(500)，不产出残缺文件。
 */
@Component
public class HealthReportPdfRenderer {

    private static final float PAGE_MARGIN = 50.0F;
    private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - PAGE_MARGIN * 2;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 未配置字体时按顺序探测的本机中文字体候选路径（Windows 优先，其次 Linux Noto）。 */
    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/Deng.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.otf"
    );

    private final HealthReportPdfProperties properties;

    public HealthReportPdfRenderer(HealthReportPdfProperties properties) {
        this.properties = properties;
    }

    /**
     * 渲染完整报告。所有文本字段均做 null 安全处理；引用列表为空时输出固定占位文案；
     * 出生日期为空或晚于报告日时年龄显示「未记录」。IO 失败抛 {@link IOException}。
     */
    public byte[] render(String petName, String species, LocalDate birthDate,
                         LocalDateTime generatedAt, String content,
                         List<HealthCitation> citations,
                         String disclaimer) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = loadFont(document);
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle("宠物健康报告");
            information.setAuthor("猫狗宠物之家");
            information.setSubject("宠物健康报告");
            PdfTextWriter writer = new PdfTextWriter(document, font);
            writer.start();
            writer.writeParagraph("宠物健康报告", 20.0F, 30.0F);
            writer.writeParagraph("宠物：" + safeText(petName)
                    + "    物种：" + safeText(species)
                    + "    年龄：" + ageText(birthDate, generatedAt.toLocalDate()), 11.0F, 20.0F);
            writer.writeParagraph("报告时间：" + DATE_TIME_FORMATTER.format(generatedAt), 10.0F, 22.0F);
            writer.writeParagraph(content, 11.0F, 18.0F);
            writer.writeParagraph("资料来源", 14.0F, 22.0F);
            writer.writeParagraph(citationText(citations), 10.0F, 17.0F);
            writer.writeParagraph("免责声明", 14.0F, 22.0F);
            writer.writeParagraph(disclaimer, 10.0F, 18.0F);
            writer.finish();
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * 字体加载顺序：显式配置路径（配置了但文件不存在直接报错，不做静默降级）
     * → classpath 内置字体 → 系统候选路径；全部落空抛 {@link BusinessException}(500)。
     */
    private PDType0Font loadFont(PDDocument document) throws IOException {
        String configuredPath = properties.getFontPath();
        if (configuredPath != null && !configuredPath.isBlank()) {
            File configuredFont = new File(configuredPath.trim());
            if (configuredFont.isFile()) {
                return PDType0Font.load(document, configuredFont);
            }
            throw new BusinessException(500, "健康报告 PDF 字体文件不存在");
        }
        try (var packagedFont = getClass().getResourceAsStream(
                "/fonts/MaShanZheng-Regular.ttf")) {
            if (packagedFont != null) {
                return PDType0Font.load(document, packagedFont);
            }
        }
        for (String candidate : FONT_CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return PDType0Font.load(document, path.toFile());
            }
        }
        throw new BusinessException(500, "健康报告 PDF 中文字体未配置");
    }

    private String citationText(List<HealthCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "本报告本次没有可列出的外部引用。";
        }
        StringBuilder text = new StringBuilder();
        for (HealthCitation citation : citations) {
            text.append('[').append(safeText(citation.getReferenceId())).append("] ")
                    .append(safeText(citation.getTitle()))
                    .append(" - ").append(safeText(citation.getPublisher()))
                    .append(" - ").append(safeText(citation.getSourceUrl()))
                    .append('\n');
        }
        return text.toString();
    }

    private String ageText(LocalDate birthDate, LocalDate reportDate) {
        if (birthDate == null || birthDate.isAfter(reportDate)) {
            return "未记录";
        }
        Period period = Period.between(birthDate, reportDate);
        if (period.getYears() > 0) {
            return period.getYears() + "岁" + period.getMonths() + "个月";
        }
        return Math.max(period.getMonths(), 0) + "个月";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private static final class PdfTextWriter {

        private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
        private static final float BOTTOM_MARGIN = 46.0F;
        private static final float LINE_FACTOR = 1.55F;

        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream contentStream;
        private float cursorY;

        private PdfTextWriter(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        private void start() throws IOException {
            startPage();
        }

        /**
     * 逐段写入文本：按字体实测宽度逐码点折行，底部空间不足时自动换页。
     * 字体不支持的码点统一替换为空格，避免 PDF 写入抛异常导致整份报告失败。
     */
    private void writeParagraph(String text, float fontSize, float spaceAfter)
                throws IOException {
            String supportedText = supportedText(text);
            String[] paragraphs = supportedText.split("\\R", -1);
            for (String paragraph : paragraphs) {
                writeWrappedLine(paragraph, fontSize);
            }
            cursorY -= spaceAfter;
        }

        private void writeWrappedLine(String text, float fontSize) throws IOException {
            float leading = fontSize * LINE_FACTOR;
            if (text.isEmpty()) {
                ensureSpace(leading);
                cursorY -= leading;
                return;
            }
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                String candidate = line + character;
                if (!line.isEmpty() && textWidth(candidate, fontSize) > CONTENT_WIDTH) {
                    drawLine(line.toString(), fontSize, leading);
                    line.setLength(0);
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty()) {
                drawLine(line.toString(), fontSize, leading);
            }
        }

        private void drawLine(String text, float fontSize, float leading) throws IOException {
            ensureSpace(leading);
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(PAGE_MARGIN, cursorY);
            contentStream.showText(text);
            contentStream.endText();
            cursorY -= leading;
        }

        private float textWidth(String text, float fontSize) throws IOException {
            return font.getStringWidth(text) / 1000.0F * fontSize;
        }

        private String supportedText(String value) throws IOException {
            if (value == null) {
                return "";
            }
            StringBuilder result = new StringBuilder(value.length());
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                if (codePoint == '\n') {
                    result.appendCodePoint(codePoint);
                } else if (codePoint == '\r') {
                    // 段落切分已统一处理换行，忽略回车字符。
                } else if (codePoint == '\t') {
                    result.append(' ');
                } else if (codePoint >= 32 && supportsCodePoint(codePoint)) {
                    result.appendCodePoint(codePoint);
                } else {
                    result.append(' ');
                }
                offset += Character.charCount(codePoint);
            }
            return result.toString();
        }

        private boolean supportsCodePoint(int codePoint) throws IOException {
            try {
                font.getStringWidth(new String(Character.toChars(codePoint)));
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private void ensureSpace(float leading) throws IOException {
            if (cursorY - leading >= BOTTOM_MARGIN) {
                return;
            }
            finishPage();
            startPage();
        }

        private void startPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            cursorY = PAGE_HEIGHT - PAGE_MARGIN;
        }

        private void finishPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }

        private void finish() throws IOException {
            finishPage();
        }
    }
}
