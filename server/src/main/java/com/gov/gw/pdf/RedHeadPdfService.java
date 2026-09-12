package com.gov.gw.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gov.gw.common.Jsons;
import com.gov.gw.doc.Document;
import com.gov.gw.template.DocTemplate;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 套红引擎：按党政机关公文版式（GB/T 9704 风格）将正文渲染为红头 PDF。
 * 版式要素：红头大字、发文字号、红线、密级标注、标题、主送、正文、附件说明、落款、成文日期、抄送、页码。
 */
@Service
public class RedHeadPdfService {
    private static final Color RED = new Color(0xD2, 0x00, 0x00);
    private static final Color BLACK = Color.BLACK;
    private static final float PAGE_W = PDRectangle.A4.getWidth();   // 595
    private static final float PAGE_H = PDRectangle.A4.getHeight();  // 842
    private static final float MARGIN_L = 79f;   // 28mm
    private static final float MARGIN_R = 74f;   // 26mm
    private static final float MARGIN_T = 105f;  // 37mm
    private static final float MARGIN_B = 99f;   // 35mm
    private static final float CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R;
    private static final float BODY_SIZE = 16f;  // 三号仿宋
    private static final float LINE_H = 28f;     // 公文固定行距
    private static final String[] SECRET_LABELS = {"公开", "内部", "秘密", "机密"};

    private final FontProvider fontProvider;

    public RedHeadPdfService(FontProvider fontProvider) {
        this.fontProvider = fontProvider;
    }

