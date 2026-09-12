package com.gov.gw.init;

import com.gov.gw.auth.AuthService;
import com.gov.gw.flow.FlowService;
import com.gov.gw.org.OrgEntity;
import com.gov.gw.org.OrgRepo;
import com.gov.gw.org.UserEntity;
import com.gov.gw.org.UserRepo;
import com.gov.gw.seal.Seal;
import com.gov.gw.seal.SealImageGenerator;
import com.gov.gw.seal.SealRepo;
import com.gov.gw.storage.StorageService;
import com.gov.gw.template.DocTemplate;
import com.gov.gw.template.TemplateRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

/**
 * 首次启动初始化演示数据：部门、用户、流程、红头模板、演示签章。
 * 演示账号（生产部署请立即修改）：
 *   admin/admin123 管理员；zhangsan 拟稿、lisi 审核、wangwu/zhaoliu 会签、qianqi 签发，密码均 123456
 */
@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepo userRepo;
    private final OrgRepo orgRepo;
    private final TemplateRepo templateRepo;
    private final FlowService flowService;
    private final SealRepo sealRepo;
    private final SealImageGenerator sealImageGenerator;
    private final StorageService storage;
    private final AuthService authService;

    public DataInitializer(UserRepo userRepo, OrgRepo orgRepo, TemplateRepo templateRepo,
                           FlowService flowService, SealRepo sealRepo,
                           SealImageGenerator sealImageGenerator, StorageService storage,
                           AuthService authService) {
        this.userRepo = userRepo;
        this.orgRepo = orgRepo;
        this.templateRepo = templateRepo;
        this.flowService = flowService;
        this.sealRepo = sealRepo;
        this.sealImageGenerator = sealImageGenerator;
        this.storage = storage;
        this.authService = authService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepo.count() > 0) return;
        log.info("初始化演示数据...");

        // 部门
        OrgEntity office = org("市政府办公室", null);
        OrgEntity fgw = org("市发展改革委", null);
        OrgEntity czj = org("市财政局", null);

        // 用户
        UserEntity admin = user("admin", "admin123", "系统管理员", office, "系统管理员", 3, true);
        UserEntity zhangsan = user("zhangsan", "123456", "张三", office, "科员", 1, false);
        UserEntity lisi = user("lisi", "123456", "李四", office, "办公室主任", 2, false);
        UserEntity wangwu = user("wangwu", "123456", "王五", fgw, "科长", 2, false);
        UserEntity zhaoliu = user("zhaoliu", "123456", "赵六", czj, "科长", 2, false);
        UserEntity qianqi = user("qianqi", "123456", "钱七", office, "副局长", 3, false);

        office.setLeaderId(lisi.getId());
        fgw.setLeaderId(wangwu.getId());
        czj.setLeaderId(zhaoliu.getId());
        orgRepo.saveAll(List.of(office, fgw, czj));

        // 流程一：审核 -> 会签(全部) -> 签发
        String mainFlow = com.gov.gw.common.Jsons.write(List.of(
                node("audit", "部门审核", "AUDIT", "ALL", List.of(lisi.getId()), 24),
                node("countersign", "部门会签", "COUNTERSIGN", "ALL", List.of(wangwu.getId(), zhaoliu.getId()), 48),
                node("issue", "领导签发", "ISSUE", "ALL", List.of(qianqi.getId()), 24)
        ));
        var flow1 = flowService.create("发文办理流程（会签）", mainFlow, "拟稿→部门审核→两部门会签→领导签发");

        // 流程二：或签 -> 串签 -> 签发
        String secondFlow = com.gov.gw.common.Jsons.write(List.of(
                node("audit2", "处室审核（或签）", "AUDIT", "ANY", List.of(lisi.getId(), wangwu.getId()), 24),
                node("seq", "财务/发改串签", "COUNTERSIGN", "SEQUENCE", List.of(zhaoliu.getId(), wangwu.getId()), 48),
                node("issue2", "领导签发", "ISSUE", "ALL", List.of(qianqi.getId()), 24)
        ));
        var flow2 = flowService.create("发文办理流程（或签+串签）", secondFlow, "或签/串签模式演示");

        // 红头模板
        template("市政府文件", "XX市人民政府文件", "XX政发", "XX市人民政府", flow1.getId());
        template("市政府办公室文件", "XX市人民政府办公室文件", "XX政办发", "XX市人民政府办公室", flow2.getId());

        // 演示签章
        seal("市政府办公室公章", Seal.OWNER_ORG, office.getId(), "市政府办公室", "专用章");
        seal("钱七个人章", Seal.OWNER_USER, qianqi.getId(), "钱七", "");

        log.info("演示数据初始化完成。管理员：admin/admin123，业务用户密码均为 123456");
    }

    private OrgEntity org(String name, Long parentId) {
        OrgEntity o = new OrgEntity();
        o.setName(name);
        o.setParentId(parentId == null ? 0L : parentId);
        return orgRepo.save(o);
    }

    private UserEntity user(String username, String password, String name, OrgEntity org,
                            String posts, int clearance, boolean admin) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setPasswordHash(authService.hashPassword(password));
        u.setName(name);
        u.setOrgId(org.getId());
        u.setPosts(posts);
        u.setClearance(clearance);
        u.setAdmin(admin);
        return userRepo.save(u);
    }

    private Map<String, Object> node(String key, String name, String type, String mode,
                                     List<Long> userIds, int timeoutHours) {
        return Map.of("key", key, "name", name, "type", type, "mode", mode,
                "assigneeType", "USERS", "userIds", userIds, "timeoutHours", timeoutHours);
    }

    private void template(String name, String redTitle, String noPrefix, String issuer, Long flowId) {
        DocTemplate t = new DocTemplate();
        t.setName(name);
        t.setRedTitle(redTitle);
        t.setNoPrefix(noPrefix);
        t.setIssuer(issuer);
        t.setFlowId(flowId);
        templateRepo.save(t);
    }

    private void seal(String name, String ownerType, Long ownerId, String arcText, String bottomText) {
        Seal seal = new Seal();
        seal.setName(name);
        seal.setOwnerType(ownerType);
        seal.setOwnerId(ownerId);
        seal = sealRepo.save(seal);
        byte[] png = sealImageGenerator.generate(arcText, bottomText);
        String key = "seal/" + seal.getId() + ".png";
        storage.put(key, new ByteArrayInputStream(png), png.length);
        seal.setImageKey(key);
        sealRepo.save(seal);
    }
}
