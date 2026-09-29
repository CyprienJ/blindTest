package com.blindtest.controller.admin;

import com.blindtest.model.dto.ImportJobResponse;
import com.blindtest.model.dto.ImportStartResponse;
import com.blindtest.model.dto.ImportTrackRequest;
import com.blindtest.model.dto.UploadTrackRequest;
import com.blindtest.service.ImportJobService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin REST controller for the YouTube and uploaded-file audio import pipeline. */
@RestController
@RequestMapping("/api/admin/tracks/import")
@RequiredArgsConstructor
public class AdminImportController {

    private final ImportJobService importJobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportStartResponse startImport(
            @Valid @RequestBody ImportTrackRequest request,
            Authentication authentication) {
        UUID adminId = UUID.fromString(authentication.getName());
        return importJobService.startImport(request, adminId);
    }

    /** Starts an import from an uploaded audio file; metadata falls back to embedded tags. */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportStartResponse startUpload(
            @RequestPart("file") MultipartFile file,
            @Valid @ModelAttribute UploadTrackRequest request,
            Authentication authentication) {
        UUID adminId = UUID.fromString(authentication.getName());
        return importJobService.startUpload(file, request, adminId);
    }

    @GetMapping("/{jobId}")
    public ImportJobResponse getJobStatus(@PathVariable UUID jobId) {
        return importJobService.getJobStatus(jobId);
    }
}
