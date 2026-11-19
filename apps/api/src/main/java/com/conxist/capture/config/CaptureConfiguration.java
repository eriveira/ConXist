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
import com.conxist.capture.adapter.secrets.SecretBox;
import com.conxist.capture.application.ConnectPlaudTeam;
import com.conxist.capture.application.ListRecorderConnections;
import com.conxist.capture.application.PlaudTeam;
import com.conxist.capture.application.SignInToPlaud;
import com.conxist.capture.application.ListPlaudRecordings;
import com.conxist.capture.application.RecordingPicks;
import com.conxist.capture.application.PlaudAccountConnections;
import com.conxist.capture.application.PlaudAccounts;
import com.conxist.capture.adapter.plaudaccount.PlaudMcpAccounts;
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
    SecretBox secretBox(SecretsProperties secrets) {
        return new SecretBox(Base64.getDecoder().decode(secrets.encryptionKey()));
    }

    @Bean
    FileStore fileStore(CaptureProperties properties, SecretBox box) {
        return new LocalFileStore(properties.storageRoot(), box);
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
    JdbcRecorderKeys recorderKeys(JdbcClient jdbc, SecretBox box, Clock clock) {
        return new JdbcRecorderKeys(jdbc, box, clock);
    }

    @Bean
    PlaudTeam plaudTeam(PlaudProperties plaud, JdbcRecorderKeys keys, Clock clock) {
        return new PlaudTeamClient(plaudHttp(plaud), plaud.apiBaseUrl(), plaud.tokenUrl(), plaud.tokenAudience(),
                plaud.pageSize(), keys, clock);
    }

    @Bean
    PlaudAccounts plaudAccounts(PlaudProperties plaud, CaptureProperties capture, JdbcClient jdbc, SecretBox box,
            Clock clock) {
        return PlaudMcpAccounts.create(plaudHttp(plaud), plaud.account().mcpUrl(), plaud.account().redirectUri(),
                clock, jdbc, box, capture.maxTextCharacters());
    }

    @Bean
    ListPlaudRecordings listPlaudRecordings(PlaudAccounts plaud, CaptureContext context) {
        return new ListPlaudRecordings(plaud, context);
    }

    @Bean
    SignInToPlaud signInToPlaud(SourceCatalog catalog, PlaudAccounts plaud, PlaudAccountConnections accounts,
            PlaudTeamConnections teams, ListPlaudRecordings recordings, RecordingPicks picks, CaptureContext context) {
        return new SignInToPlaud(catalog, plaud, accounts, teams, recordings, picks, context);
    }

    /** Plaud's APIs: timeouts, and no redirects, so a token is never sent to another host. */
    private static RestClient plaudHttp(PlaudProperties plaud) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(plaud.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        requests.setReadTimeout(plaud.readTimeout());
        return RestClient.builder().requestFactory(requests).build();
    }

    @Bean
    ConnectPlaudTeam connectPlaudTeam(SourceCatalog catalog, JdbcRecorderKeys keys, PlaudTeam plaud,
            PlaudTeamConnections connections, PlaudAccountConnections accounts, CaptureContext context) {
        return new ConnectPlaudTeam(catalog, keys, plaud, connections, accounts, context);
    }

    @Bean
    SyncRecorder syncRecorder(SourceCatalog catalog, PlaudTeam plaud, PlaudTeamConnections connections,
            PlaudAccounts plaudAccounts, PlaudAccountConnections accounts, RecordingPicks picks,
            CapturedDocumentRepository documents, CaptureContext context, CaptureProperties capture) {
        return new SyncRecorder(catalog, plaud, connections, plaudAccounts, accounts, picks, documents, context,
                capture.maxTextCharacters());
    }

    @Bean
    ListRecorderConnections listRecorderConnections(PlaudTeamConnections connections,
            PlaudAccountConnections accounts, CurrentWorkspace workspace) {
        return new ListRecorderConnections(connections, accounts, workspace);
    }

    @Bean
    ListDocuments listDocuments(CapturedDocumentRepository documents, CurrentWorkspace workspace) {
        return new ListDocuments(documents, workspace);
    }
}
