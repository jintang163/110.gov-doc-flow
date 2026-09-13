package com.gov.gw.doc;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 站内通知（待办/催办/超时/退回/办结） */
@Entity
@Table(name = "gw_notification", indexes = @Index(name = "idx_notify_user", columnList = "userId,readFlag"))
public class Notification {
    public static final String TYPE_TODO = "TODO";
    public static final String TYPE_URGE = "URGE";
    public static final String TYPE_OVERDUE = "OVERDUE";
    public static final String TYPE_RETURN = "RETURN";
    public static final String TYPE_ARCHIVE = "ARCHIVE";
    public static final String TYPE_SUPERVISE = "SUPERVISE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 512)
    private String content;

    private Long docId;

    @Column(nullable = false)
    private Boolean readFlag = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Boolean getReadFlag() { return readFlag; }
    public void setReadFlag(Boolean readFlag) { this.readFlag = readFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
