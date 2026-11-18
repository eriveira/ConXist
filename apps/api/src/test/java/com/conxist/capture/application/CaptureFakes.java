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
import com.conxist.capture.domain.MeetingText.Line;
import com.conxist.capture.domain.PlaudTeamConnection;
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
        public boolean exists(UUID workspaceId, UUID sourceId, String originReference) {
            return saved.stream().anyMatch(d -> d.workspaceId().equals(workspaceId) && d.sourceId().equals(sourceId)
                    && d.originReference().equals(originReference));
        }

        @Override
        public List<CapturedDocument> findBySource(UUID workspaceId, UUID sourceId) {
            return saved.stream().filter(d -> d.workspaceId().equals(workspaceId) && d.sourceId().equals(sourceId))
                    .toList();
        }
    }

    static final class Keys implements RecorderKeys {
        final List<String> created = new ArrayList<>();

        @Override
        public PublicKey create(UUID workspaceId) {
            String keyId = "cx-" + String.format("%016x", created.size() + 1);
            created.add(keyId);
            return new PublicKey(keyId, "-----BEGIN PUBLIC KEY-----\nfake\n-----END PUBLIC KEY-----\n");
        }

        @Override
        public boolean exists(UUID workspaceId, String keyId) {
            return created.contains(keyId);
        }
    }

    static final class Connections implements PlaudTeamConnections {
        final Map<UUID, PlaudTeamConnection> bySource = new HashMap<>();

        @Override
        public void save(PlaudTeamConnection connection) {
            bySource.put(connection.sourceId(), connection);
        }

        @Override
        public Optional<PlaudTeamConnection> find(UUID workspaceId, UUID sourceId) {
            return Optional.ofNullable(bySource.get(sourceId)).filter(c -> c.workspaceId().equals(workspaceId));
        }

        @Override
        public List<PlaudTeamConnection> findAll(UUID workspaceId) {
            return bySource.values().stream().filter(c -> c.workspaceId().equals(workspaceId)).toList();
        }
    }

    /** A Plaud Team with a change feed served in pages of {@code pageSize}. */
    static final class Plaud implements PlaudTeam {
        static final String CLIENT_ID = "client_acme";
        final List<TeamFile> feed = new ArrayList<>();
        final Map<String, Transcript> transcripts = new HashMap<>();
        final Map<String, List<String>> summaries = new HashMap<>();
        final List<String> checkpointsAsked = new ArrayList<>();
        int pageSize = 2;
        int transcriptCalls;
        boolean denying;
        boolean down;
        boolean checkpointExpired;

        TeamFile record(String id, String title, Instant recordedAt) {
            TeamFile file = new TeamFile(id, title, false, recordedAt, true, true);
            feed.add(file);
            transcripts.put(id, new Transcript("en", List.of(new Line("Speaker 1", 1_000, "Text of " + title))));
            summaries.put(id, List.of("Summary of " + title));
            return file;
        }

        private void check(Credential credential) {
            if (down) {
                throw new Unavailable("down");
            }
            if (denying || !CLIENT_ID.equals(credential.clientId())) {
                throw new AccessDenied("invalid_client");
            }
        }

        @Override
        public String teamName(Credential credential) {
            check(credential);
            return "Acme Engineering";
        }

        /** The checkpoint is the number of files already delivered; the cursor is the offset within this run. */
        @Override
        public ChangePage changes(Credential credential, String checkpoint, String cursor) {
            check(credential);
            checkpointsAsked.add(checkpoint);
            if (checkpointExpired && checkpoint != null) {
                checkpointExpired = false;
                throw new CheckpointExpired();
            }
            int start = (checkpoint == null ? 0 : Integer.parseInt(checkpoint)) + (cursor == null ? 0 : Integer.parseInt(cursor));
            int end = Math.min(feed.size(), start + pageSize);
            boolean more = end < feed.size();
            int base = checkpoint == null ? 0 : Integer.parseInt(checkpoint);
            return new ChangePage(List.copyOf(feed.subList(start, end)), more ? String.valueOf(end - base) : null,
                    more ? null : String.valueOf(end), more);
        }

        @Override
        public Optional<Transcript> transcript(Credential credential, String fileId) {
            check(credential);
            transcriptCalls++;
            return Optional.ofNullable(transcripts.get(fileId));
        }

        @Override
        public List<String> summaries(Credential credential, String fileId) {
            check(credential);
            return summaries.getOrDefault(fileId, List.of());
        }
    }
}
