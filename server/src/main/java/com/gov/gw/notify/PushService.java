package com.gov.gw.notify;

/** 待办/通知实时推送 */
public interface PushService {
    void pushToUser(Long userId, Object payload);
}
