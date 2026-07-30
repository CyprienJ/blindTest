package com.blindtest.service;

import com.blindtest.model.dto.ImportJobResponse;
import com.blindtest.model.dto.ImportStartResponse;
import com.blindtest.model.dto.ImportTrackRequest;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.port.ImportJobScheduler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Service managing track import job lifecycle. */
@Service
@RequiredArgsConstructor
public class ImportJobService {

    private final TrackImportJobRepository jobRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final ImportJobScheduler importJobScheduler;

    /** Creates a PENDING track and a QUEUED import job, then triggers async download. */
    @Transactional
    public ImportStartResponse startImport(ImportTrackRequest request, UUID adminId) {
        UserEntity admin = userRepository.findById(adminId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        TrackEntity track = buildTrack(request);
        track = trackRepository.save(track);

        TrackImportJobEntity job = new TrackImportJobEntity();
        job.setYoutubeUrl(request.getYoutubeUrl());
        job.setStatus(ImportJobStatus.QUEUED);
        job.setAdmin(admin);
        job.setTrack(track);
        job = jobRepository.save(job);

        importJobScheduler.schedule(job.getId());
        return new ImportStartResponse(job.getId());
    }

    /** Returns the current status of an import job. Returns 404 if not found. */
    @Transactional(readOnly = true)
    public ImportJobResponse getJobStatus(UUID jobId) {
        TrackImportJobEntity job = jobRepository.findByIdWithTrack(jobId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ImportJobResponse.from(job);
    }

    private TrackEntity buildTrack(ImportTrackRequest request) {
        TrackEntity track = new TrackEntity();
        track.setTitle(request.getTitle());
        track.setArtist(request.getArtist());
        track.setAlbum(request.getAlbum());
        track.setComposer(request.getComposer());
        track.setWork(request.getWork());
        track.setCategory(request.getCategory());
        track.setDecade(request.getDecade());
        track.setDurationSeconds(request.getDurationSeconds());
        track.setImageUrl(request.getImageUrl());
        track.setYoutubeUrl(request.getYoutubeUrl());
        track.setAudioUrl(null);
        track.setStatus(TrackStatus.PENDING);
        return track;
    }
}
