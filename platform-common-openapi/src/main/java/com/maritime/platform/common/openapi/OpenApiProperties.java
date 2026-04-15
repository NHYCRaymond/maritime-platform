package com.maritime.platform.common.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "maritime.openapi")
public class OpenApiProperties {

    /** API title shown in the docs UI. */
    private String title = "API Documentation";

    /** API description. */
    private String description = "";

    /** API version. */
    private String version = "1.0.0";

    /** Contact name in the info block. */
    private String contactName = "Maritime Platform";

    /** Whether to register the Bearer JWT security scheme. */
    private boolean enableBearerAuth = true;

    /** Whether to add X-Trace-ID as a documented global header parameter. */
    private boolean enableTraceIdHeader = false;

    /** Whether to add X-Tenant-ID as a documented global header parameter. */
    private boolean enableTenantIdHeader = false;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public boolean isEnableBearerAuth() { return enableBearerAuth; }
    public void setEnableBearerAuth(boolean enableBearerAuth) { this.enableBearerAuth = enableBearerAuth; }
    public boolean isEnableTraceIdHeader() { return enableTraceIdHeader; }
    public void setEnableTraceIdHeader(boolean enableTraceIdHeader) { this.enableTraceIdHeader = enableTraceIdHeader; }
    public boolean isEnableTenantIdHeader() { return enableTenantIdHeader; }
    public void setEnableTenantIdHeader(boolean enableTenantIdHeader) { this.enableTenantIdHeader = enableTenantIdHeader; }
}
