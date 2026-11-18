package com.conxist.capture.config;

import java.time.Clock;
import java.net.http.HttpClient;
import java.util.Base64;
import com.conxist.capture.adapter.extraction.TikaTextExtractor;
import com.conxist.capture.adapter.files.LocalFileStore;
import com.conxist.capture.application.CaptureContext;
import com.conxist.capture.application.CaptureUpload;
import com.conxist.capture.application.CapturedDocumentRepository;
import com.conxist.capture.application.FileStore;
import com.conxist.capture.application.ListDocuments;
import com.conxist.capture.adapter.plaud.JdbcRecorderKeys;
import com.conxist.capture.adapter.plaud.PlaudTeamClient;
import com.conxist.capture.adapter.plaud.SecretBox;
import com.conxist.capture.application.ConnectPlaudTeam;
import com.conxist.capture.application.ListRecorderConnections;
import com.conxist.capture.application.PlaudTeam;
import com.conxist.capture.application.PlaudTeamConnections;
import com.conxist.capture.application.SyncRecorder;
import com.conxist.capture.application.TextExtractor;
import com.conxist.capture.domain.UploadPolicy;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.EventPublisher;
import com.conxist.platform.UnitOfWork;
import com.conxist.sources.SourceCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** Wires the capture use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class CaptureConfiguration {

    @Bean
    TextExtractor textExtractor(CaptureProperties properties) {
        return new TikaTextExtractor(properties.maxTextCharacters());
    }

    @Bean
    FileStore fileStore(CaptureProperties properties) {
        return new LocalFileStore(properties.storageRoot());
    }

    @Bean
    CaptureContext captureContext(CurrentWorkspace workspace, EventPublisher events, UnitOfWork unitOfWork,
            Clock clock) {
        return new CaptureContext(workspace, events, unitOfWork, clock);
    }

    @Bean
    CaptureUpload captureUpload(SourceCatalog catalog, CaptureProperties properties, TextExtractor extractor,
            FileStore files, CapturedDocumentRepository documents, CaptureContext context) {
        UploadPolicy policy = new UploadPolicy(properties.maxFileBytes(), properties.acceptedMediaTypes());
        return new CaptureUpload(catalog, policy, extractor, files, documents, context);
    }

    @Bean
    JdbcRecorderKeys recorderKeys(JdbcClient jdbc, SecretsProperties secrets, Clock clock) {
        return new JdbcRecorderKeys(jdbc, new SecretBox(Base64.getDecoder().decode(secrets.encryptionKey())), clock);
    }

    @Bean
    PlaudTeam plaudTeam(PlaudProperties plaud, JdbcRecorderKeys keys, Clock clock) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(plaud.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        requests.setReadTimeout(plaud.readTimeout());
        RestClient http = RestClient.builder().requestFactory(requests).build();
        return new PlaudTeamClient(http, plaud.apiBaseUrl(), plaud.tokenUrl(), plaud.tokenAudience(),
                plaud.pageSize(), keys, clock);
    }

    @Bean
    ConnectPlaudTeam connectPlaudTeam(SourceCatalog catalog, JdbcRecorderKeys keys, PlaudTeam plaud,
            PlaudTeamConnections connections, CaptureContext context) {
        return new ConnectPlaudTeam(catalog, keys, plaud, connections, context);
    }

    @Bean
    SyncRecorder syncRecorder(SourceCatalog catalog, PlaudTeam plaud, PlaudTeamConnections connections,
            CapturedDocumentRepository documents, CaptureContext context, CaptureProperties capture) {
        return new SyncRecorder(catalog, plaud, connections, documents, context, capture.maxTextCharacters());
    }

    @Bean
    ListRecorderConnections listRecorderConnections(PlaudTeamConnections connections, CurrentWorkspace workspace) {
        return new ListRecorderConnections(connections, workspace);
    }

    @Bean
    ListDocuments listDocuments(CapturedDocumentRepository documents, CurrentWorkspace workspace) {
        return new ListDocuments(documents, workspace);
    }
}
