package com.conxist.capture.adapter.plaudaccount;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The client ids ConXist received from authorization servers, so it registers once per redirect address. */
class ClientRegistrations {

    private final JdbcClient jdbc;

    ClientRegistrations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<String> find(String issuer, String redirectUri) {
        return jdbc.sql("select client_id from oauth_clients where issuer = :issuer and redirect_uri = :redirect")
                .param("issuer", issuer)
                .param("redirect", redirectUri)
                .query(String.class)
                .optional();
    }

    void forget(String issuer, String redirectUri) {
        jdbc.sql("delete from oauth_clients where issuer = :issuer and redirect_uri = :redirect")
                .param("issuer", issuer)
                .param("redirect", redirectUri)
                .update();
    }

    void save(String issuer, String redirectUri, String clientId, Instant at) {
        jdbc.sql("""
                insert into oauth_clients (issuer, redirect_uri, client_id, registered_at)
                values (:issuer, :redirect, :clientId, :at)
                on conflict (issuer, redirect_uri) do update set client_id = excluded.client_id,
                  registered_at = excluded.registered_at
                """)
                .param("issuer", issuer)
                .param("redirect", redirectUri)
                .param("clientId", clientId)
                .param("at", Timestamp.from(at))
                .update();
    }
}
