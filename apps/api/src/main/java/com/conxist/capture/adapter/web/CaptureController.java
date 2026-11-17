package com.conxist.capture.adapter.web;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.application.CaptureUpload;
import com.conxist.capture.application.DocumentSummary;
import com.conxist.capture.application.ListDocuments;
import com.conxist.capture.application.RecorderConnector.RecorderAccount;
import com.conxist.capture.application.SignInToRecorder;
import com.conxist.capture.application.SyncRecorder;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
class CaptureController {

    private final CaptureUpload captureUpload;
    private final SyncRecorder syncRecorder;
    private final SignInToRecorder signInToRecorder;
    private final ListDocuments listDocuments;

    CaptureController(CaptureUpload captureUpload, SyncRecorder syncRecorder, SignInToRecorder signInToRecorder,
            ListDocuments listDocuments) {
        this.captureUpload = captureUpload;
        this.syncRecorder = syncRecorder;
        this.signInToRecorder = signInToRecorder;
        this.listDocuments = listDocuments;
    }

    @PostMapping("/sources/{sourceId}/files")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentSummary upload(@PathVariable UUID sourceId, @RequestParam("file") MultipartFile file) throws IOException {
        return captureUpload.capture(sourceId, file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/sources/{sourceId}/documents")
    List<DocumentSummary> documents(@PathVariable UUID sourceId) {
        return listDocuments.list(sourceId);
    }

    @PostMapping("/recorders/plaud/sign-in")
    RecorderAccount signInToPlaud() {
        return signInToRecorder.signIn();
    }

    @PostMapping("/sources/{sourceId}/sync")
    SyncResult sync(@PathVariable UUID sourceId) {
        return syncRecorder.sync(sourceId);
    }
}
