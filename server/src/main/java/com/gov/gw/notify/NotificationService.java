package com.gov.gw.notify;

import com.gov.gw.doc.Notification;
import com.gov.gw.doc.NotificationRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepo notificationRepo;
    private final PushService pushService;

    public NotificationService(NotificationRepo notificationRepo, PushService pushService) {
        this.notificationRepo = notificationRepo;
        this.pushService = pushService;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Notification send(Long userId, String type, String title, String content, Long docId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setDocId(docId);
        Notification saved = notificationRepo.save(n);
        pushService.pushToUser(userId, saved);
        return saved;
    }
}
