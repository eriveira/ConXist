package com.conxist.platform;

import java.util.UUID;

/**
 * The workspace the current request acts for: the one the app names in each request (ADR 0006). There is no
 * sign-in yet, so any workspace on this machine may be named (ADR 0002).
 */
public interface CurrentWorkspace {

    UUID id();
}
