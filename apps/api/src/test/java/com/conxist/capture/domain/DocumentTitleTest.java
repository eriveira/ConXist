package com.conxist.capture.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentTitleTest {

    @Test
    void keeps_only_the_file_name_from_a_path() {
        assertThat(DocumentTitle.from("../../etc/specs.pdf").value()).isEqualTo("specs.pdf");
        assertThat(DocumentTitle.from("C:\\Users\\me\\notes.md").value()).isEqualTo("notes.md");
    }

    @Test
    void removes_control_characters() {
        assertThat(DocumentTitle.from("plan\u0000\n.txt").value()).isEqualTo("plan.txt");
    }

    @Test
    void falls_back_when_there_is_no_name() {
        assertThat(DocumentTitle.from(null).value()).isEqualTo(DocumentTitle.UNTITLED);
        assertThat(DocumentTitle.from("   ").value()).isEqualTo(DocumentTitle.UNTITLED);
    }

    @Test
    void cuts_long_names_to_the_limit() {
        assertThat(DocumentTitle.from("a".repeat(500)).value()).hasSize(DocumentTitle.MAX_LENGTH);
    }
}
