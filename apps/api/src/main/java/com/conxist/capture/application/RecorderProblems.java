package com.conxist.capture.application;

import com.conxist.capture.domain.InvalidRecorderLink;
import com.conxist.platform.RuleViolation;

/** The messages a person sees when Plaud refuses or does not answer. */
final class RecorderProblems {

    private RecorderProblems() {
    }

    static RuleViolation notAccepted() {
        return new InvalidRecorderLink("not-accepted", "Plaud did not accept this Client ID and key. Check that your "
                + "Team admin registered the ConXist key for this Client ID, then press Test again.");
    }

    static RuleViolation accessLost() {
        return new InvalidRecorderLink("access-lost", "Plaud stopped accepting the ConXist key. Ask your Plaud Team "
                + "admin to check the key, then press Retry. Meetings already imported stay.");
    }

    static RuleViolation signedOut() {
        return new InvalidRecorderLink("signed-out", "Plaud signed ConXist out of this account. Sign in with Plaud "
                + "again to keep importing. Meetings already imported stay.");
    }

    static RuleViolation signInNotFinished() {
        return new InvalidRecorderLink("sign-in-not-finished", "Sign in with Plaud first, then press Add.");
    }

    static RuleViolation otherAccount() {
        return new InvalidRecorderLink("other-account", "That is a different Plaud account. Sign in with the account "
                + "this source was connected to, or add another meeting recorder for this one.");
    }

    static RuleViolation unavailable() {
        return new InvalidRecorderLink("unavailable", "Plaud is not answering right now. Try again in a minute.");
    }
}
