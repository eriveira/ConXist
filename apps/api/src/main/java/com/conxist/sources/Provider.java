package com.conxist.sources;

import static com.conxist.sources.Availability.COMING_SOON;
import static com.conxist.sources.Availability.LIVE;
import static com.conxist.sources.Availability.PREVIEW;
import static com.conxist.sources.SourceCategory.CAPTURE;
import static com.conxist.sources.SourceCategory.REPO;
import static com.conxist.sources.SourceCategory.STORAGE;

/** Every provider in the Phase 1 scope set by the owner (AGENTS.md). */
public enum Provider {
    UPLOAD(CAPTURE, LIVE, "File upload", false),
    PLAUD(CAPTURE, PREVIEW, "Plaud", true),
    EMAIL(CAPTURE, COMING_SOON, "Email", false),
    GITHUB(REPO, COMING_SOON, "GitHub", true),
    BITBUCKET(REPO, COMING_SOON, "Bitbucket", true),
    R2(STORAGE, COMING_SOON, "Cloudflare R2", false),
    S3(STORAGE, COMING_SOON, "Amazon S3", false),
    GOOGLE_DRIVE(STORAGE, COMING_SOON, "Google Drive", true),
    ICLOUD(STORAGE, COMING_SOON, "iCloud", true),
    ONEDRIVE(STORAGE, COMING_SOON, "OneDrive / SharePoint", true),
    CONFLUENCE(STORAGE, COMING_SOON, "Confluence", true);

    private final SourceCategory category;
    private final Availability availability;
    private final String displayName;
    private final boolean signInRequired;

    Provider(SourceCategory category, Availability availability, String displayName, boolean signInRequired) {
        this.category = category;
        this.availability = availability;
        this.displayName = displayName;
        this.signInRequired = signInRequired;
    }

    public SourceCategory category() {
        return category;
    }

    public Availability availability() {
        return availability;
    }

    public String displayName() {
        return displayName;
    }

    public boolean signInRequired() {
        return signInRequired;
    }

    public boolean canBeAdded() {
        return availability != COMING_SOON;
    }
}