    public byte[] generate(Document docEntity, DocTemplate template) {
        try (PDDocument pdf = new PDDocument()) {
            TrueTypeFont ttf = fontProvider.loadTtf();
            PDType0Font font = PDType0Font.load(pdf, ttf, true);
            Layout layout = new Layout(pdf, font);

            // ===== 首页版头 =====
            // 密级标注（右上角）
            int level = docEntity.getSecretLevel() == null ? 1 : docEntity.getSecretLevel();
            if (level >= 1 && level < SECRET_LABELS.length) {
                layout.rightText(SECRET_LABELS[level], BODY_SIZE, BLACK, PAGE_H - 72);
            }
            // 红头大字
            String redTitle = template.getRedTitle();
            float redSize = Math.min(60f, CONTENT_W / Math.max(redTitle.length(), 1) * 0.98f);
            layout.centered(redTitle, redSize, RED, PAGE_H - 150);
            // 发文字号
            String docNo = docEntity.getDocNo() == null ? "" : docEntity.getDocNo();
            layout.centered(docNo, BODY_SIZE, BLACK, PAGE_H - 195);
            // 红线
            layout.hline(RED, 2.5f, PAGE_H - 212);

            layout.startBody(PAGE_H - 250);

            // ===== 标题 =====
            for (String line : wrap(docEntity.getTitle(), 22f, CONTENT_W, font)) {
                layout.ensure(LINE_H * 1.4f);
                layout.centered(line, 22f, BLACK, layout.y());
                layout.advance(36f);
            }
            layout.advance(8f);

            // ===== 主送 =====
            if (docEntity.getMainSend() != null && !docEntity.getMainSend().isBlank()) {
                String mainSend = docEntity.getMainSend().trim();
                if (!mainSend.endsWith("：") && !mainSend.endsWith(":")) mainSend += "：";
                for (String line : wrap(mainSend, BODY_SIZE, CONTENT_W, font)) {
                    layout.ensure(LINE_H);
                    layout.text(line, BODY_SIZE, BLACK, MARGIN_L, layout.y());
                    layout.advance(LINE_H);
                }
            }

            // ===== 正文 =====
            String content = docEntity.getContent() == null ? "" : docEntity.getContent();
            for (String para : content.split("\n")) {
                String p = para.trim();
                if (p.isEmpty()) continue;
                List<String> lines = wrap(p, BODY_SIZE, CONTENT_W - 32, font);
                boolean first = true;
                for (String line : lines) {
                    layout.ensure(LINE_H);
                    float x = first ? MARGIN_L + 32 : MARGIN_L;
                    layout.text(line, BODY_SIZE, BLACK, x, layout.y());
                    layout.advance(LINE_H);
                    first = false;
                }
            }

            // ===== 附件说明 =====
            List<Map<String, Object>> attachments = readAttachments(docEntity);
            if (!attachments.isEmpty()) {
                layout.advance(10f);
                for (int i = 0; i < attachments.size(); i++) {
                    String name = String.valueOf(attachments.get(i).get("name"));
                    String line = "附件：" + (attachments.size() > 1 ? (i + 1) + "．" : "") + name;
                    layout.ensure(LINE_H);
                    layout.text(line, BODY_SIZE, BLACK, MARGIN_L + 32, layout.y());
                    layout.advance(LINE_H);
                }
            }

            // ===== 落款 + 成文日期 =====
            layout.ensure(LINE_H * 3);
            layout.advance(20f);
            String issuer = template.getIssuer();
            float issuerX = PAGE_W - MARGIN_R - width(font, issuer, BODY_SIZE) - 16;
            layout.text(issuer, BODY_SIZE, BLACK, issuerX, layout.y());
            layout.advance(LINE_H + 4);
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
            float dateX = PAGE_W - MARGIN_R - width(font, dateStr, BODY_SIZE) - 32;
            layout.text(dateStr, BODY_SIZE, BLACK, dateX, layout.y());

            // ===== 抄送（末页下端） =====
            if (docEntity.getCopySend() != null && !docEntity.getCopySend().isBlank()) {
                float lineY = MARGIN_B + 36;
                layout.hline(BLACK, 0.7f, lineY);
                layout.text("抄送：" + docEntity.getCopySend().trim(), 14f, BLACK, MARGIN_L + 14, lineY - 18);
                layout.hline(BLACK, 0.7f, lineY - 30);
            }

            layout.close();

            // ===== 页码 =====
            int total = pdf.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = pdf.getPage(i);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                        PDPageContentStream.AppendMode.APPEND, true)) {
                    String label = "- " + (i + 1) + " -";
                    float w = width(font, label, 14f);
                    cs.beginText();
                    cs.setFont(font, 14f);
                    cs.setNonStrokingColor(BLACK);
                    cs.newLineAtOffset((PAGE_W - w) / 2, 55);
                    cs.showText(label);
                    cs.endText();
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("套红 PDF 生成失败", e);
        }
    }

    private List<Map<String, Object>> readAttachments(Document docEntity) {
        try {
            if (docEntity.getAttachmentsJson() == null || docEntity.getAttachmentsJson().isBlank()) {
                return List.of();
            }
            return Jsons.read(docEntity.getAttachmentsJson(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static float width(PDType0Font font, String text, float size) {
        try {
            return font.getStringWidth(text) / 1000f * size;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 按版心宽度折行（逐字测量，CJK 友好） */
    private static List<String> wrap(String text, float size, float maxWidth, PDType0Font font) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') continue;
            cur.append(c);
            if (width(font, cur.toString(), size) > maxWidth && cur.length() > 1) {
                cur.setLength(cur.length() - 1);
                lines.add(cur.toString());
                cur = new StringBuilder().append(c);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    /** 版式写入器：管理分页与纵向游标 */
    private class Layout {
        private final PDDocument pdf;
        private final PDType0Font font;
        private PDPageContentStream cs;
        private float y;

        Layout(PDDocument pdf, PDType0Font font) throws IOException {
            this.pdf = pdf;
            this.font = font;
            newPage();
        }

        void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            cs = new PDPageContentStream(pdf, page);
            y = PAGE_H - MARGIN_T;
        }

        /** 首页版头画完后，正文从指定 y 开始 */
        void startBody(float startY) {
            y = startY;
        }

        float y() { return y; }

        void advance(float dy) { y -= dy; }

        void ensure(float needed) throws IOException {
            if (y - needed < MARGIN_B + 40) {
                newPage();
            }
        }

        void text(String s, float size, Color color, float x, float baselineY) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, baselineY);
            cs.showText(s);
            cs.endText();
        }

        void centered(String s, float size, Color color, float baselineY) throws IOException {
            float w = width(font, s, size);
            text(s, size, color, Math.max(MARGIN_L, (PAGE_W - w) / 2), baselineY);
        }

        void rightText(String s, float size, Color color, float baselineY) throws IOException {
            float w = width(font, s, size);
            text(s, size, color, PAGE_W - MARGIN_R - w, baselineY);
        }

        void hline(Color color, float thickness, float lineY) throws IOException {
            cs.setStrokingColor(color);
            cs.setLineWidth(thickness);
            cs.moveTo(MARGIN_L, lineY);
            cs.lineTo(PAGE_W - MARGIN_R, lineY);
            cs.stroke();
        }

        private void closeStream() throws IOException {
            if (cs != null) {
                cs.close();
                cs = null;
            }
        }

        void close() throws IOException {
            closeStream();
        }
    }
}
