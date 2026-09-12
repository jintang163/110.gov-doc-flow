package com.gov.gw.template;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 红头文件模板（文号规则 + 版式要素 + 绑定流程） */
@Entity
@Table(name = "gw_template")
public class DocTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板名，如“市政府文件” */
    @Column(nullable = false, length = 64)
    private String name;

    /** 红头大字，如“XX市人民政府文件” */
    @Column(nullable = false, length = 64)
    private String redTitle;

    /** 发文字号前缀，如“XX政发”，完整文号 = 前缀〔年份〕序号号 */
    @Column(nullable = false, length = 32)
    private String noPrefix;

    /** 落款单位（发文机关署名） */
    @Column(nullable = false, length = 64)
    private String issuer;

    /** 绑定流程配置 */
    @Column(nullable = false)
    private Long flowId;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRedTitle() { return redTitle; }
    public void setRedTitle(String redTitle) { this.redTitle = redTitle; }
    public String getNoPrefix() { return noPrefix; }
    public void setNoPrefix(String noPrefix) { this.noPrefix = noPrefix; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
