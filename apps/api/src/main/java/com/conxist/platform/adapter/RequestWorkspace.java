package com.conxist.platform.adapter;

import java.util.UUID;
import java.util.regex.Pattern;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.platform.WorkspaceDirectory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads the workspace from the request's {@value #HEADER} header and checks that it exists, once per request. Only
 * requests that act on a workspace read it, so listing or creating workspaces works without one.
 */
@Component
class RequestWorkspace implements CurrentWorkspace {

    static final String HEADER = "X-Workspace-Id";
    /** Only the full 36-character form; {@link UUID#fromString} alone also takes short forms such as 0-0-0-0-1. */
    private static final Pattern UUID_FORM =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final String CHECKED = RequestWorkspace.class.getName() + ".checked";

    private final WorkspaceDirectory directory;

    RequestWorkspace(WorkspaceDirectory directory) {
        this.directory = directory;
    }

    @Override
    public UUID id() {
        ServletRequestAttributes request = currentRequest();
        Object checked = request.getAttribute(CHECKED, RequestAttributes.SCOPE_REQUEST);
        if (checked instanceof UUID id) {
            return id;
        }
        UUID id = parse(request.getRequest().getHeader(HEADER));
        if (!directory.exists(id)) {
            throw new NotFound("workspace.not-found", "This workspace no longer exists. Choose another one.");
        }
        request.setAttribute(CHECKED, id, RequestAttributes.SCOPE_REQUEST);
        return id;
    }

    private static ServletRequestAttributes currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes;
        }
        throw new IllegalStateException("The current workspace is only known inside a request; pass the id instead");
    }

    /** A missing or malformed header both mean the app has not chosen a workspace. */
    private static UUID parse(String header) {
        String value = header == null ? "" : header.strip();
        if (!UUID_FORM.matcher(value).matches()) {
            throw new RuleViolation("workspace.required", "Choose a workspace first.");
        }
        return UUID.fromString(value);
    }
}
