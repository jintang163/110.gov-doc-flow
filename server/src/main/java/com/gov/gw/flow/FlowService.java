package com.gov.gw.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gov.gw.common.ApiException;
import com.gov.gw.common.Jsons;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ProcessBuilder;
import org.camunda.bpm.model.bpmn.builder.UserTaskBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程配置服务：校验节点配置、动态生成 BPMN 并部署到 Camunda。
 * 每个节点生成一个多实例用户任务：
 *   ALL      -> 并行多实例，全部办理（会签）
 *   ANY      -> 并行多实例 + 完成条件（或签，一人办理后其余自动取消）
 *   SEQUENCE -> 串行多实例（串签，依次办理）
 * 节点办理人集合在启动流程时以变量 users_{key} 传入。
 */
@Service
public class FlowService {
    public static final String TYPE_AUDIT = "AUDIT";
    public static final String TYPE_COUNTERSIGN = "COUNTERSIGN";
    public static final String TYPE_ISSUE = "ISSUE";

    private final FlowRepo flowRepo;
    private final RepositoryService repositoryService;

    public FlowService(FlowRepo flowRepo, RepositoryService repositoryService) {
        this.flowRepo = flowRepo;
        this.repositoryService = repositoryService;
    }

    public List<FlowNodeCfg> parseNodes(String nodesJson) {
        List<FlowNodeCfg> nodes = Jsons.read(nodesJson, new TypeReference<List<FlowNodeCfg>>() {});
        validate(nodes);
        return nodes;
    }

    public void validate(List<FlowNodeCfg> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new ApiException("流程至少需要一个办理节点");
        }
        Set<String> keys = new HashSet<>();
        int issueCount = 0;
        for (FlowNodeCfg n : nodes) {
            if (n.getKey() == null || !n.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,30}")) {
                throw new ApiException("节点标识须为字母开头，仅含字母数字下划线：" + n.getKey());
            }
            if (!keys.add(n.getKey())) {
                throw new ApiException("节点标识重复：" + n.getKey());
            }
            if (n.getName() == null || n.getName().isBlank()) {
                throw new ApiException("节点名称不能为空");
            }
            switch (n.getType()) {
                case TYPE_AUDIT, TYPE_COUNTERSIGN, TYPE_ISSUE -> {}
                default -> throw new ApiException("未知节点类型：" + n.getType());
            }
            switch (n.getMode()) {
                case "ALL", "ANY", "SEQUENCE" -> {}
                default -> throw new ApiException("未知会签模式：" + n.getMode());
            }
            switch (n.getAssigneeType()) {
                case "USERS" -> {
                    if (n.getUserIds() == null || n.getUserIds().isEmpty()) {
                        throw new ApiException("节点「" + n.getName() + "」未指定办理人");
                    }
                }
                case "POST" -> {
                    if (n.getPost() == null || n.getPost().isBlank()) {
                        throw new ApiException("节点「" + n.getName() + "」未指定岗位");
                    }
                }
                case "ORG_LEADER" -> {
                    if (n.getOrgId() == null) {
                        throw new ApiException("节点「" + n.getName() + "」未指定部门");
                    }
                }
                default -> throw new ApiException("未知办理人类型：" + n.getAssigneeType());
            }
            if (n.getTimeoutHours() == null || n.getTimeoutHours() <= 0) {
                n.setTimeoutHours(24);
            }
            if (TYPE_ISSUE.equals(n.getType())) issueCount++;
        }
        if (issueCount > 1) {
            throw new ApiException("签发节点至多配置一个");
        }
    }

    public static String processKey(Long flowId) {
        return "flow_" + flowId;
    }

    /** 生成 BPMN 模型并部署。每次保存流程都会产生 Camunda 新版本，历史在办件仍按旧版本流转。 */
    public void deploy(FlowConfig flow) {
        List<FlowNodeCfg> nodes = parseNodes(flow.getNodesJson());
        String key = processKey(flow.getId());
        ProcessBuilder process = Bpmn.createExecutableProcess(key).name(flow.getName());
        AbstractFlowNodeBuilder<?, ?> builder = process.startEvent("start").name("提交");
        for (FlowNodeCfg node : nodes) {
            UserTaskBuilder ut = builder.userTask(node.getKey()).name(node.getName());
            var mi = ut.multiInstance();
            mi.camundaCollection("${users_" + node.getKey() + "}");
            mi.camundaElementVariable("assignee");
            if ("SEQUENCE".equals(node.getMode())) {
                mi.sequential();
            } else {
                mi.parallel();
            }
            if ("ANY".equals(node.getMode())) {
                mi.completionCondition("${nrOfCompletedInstances >= 1}");
            }
            ut.camundaAssignee("${assignee}");
            builder = ut;
        }
        BpmnModelInstance model = builder.endEvent("end").name("办结").done();
        Bpmn.validateModel(model);
        repositoryService.createDeployment()
                .name(key + "-v" + flow.getVersion())
                .addModelInstance(key + ".bpmn", model)
                .deploy();
    }

    public FlowConfig create(String name, String nodesJson, String remark) {
        List<FlowNodeCfg> nodes = parseNodes(nodesJson);
        FlowConfig flow = new FlowConfig();
        flow.setName(name);
        flow.setNodesJson(Jsons.write(nodes));
        flow.setRemark(remark);
        flow.setVersion(1);
        FlowConfig saved = flowRepo.save(flow);
        deploy(saved);
        return saved;
    }

    public FlowConfig update(Long id, String name, String nodesJson, String remark, Boolean enabled) {
        FlowConfig flow = flowRepo.findById(id).orElseThrow(() -> ApiException.notFound("流程"));
        List<FlowNodeCfg> nodes = parseNodes(nodesJson);
        flow.setName(name);
        flow.setNodesJson(Jsons.write(nodes));
        flow.setRemark(remark);
        if (enabled != null) flow.setEnabled(enabled);
        flow.setVersion(flow.getVersion() + 1);
        flow.setUpdatedAt(LocalDateTime.now());
        FlowConfig saved = flowRepo.save(flow);
        deploy(saved);
        return saved;
    }
}
