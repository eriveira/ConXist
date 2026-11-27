package com.conxist.connectors.application;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.application.GmailSignIns.SignIn;
import com.conxist.connectors.application.GmailSignIns.Status;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.InvalidIntegration;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects Gmail with Sign in with Google (ADR 0021): ConXist asks Google for read-only Gmail access only, keeps the
 * refresh token encrypted once the person adds the integration, and lists the mailbox's labels so the person picks the
 * ones agents may read. The same sign-in serves a Gmail that signs in again.
 */
public final class ConnectGmail {

    private static final Logger log = LoggerFactory.getLogger(ConnectGmail.class);
    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();

    private final GoogleSignIn google;
    private final IntegrationApi gmail;
    private final GmailSignIns signIns;
    private final IntegrationRepository integrations;
    private final ManageIntegrations manage;
    private final CurrentWorkspace workspace;
    private final SecureRandom random = new SecureRandom();

    public ConnectGmail(GoogleSignIn google, List<IntegrationApi> apis, GmailSignIns signIns,
            IntegrationRepository integrations, ManageIntegrations manage, CurrentWorkspace workspace) {
        this.google = google;
        this.gmail = apis.stream().filter(api -> api.kind() == Integration.Kind.GMAIL).findFirst()
                .orElseThrow(() -> new IllegalStateException("No API for GMAIL"));
        this.signIns = signIns;
        this.integrations = integrations;
        this.manage = manage;
        this.workspace = workspace;
    }

    /** The kinds this ConXist can connect: Gmail only with a Google OAuth client set up. */
    public List<Integration.Kind> offeredKinds() {
        return Arrays.stream(Integration.Kind.values())
                .filter(kind -> kind != Integration.Kind.GMAIL || google.configured()).toList();
    }

    /** Starts a Google sign-in, for a new Gmail or for {@code integrationId} signing in again. */
    public IntegrationViews.GmailSignInView start(UUID integrationId) {
        if (!google.configured()) {
            throw new InvalidIntegration("gmail-not-set-up", "Gmail isn't set up on this ConXist.");
        }
        UUID workspaceId = workspace.id();
        if (integrationId != null) {
            integrations.find(workspaceId, integrationId).filter(found -> found.kind() == Integration.Kind.GMAIL)
                    .orElseThrow(ConnectGmail::integrationGone);
        }
        String state = secret();
        String verifier = secret();
        SignIn started = new SignIn(UUID.randomUUID(), workspaceId, integrationId, Status.SIGNING_IN, state, verifier,
                google.authorizationUrl(state, verifier), null, null, null, null, signIns.now());
        return view(signIns.save(started));
    }

    /** The sign-in as it is now; the app asks until Google sends the person back. */
    public IntegrationViews.GmailSignInView status(UUID signInId) {
        return view(load(signInId));
    }

    /** Lists the mailbox's labels again, after the person made one in Gmail. */
    public IntegrationViews.GmailSignInView relist(UUID signInId) {
        SignIn signIn = ready(signInId);
        return view(signIns.save(signIn.relisted(account(signIn.credential()))));
    }

    /** Google sends the person back here with a code or an error. An unknown or used state does nothing. */
    public void finish(String state, String code, String error) {
        signIns.claim(state).ifPresent(signIn -> {
            if (error != null || code == null || code.isEmpty()) {
                log.info("Workspace {} did not finish signing in to Google", signIn.workspaceId());
                signIns.save(signIn.failed("sign-in-refused"));
                return;
            }
            try {
                String credential = google.exchange(code, signIn.verifier());
                signIns.save(signIn.ready(credential, gmail.account(credential)));
                log.info("Workspace {} signed in to Google for Gmail", signIn.workspaceId());
            } catch (IntegrationApi.TokenRefused refused) {
                fail(signIn, "sign-in-refused", refused);
            } catch (IntegrationApi.Refused refused) {
                fail(signIn, "scope-refused", refused);
            } catch (RuntimeException unreachable) {
                // Missing, Unreachable, or anything unexpected: the app stops waiting instead of asking for 15 minutes.
                fail(signIn, "unreachable", unreachable);
            }
        });
    }

    /** Adds Gmail from a finished sign-in, with the labels agents may read (or all mail) and the ticked tools. */
    public IntegrationViews.IntegrationView add(UUID signInId, List<String> labels, Set<String> allowed) {
        SignIn signIn = ready(signInId);
        if (signIn.integrationId() != null) {
            throw signInGone();
        }
        IntegrationViews.IntegrationView added = manage.addSignedIn(Integration.Kind.GMAIL, signIn.credential(),
                labels, allowed);
        signIns.forget(signInId);
        return added;
    }

    /** Saves the new sign-in of a Gmail that signed in again; the labels it still has stay picked. */
    public IntegrationViews.IntegrationView replace(UUID integrationId, UUID signInId) {
        SignIn signIn = ready(signInId);
        if (!integrationId.equals(signIn.integrationId())) {
            throw signInGone();
        }
        IntegrationViews.IntegrationView replaced = manage.replaceSignedIn(integrationId, signIn.credential());
        signIns.forget(signInId);
        return replaced;
    }

    /** Never logs the code, the token or Google's answer: only the workspace and what went wrong. */
    private void fail(SignIn signIn, String error, RuntimeException cause) {
        log.warn("Workspace {} could not finish signing in to Google: {} ({})", signIn.workspaceId(), error,
                cause.getClass().getSimpleName());
        signIns.save(signIn.failed(error));
    }

    private IntegrationApi.Account account(String credential) {
        try {
            return gmail.account(credential);
        } catch (IntegrationApi.TokenRefused refused) {
            throw new InvalidIntegration("gmail-token-refused", "Google refused the sign-in. Sign in again.");
        } catch (IntegrationApi.Refused | IntegrationApi.Unreachable | IntegrationApi.Missing unreachable) {
            throw new InvalidIntegration("gmail-unreachable", "Gmail did not answer. Try again in a minute.");
        }
    }

    private SignIn ready(UUID signInId) {
        SignIn signIn = load(signInId);
        if (signIn.status() != Status.READY) {
            throw signInGone();
        }
        return signIn;
    }

    private SignIn load(UUID signInId) {
        return signIns.find(workspace.id(), signInId).orElseThrow(ConnectGmail::signInGone);
    }

    private IntegrationViews.GmailSignInView view(SignIn signIn) {
        return new IntegrationViews.GmailSignInView(signIn.id(), signIn.status(), signIn.url(), signIn.account(),
                signIn.labels(), signIn.status() == Status.READY ? Integration.Kind.GMAIL.tools().stream()
                        .map(tool -> McpServerViews.tool(tool, List.of())).toList() : List.of(), signIn.error());
    }

    private String secret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return URL_SAFE.encodeToString(bytes);
    }

    private static NotFound signInGone() {
        return new NotFound("integration.gmail-sign-in-gone", "This sign-in expired. Sign in with Google again.");
    }

    private static NotFound integrationGone() {
        return new NotFound("integration.not-found", "This integration is no longer in this workspace.");
    }
}
