package com.conxist.sources;

/** How far a provider is built. The canvas and the product page both read this. */
public enum Availability {
    /** Works end to end. */
    LIVE,
    /** Can be added, but its sign-in is simulated with sample data. */
    PREVIEW,
    /** Shown so people know it is planned; cannot be added yet. */
    COMING_SOON
}
