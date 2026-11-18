package com.conxist.sources.domain;

import com.conxist.sources.Provider;

/** What a person filled in to connect a source. The account label is empty for providers without sign-in. */
public record SourceDraft(Provider provider, String name, String accountLabel) {
}
