package com.conxist.platform.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.platform.WorkspaceDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestWorkspaceTest {

    private static final UUID KNOWN = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final AtomicInteger lookups = new AtomicInteger();
    private final RequestWorkspace workspace = new RequestWorkspace(new WorkspaceDirectory() {
        @Override
        public boolean exists(UUID id) {
            lookups.incrementAndGet();
            return KNOWN.equals(id);
        }

        @Override
        public List<UUID> ids() {
            return List.of(KNOWN);
        }
    });

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void reads_the_header_and_checks_it_once_per_request() {
        request(KNOWN.toString());

        assertThat(workspace.id()).isEqualTo(KNOWN);
        assertThat(workspace.id()).isEqualTo(KNOWN);
        assertThat(lookups).hasValue(1);
    }

    @Test
    void refuses_a_missing_malformed_or_unknown_workspace() {
        request(null);
        assertThatThrownBy(workspace::id).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("workspace.required");
        request("not-an-id");
        assertThatThrownBy(workspace::id).extracting(e -> ((RuleViolation) e).code()).isEqualTo("workspace.required");
        request(UUID.randomUUID().toString());
        assertThatThrownBy(workspace::id).isInstanceOf(NotFound.class);
    }

    @Test
    void is_not_known_outside_a_request() {
        assertThatThrownBy(workspace::id).isInstanceOf(IllegalStateException.class);
    }

    private static void request(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(RequestWorkspace.HEADER, header);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
