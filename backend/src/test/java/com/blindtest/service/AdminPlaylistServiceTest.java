package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.dto.AddTrackToPlaylistRequest;
import com.blindtest.model.dto.CreatePlaylistRequest;
import com.blindtest.model.dto.ReorderPlaylistTracksRequest;
import com.blindtest.model.dto.UpdatePlaylistRequest;
import com.blindtest.model.entity.CoverSource;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.TrackCoverService.StoredCover;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminPlaylistServiceTest {

    private static final UUID PLAYLIST_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111");
    private static final UUID FIRST_TRACK_ID = UUID.fromString(
            "22222222-2222-4222-8222-222222222222");
    private static final UUID SECOND_TRACK_ID = UUID.fromString(
            "33333333-3333-4333-8333-333333333333");

    private PlaylistRepository playlistRepository;
    private PlaylistTrackRepository playlistTrackRepository;
    private TrackRepository trackRepository;
    private UserRepository userRepository;
    private TrackCoverService coverService;
    private AdminPlaylistService service;
    private PlaylistEntity playlist;
    private TrackEntity firstTrack;
    private TrackEntity secondTrack;

    @BeforeEach
    void setUp() {
        playlistRepository = mock(PlaylistRepository.class);
        playlistTrackRepository = mock(PlaylistTrackRepository.class);
        trackRepository = mock(TrackRepository.class);
        userRepository = mock(UserRepository.class);
        coverService = mock(TrackCoverService.class);
        service = new AdminPlaylistService(playlistRepository, playlistTrackRepository,
                trackRepository, userRepository, coverService);

        playlist = mock(PlaylistEntity.class);
        when(playlist.getId()).thenReturn(PLAYLIST_ID);
        when(playlist.getName()).thenReturn("Coverage playlist");
        when(playlistRepository.findByIdForUpdate(PLAYLIST_ID)).thenReturn(Optional.of(playlist));

        firstTrack = track(FIRST_TRACK_ID, "First track", TrackStatus.PUBLISHED);
        secondTrack = track(SECOND_TRACK_ID, "Second track", TrackStatus.PUBLISHED);
    }

    @Test
    void createPlaylistRejectsAnUnknownAdministratorBeforePersisting() {
        UUID unknownAdminId = UUID.randomUUID();
        CreatePlaylistRequest request = new CreatePlaylistRequest();
        ReflectionTestUtils.setField(request, "name", "New playlist");
        when(userRepository.findById(unknownAdminId)).thenReturn(Optional.empty());

        assertStatus(401, () -> service.createPlaylist(request, unknownAdminId));

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void createPlaylistStoresTheUploadedCover() {
        UUID adminId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID coverId = UUID.randomUUID();
        CreatePlaylistRequest request = new CreatePlaylistRequest();
        ReflectionTestUtils.setField(request, "name", "New playlist");
        ReflectionTestUtils.setField(request, "coverCandidateId", candidateId);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(new UserEntity()));
        when(coverService.storeCandidate(candidateId)).
                thenReturn(new StoredCover(coverId, CoverSource.UPLOAD));
        when(playlistRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createPlaylist(request, adminId);

        assertThat(response.getImageUrl()).isEqualTo("/api/covers/" + coverId);
    }

    @Test
    void updatePlaylistReplacesTheStoredCoverAndClearsTheLegacyUrl() {
        UUID previousCover = UUID.randomUUID();
        UUID newCover = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        PlaylistEntity stored = storedPlaylist(previousCover, "https://example.test/old.jpg");
        UpdatePlaylistRequest request = new UpdatePlaylistRequest();
        ReflectionTestUtils.setField(request, "coverCandidateId", candidateId);
        when(coverService.storeCandidate(candidateId)).
                thenReturn(new StoredCover(newCover, CoverSource.UPLOAD));

        var response = service.updatePlaylist(PLAYLIST_ID, request);

        assertThat(stored.getCoverId()).isEqualTo(newCover);
        assertThat(stored.getImageUrl()).isNull();
        assertThat(response.getImageUrl()).isEqualTo("/api/covers/" + newCover);
        verify(coverService).discard(previousCover);
    }

    @Test
    void updatePlaylistRemovesTheStoredCoverAndLegacyUrl() {
        UUID previousCover = UUID.randomUUID();
        PlaylistEntity stored = storedPlaylist(previousCover, "https://example.test/old.jpg");
        UpdatePlaylistRequest request = new UpdatePlaylistRequest();
        ReflectionTestUtils.setField(request, "removeCover", true);
        ReflectionTestUtils.setField(request, "coverCandidateId", UUID.randomUUID());

        var response = service.updatePlaylist(PLAYLIST_ID, request);

        assertThat(stored.getCoverId()).isNull();
        assertThat(response.getImageUrl()).isNull();
        verify(coverService).discard(previousCover);
        verify(coverService, never()).storeCandidate(any());
    }

    @Test
    void deletePlaylistDiscardsItsStoredCover() {
        UUID cover = UUID.randomUUID();
        when(playlist.getCoverId()).thenReturn(cover);

        service.deletePlaylist(PLAYLIST_ID);

        verify(coverService).discard(cover);
        verify(playlistRepository).deleteById(PLAYLIST_ID);
    }

    @Test
    void addTrackRejectsUnavailableTracksWithoutChangingTheOrder() {
        AddTrackToPlaylistRequest request = addRequest(FIRST_TRACK_ID, 0);
        when(playlistTrackRepository.findAllByPlaylistIdForUpdate(PLAYLIST_ID)).
                thenReturn(new ArrayList<>());
        when(trackRepository.findById(FIRST_TRACK_ID)).thenReturn(Optional.of(firstTrack));
        when(firstTrack.getStatus()).thenReturn(TrackStatus.PENDING);

        assertStatus(409, () -> service.addTrack(PLAYLIST_ID, request));

        verify(playlistTrackRepository, never()).save(any());
        verify(playlistTrackRepository, never()).saveAll(any());
    }

    @Test
    void reorderRejectsPartialSnapshotsAtomically() {
        List<PlaylistTrackEntity> entries = entries();
        when(playlistTrackRepository.findAllByPlaylistIdForUpdate(PLAYLIST_ID)).
                thenReturn(entries);
        ReorderPlaylistTracksRequest request = reorderRequest(List.of(FIRST_TRACK_ID));

        assertStatus(400, () -> service.reorderTracks(PLAYLIST_ID, request));

        assertThat(entries).extracting(PlaylistTrackEntity::getTrackOrder).containsExactly(0, 1);
        verify(playlistTrackRepository, never()).saveAll(any());
    }

    @Test
    void reorderAppliesACompleteSnapshotAndReturnsTheNewOrder() {
        List<PlaylistTrackEntity> entries = entries();
        when(playlistTrackRepository.findAllByPlaylistIdForUpdate(PLAYLIST_ID)).
                thenReturn(entries);
        ReorderPlaylistTracksRequest request = reorderRequest(
                List.of(SECOND_TRACK_ID, FIRST_TRACK_ID));

        var response = service.reorderTracks(PLAYLIST_ID, request);

        assertThat(entries).extracting(PlaylistTrackEntity::getTrackOrder).containsExactly(0, 1);
        assertThat(response.getTracks()).extracting(track -> track.id()).
                containsExactly(SECOND_TRACK_ID, FIRST_TRACK_ID);
        verify(playlistTrackRepository).saveAll(entries);
    }

    @Test
    void removeTrackCompactsEveryFollowingPosition() {
        List<PlaylistTrackEntity> entries = entries();
        when(playlistTrackRepository.findAllByPlaylistIdForUpdate(PLAYLIST_ID)).
                thenReturn(entries);

        service.removeTrack(PLAYLIST_ID, FIRST_TRACK_ID);

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getTrack()).isEqualTo(secondTrack);
        assertThat(entries.getFirst().getTrackOrder()).isZero();
        verify(playlistTrackRepository).delete(any(PlaylistTrackEntity.class));
        verify(playlistTrackRepository).saveAll(entries);
    }

    private PlaylistEntity storedPlaylist(UUID coverId, String imageUrl) {
        PlaylistEntity stored = new PlaylistEntity();
        stored.setName("Stored playlist");
        stored.setCoverId(coverId);
        stored.setImageUrl(imageUrl);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(stored));
        when(playlistRepository.save(stored)).thenReturn(stored);
        return stored;
    }

    private List<PlaylistTrackEntity> entries() {
        return new ArrayList<>(List.of(
                new PlaylistTrackEntity(playlist, firstTrack, 0),
                new PlaylistTrackEntity(playlist, secondTrack, 1)));
    }

    private TrackEntity track(UUID id, String title, TrackStatus status) {
        TrackEntity track = mock(TrackEntity.class);
        when(track.getId()).thenReturn(id);
        when(track.getTitle()).thenReturn(title);
        when(track.getArtist()).thenReturn("Artist");
        when(track.getStatus()).thenReturn(status);
        return track;
    }

    private AddTrackToPlaylistRequest addRequest(UUID trackId, int order) {
        AddTrackToPlaylistRequest request = new AddTrackToPlaylistRequest();
        ReflectionTestUtils.setField(request, "trackId", trackId);
        ReflectionTestUtils.setField(request, "order", order);
        return request;
    }

    private ReorderPlaylistTracksRequest reorderRequest(List<UUID> trackIds) {
        ReorderPlaylistTracksRequest request = new ReorderPlaylistTracksRequest();
        ReflectionTestUtils.setField(request, "trackIds", trackIds);
        return request;
    }

    private void assertStatus(int expectedStatus, Runnable operation) {
        assertThatThrownBy(operation::run).
                isInstanceOf(ResponseStatusException.class).
                satisfies(error -> assertThat(((ResponseStatusException) error).
                        getStatusCode().value()).isEqualTo(expectedStatus));
    }
}
