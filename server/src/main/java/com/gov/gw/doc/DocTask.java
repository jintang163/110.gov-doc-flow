package com.gov.gw.doc;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 办理任务（待办/已办），与 Camunda 用户任务一一对应 */
@Entity
@Table(name = "gw_doc_task", indexes = {
        @Index(name = "idx_task_assignee", columnList = "assigneeId,status"),
        @Index(name = "idx_task_doc", columnList = "docId")
})
public class DocTask {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_CANCELED = "CANCELED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long docId;

    @Column(nullable = false, length = 64)
    private String nodeKey;

    @Column(nullable = false, length = 64)
    private String nodeName;

    @Column(nullable = false)
    private Long assigneeId;

    @Column(nullable = false, length = 64)
    private String camundaTaskId;

    @Column(nullable = false, length = 16)
    private String status = STATUS_PENDING;

    /** APPROVE 同意 | RETURN_PREV 退回上一步 | RETURN_DRAFT 退回拟稿 */
    @Column(length = 16)
    private String action;

    @Column(length = 512)
    private String comment;

    /** 第几次提交产生的任务 */
    @Column(nullable = false)
    private Integer attempt = 1;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime doneAt;

    @Column(nullable = false)
    private Boolean overdueNotified = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public String getCamundaTaskId() { return camundaTaskId; }
    public void setCamundaTaskId(String camundaTaskId) { this.camundaTaskId = camundaTaskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDoneAt() { return doneAt; }
    public void setDoneAt(LocalDateTime doneAt) { this.doneAt = doneAt; }
    public Boolean getOverdueNotified() { return overdueNotified; }
    public void setOverdueNotified(Boolean overdueNotified) { this.overdueNotified = overdueNotified; }
}
