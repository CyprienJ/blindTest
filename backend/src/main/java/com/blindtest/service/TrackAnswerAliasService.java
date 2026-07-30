package com.blindtest.service;

import com.blindtest.model.dto.TrackAnswerAliasRequest;
import com.blindtest.model.dto.TrackAnswerAliasResponse;
import com.blindtest.model.entity.TrackAnswerAliasEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.repository.TrackAnswerAliasRepository;
import com.blindtest.repository.TrackRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Administrator CRUD boundary for manually configured track answer aliases. */
@Service
@RequiredArgsConstructor
public class TrackAnswerAliasService {

    private final TrackRepository trackRepository;
    private final TrackAnswerAliasRepository aliasRepository;
    private final AnswerAliasNormalizer normalizer;

    /** Lists aliases for an existing track without exposing internal normalization. */
    @Transactional(readOnly = true)
    public List<TrackAnswerAliasResponse> list(UUID trackId) {
        requireTrack(trackId);
        return aliasRepository.findByTrackIdOrderByAnswerTypeAscValueAscIdAsc(trackId).
                stream().
                map(TrackAnswerAliasResponse::from).
                toList();
    }

    /** Creates a nonblank, nonduplicate alias. */
    @Transactional
    public TrackAnswerAliasResponse create(UUID trackId, TrackAnswerAliasRequest request) {
        TrackEntity track = requireTrack(trackId);
        String value = request.getValue().strip();
        String normalized = normalizer.normalize(value);
        if (aliasRepository.existsByTrackIdAndAnswerTypeAndNormalizedValue(
                trackId, request.getType(), normalized)) {
            throw duplicate();
        }
        TrackAnswerAliasEntity alias = new TrackAnswerAliasEntity(
                track, request.getType(), value, normalized);
        return TrackAnswerAliasResponse.from(aliasRepository.saveAndFlush(alias));
    }

    /** Replaces the type and display value of an alias belonging to the track. */
    @Transactional
    public TrackAnswerAliasResponse update(
            UUID trackId, UUID aliasId, TrackAnswerAliasRequest request) {
        requireTrack(trackId);
        TrackAnswerAliasEntity alias = findAlias(trackId, aliasId);
        String value = request.getValue().strip();
        String normalized = normalizer.normalize(value);
        if (aliasRepository.existsByTrackIdAndAnswerTypeAndNormalizedValueAndIdNot(
                trackId, request.getType(), normalized, aliasId)) {
            throw duplicate();
        }
        alias.setAnswerType(request.getType());
        alias.setValue(value);
        alias.setNormalizedValue(normalized);
        return TrackAnswerAliasResponse.from(aliasRepository.saveAndFlush(alias));
    }

    /** Deletes an alias belonging to the requested track. */
    @Transactional
    public void delete(UUID trackId, UUID aliasId) {
        requireTrack(trackId);
        aliasRepository.delete(findAlias(trackId, aliasId));
    }

    private TrackEntity requireTrack(UUID trackId) {
        return trackRepository.findById(trackId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private TrackAnswerAliasEntity findAlias(UUID trackId, UUID aliasId) {
        return aliasRepository.findByIdAndTrackId(aliasId, trackId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "ANSWER_ALIAS_DUPLICATE");
    }
}
