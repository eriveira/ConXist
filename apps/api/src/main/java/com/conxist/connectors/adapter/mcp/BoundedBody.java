package com.conxist.connectors.adapter.mcp;

import java.io.IOException;
import java.io.InputStream;
import com.conxist.connectors.application.McpGateway;

/** Reads an answer up to a limit, so a broken or hostile server cannot fill the memory with one answer. */
final class BoundedBody {

    private BoundedBody() {
    }

    static byte[] read(InputStream body, int maxBytes) throws IOException {
        byte[] bytes = body.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new McpGateway.Unreachable("answer too large");
        }
        return bytes;
    }
}
