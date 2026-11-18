package com.conxist.capture.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.RejectedDocument;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCatalog;
import com.conxist.sources.SourceSummary;

/** Network-free stand-ins for the capture ports. */
final class CaptureFakes {

    private CaptureFakes() {
    }

    static final class Sources implements SourceCatalog {
        private final Map<UUID, SourceSummary> byId = new HashMap<>();

        SourceSummary add(Provider provider, String accountLabel) {
            SourceSummary source = new SourceSummary(UUID.randomUUID(), provider.category(), provider,
                    provider.displayName(), accountLabel, Instant.EPOCH);
            byId.put(source.id(), source);
            return source;
        }

        @Override
        public Optional<SourceSummary> find(UUID sourceId) {
            return Optional.ofNullable(byId.get(sourceId));
        }
    }

    /** Treats the content as UTF-8 text; the media type is whatever the test set. */
    static final class Extractor implements TextExtractor {
        String mediaType = "text/plain";
        boolean unreadable;

        @Override
        public String detectMediaType(byte[] content, String fileName) {
            return mediaType;
        }

        @Override
        public String extractText(byte[] content, String mediaType) {
            if (unreadable) {
                throw new RejectedDocument("unreadable", "We could not read the text in this file.");
            }
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    static final class Files implements FileStore {
        final List<byte[]> stored = new ArrayList<>();

        @Override
        public String store(UUID workspaceId, UUID sourceId, byte[] content) {
            stored.add(content);
            return "key-" + stored.size();
        }

        @Override
        public void delete(String key) {
            stored.remove(Integer.parseInt(key.substring("key-".length())) - 1);
        }
    }

    static final class Documents implements CapturedDocumentRepository {
        final List<CapturedDocument> saved = new ArrayList<>();
        boolean failing;

        @Override
        public boolean saveIfNew(CapturedDocument document) {
            if (failing) {
                throw new IllegalStateException("database unavailable");
            }
            boolean known = saved.stream().anyMatch(d -> d.sourceId().equals(document.sourceId())
                    && d.originReference().equals(document.originReference()));
            if (!known) {
                saved.add(document);
            }
            return !known;
        }

        @Override
        public List<CapturedDocument> findBySource(UUID workspaceId, UUID sourceId) {
            return saved.stream().filter(d -> d.workspaceId().equals(workspaceId) && d.sourceId().equals(sourceId))
                    .toList();
        }
    }

    static final class Recorder implements RecorderConnector {
        final List<Recording> recordings = new ArrayList<>();
        String lastAccount;

        @Override
        public RecorderAccount signIn() {
            return new RecorderAccount("Team account");
        }

        @Override
        public List<Recording> recordings(String accountLabel) {
            lastAccount = accountLabel;
            return recordings;
        }
    }
}
