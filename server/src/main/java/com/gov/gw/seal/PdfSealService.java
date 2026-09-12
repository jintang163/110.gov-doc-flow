package com.gov.gw.seal;

import com.gov.gw.common.ApiException;
import com.gov.gw.doc.Document;
import com.gov.gw.org.UserEntity;
import com.gov.gw.storage.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/** PDF 盖章：定位盖章、骑缝章；每次盖章生成新 PDF 版本并记录 SHA-256 供验章 */
@Service
public class PdfSealService {
    private final StorageService storage;
    private final SealRecordRepo sealRecordRepo;

    public PdfSealService(StorageService storage, SealRecordRepo sealRecordRepo) {
        this.storage = storage;
        this.sealRecordRepo = sealRecordRepo;
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 在公文当前 PDF 上盖章，返回盖章记录（doc.pdfKey 由调用方更新为 record.pdfKey）。
     *
     * @param type   LOCATE 定位盖章 / STITCH 骑缝章
     * @param pageNo 定位盖章页码（从 1 开始，空则最后一页）
     * @param x/y    定位坐标（PDF 点，左下角原点），空则默认落款处
     * @param width  章面宽度（点），空则 140
     */
    public SealRecord stamp(Document doc, Seal seal, String type, Integer pageNo,
                            Float x, Float y, Float width, UserEntity operator) {
        if (doc.getPdfKey() == null || !storage.exists(doc.getPdfKey())) {
            throw new ApiException("套红 PDF 尚未生成，不能盖章");
        }
        byte[] pdfBytes = storage.getBytes(doc.getPdfKey());
        byte[] sealImg = storage.getBytes(seal.getImageKey());
        byte[] result;
        float usedX, usedY, usedW, usedH;
        int usedPage;

        try (PDDocument pdf = PDDocument.load(pdfBytes)) {
            int pages = pdf.getNumberOfPages();
            if (SealRecord.TYPE_STITCH.equals(type)) {
                stitchAll(pdf, sealImg);
                usedPage = 0;
                usedX = usedY = usedW = usedH = 0;
            } else {
                int idx = (pageNo == null || pageNo < 1) ? pages - 1 : Math.min(pageNo - 1, pages - 1);
                PDPage page = pdf.getPage(idx);
                float w = width == null || width <= 0 ? 140f : width;
                float px = x == null ? page.getMediaBox().getWidth() - w - 90 : x;
                float py = y == null ? 170 : y;
                locate(pdf, page, sealImg, px, py, w, w);
                usedPage = idx + 1;
                usedX = px;
                usedY = py;
                usedW = usedH = w;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            result = out.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("盖章失败：" + e.getMessage(), e);
        }

        String key = "doc/" + doc.getId() + "/pdf/sealed-" + System.currentTimeMillis() + ".pdf";
        storage.put(key, new ByteArrayInputStream(result), result.length);

        SealRecord record = new SealRecord();
        record.setDocId(doc.getId());
        record.setSealId(seal.getId());
        record.setSealName(seal.getName());
        record.setType(type);
        record.setPageNo(usedPage);
        record.setX(usedX);
        record.setY(usedY);
        record.setWidth(usedW);
        record.setHeight(usedH);
        record.setPdfKey(key);
        record.setPdfHash(sha256Hex(result));
        record.setOperatorId(operator.getId());
        record.setOperatorName(operator.getName());
        return sealRecordRepo.save(record);
    }

    /** 定位盖章 */
    private void locate(PDDocument pdf, PDPage page, byte[] sealImg,
                        float x, float y, float w, float h) throws Exception {
        PDImageXObject image = PDImageXObject.createFromByteArray(pdf, sealImg, "seal");
        try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.drawImage(image, x, y, w, h);
        }
    }

    /** 骑缝章：章图纵向切成 N 条，每页右边缘贴一条 */
    private void stitchAll(PDDocument pdf, byte[] sealImg) throws Exception {
        BufferedImage full = ImageIO.read(new ByteArrayInputStream(sealImg));
        int n = pdf.getNumberOfPages();
        if (n == 0) throw new ApiException("PDF 无页面");
        float displayH = 150f;
        float ratio = displayH / full.getHeight();
        int stripW = Math.max(1, full.getWidth() / n);
        for (int i = 0; i < n; i++) {
            int sx = i * stripW;
            int sw = (i == n - 1) ? full.getWidth() - sx : stripW;
            BufferedImage strip = full.getSubimage(sx, 0, sw, full.getHeight());
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            ImageIO.write(strip, "png", buf);
            PDImageXObject image = PDImageXObject.createFromByteArray(pdf, buf.toByteArray(), "strip");
            PDPage page = pdf.getPage(i);
            float pageW = page.getMediaBox().getWidth();
            float pageH = page.getMediaBox().getHeight();
            float stripPtW = sw * ratio;
            float px = pageW - stripPtW / 2f; // 半幅压在页边，形成骑缝效果
            float py = (pageH - displayH) / 2f;
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.drawImage(image, px, py, stripPtW, displayH);
            }
        }
    }
}
