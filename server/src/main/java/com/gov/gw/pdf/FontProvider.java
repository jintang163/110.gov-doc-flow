package com.gov.gw.pdf;

import org.apache.fontbox.ttf.TTFParser;import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 中文字体提供者。查找顺序：app.pdf.font-path -> classpath:fonts/gongwen.ttf -> 常见系统字体。
 * 字体文件同时用于 PDF 套红（PDFBox）与章图生成（AWT）。
 */
@Component
public class FontProvider {
    private static final Logger log = LoggerFactory.getLogger(FontProvider.class);

    private final String configuredPath;
    private volatile File fontFile;
    private volatile java.awt.Font awtFont;

    public FontProvider(@Value("${app.pdf.font-path:}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    public File fontFile() {
        if (fontFile == null) {
            synchronized (this) {
                if (fontFile == null) {
                    fontFile = locate();
                }
            }
        }
        return fontFile;
    }

    private File locate() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            File f = new File(configuredPath);
            if (f.isFile()) return f;
            throw new IllegalStateException("配置的字体文件不存在：" + configuredPath);
        }
        try {
            for (String name : List.of("fonts/gongwen.ttf", "fonts/gongwen.ttc")) {
                ClassPathResource res = new ClassPathResource(name);
                if (res.exists()) {
                    // 解压到临时文件，PDFBox/AWT 均需真实文件
                    Path tmp = Files.createTempFile("gongwen-font", name.substring(name.lastIndexOf('.')));
                    try (InputStream in = res.getInputStream()) {
                        Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    tmp.toFile().deleteOnExit();
                    log.info("使用内置中文字体 {}", name);
                    return tmp.toFile();
                }
            }
        } catch (Exception e) {
            log.warn("内置字体加载失败：{}", e.getMessage());
        }
        List<String> candidates = List.of(
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/usr/share/fonts/wqy-microhei/wqy-microhei.ttc",
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsun.ttc"
        );
        for (String p : candidates) {
            File f = new File(p);
            if (f.isFile()) {
                log.info("使用系统中文字体 {}", p);
                return f;
            }
        }
        throw new IllegalStateException("未找到中文字体，请配置 app.pdf.font-path 指向 TTF/TTC 字体文件");
    }

    /** 供 PDFBox 使用：解析 TTF/TTC */
    public TrueTypeFont loadTtf() {
        File f = fontFile();
        try {
            if (f.getName().toLowerCase().endsWith(".ttc")) {
                // 注意：集合不能关闭，否则返回的字体数据流随之失效
                TrueTypeCollection collection = new TrueTypeCollection(f);
                TrueTypeFont[] holder = new TrueTypeFont[1];
                collection.processAllFonts(ttf -> {
                    if (holder[0] == null) holder[0] = ttf;
                });
                if (holder[0] == null) throw new IllegalStateException("TTC 中无字体");
                return holder[0];
            }
            return new TTFParser().parse(f);
        } catch (Exception e) {
            throw new IllegalStateException("字体解析失败：" + f, e);
        }
    }

    /** 供 AWT（章图生成）使用 */
    public synchronized java.awt.Font awtFont(float size) {
        if (awtFont == null) {
            try {
                awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontFile());
            } catch (Exception e) {
                throw new IllegalStateException("AWT 字体加载失败", e);
            }
        }
        return awtFont.deriveFont(size);
    }
}
