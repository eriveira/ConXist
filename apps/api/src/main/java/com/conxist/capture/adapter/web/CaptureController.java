package com.conxist.capture.adapter.web;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.conxist.capture.application.RecorderKeys;
import com.conxist.capture.application.CaptureUpload;
import com.conxist.capture.application.ConnectPlaudTeam;
import com.conxist.capture.application.ConnectPlaudTeam.TeamCheck;
import com.conxist.capture.application.ConnectionView;
import com.conxist.capture.application.DocumentSummary;
import com.conxist.capture.application.DeleteDocument;
import com.conxist.capture.application.EditMeeting;
import com.conxist.capture.application.ListDocuments;
import com.conxist.capture.application.ListRecorderConnections;
import com.conxist.capture.application.ListSyncHistory;
import com.conxist.capture.application.ListSyncHistory.SyncRunView;
import com.conxist.capture.application.ReadDocument;
import com.conxist.capture.application.ReadDocument.DocumentView;
import com.conxist.capture.application.RecorderKeys.PublicKey;
import com.conxist.capture.application.SyncRecorder;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.capture.domain.MeetingText;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
class CaptureController {

    /** A person edits a few lines at a time (only changed lines are sent), so this bounds the request, not the meeting. */
    private static final int MAX_SPEAKERS = 100;
    private static final int MAX_LINES = 500;
    private static final int MAX_LINE_LENGTH = 5_000;

    private final CaptureUpload captureUpload;
    private final SyncRecorder syncRecorder;
    private final ConnectPlaudTeam connectPlaudTeam;
    private final ListRecorderConnections listConnections;
    private final ListDocuments listDocuments;
    private final ReadDocument readDocument;
    private final DeleteDocument deleteDocument;
    private final ListSyncHistory listSyncHistory;
    private final EditMeeting editMeeting;

    CaptureController(CaptureUpload captureUpload, SyncRecorder syncRecorder, ConnectPlaudTeam connectPlaudTeam,
            ListRecorderConnections listConnections, ListDocuments listDocuments, ReadDocument readDocument,
            ListSyncHistory listSyncHistory, DeleteDocument deleteDocument, EditMeeting editMeeting) {
        this.editMeeting = editMeeting;
        this.readDocument = readDocument;
        this.deleteDocument = deleteDocument;
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

    /** Removes the document from ConXist only; the source keeps its own copy. */
    @DeleteMapping("/sources/{sourceId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDocument(@PathVariable UUID sourceId, @PathVariable UUID documentId) {
        deleteDocument.delete(sourceId, documentId);
    }

    /**
     * A person's corrections to a recorded meeting in ConXist. {@code editedAt} is the edit the person started from
     * (null for a meeting as imported), so an older tab cannot overwrite a newer edit. The answer is never cached.
     */
    @PutMapping("/sources/{sourceId}/documents/{documentId}/meeting")
    ResponseEntity<DocumentView> editMeeting(@PathVariable UUID sourceId, @PathVariable UUID documentId,
            @Valid @RequestBody MeetingEdit edit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(editMeeting.edit(sourceId, documentId,
                edit.editedAt(), edit.title(), edit.speakers() == null ? Map.of() : edit.speakers(),
                edit.lines() == null ? Map.of() : edit.lines()));
    }

    /** Puts back the meeting as it was imported; {@code editedAt} is the edit the person saw. */
    @PostMapping("/sources/{sourceId}/documents/{documentId}/restore")
    ResponseEntity<DocumentView> restoreMeeting(@PathVariable UUID sourceId, @PathVariable UUID documentId,
            @Valid @RequestBody RestoreRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(editMeeting.restore(sourceId, documentId, request.editedAt()));
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

    record MeetingEdit(
            Instant editedAt,
            @NotBlank @Size(max = DocumentTitle.MAX_LENGTH) String title,
            @Size(max = MAX_SPEAKERS) Map<@NotBlank @Size(max = MeetingText.MAX_SPEAKER_LENGTH) String,
                    @NotNull @Size(max = MeetingText.MAX_SPEAKER_LENGTH) String> speakers,
            @Size(max = MAX_LINES) Map<@NotNull Integer, @NotNull @Size(max = MAX_LINE_LENGTH) String> lines) {
    }

    record RestoreRequest(@NotNull Instant editedAt) {
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
