package com.gov.gw.doc;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 流转留痕：每一步操作可追溯 */
@Entity
@Table(name = "gw_doc_trace", indexes = @Index(name = "idx_trace_doc", columnList = "docId"))
public class DocTrace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long docId;

    @Column(nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 32)
    private String actorName;

    /** CREATE/SUBMIT/APPROVE/RETURN_PREV/RETURN_DRAFT/RESUBMIT/SEAL/ISSUE/ARCHIVE/URGE/OVERDUE */
    @Column(nullable = false, length = 24)
    private String action;

    @Column(length = 64)
    private String nodeName;

    @Column(length = 512)
    private String comment;

    @Column(length = 512)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
