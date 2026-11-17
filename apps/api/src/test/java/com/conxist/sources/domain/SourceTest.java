package com.conxist.sources.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCategory;
import org.junit.jupiter.api.Test;

class SourceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    void adds_an_upload_source_with_a_trimmed_name() {
        Source source = Source.add(ID, WORKSPACE, new SourceDraft(Provider.UPLOAD, "  Specs  ", null), NOW);

        assertThat(source.name()).isEqualTo("Specs");
        assertThat(source.accountLabel()).isEmpty();
        assertThat(source.toSummary().category()).isEqualTo(SourceCategory.CAPTURE);
    }

    @Test
    void rejects_a_blank_name() {
        assertThatThrownBy(() -> Source.add(ID, WORKSPACE, new SourceDraft(Provider.UPLOAD, "  ", null), NOW))
                .isInstanceOf(InvalidSource.class).hasMessage("Give the source a name.");
    }

    @Test
    void rejects_a_name_over_the_limit() {
        String longName = "x".repeat(Source.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> Source.add(ID, WORKSPACE, new SourceDraft(Provider.UPLOAD, longName, null), NOW))
                .isInstanceOf(InvalidSource.class);
    }

    @Test
    void rejects_a_provider_that_is_coming_soon() {
        assertThatThrownBy(() -> Source.add(ID, WORKSPACE, new SourceDraft(Provider.GITHUB, "Code", "me"), NOW))
                .isInstanceOf(InvalidSource.class).hasMessage("GitHub is coming soon.");
    }

    @Test
    void requires_a_sign_in_for_a_recorder() {
        assertThatThrownBy(() -> Source.add(ID, WORKSPACE, new SourceDraft(Provider.PLAUD, "Meetings", ""), NOW))
                .isInstanceOf(InvalidSource.class).hasMessage("Sign in to Plaud first.");
    }

    @Test
    void rejects_a_missing_provider() {
        assertThatThrownBy(() -> Source.add(ID, WORKSPACE, new SourceDraft(null, "Specs", null), NOW))
                .isInstanceOf(InvalidSource.class);
    }
}
