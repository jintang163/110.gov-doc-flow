package com.gov.gw.seal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 电子签章（章模） */
@Entity
@Table(name = "gw_seal")
public class Seal {
    public static final String OWNER_ORG = "ORG";
    public static final String OWNER_USER = "USER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** ORG 单位章 | USER 个人章 */
    @Column(nullable = false, length = 8)
    private String ownerType;

    /** ownerType=ORG 时为部门 id，USER 时为用户 id */
    @Column(nullable = false)
    private Long ownerId;

    /** 章图（PNG）存储 key */
    @Column(length = 255)
    private String imageKey;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
