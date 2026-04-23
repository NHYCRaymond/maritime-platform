package com.maritime.iam.sdk.dataperm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maritime.iam.sdk.context.IamContext;
import com.maritime.iam.sdk.model.PagePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generates five-line (PARTY/GOVERNMENT/DISCIPLINE/UNION/YOUTH)
 * SQL conditions from page policies and user's line roles.
 *
 * <p>Usage: business systems call this utility to build WHERE
 * conditions for line-type data filtering. IAM defines policy;
 * business systems execute the SQL filter.
 *
 * <p>Merge rule (ADR-005): multiple LINE roles use <b>union</b>
 * semantics. Each held line type contributes a condition
 * {@code (line_type = ? AND <scope_filter>)}, joined by OR.
 * This means holding more LINE roles = seeing more data.
 *
 * <p>Supports four {@code dataScopeType} modes:
 * <ul>
 *   <li>{@code ALL} — no scope filter for this line type
 *       (just {@code line_type = ?})</li>
 *   <li>{@code ORG} — {@code line_type = ? AND org_id = ?}</li>
 *   <li>{@code SELF} — {@code line_type = ? AND user_id = ?}</li>
 *   <li>{@code CUSTOM} — {@code line_type = ? AND <expr>}</li>
 * </ul>
 *
 * <p>When no policy applies to any of the user's LINE roles,
 * returns {@code 1 = 0} (deny all).
 */
public final class LineRoleFilter {

    private static final Set<String> VALID_LINE_TYPES = Set.of(
            "PARTY", "GOVERNMENT", "DISCIPLINE",
            "UNION", "YOUTH");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Default column name for line type discrimination. */
    static final String DEFAULT_LINE_TYPE_COLUMN = "line_type";

    private LineRoleFilter() {
    }

    /**
     * Generates a parameterized SQL condition for line-type
     * filtering using default column names ({@code line_type} /
     * {@code org_id} / {@code user_id}).
     *
     * @param pagePolicies policies from page snapshot
     * @param userLineRoles user's LINE role types from nav snapshot
     * @return SQL fragment with union semantics. Empty fragment
     *         if page has no line policy. {@code 1 = 0} if user
     *         has no applicable LINE roles.
     */
    public static ExpressionParser.SqlFragment generateLineCondition(
            List<PagePolicy> pagePolicies,
            List<String> userLineRoles) {
        return generateLineCondition(
                pagePolicies, userLineRoles, ScopeColumns.DEFAULT);
    }

    /**
     * Deprecated overload — prefer
     * {@link #generateLineCondition(List, List, ScopeColumns)}.
     * Retained so 1.0.7 callers keep compiling; only customises
     * {@code line_type} and leaves ORG/SELF columns at defaults.
     */
    @Deprecated
    public static ExpressionParser.SqlFragment generateLineCondition(
            List<PagePolicy> pagePolicies,
            List<String> userLineRoles,
            String lineTypeColumn) {
        return generateLineCondition(
                pagePolicies, userLineRoles,
                new ScopeColumns(
                        ScopeColumns.DEFAULT.orgColumn(),
                        ScopeColumns.DEFAULT.selfColumn(),
                        lineTypeColumn));
    }

