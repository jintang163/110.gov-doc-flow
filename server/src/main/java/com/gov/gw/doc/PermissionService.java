package com.gov.gw.doc;

import com.gov.gw.common.ApiException;
import com.gov.gw.org.OrgEntity;
import com.gov.gw.org.OrgRepo;
import com.gov.gw.org.UserEntity;
import org.springframework.stereotype.Service;

/**
 * 可见性/办理权限：
 *  - 密级：只能查看不高于自身密级许可的公文；
 *  - 可见范围：拟稿人、流程参与人、主送/抄送单位成员、管理员；
 *  - 办理：仅当前待办任务的办理人。
 */
@Service
public class PermissionService {
    private final DocTaskRepo taskRepo;
    private final OrgRepo orgRepo;

    public PermissionService(DocTaskRepo taskRepo, OrgRepo orgRepo) {
        this.taskRepo = taskRepo;
        this.orgRepo = orgRepo;
    }

    public boolean canView(Document doc, UserEntity user) {
        if (Boolean.TRUE.equals(user.getAdmin())) return true;
        if (user.getClearance() < doc.getSecretLevel()) return false;
        if (doc.getCreatedBy().equals(user.getId())) return true;
        if (taskRepo.existsByDocIdAndAssigneeId(doc.getId(), user.getId())) return true;
        String orgName = orgRepo.findById(user.getOrgId()).map(OrgEntity::getName).orElse("");
        if (!orgName.isBlank()) {
            if (doc.getMainSend() != null && doc.getMainSend().contains(orgName)) return true;
            if (doc.getCopySend() != null && doc.getCopySend().contains(orgName)) return true;
        }
        return false;
    }

    public void requireView(Document doc, UserEntity user) {
        if (user.getClearance() < doc.getSecretLevel()) {
            throw ApiException.forbidden("密级不足，无权查看该公文");
        }
        if (!canView(doc, user)) {
            throw ApiException.forbidden("您不在该公文的可见范围内");
        }
    }
}
