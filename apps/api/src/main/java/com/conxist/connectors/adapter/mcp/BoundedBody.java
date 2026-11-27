package com.conxist.connectors.adapter.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.conxist.connectors.application.McpGateway;

/**
 * Reads an answer up to a limit and within a deadline, so a broken or hostile server can neither fill the memory with
 * one answer nor hold a thread by sending it a byte at a time. The client's read timeout only covers the headers.
 */
public final class BoundedBody {

    static final Duration DEADLINE = Duration.ofSeconds(30);
    private static final ScheduledExecutorService CLOSER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "mcp-body-deadline");
        thread.setDaemon(true);
        return thread;
    });

    private BoundedBody() {
    }

    public static byte[] read(InputStream body, int maxBytes) throws IOException {
        return read(body, maxBytes, DEADLINE);
    }

    /** Up to {@code maxBytes}, cutting a longer answer there instead of refusing it, such as a page's head. */
    public static byte[] read(InputStream body, int maxBytes, boolean cut) throws IOException {
        return read(body, maxBytes, DEADLINE, cut);
    }

    static byte[] read(InputStream body, int maxBytes, Duration deadline) throws IOException {
        return read(body, maxBytes, deadline, false);
    }

    private static byte[] read(InputStream body, int maxBytes, Duration deadline, boolean cut) throws IOException {
        AtomicBoolean late = new AtomicBoolean();
        ScheduledFuture<?> closing = CLOSER.schedule(() -> {
            late.set(true);
            try {
                body.close();
            } catch (IOException ignored) {
                // The read below fails either way.
            }
        }, deadline.toMillis(), TimeUnit.MILLISECONDS);
        byte[] bytes;
        try {
            bytes = body.readNBytes(maxBytes + 1);
        } catch (IOException failed) {
            if (late.get()) {
                throw new McpGateway.Unreachable("answer too slow");
            }
            throw failed;
        } finally {
            closing.cancel(false);
        }
        if (late.get()) {
            throw new McpGateway.Unreachable("answer too slow");
        }
        if (bytes.length > maxBytes) {
            if (cut) {
                return java.util.Arrays.copyOf(bytes, maxBytes);
            }
            throw new McpGateway.Unreachable("answer too large");
        }
        return bytes;
    }
}