    /**
     * Generates a parameterized SQL condition for line-type
     * filtering using caller-supplied column names. Use this
     * overload when your table does not follow the default
     * {@code org_id} / {@code user_id} / {@code line_type}
     * naming — see {@link ScopeColumns}.
     *
     * @param pagePolicies  policies from page snapshot
     * @param userLineRoles user's LINE role types from nav snapshot
     * @param columns       column overrides (non-null)
     * @return SQL fragment with union semantics
     */
    public static ExpressionParser.SqlFragment generateLineCondition(
            List<PagePolicy> pagePolicies,
            List<String> userLineRoles,
            ScopeColumns columns) {
        if (columns == null) {
            columns = ScopeColumns.DEFAULT;
        }
        if (pagePolicies == null || pagePolicies.isEmpty()) {
            return new ExpressionParser.SqlFragment("", List.of());
        }

        List<String> effectiveTypes = filterEffective(userLineRoles);
        if (effectiveTypes.isEmpty()) {
            return new ExpressionParser.SqlFragment(
                    "1 = 0", List.of());
        }

        // Build per-type conditions, joined by OR (union)
        List<String> branches = new ArrayList<>();
        List<Object> allValues = new ArrayList<>();

        for (String heldType : effectiveTypes) {
            PagePolicy policy = pagePolicies.stream()
                    .filter(p -> heldType.equals(p.lineRoleType()))
                    .findFirst()
                    .orElse(null);
            if (policy == null) {
                // User holds this LINE type but page has no policy
                // for it — this type contributes nothing
                continue;
            }

            ExpressionParser.SqlFragment scopeFrag =
                    buildScopeFragment(policy, columns);

            // Compose: line_type = ? [AND <scope>]
            if (scopeFrag.condition().isEmpty()) {
                // ALL scope — just line_type match
                branches.add(columns.lineTypeColumn() + " = ?");
                allValues.add(heldType);
            } else if ("1 = 0".equals(scopeFrag.condition())) {
                // Scope resolved to deny (missing context) — skip
                continue;
            } else {
                branches.add("(" + columns.lineTypeColumn() + " = ? AND "
                        + scopeFrag.condition() + ")");
                allValues.add(heldType);
                allValues.addAll(scopeFrag.values());
            }
        }

        if (branches.isEmpty()) {
            return new ExpressionParser.SqlFragment(
                    "1 = 0", List.of());
        }

        if (branches.size() == 1) {
            return new ExpressionParser.SqlFragment(
                    branches.get(0), allValues);
        }

        // Multiple branches → OR union
        String combined = "(" + String.join(" OR ", branches) + ")";
        return new ExpressionParser.SqlFragment(combined, allValues);
    }

    /**
     * Convenience overload — returns condition string only.
     */
    public static String generateLineConditionSimple(
            List<PagePolicy> pagePolicies,
            List<String> userLineRoles) {
        return generateLineCondition(
                pagePolicies, userLineRoles).condition();
    }

    /**
     * Build the scope fragment for a single policy (without
     * the line_type qualifier).
     */
    private static ExpressionParser.SqlFragment buildScopeFragment(
            PagePolicy policy, ScopeColumns columns) {
        String scopeType = policy.dataScopeType();
        if (scopeType == null) {
            scopeType = "ALL";
        }

        return switch (scopeType) {
            case "ALL" -> new ExpressionParser.SqlFragment(
                    "", List.of());
            case "ORG" -> {
                String orgCode = IamContext.activeOrgCode();
                if (orgCode == null || orgCode.isBlank()) {
                    yield new ExpressionParser.SqlFragment(
                            "1 = 0", List.of());
                }
                yield new ExpressionParser.SqlFragment(
                        columns.orgColumn() + " = ?",
                        List.of(orgCode));
            }
            case "SELF" -> {
                String uid = IamContext.userId();
                if (uid == null || uid.isBlank()) {
                    yield new ExpressionParser.SqlFragment(
                            "1 = 0", List.of());
                }
                yield new ExpressionParser.SqlFragment(
                        columns.selfColumn() + " = ?",
                        List.of(uid));
            }
            case "CUSTOM" -> parseCustomExpr(
                    policy.dataScopeExpr());
            default -> new ExpressionParser.SqlFragment(
                    "1 = 0", List.of());
        };
    }

    private static ExpressionParser.SqlFragment parseCustomExpr(
            String exprJson) {
        if (exprJson == null || exprJson.isBlank()) {
            return new ExpressionParser.SqlFragment("", List.of());
        }
        try {
            List<DataPermissionExpression> exprs = MAPPER.readValue(
                    exprJson, new TypeReference<>() {});
            return ExpressionParser.toSqlFragment(exprs);
        } catch (Exception e) {
            return new ExpressionParser.SqlFragment(
                    "1 = 0", List.of());
        }
    }

    private static List<String> filterEffective(
            List<String> userRoles) {
        if (userRoles == null) {
            return List.of();
        }
        return userRoles.stream()
                .filter(VALID_LINE_TYPES::contains)
                .distinct()
                .toList();
    }
}
