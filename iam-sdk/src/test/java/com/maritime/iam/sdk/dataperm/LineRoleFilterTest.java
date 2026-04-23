package com.maritime.iam.sdk.dataperm;

import com.maritime.iam.sdk.context.IamContext;
import com.maritime.iam.sdk.model.PagePolicy;
import com.maritime.iam.sdk.model.PageSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineRoleFilterTest {

    @AfterEach
    void clearCtx() {
        IamContext.clear();
    }

    // ─── default columns (backward compat) ───────────────────────────

    @Test
    void default_columns_emit_hardcoded_line_type() {
        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("PARTY", "ALL")),
                List.of("PARTY"));

        assertThat(frag.condition()).isEqualTo("line_type = ?");
        assertThat(frag.values()).containsExactly("PARTY");
    }

    @Test
    void default_columns_org_scope_uses_org_id() {
        IamContext.set(new IamContext.ContextData(
                null, "sys-a", "ORG-42", null, null, List.of("PARTY")));

        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("PARTY", "ORG")),
                List.of("PARTY"));

        assertThat(frag.condition())
                .isEqualTo("(line_type = ? AND org_id = ?)");
        assertThat(frag.values()).containsExactly("PARTY", "ORG-42");
    }

    @Test
    void default_columns_self_scope_uses_user_id() {
        IamContext.set(new IamContext.ContextData(
                "alice", "sys-a", null, null, null, List.of("GOVERNMENT")));

        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("GOVERNMENT", "SELF")),
                List.of("GOVERNMENT"));

        assertThat(frag.condition())
                .isEqualTo("(line_type = ? AND user_id = ?)");
        assertThat(frag.values()).containsExactly("GOVERNMENT", "alice");
    }

    // ─── custom columns (1.0.8 new) ──────────────────────────────────

    @Test
    void custom_columns_override_all_three_names() {
        IamContext.set(new IamContext.ContextData(
                "bob", "sys-b", "DEPT-99", null, null, List.of("PARTY")));
        ScopeColumns cols = new ScopeColumns(
                "creator_org_code", "creator_user_id", "kind");

        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("PARTY", "ORG")),
                List.of("PARTY"),
                cols);

        assertThat(frag.condition())
                .isEqualTo("(kind = ? AND creator_org_code = ?)");
        assertThat(frag.values()).containsExactly("PARTY", "DEPT-99");
    }

    @Test
    void custom_columns_self_scope() {
        IamContext.set(new IamContext.ContextData(
                "carol", null, null, null, null, List.of("DISCIPLINE")));
        ScopeColumns cols = new ScopeColumns(
                "dept_code", "owner_id", "category");

        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("DISCIPLINE", "SELF")),
                List.of("DISCIPLINE"),
                cols);

        assertThat(frag.condition())
                .isEqualTo("(category = ? AND owner_id = ?)");
        assertThat(frag.values()).containsExactly("DISCIPLINE", "carol");
    }

    @Test
    void null_columns_fall_back_to_default() {
        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("PARTY", "ALL")),
                List.of("PARTY"),
                (ScopeColumns) null);

        assertThat(frag.condition()).isEqualTo("line_type = ?");
    }

    // ─── legacy overload kept for 1.0.7 callers ──────────────────────

    @Test
    @SuppressWarnings("deprecation")
    void legacy_string_overload_only_overrides_line_type() {
        IamContext.set(new IamContext.ContextData(
                null, null, "X-1", null, null, List.of("UNION")));

        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("UNION", "ORG")),
                List.of("UNION"),
                "discriminator");

        assertThat(frag.condition())
                .as("org_id stays hard-coded in the legacy overload")
                .isEqualTo("(discriminator = ? AND org_id = ?)");
    }

    // ─── edge cases ──────────────────────────────────────────────────

    @Test
    void no_user_lines_yields_deny_all() {
        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("PARTY", "ALL")),
                List.of());

        assertThat(frag.condition()).isEqualTo("1 = 0");
    }

    @Test
    void missing_org_context_denies() {
        // No IamContext set → activeOrgCode() == null → ORG scope yields 1=0
        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(new PagePolicy("PARTY", "ORG")),
                List.of("PARTY"));

        assertThat(frag.condition()).isEqualTo("1 = 0");
    }

    @Test
    void empty_policies_yields_empty_fragment() {
        ExpressionParser.SqlFragment frag = LineRoleFilter.generateLineCondition(
                List.of(),
                List.of("PARTY"));

        assertThat(frag.condition()).isEmpty();
    }

    @SuppressWarnings("unused")
    private static PageSnapshot snapshotOf(PagePolicy... policies) {
        // reserved for future tests that exercise full ContextData;
        // kept to document the link between LineRoleFilter + PageSnapshot
        return null;
    }
}
