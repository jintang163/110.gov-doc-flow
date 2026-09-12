package com.gov.gw.org;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 用户（机关工作人员） */
@Entity
@Table(name = "gw_user", indexes = @Index(name = "uk_user_username", columnList = "username", unique = true))
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(nullable = false)
    private Long orgId;

    /** 岗位，逗号分隔，如 "科员,办公室主任"。流程节点可按岗位指派 */
    @Column(length = 128)
    private String posts = "";

    /** 密级许可：0 公开 1 内部 2 秘密 3 机密。只能看不高于自己密级的公文 */
    @Column(nullable = false)
    private Integer clearance = 1;

    @Column(nullable = false)
    private Boolean admin = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean hasPost(String post) {
        if (post == null || post.isBlank()) return false;
        for (String p : posts.split(",")) {
            if (p.trim().equals(post.trim())) return true;
        }
        return false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getPosts() { return posts; }
    public void setPosts(String posts) { this.posts = posts; }
    public Integer getClearance() { return clearance; }
    public void setClearance(Integer clearance) { this.clearance = clearance; }
    public Boolean getAdmin() { return admin; }
    public void setAdmin(Boolean admin) { this.admin = admin; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
