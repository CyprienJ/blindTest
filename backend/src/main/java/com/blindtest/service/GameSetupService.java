package com.blindtest.service;

import com.blindtest.error.ApiErrorCode;
import com.blindtest.model.dto.CreateSoloSessionRequest;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackAnswerAliasEntity;
import com.blindtest.model.entity.TrackAnswerType;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.game.GameAnswerSnapshot;
import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.game.GameConfiguration;
import com.blindtest.model.game.GameConfigurationError;
import com.blindtest.model.game.GameConfigurationValidationException;
import com.blindtest.model.game.GameConfigurationValidator;
import com.blindtest.model.game.GameMediaSnapshot;
import com.blindtest.model.game.GamePlaybackSnapshot;
import com.blindtest.model.game.GameRandom;
import com.blindtest.model.game.GameStreamingLinks;
import com.blindtest.model.game.GameTrackCandidate;
import com.blindtest.model.game.GameTrackSelection;
import com.blindtest.model.game.GameTrackSelector;
import com.blindtest.model.game.PlaylistEligibilitySnapshot;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackAnswerAliasRepository;
import com.blindtest.repository.UserRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates a requested configuration against the published catalogue and snapshots a
 * randomized track selection; shared by solo sessions and multiplayer lobbies.
 */
@Service
public class GameSetupService {

    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final TrackAnswerAliasRepository aliasRepository;
    private final GameTrackSelector trackSelector;
    private final GameConfigurationValidator configurationValidator =
            new GameConfigurationValidator();

    /** Wires catalogue repositories and the random track selector. */
    public GameSetupService(
            UserRepository userRepository,
            PlaylistRepository playlistRepository,
            PlaylistTrackRepository playlistTrackRepository,
            TrackAnswerAliasRepository aliasRepository,
            GameRandom gameRandom) {
        this.userRepository = userRepository;
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.aliasRepository = aliasRepository;
        this.trackSelector = new GameTrackSelector(gameRandom);
    }

    /** Validated configuration and the immutable tracks selected for it. */
    public record GameSetup(GameConfiguration configuration, GameTrackSelection selection) {
    }

    /** Validates the request against current eligibility and selects its tracks. */
    @Transactional(readOnly = true)
    public GameSetup prepare(RequestedGameConfiguration requested) {
        if (requested == null) {
            throw new GameConfigurationValidationException(
                    GameConfigurationError.CONFIGURATION_REQUIRED);
        }
        UUID playlistId = requested.playlistId();
        List<GameTrackCandidate> candidates = loadCandidates(playlistId);
        PlaylistEligibilitySnapshot eligibility = playlistId != null
                && playlistRepository.existsById(playlistId)
                ? new PlaylistEligibilitySnapshot(
                        playlistId, candidates.size(), countByDifficulty(candidates))
                : null;
        GameConfiguration configuration =
                configurationValidator.validate(requested, eligibility);
        return new GameSetup(configuration, trackSelector.select(configuration, candidates));
    }

    /** Returns the authenticated account when it may host or join a game. */
    public UserEntity requireVerifiedPlayer(UUID accountId) {
        UserEntity owner = userRepository.findById(accountId).orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_ACCESS_TOKEN.name()));
        if (!owner.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, ApiErrorCode.EMAIL_NOT_VERIFIED.name());
        }
        return owner;
    }

    /** Maps a REST creation request to the domain request, or {@code null} when absent. */
    public RequestedGameConfiguration toRequestedConfiguration(CreateSoloSessionRequest request) {
        if (request == null) {
            return null;
        }
        return new RequestedGameConfiguration(
                request.playlistId(),
                request.trackCount(),
                request.maxRoundSeconds(),
                request.extensions(),
                request.difficultyLevels(),
                !Boolean.FALSE.equals(request.includeUnrated()));
    }

    private Map<Integer, Integer> countByDifficulty(List<GameTrackCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(
                candidate -> candidate.difficulty() == null
                        ? PlaylistEligibilitySnapshot.UNRATED : candidate.difficulty(),
                candidate -> 1,
                Integer::sum));
    }

    private List<GameTrackCandidate> loadCandidates(UUID playlistId) {
        if (playlistId == null) {
            return List.of();
        }
        List<PlaylistTrackEntity> memberships =
                playlistTrackRepository.findPublishedByPlaylistIdOrdered(
                        playlistId, TrackStatus.PUBLISHED);
        List<TrackEntity> playableTracks = memberships.stream().
                map(PlaylistTrackEntity::getTrack).
                filter(this::hasSnapshotMedia).
                toList();
        if (playableTracks.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<TrackAnswerAliasEntity>> aliases = aliasRepository.
                findAllByTrackIdIn(playableTracks.stream().map(TrackEntity::getId).toList()).
                stream().collect(Collectors.groupingBy(alias -> alias.getTrack().getId()));
        return playableTracks.stream().
                map(track -> toCandidate(track, aliases.getOrDefault(track.getId(), List.of()))).
                toList();
    }

    private boolean hasSnapshotMedia(TrackEntity track) {
        return hasText(track.getAudioObjectKey()) && hasText(track.getAudioChecksum());
    }

    private GameTrackCandidate toCandidate(
            TrackEntity track,
            List<TrackAnswerAliasEntity> aliases) {
        Map<GameAnswerType, List<String>> groupedAliases = new EnumMap<>(GameAnswerType.class);
        for (TrackAnswerAliasEntity alias : aliases) {
            groupedAliases.computeIfAbsent(toGameAnswerType(alias.getAnswerType()), ignored ->
                    new ArrayList<>()).add(alias.getValue());
        }
        return new GameTrackCandidate(
                track.getId(),
                true,
                new GameAnswerSnapshot(
                        track.getTitle(),
                        track.getArtist(),
                        track.getComposer(),
                        track.getAlbum(),
                        groupedAliases),
                new GamePlaybackSnapshot(
                        track.getExcerptStartSeconds(), track.getDurationSeconds(),
                        track.getExcerptDurationSeconds()),
                new GameMediaSnapshot(
                        track.getAudioObjectKey(), track.getAudioChecksum()),
                track.getDifficulty() == null ? null : track.getDifficulty().intValue(),
                GameStreamingLinks.of(
                        track.getDeezerUrl(), track.getStreamingYoutubeUrl(), track.getSpotifyUrl(),
                        track.getQobuzUrl(), track.getTidalUrl(), track.getSoundcloudUrl()),
                track.getCoverId());
    }

    private GameAnswerType toGameAnswerType(TrackAnswerType answerType) {
        return GameAnswerType.valueOf(answerType.name());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
