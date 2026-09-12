package com.gov.gw.doc;

import com.gov.gw.flow.FlowNodeCfg;
import com.gov.gw.notify.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 超时提醒：每分钟扫描待办任务，超过节点办理时限则通知办理人（每任务只提醒一次） */
@Component
public class ReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final DocTaskRepo taskRepo;
    private final DocumentRepo documentRepo;
    private final DocService docService;
    private final NotificationService notificationService;

    public ReminderScheduler(DocTaskRepo taskRepo, DocumentRepo documentRepo,
                             DocService docService, NotificationService notificationService) {
        this.taskRepo = taskRepo;
        this.documentRepo = documentRepo;
        this.docService = docService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void scanOverdue() {
        List<DocTask> pending = taskRepo.findByStatus(DocTask.STATUS_PENDING);
        for (DocTask task : pending) {
            if (Boolean.TRUE.equals(task.getOverdueNotified())) continue;
            Document doc = documentRepo.findById(task.getDocId()).orElse(null);
            if (doc == null || !Document.STATUS_RUNNING.equals(doc.getStatus())) continue;
            Integer timeoutHours = docService.snapshotNodes(doc).stream()
                    .filter(n -> n.getKey().equals(task.getNodeKey()))
                    .findFirst()
                    .map(FlowNodeCfg::getTimeoutHours)
                    .orElse(24);
            if (task.getCreatedAt().plusHours(timeoutHours).isBefore(LocalDateTime.now())) {
                notificationService.send(task.getAssigneeId(), Notification.TYPE_OVERDUE, "超时提醒",
                        "《" + doc.getTitle() + "》已超过办理时限（" + timeoutHours + " 小时），请尽快处理",
                        doc.getId());
                task.setOverdueNotified(true);
                taskRepo.save(task);
                docService.trace(doc, null, "OVERDUE", task.getNodeName(), null,
                        "超过办理时限 " + timeoutHours + " 小时");
                log.info("任务超时提醒 taskId={} docId={}", task.getId(), doc.getId());
            }
        }
    }
}
