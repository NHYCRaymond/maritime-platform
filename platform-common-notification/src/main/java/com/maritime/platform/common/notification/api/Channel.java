package com.maritime.platform.common.notification.api;

/**
 * Supported notification delivery channels.
 */
public enum Channel {
    /** In-app station (inbox) message. */
    STATION,
    /** Short message service. */
    SMS,
    /** Email. */
    EMAIL,
    /** DingTalk IM. */
    IM_DINGTALK,
    /** WeChat / WeCom IM. */
    IM_WECHAT,
    /** Generic outbound HTTP webhook. */
    WEBHOOK
}
