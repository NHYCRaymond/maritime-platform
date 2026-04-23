package com.maritime.iam.sdk.dataperm;

/**
 * Column names used when the SDK renders data-permission scope
 * fragments ({@code ORG} / {@code SELF}) and line-role filters
 * ({@code line_type}). Previously hard-coded in
 * {@link LineRoleFilter}; made configurable so that downstream
 * services whose tables do not use {@code org_id} / {@code user_id}
 * / {@code line_type} can still benefit from the interceptor
 * without forking the SDK.
 *
 * <p>Configure via {@code IamSdkProperties.Sdk.Scope} in
 * application.yml:</p>
 * <pre>
 * iam:
 *   sdk:
 *     scope:
 *       org-column: creator_org_code
 *       self-column: creator_user_id
 *       line-type-column: discriminator
 * </pre>
 *
 * <p>{@link #DEFAULT} preserves the pre-1.0.8 behaviour
 * ({@code org_id} / {@code user_id} / {@code line_type}).</p>
 */
public record ScopeColumns(
        String orgColumn,
        String selfColumn,
        String lineTypeColumn) {

    public static final ScopeColumns DEFAULT =
            new ScopeColumns("org_id", "user_id", "line_type");

    public ScopeColumns {
        require(orgColumn, "orgColumn");
        require(selfColumn, "selfColumn");
        require(lineTypeColumn, "lineTypeColumn");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " cannot be null or blank");
        }
    }
}
