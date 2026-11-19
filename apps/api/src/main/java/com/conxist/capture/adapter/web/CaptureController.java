package com.conxist.capture.adapter.web;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.application.RecorderKeys;
import com.conxist.capture.application.CaptureUpload;
import com.conxist.capture.application.ConnectPlaudTeam;
import com.conxist.capture.application.ConnectPlaudTeam.TeamCheck;
import com.conxist.capture.application.ConnectionView;
import com.conxist.capture.application.DocumentSummary;
import com.conxist.capture.application.ListDocuments;
import com.conxist.capture.application.ListRecorderConnections;
import com.conxist.capture.application.ListSyncHistory;
import com.conxist.capture.application.ListSyncHistory.SyncRunView;
import com.conxist.capture.application.ReadDocument;
import com.conxist.capture.application.ReadDocument.DocumentView;
import com.conxist.capture.application.RecorderKeys.PublicKey;
import com.conxist.capture.application.SyncRecorder;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.capture.domain.StartFrom;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ConnectPlaudTeam connectPlaudTeam;
    private final ListRecorderConnections listConnections;
    private final ListDocuments listDocuments;
    private final ReadDocument readDocument;
    private final ListSyncHistory listSyncHistory;

    CaptureController(CaptureUpload captureUpload, SyncRecorder syncRecorder, ConnectPlaudTeam connectPlaudTeam,
            ListRecorderConnections listConnections, ListDocuments listDocuments, ReadDocument readDocument,
            ListSyncHistory listSyncHistory) {
        this.readDocument = readDocument;
        this.listSyncHistory = listSyncHistory;
        this.captureUpload = captureUpload;
        this.syncRecorder = syncRecorder;
        this.connectPlaudTeam = connectPlaudTeam;
        this.listConnections = listConnections;
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

    /** The document's text is customer data: never cached by the browser or a proxy. */
    @GetMapping("/sources/{sourceId}/documents/{documentId}")
    ResponseEntity<DocumentView> document(@PathVariable UUID sourceId, @PathVariable UUID documentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(readDocument.read(sourceId, documentId));
    }

    /** {@code all=false} (the default) leaves out checks that found nothing new. */
    @GetMapping("/sources/{sourceId}/syncs")
    List<SyncRunView> syncs(@PathVariable UUID sourceId, @RequestParam(defaultValue = "false") boolean all) {
        return listSyncHistory.list(sourceId, !all);
    }

    /** A new key pair; only the public half is returned, for the Plaud Team admin to register. */
    @PostMapping("/recorders/plaud/keys")
    @ResponseStatus(HttpStatus.CREATED)
    PublicKey createPlaudKey() {
        return connectPlaudTeam.createKey();
    }

    @PostMapping("/recorders/plaud/test")
    TeamCheck testPlaudTeam(@Valid @RequestBody TeamCredentialRequest request) {
        return connectPlaudTeam.test(request.clientId(), request.keyId());
    }

    @GetMapping("/recorders/plaud/connections")
    List<ConnectionView> plaudConnections() {
        return listConnections.list();
    }

    @PostMapping("/sources/{sourceId}/plaud-team")
    @ResponseStatus(HttpStatus.CREATED)
    ConnectionView connectPlaudTeam(@PathVariable UUID sourceId, @Valid @RequestBody ConnectRequest request) {
        return connectPlaudTeam.connect(sourceId, request.clientId(), request.keyId(), request.startFrom(),
                request.includeSummaries());
    }

    @PostMapping("/sources/{sourceId}/sync")
    SyncResult sync(@PathVariable UUID sourceId) {
        return syncRecorder.sync(sourceId);
    }

    record TeamCredentialRequest(
            @NotBlank @Size(max = PlaudTeamConnection.MAX_CLIENT_ID_LENGTH) String clientId,
            @NotBlank @Pattern(regexp = RecorderKeys.KEY_ID_PATTERN) String keyId) {
    }

    record ConnectRequest(
            @NotBlank @Size(max = PlaudTeamConnection.MAX_CLIENT_ID_LENGTH) String clientId,
            @NotBlank @Pattern(regexp = RecorderKeys.KEY_ID_PATTERN) String keyId,
            @NotNull StartFrom startFrom,
            boolean includeSummaries) {
    }
}
