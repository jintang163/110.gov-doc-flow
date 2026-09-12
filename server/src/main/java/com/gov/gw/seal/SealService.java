package com.gov.gw.seal;

import com.gov.gw.common.ApiException;
import com.gov.gw.org.UserEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SealService {
    private final SealRepo sealRepo;

    public SealService(SealRepo sealRepo) {
        this.sealRepo = sealRepo;
    }

    /** 我可见/可用的章：个人章（本人）+ 单位章（本单位）+ 管理员全部 */
    public List<Seal> visibleTo(UserEntity user) {
        return sealRepo.findByEnabledTrue().stream()
                .filter(s -> canUse(s, user))
                .toList();
    }

    public boolean canUse(Seal seal, UserEntity user) {
        if (Boolean.TRUE.equals(user.getAdmin())) return true;
        if (Seal.OWNER_USER.equals(seal.getOwnerType())) {
            return seal.getOwnerId().equals(user.getId());
        }
        return seal.getOwnerId().equals(user.getOrgId());
    }

    public Seal requireUsable(Long sealId, UserEntity user) {
        Seal seal = sealRepo.findById(sealId).orElseThrow(() -> ApiException.notFound("签章"));
        if (!Boolean.TRUE.equals(seal.getEnabled())) {
            throw new ApiException("签章已停用");
        }
        if (!canUse(seal, user)) {
            throw ApiException.forbidden("无权使用该签章");
        }
        if (seal.getImageKey() == null) {
            throw new ApiException("签章尚未生成章图，请先生成或上传");
        }
        return seal;
    }
}
