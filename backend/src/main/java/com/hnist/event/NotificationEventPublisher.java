package com.hnist.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 通知事件发布器：仅 Spring 进程内发布（ApplicationEventPublisher），事件不跨进程、无持久化投递保证，
 * 落库的可靠性与去重由 NotificationEventListener 在 AFTER_COMMIT 阶段完成。
 */
@Component
public class NotificationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public NotificationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(NotificationEvent event) {
        eventPublisher.publishEvent(event);
    }
}
