package com.blindtest.controller.admin;

import com.blindtest.model.dto.CoverSuggestionRequest;
import com.blindtest.model.dto.CoverSuggestionResponse;
import com.blindtest.service.CoverSuggestionService;
import com.blindtest.service.CoverSuggestionService.CoverThumbnail;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin REST controller for cover-art suggestions and uploads. */
@RestController
@RequestMapping("/api/admin/cover-suggestions")
@RequiredArgsConstructor
public class AdminCoverController {

    private final CoverSuggestionService coverSuggestionService;

    @PostMapping
    public List<CoverSuggestionResponse> suggest(@Valid @RequestBody CoverSuggestionRequest request) {
        return coverSuggestionService.suggest(request);
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CoverSuggestionResponse upload(@RequestPart("file") MultipartFile file) {
        return coverSuggestionService.upload(file);
    }

    /** Streams a candidate thumbnail through the server so the browser never contacts a provider. */
    @GetMapping("/{candidateId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable UUID candidateId) {
        CoverThumbnail thumbnail = coverSuggestionService.thumbnail(candidateId);
        return ResponseEntity.ok().
                contentType(MediaType.parseMediaType(thumbnail.contentType())).
                cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePrivate()).
                body(thumbnail.content());
    }
}
