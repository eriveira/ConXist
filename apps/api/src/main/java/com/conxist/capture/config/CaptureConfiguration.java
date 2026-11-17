package com.conxist.capture.config;

import java.time.Clock;
import com.conxist.capture.application.CaptureContext;
import com.conxist.capture.application.CaptureUpload;
import com.conxist.capture.application.CapturedDocumentRepository;
import com.conxist.capture.application.FileStore;
import com.conxist.capture.application.ListDocuments;
import com.conxist.capture.application.RecorderConnector;
import com.conxist.capture.application.SignInToRecorder;
import com.conxist.capture.application.SyncRecorder;
import com.conxist.capture.application.TextExtractor;
import com.conxist.capture.domain.UploadPolicy;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.EventPublisher;
import com.conxist.platform.UnitOfWork;
import com.conxist.sources.SourceCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the capture use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
class CaptureConfiguration {

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
    SyncRecorder syncRecorder(SourceCatalog catalog, RecorderConnector recorder, CapturedDocumentRepository documents,
            CaptureContext context) {
        return new SyncRecorder(catalog, recorder, documents, context);
    }

    @Bean
    SignInToRecorder signInToRecorder(RecorderConnector recorder) {
        return new SignInToRecorder(recorder);
    }

    @Bean
    ListDocuments listDocuments(CapturedDocumentRepository documents, CurrentWorkspace workspace) {
        return new ListDocuments(documents, workspace);
    }
}
