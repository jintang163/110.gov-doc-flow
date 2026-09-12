package com.gov.gw.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** 单机推送：直接经 STOMP 下发 /topic/notify.{userId} */
@Service
@ConditionalOnProperty(name = "app.push", havingValue = "stomp", matchIfMissing = true)
public class StompPushService implements PushService {
    private static final Logger log = LoggerFactory.getLogger(StompPushService.class);
    private final SimpMessagingTemplate messagingTemplate;

    public StompPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void pushToUser(Long userId, Object payload) {
        try {
            messagingTemplate.convertAndSend("/topic/notify." + userId, payload);
        } catch (Exception e) {
            log.warn("STOMP 推送失败 userId={}: {}", userId, e.getMessage());
        }
    }
}
