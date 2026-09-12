package com.gov.gw.doc;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 公文（发文） */
@Entity
@Table(name = "gw_document", indexes = {
        @Index(name = "idx_doc_status", columnList = "status"),
        @Index(name = "idx_doc_creator", columnList = "createdBy"),
        @Index(name = "idx_doc_no", columnList = "docNo")
})
public class Document {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private Long flowId;

    /** 提交时流程节点快照（JSON），保证在办件不受流程改版影响 */
    @Lob
    @Column(columnDefinition = "CLOB")
    private String flowSnapshotJson;

    /** 密级：0 公开 1 内部 2 秘密 3 机密 */
    @Column(nullable = false)
    private Integer secretLevel = 1;

    @Column(length = 255)
    private String mainSend;

    @Column(length = 255)
    private String copySend;

    /** 正文（纯文本，套红时按段落排版） */
    @Lob
    @Column(columnDefinition = "CLOB")
    private String content;

    /** 附件 [{name,key,size}] */
    @Lob
    @Column(columnDefinition = "CLOB")
    private String attachmentsJson = "[]";

    @Column(nullable = false, length = 16)
    private String status = STATUS_DRAFT;

    /** 发文字号（签发时分配，退回拟稿则作废） */
    @Column(length = 64)
    private String docNo;

    @Column(length = 64)
    private String currentNodeKey;

    @Column(length = 64)
    private String currentNodeName;

    @Column(length = 64)
    private String processInstanceId;

    /** 提交次数（退回补正后重新提交 +1） */
    @Column(nullable = false)
    private Integer attempt = 0;

    /** 当前套红 PDF（盖章后指向最新版本） */
    @Column(length = 255)
    private String pdfKey;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime submittedAt;
    private LocalDateTime issuedAt;
    private LocalDateTime archivedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public String getFlowSnapshotJson() { return flowSnapshotJson; }
    public void setFlowSnapshotJson(String flowSnapshotJson) { this.flowSnapshotJson = flowSnapshotJson; }
    public Integer getSecretLevel() { return secretLevel; }
    public void setSecretLevel(Integer secretLevel) { this.secretLevel = secretLevel; }
    public String getMainSend() { return mainSend; }
    public void setMainSend(String mainSend) { this.mainSend = mainSend; }
    public String getCopySend() { return copySend; }
    public void setCopySend(String copySend) { this.copySend = copySend; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAttachmentsJson() { return attachmentsJson; }
    public void setAttachmentsJson(String attachmentsJson) { this.attachmentsJson = attachmentsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    public String getCurrentNodeKey() { return currentNodeKey; }
    public void setCurrentNodeKey(String currentNodeKey) { this.currentNodeKey = currentNodeKey; }
    public String getCurrentNodeName() { return currentNodeName; }
    public void setCurrentNodeName(String currentNodeName) { this.currentNodeName = currentNodeName; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public String getPdfKey() { return pdfKey; }
    public void setPdfKey(String pdfKey) { this.pdfKey = pdfKey; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
