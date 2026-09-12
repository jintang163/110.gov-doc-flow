package com.gov.gw.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 集群推送：通知先发布到 Redis 频道 gw:notify，
 * 各实例订阅后转发到本机 STOMP 连接，实现多实例待办推送。
 */
@Service
@ConditionalOnProperty(name = "app.push", havingValue = "redis")
public class RedisPushService implements PushService {
    public static final String CHANNEL = "gw:notify";
    private static final Logger log = LoggerFactory.getLogger(RedisPushService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final StringRedisTemplate redis;

    public RedisPushService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void pushToUser(Long userId, Object payload) {
        try {
            redis.convertAndSend(CHANNEL, MAPPER.writeValueAsString(Map.of(
                    "userId", userId, "payload", MAPPER.valueToTree(payload))));
        } catch (Exception e) {
            log.warn("Redis 推送失败 userId={}: {}", userId, e.getMessage());
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "app.push", havingValue = "redis")
    static class RedisPushConfig {
        @Bean
        RedisMessageListenerContainer notifyListener(RedisConnectionFactory factory,
                                                     SimpMessagingTemplate messagingTemplate) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            container.addMessageListener((message, pattern) -> {
                try {
                    var node = MAPPER.readTree(message.getBody());
                    long userId = node.get("userId").asLong();
                    messagingTemplate.convertAndSend("/topic/notify." + userId,
                            MAPPER.treeToValue(node.get("payload"), Object.class));
                } catch (Exception e) {
                    log.warn("Redis 通知转发失败: {}", e.getMessage());
                }
            }, new ChannelTopic(CHANNEL));
            return container;
        }
    }
}
