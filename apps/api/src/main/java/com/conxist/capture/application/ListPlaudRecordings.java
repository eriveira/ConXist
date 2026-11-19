package com.conxist.capture.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.conxist.capture.application.PlaudAccounts.AccountFile;
import com.conxist.capture.application.PlaudAccounts.DateRange;
import com.conxist.capture.application.PlaudAccounts.RecentFiles;
import com.conxist.capture.application.RecordingPicks.Pick;

/**
 * The recordings a person can pick from while connecting their Plaud account: the newest ones, optionally between
 * two dates. Plaud lists recordings only by date, so searching and sorting by name happen in the web app over what
 * this returns. A list is kept for a few minutes per sign-in, so moving between filters does not ask Plaud again, and
 * so the picks sent with Add can be checked against what the person was shown.
 */
public final class ListPlaudRecordings {

    static final Duration KEEP = Duration.ofMinutes(10);
    /** Lists kept at once, across all sign-ins; the oldest goes first. */
    static final int MAX_LISTS = 100;
    /** Date ranges remembered per sign-in, to list again when a list expired before Add was pressed. */
    static final int MAX_RANGES = 20;

    private final PlaudAccounts plaud;
    private final CaptureContext context;
    private final Map<Key, Listed> lists = new LinkedHashMap<>();
    /** The ranges each sign-in listed, kept longer than the lists themselves. */
    private final Map<SignInKey, Set<DateRange>> ranges = new LinkedHashMap<>();

    public ListPlaudRecordings(PlaudAccounts plaud, CaptureContext context) {
        this.plaud = plaud;
        this.context = context;
    }

    public RecordingList list(UUID signInId, LocalDate from, LocalDate to) {
        DateRange dates = new DateRange(from, to);
        if (dates.reversed()) {
            throw RecorderProblems.datesReversed();
        }
        RecentFiles files = read(new Key(new SignInKey(context.workspace().id(), signInId), dates));
        return new RecordingList(files.files().stream().map(Recording::of).toList(), files.capped(),
                PlaudAccounts.MAX_RECENT_FILES);
    }

    /**
     * The picked recordings, as this sign-in listed them. A pick the person was not shown, or that is no longer in
     * Plaud, is refused, so a request cannot make ConXist read a recording by guessing its id. When the lists the
     * person saw have expired, the same date ranges are listed again.
     */
    List<Pick> resolve(UUID workspaceId, UUID signInId, Collection<String> fileIds) {
        SignInKey signIn = new SignInKey(workspaceId, signInId);
        Map<String, AccountFile> listed = listedBy(signIn);
        if (!listed.keySet().containsAll(fileIds)) {
            for (DateRange dates : rangesOf(signIn)) {
                read(new Key(signIn, dates));
            }
            listed = listedBy(signIn);
        }
        List<Pick> picks = new ArrayList<>();
        for (String id : fileIds) {
            AccountFile file = listed.get(id);
            if (file == null) {
                throw RecorderProblems.pickNotListed();
            }
            picks.add(new Pick(id, file.recordedAt()));
        }
        return picks;
    }

    /** The sign-in now belongs to a source: its lists are no longer needed. */
    void forget(UUID signInId) {
        synchronized (lists) {
            lists.keySet().removeIf(key -> key.signIn().signInId().equals(signInId));
            ranges.keySet().removeIf(key -> key.signInId().equals(signInId));
        }
    }

    private RecentFiles read(Key key) {
        Instant now = context.clock().instant();
        synchronized (lists) {
            lists.values().removeIf(listed -> listed.expired(now));
            Listed kept = lists.get(key);
            if (kept != null) {
                return kept.files();
            }
        }
        RecentFiles files = withDates(fetch(key));
        synchronized (lists) {
            lists.put(key, new Listed(files, now));
            evictOldest(lists, MAX_LISTS);
            Set<DateRange> used = ranges.computeIfAbsent(key.signIn(), signIn -> new LinkedHashSet<>());
            used.add(key.dates());
            if (used.size() > MAX_RANGES) {
                used.remove(used.iterator().next());
            }
            evictOldest(ranges, MAX_LISTS);
        }
        return files;
    }

    private RecentFiles fetch(Key key) {
        try {
            return plaud.recentFiles(key.signIn().workspaceId(), key.signIn().signInId(), key.dates());
        } catch (PlaudAccounts.AccessDenied denied) {
            throw RecorderProblems.signInExpired();
        } catch (PlaudAccounts.Unavailable unavailable) {
            throw RecorderProblems.unavailable();
        }
    }

    /** A recording without a readable date cannot be shown by day nor imported in order, so it is left out. */
    private static RecentFiles withDates(RecentFiles files) {
        return new RecentFiles(files.files().stream().filter(file -> file.recordedAt() != null).toList(),
                files.capped());
    }

    private Map<String, AccountFile> listedBy(SignInKey signIn) {
        Instant now = context.clock().instant();
        Map<String, AccountFile> listed = new HashMap<>();
        synchronized (lists) {
            lists.forEach((key, kept) -> {
                if (key.signIn().equals(signIn) && !kept.expired(now)) {
                    kept.files().files().forEach(file -> listed.put(file.id(), file));
                }
            });
        }
        return listed;
    }

    private List<DateRange> rangesOf(SignInKey signIn) {
        synchronized (lists) {
            Set<DateRange> used = ranges.get(signIn);
            return used == null ? List.of(DateRange.ANY) : List.copyOf(used);
        }
    }

    private static void evictOldest(Map<?, ?> map, int max) {
        while (map.size() > max) {
            map.remove(map.keySet().iterator().next());
        }
    }

    /** {@code capped}: there are more than {@code max} recordings in the range; only the newest are listed. */
    public record RecordingList(List<Recording> recordings, boolean capped, int max) {
    }

    /** {@code lengthSeconds} is null when Plaud does not give it. The title is customer data, shown, never logged. */
    public record Recording(String id, String title, Instant recordedAt, Long lengthSeconds) {

        static Recording of(AccountFile file) {
            return new Recording(file.id(), file.title(), file.recordedAt(),
                    file.length() == null ? null : file.length().toSeconds());
        }
    }

    private record SignInKey(UUID workspaceId, UUID signInId) {
    }

    private record Key(SignInKey signIn, DateRange dates) {

        Key {
            Objects.requireNonNull(signIn);
            Objects.requireNonNull(dates);
        }
    }

    private record Listed(RecentFiles files, Instant at) {

        boolean expired(Instant now) {
            return at.plus(KEEP).isBefore(now);
        }
    }
}
