package com.maritime.platform.common.notification.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable request describing a single notification to dispatch.
 *
 * <p>Deliberately uses only primitive types so the platform library remains free of
 * business-domain concepts. Recipients are opaque string references — the concrete
 * channel handler decides how to resolve them (user id, email, phone, etc.).</p>
 *
 * @param templateCode   non-blank template identifier resolved by the handler
 * @param channels       non-empty set of channels to deliver through
 * @param recipients     opaque recipient references; {@code null} is normalised to empty
 * @param params         template parameters; {@code null} is normalised to empty
 * @param tenantId       non-blank tenant identifier for multi-tenant routing
 * @param correlationId  optional trace / business correlation id; {@code null} is normalised to empty string
 * @param idempotencyKey nullable; when set, implementations may de-dupe duplicate dispatches
 */
public record NotificationRequest(
        String templateCode,
        Set<Channel> channels,
        List<String> recipients,
        Map<String, Object> params,
        String tenantId,
        String correlationId,
        String idempotencyKey
) {
    public NotificationRequest {
        if (templateCode == null || templateCode.isBlank()) {
            throw new IllegalArgumentException("templateCode cannot be blank");
        }
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("channels cannot be empty");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        channels = Set.copyOf(channels);
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        params = params == null ? Map.of() : Map.copyOf(params);
        correlationId = correlationId == null ? "" : correlationId;
    }
}
