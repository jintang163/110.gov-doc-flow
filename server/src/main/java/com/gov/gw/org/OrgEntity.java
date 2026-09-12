package com.gov.gw.org;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 部门/机关单位 */
@Entity
@Table(name = "gw_org")
public class OrgEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** 上级部门，根为 0 */
    @Column(nullable = false)
    private Long parentId = 0L;

    /** 部门负责人（用户 id），流程节点可按“部门负责人”指派 */
    private Long leaderId;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getLeaderId() { return leaderId; }
    public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
