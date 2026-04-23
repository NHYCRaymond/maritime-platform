package com.maritime.iam.sdk.dataperm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopeColumnsTest {

    @Test
    void default_matches_pre_1_0_8_hardcoded_columns() {
        assertThat(ScopeColumns.DEFAULT.orgColumn()).isEqualTo("org_id");
        assertThat(ScopeColumns.DEFAULT.selfColumn()).isEqualTo("user_id");
        assertThat(ScopeColumns.DEFAULT.lineTypeColumn()).isEqualTo("line_type");
    }

    @Test
    void custom_columns_are_honoured() {
        ScopeColumns c = new ScopeColumns(
                "creator_org_code", "creator_user_id", "discriminator");

        assertThat(c.orgColumn()).isEqualTo("creator_org_code");
        assertThat(c.selfColumn()).isEqualTo("creator_user_id");
        assertThat(c.lineTypeColumn()).isEqualTo("discriminator");
    }

    @Test
    void blank_column_names_are_rejected() {
        assertThatThrownBy(() -> new ScopeColumns(null, "u", "l"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orgColumn");
        assertThatThrownBy(() -> new ScopeColumns("o", " ", "l"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selfColumn");
        assertThatThrownBy(() -> new ScopeColumns("o", "u", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineTypeColumn");
    }
}
