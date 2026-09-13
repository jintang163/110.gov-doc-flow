package com.gov.gw.analysis;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 公文效能分析表（每公文一行，由定时任务从 Camunda 历史表 ACT_HI_* 与业务库抽取刷新）。
 * 仅存统计结果，不回写业务表。
 */
@Entity
@Table(name = "doc_analysis", indexes = {
        @Index(name = "idx_analysis_doc", columnList = "docId", unique = true),
        @Index(name = "idx_analysis_org", columnList = "orgId"),
        @Index(name = "idx_analysis_template", columnList = "templateId"),
        @Index(name = "idx_analysis_archived", columnList = "archivedAt")
})
public class DocAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long docId;

    @Column(length = 128)
    private String title;

    @Column(length = 64)
    private String docNo;

    private Long templateId;

    @Column(length = 64)
    private String templateName;

    /** 拟稿部门 */
    private Long orgId;

    @Column(length = 64)
    private String orgName;

    private Long creatorId;

    @Column(length = 32)
    private String creatorName;

    /** 拟稿人主岗位（posts 第一个） */
    @Column(length = 64)
    private String creatorPost;

    @Column(length = 16)
    private String status;

    @Column(nullable = false)
    private Integer attempt = 0;

    /** 拟稿时长（小时）：创建 → 首次提交 */
    private Double draftHours;

    /** 全程时长（小时）：首次提交 → 归档，未办结为 null */
    private Double totalHours;

    /** 会签完成周期（小时）：会签节点最早任务开始 → 最晚任务完成 */
    private Double countersignHours;

    /**
     * 节点任务明细 [{key,name,type,assigneeId,assigneeName,orgName,post,start,end,hours,action}]
     * 主要取自 ACT_HI_TASKINST，历史缺失时回退 gw_doc_task
     */
    @Lob
    @Column(columnDefinition = "CLOB")
    private String nodeStatsJson = "[]";

    /** 退回意见 [{nodeName,actorName,comment,at}] */
    @Lob
    @Column(columnDefinition = "CLOB")
    private String returnReasonsJson = "[]";

    @Column(nullable = false)
    private Integer returnCount = 0;

    @Column(nullable = false)
    private Integer urgeCount = 0;

    /** 当前超时天数（仅在办件有意义，办结/草稿为 0） */
    @Column(nullable = false)
    private Integer overdueDays = 0;

    private LocalDateTime submittedAt;
    private LocalDateTime archivedAt;

    @Column(nullable = false)
    private LocalDateTime extractedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public String getCreatorPost() { return creatorPost; }
    public void setCreatorPost(String creatorPost) { this.creatorPost = creatorPost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public Double getDraftHours() { return draftHours; }
    public void setDraftHours(Double draftHours) { this.draftHours = draftHours; }
    public Double getTotalHours() { return totalHours; }
    public void setTotalHours(Double totalHours) { this.totalHours = totalHours; }
    public Double getCountersignHours() { return countersignHours; }
    public void setCountersignHours(Double countersignHours) { this.countersignHours = countersignHours; }
    public String getNodeStatsJson() { return nodeStatsJson; }
    public void setNodeStatsJson(String nodeStatsJson) { this.nodeStatsJson = nodeStatsJson; }
    public String getReturnReasonsJson() { return returnReasonsJson; }
    public void setReturnReasonsJson(String returnReasonsJson) { this.returnReasonsJson = returnReasonsJson; }
    public Integer getReturnCount() { return returnCount; }
    public void setReturnCount(Integer returnCount) { this.returnCount = returnCount; }
    public Integer getUrgeCount() { return urgeCount; }
    public void setUrgeCount(Integer urgeCount) { this.urgeCount = urgeCount; }
    public Integer getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Integer overdueDays) { this.overdueDays = overdueDays; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
    public LocalDateTime getExtractedAt() { return extractedAt; }
    public void setExtractedAt(LocalDateTime extractedAt) { this.extractedAt = extractedAt; }
}
