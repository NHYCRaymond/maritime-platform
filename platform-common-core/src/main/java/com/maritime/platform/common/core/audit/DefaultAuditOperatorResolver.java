package com.maritime.platform.common.core.audit;

public class DefaultAuditOperatorResolver implements AuditOperatorResolver {

    @Override
    public String currentUserId() {
        return "SYSTEM";
    }

    @Override
    public String currentSystemCode() {
        return "UNKNOWN";
    }
}