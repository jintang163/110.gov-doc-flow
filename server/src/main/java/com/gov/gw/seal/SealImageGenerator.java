package com.gov.gw.seal;

import com.gov.gw.pdf.FontProvider;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/**
 * 演示章图生成器：红色圆形公章（外圈 + 五角星 + 环形单位名称 + 底部横排文字）。
 * 生产环境应替换为 CA 机构签发的真实电子印章，此处用于流程演示与联调。
 */
@Component
public class SealImageGenerator {
    private static final Color SEAL_RED = new Color(0xC8, 0x00, 0x00);
    private final FontProvider fontProvider;

    public SealImageGenerator(FontProvider fontProvider) {
        this.fontProvider = fontProvider;
    }

    public byte[] generate(String orgText, String bottomText) {
        int size = 400;
        int cx = size / 2;
        int cy = size / 2;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(SEAL_RED);

        // 外圈
        g.setStroke(new BasicStroke(12f));
        g.drawOval(16, 16, size - 32, size - 32);

        // 五角星
        g.fill(star(cx, cy, 58));

        // 环形单位名称（上半圆，自左而右）
        if (orgText != null && !orgText.isBlank()) {
            drawArcText(g, orgText.trim(), cx, cy, 148, 40);
        }

        // 底部横排文字
        if (bottomText != null && !bottomText.isBlank()) {
            java.awt.Font font = fontProvider.awtFont(34f);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            String text = bottomText.trim();
            int w = fm.stringWidth(text);
            g.drawString(text, cx - w / 2f, cy + 118);
        }

        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("章图生成失败", e);
        }
    }

    private Path2D star(double cx, double cy, double r) {
        Path2D path = new Path2D.Double();
        double inner = r * 0.382;
        for (int i = 0; i < 10; i++) {
            double radius = (i % 2 == 0) ? r : inner;
            double angle = Math.toRadians(-90 + i * 36);
            double x = cx + radius * Math.cos(angle);
            double y = cy + radius * Math.sin(angle);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.closePath();
        return path;
    }

    /** 沿上半圆排布文字，字头朝外 */
    private void drawArcText(Graphics2D g, String text, int cx, int cy, int radius, float fontSize) {
        char[] chars = text.toCharArray();
        int n = chars.length;
        if (n == 0) return;
        java.awt.Font font = fontProvider.awtFont(fontSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        // 总跨度随字数变化，最多铺满 300°
        double spread = Math.toRadians(Math.min(300, 36 * n));
        double start = Math.toRadians(90) + spread / 2;
        double step = n > 1 ? spread / (n - 1) : 0;
        for (int i = 0; i < n; i++) {
            double angle = start - i * step;
            double x = cx + radius * Math.cos(angle);
            double y = cy - radius * Math.sin(angle);
            AffineTransform old = g.getTransform();
            g.translate(x, y);
            g.rotate(Math.PI / 2 - angle);
            String s = String.valueOf(chars[i]);
            int w = fm.stringWidth(s);
            g.drawString(s, -w / 2f, fm.getAscent() / 2.6f);
            g.setTransform(old);
        }
    }
}
