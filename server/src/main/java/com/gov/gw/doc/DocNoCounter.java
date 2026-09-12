package com.gov.gw.doc;

import jakarta.persistence.*;

/** 文号计数器：每模板每年递增，退回作废的号不回收 */
@Entity
@Table(name = "gw_doc_no_counter", uniqueConstraints =
        @UniqueConstraint(name = "uk_counter_tpl_year", columnNames = {"templateId", "yearNo"}))
public class DocNoCounter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private Integer yearNo;

    @Column(nullable = false)
    private Integer nextNo = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Integer getYearNo() { return yearNo; }
    public void setYearNo(Integer yearNo) { this.yearNo = yearNo; }
    public Integer getNextNo() { return nextNo; }
    public void setNextNo(Integer nextNo) { this.nextNo = nextNo; }
}
