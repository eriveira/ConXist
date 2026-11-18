package com.conxist.platform;

import java.util.UUID;

/** The workspace the current request acts for. Single workspace until sign-in exists (ADR 0002). */
public interface CurrentWorkspace {

    UUID id();
}
