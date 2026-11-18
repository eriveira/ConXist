package com.conxist.capture.domain;

/** Whether ConXist can still read a recorder account. */
public enum ConnectionStatus {
    /** Checked on a schedule; new recordings arrive on their own. */
    ACTIVE,
    /** The recorder stopped accepting ConXist's credential. Imported meetings stay; a person has to reconnect. */
    PAUSED
}
