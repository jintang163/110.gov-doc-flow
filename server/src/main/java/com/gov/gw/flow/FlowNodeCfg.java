package com.gov.gw.flow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 流程节点配置（JSON 存于 FlowConfig.nodesJson，随公文快照保存）。
 * type: AUDIT 审核 | COUNTERSIGN 会签 | ISSUE 签发
 * mode: ALL 会签(全部同意) | ANY 或签(一人即可) | SEQUENCE 串签(依次办理)
 * assigneeType: USERS 指定人员 | POST 按岗位 | ORG_LEADER 部门负责人
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowNodeCfg {
    @NotBlank
    private String key;
    @NotBlank
    private String name;
    private String type = "AUDIT";
    private String mode = "ALL";
    private String assigneeType = "USERS";
    private List<Long> userIds = List.of();
    private String post;
    private Long orgId;
    /** 办理时限（小时），超时自动提醒 */
    private Integer timeoutHours = 24;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getAssigneeType() { return assigneeType; }
    public void setAssigneeType(String assigneeType) { this.assigneeType = assigneeType; }
    public List<Long> getUserIds() { return userIds; }
    public void setUserIds(List<Long> userIds) { this.userIds = userIds; }
    public String getPost() { return post; }
    public void setPost(String post) { this.post = post; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Integer getTimeoutHours() { return timeoutHours; }
    public void setTimeoutHours(Integer timeoutHours) { this.timeoutHours = timeoutHours; }
}
