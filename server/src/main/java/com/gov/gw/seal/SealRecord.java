package com.gov.gw.seal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 盖章记录（验章依据）：每次盖章生成新 PDF 版本并记录 SHA-256 */
@Entity
@Table(name = "gw_seal_record", indexes = @Index(name = "idx_sealrec_doc", columnList = "docId"))
public class SealRecord {
    public static final String TYPE_LOCATE = "LOCATE";
    public static final String TYPE_STITCH = "STITCH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long docId;

    @Column(nullable = false)
    private Long sealId;

    @Column(nullable = false, length = 64)
    private String sealName;

    /** LOCATE 定位盖章 | STITCH 骑缝章 */
    @Column(nullable = false, length = 8)
    private String type;

    private Integer pageNo;
    private Float x;
    private Float y;
    private Float width;
    private Float height;

    /** 盖章后生成的 PDF 版本 */
    @Column(nullable = false, length = 255)
    private String pdfKey;

    /** 盖章后 PDF 的 SHA-256 */
    @Column(nullable = false, length = 64)
    private String pdfHash;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 32)
    private String operatorName;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Long getSealId() { return sealId; }
    public void setSealId(Long sealId) { this.sealId = sealId; }
    public String getSealName() { return sealName; }
    public void setSealName(String sealName) { this.sealName = sealName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Float getX() { return x; }
    public void setX(Float x) { this.x = x; }
    public Float getY() { return y; }
    public void setY(Float y) { this.y = y; }
    public Float getWidth() { return width; }
    public void setWidth(Float width) { this.width = width; }
    public Float getHeight() { return height; }
    public void setHeight(Float height) { this.height = height; }
    public String getPdfKey() { return pdfKey; }
    public void setPdfKey(String pdfKey) { this.pdfKey = pdfKey; }
    public String getPdfHash() { return pdfHash; }
    public void setPdfHash(String pdfHash) { this.pdfHash = pdfHash; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
