package com.conxist.capture.domain;

import java.util.Set;

/** Which files a workspace accepts for upload. */
public record UploadPolicy(long maxBytes, Set<String> acceptedMediaTypes) {

    public UploadPolicy {
        acceptedMediaTypes = Set.copyOf(acceptedMediaTypes);
    }

    public void check(String mediaType, long sizeBytes) {
        if (sizeBytes == 0) {
            throw new RejectedDocument("empty", "This file is empty.");
        }
        if (sizeBytes > maxBytes) {
            throw new RejectedDocument("too-large", "This file is too large to upload.");
        }
        if (!acceptedMediaTypes.contains(mediaType)) {
            throw new RejectedDocument("unsupported-type", "This file type is not supported yet. Upload a PDF, Word, Markdown or text file.");
        }
    }
}
