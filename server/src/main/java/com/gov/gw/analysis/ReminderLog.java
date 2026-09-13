package com.gov.gw.analysis;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 督办单：超时未办结或催办次数过多的公文，自动生成并推送分管领导 */
@Entity
@Table(name = "reminder_log", indexes = {
        @Index(name = "idx_reminder_doc", columnList = "docId,status"),
        @Index(name = "idx_reminder_status", columnList = "status")
})
public class ReminderLog {
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_HANDLED = "HANDLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long docId;

    @Column(length = 64)
    private String docNo;

    @Column(length = 128)
    private String title;

    /** 拟稿部门 */
    @Column(length = 64)
    private String orgName;

    /** 督办原因：超时未办结 / 催办≥3次（可并列） */
    @Column(nullable = false, length = 128)
    private String reason;

    @Column(nullable = false)
    private Integer overdueDays = 0;

    @Column(nullable = false)
    private Integer urgeCount = 0;

    @Column(length = 64)
    private String currentNodeName;

    /** 当前办理人（责任人），逗号分隔 */
    @Column(length = 255)
    private String assigneeNames;

    /** 推送的分管领导 */
    private Long leaderId;

    @Column(length = 32)
    private String leaderName;

    @Column(nullable = false, length = 16)
    private String status = STATUS_OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime handledAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Integer overdueDays) { this.overdueDays = overdueDays; }
    public Integer getUrgeCount() { return urgeCount; }
    public void setUrgeCount(Integer urgeCount) { this.urgeCount = urgeCount; }
    public String getCurrentNodeName() { return currentNodeName; }
    public void setCurrentNodeName(String currentNodeName) { this.currentNodeName = currentNodeName; }
    public String getAssigneeNames() { return assigneeNames; }
    public void setAssigneeNames(String assigneeNames) { this.assigneeNames = assigneeNames; }
    public Long getLeaderId() { return leaderId; }
    public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
}
