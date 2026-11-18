package com.conxist.capture.adapter.web;

import java.util.UUID;
import com.conxist.capture.application.ConnectionView;
import com.conxist.capture.application.PlaudAccounts.SignIn;
import com.conxist.capture.application.PlaudAccounts.SignInStart;
import com.conxist.capture.application.SignInToPlaud;
import com.conxist.capture.domain.StartFrom;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign in with Plaud: start on Plaud's page, come back to the callback, then link the account to a source. */
@RestController
@RequestMapping("/api")
class PlaudSignInController {

    /** Shown in the Plaud window after it sends the person back. Fixed text: nothing from the request is echoed. */
    private static final String DONE_PAGE = """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1"><title>ConXist</title></head>
            <body style="font-family: system-ui, sans-serif; padding: 32px; line-height: 1.5">
            <h1 style="font-size: 20px">Back to ConXist</h1>
            <p>You can close this window. ConXist shows the result in the window you came from.</p>
            <p lang="es">Puedes cerrar esta ventana. ConXist muestra el resultado en la ventana de la que viniste.</p>
            <script>window.close()</script></body></html>
            """;

    private final SignInToPlaud signInToPlaud;

    PlaudSignInController(SignInToPlaud signInToPlaud) {
        this.signInToPlaud = signInToPlaud;
    }

    @PostMapping("/recorders/plaud/sign-ins")
    @ResponseStatus(HttpStatus.CREATED)
    SignInStart start() {
        return signInToPlaud.start();
    }

    @GetMapping("/recorders/plaud/sign-ins/{signInId}")
    SignIn status(@PathVariable UUID signInId) {
        return signInToPlaud.status(signInId);
    }

    /** Plaud's sign-in page sends the person here, with a code or an error. */
    @GetMapping("/recorders/plaud/callback")
    ResponseEntity<String> callback(@RequestParam(required = false) @Size(max = 256) String state,
            @RequestParam(required = false) @Size(max = 4096) String code,
            @RequestParam(required = false) @Size(max = 256) String error) {
        signInToPlaud.finish(state, code, error);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(DONE_PAGE);
    }

    @PostMapping("/sources/{sourceId}/plaud-account")
    @ResponseStatus(HttpStatus.CREATED)
    ConnectionView connect(@PathVariable UUID sourceId, @Valid @RequestBody AccountRequest request) {
        return signInToPlaud.connect(sourceId, request.signInId(), request.startFrom(), request.includeSummaries());
    }

    @PostMapping("/sources/{sourceId}/plaud-account/sign-in")
    ConnectionView reconnect(@PathVariable UUID sourceId, @Valid @RequestBody ReconnectRequest request) {
        return signInToPlaud.reconnect(sourceId, request.signInId());
    }

    record AccountRequest(@NotNull UUID signInId, @NotNull StartFrom startFrom, boolean includeSummaries) {
    }

    record ReconnectRequest(@NotNull UUID signInId) {
    }
}
