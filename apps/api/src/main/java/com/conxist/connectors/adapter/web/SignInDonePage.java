package com.conxist.connectors.adapter.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The page a sign-in window shows once a server's sign-in (ADR 0017) or Google's (ADR 0021) sends the person back.
 * Fixed text: nothing from the request is echoed, and only its one inline script may run.
 */
final class SignInDonePage {

    private static final String PAGE = """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1"><title>ConXist</title></head>
            <body style="font-family: system-ui, sans-serif; padding: 32px; line-height: 1.5">
            <h1 style="font-size: 20px">Back to ConXist</h1>
            <p>You can close this window. ConXist shows the result in the window you came from.</p>
            <p lang="es">Puedes cerrar esta ventana. ConXist muestra el resultado en la ventana de la que viniste.</p>
            <script>window.close()</script></body></html>
            """;
    private static final String POLICY = "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-"
            + scriptHash("window.close()") + "'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    private SignInDonePage() {
    }

    static ResponseEntity<String> response() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", POLICY)
                .body(PAGE);
    }

    private static String scriptHash(String script) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(script.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
