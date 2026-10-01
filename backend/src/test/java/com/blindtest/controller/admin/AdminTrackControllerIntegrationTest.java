package com.blindtest.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.TrackAnswerAliasRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.repository.MediaDeletionTaskRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.TurnstileVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:admintest;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "app.media-maintenance.deletion-poll-delay-ms=3600000"
})
class AdminTrackControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private TrackImportJobRepository importJobRepository;

    @Autowired
    private TrackAnswerAliasRepository aliasRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @Autowired
    private MediaDeletionTaskRepository deletionTaskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    @MockBean
    private MediaStorage mediaStorage;

    private String adminToken;
    private TrackEntity pendingTrack;
    private TrackEntity publishedTrack;

    @BeforeEach
    void setUp() {
        deletionTaskRepository.deleteAll();
        importJobRepository.deleteAll();
        aliasRepository.deleteAll();
        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity admin = new UserEntity();
        admin.setUsername("track-admin");
        admin.setEmail("track-admin@example.com");
        admin.setPasswordHash("hash");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);

        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin.getId(), UserRole.ADMIN);

        TrackEntity pending = buildTrack("Pending Track", TrackStatus.PENDING);
        pending.setAudioObjectKey("tracks/pending/audio.mp3");
        pending.setAudioChecksum("a".repeat(64));
        pending.setAudioSizeBytes(1234L);
        pending.setWaveformPeaks("[0,25,100]");
        pendingTrack = trackRepository.save(pending);

        TrackImportJobEntity completedJob = new TrackImportJobEntity();
        completedJob.setYoutubeUrl("https://youtube.example.test/watch?v=preview");
        completedJob.setStatus(ImportJobStatus.DONE);
        completedJob.setProgress((short) 100);
        completedJob.setAdmin(admin);
        completedJob.setTrack(pendingTrack);
        importJobRepository.save(completedJob);

        when(mediaStorage.exists("tracks/pending/audio.mp3")).thenReturn(true);
        when(mediaStorage.presignedGetUrl("tracks/pending/audio.mp3")).thenReturn(
                URI.create("https://media.example.test/tracks/pending/audio.mp3?signature=test"));
        when(mediaStorage.listObjectKeys()).thenReturn(Set.of(
                "tracks/pending/audio.mp3", "tracks/orphan/audio.mp3"));

        TrackEntity published = buildTrack("Published Track", TrackStatus.PUBLISHED);
        publishedTrack = trackRepository.save(published);
    }

    @Test
    void getAllTracksRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/tracks")).
                andExpect(status().isUnauthorized());

        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);
        mockMvc.perform(get("/api/admin/tracks").
                        header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden());
    }

    @Test
    void getAllTracksReturnsAllStatuses() throws Exception {
        mockMvc.perform(get("/api/admin/tracks").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalElements").value(2)).
                andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getAllTracksFiltersByStatusAndSearchesMetadata() throws Exception {
        pendingTrack.setAlbum("Hidden Gem");
        pendingTrack.setCategory("Cinema");
        trackRepository.saveAndFlush(pendingTrack);

        mockMvc.perform(get("/api/admin/tracks").
                        param("status", "PENDING").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalElements").value(1)).
                andExpect(jsonPath("$.content[0].id").value(pendingTrack.getId().toString()));

        mockMvc.perform(get("/api/admin/tracks").
                        param("search", "hidden").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalElements").value(1)).
                andExpect(jsonPath("$.content[0].title").value("Pending Track"));

        mockMvc.perform(get("/api/admin/tracks").
                        param("status", "PUBLISHED").
                        param("search", "pending").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getAllTracksIncludesMediaMetadataButNotObjectKey() throws Exception {
        pendingTrack.setAudioChecksum("a".repeat(64));
        pendingTrack.setAudioSizeBytes(1234L);
        publishedTrack.setAudioChecksum("a".repeat(64));
        publishedTrack.setAudioSizeBytes(1234L);
        trackRepository.saveAllAndFlush(List.of(pendingTrack, publishedTrack));

        mockMvc.perform(get("/api/admin/tracks").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.content[0].audioUrl").isNotEmpty()).
                andExpect(jsonPath("$.content[0].audioChecksum").value("a".repeat(64))).
                andExpect(jsonPath("$.content[0].audioSizeBytes").value(1234)).
                andExpect(jsonPath("$.content[0].audioObjectKey").doesNotExist()).
                andExpect(jsonPath("$.content[0].status").isNotEmpty());
    }

    @Test
    void adminCanRequestPreviewForCompletedUnpublishedImport() throws Exception {
        mockMvc.perform(get("/api/admin/tracks/" + pendingTrack.getId() + "/preview-url").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.url").value(
                        "https://media.example.test/tracks/pending/audio.mp3?signature=test")).
                andExpect(jsonPath("$.expiresAt").isNotEmpty()).
                andExpect(jsonPath("$.audioObjectKey").doesNotExist());
    }

    @Test
    void adminCanRequestBoundedWaveformData() throws Exception {
        pendingTrack.setDurationSeconds(180);
        trackRepository.saveAndFlush(pendingTrack);
        mockMvc.perform(get("/api/admin/tracks/" + pendingTrack.getId() + "/waveform").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.durationSeconds").value(180)).
                andExpect(jsonPath("$.peaks.length()").value(3)).
                andExpect(jsonPath("$.peaks[2]").value(100)).
                andExpect(jsonPath("$.waveformPeaks").doesNotExist());
    }

    @Test
    void normalUserCannotRequestUnpublishedPreview() throws Exception {
        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(), UserRole.USER);

        mockMvc.perform(get("/api/admin/tracks/" + pendingTrack.getId() + "/preview-url").
                        header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden()).
                andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanReconcileMediaWithoutReceivingInternalObjectKeys() throws Exception {
        mockMvc.perform(get("/api/admin/media/reconciliation").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.missingCount").value(0)).
                andExpect(jsonPath("$.orphanedCount").value(1)).
                andExpect(jsonPath("$.orphanedObjectFingerprints[0]").isString()).
                andExpect(jsonPath("$.orphanedObjectKeys").doesNotExist()).
                andExpect(jsonPath("$.missingObjects").isEmpty());
    }

    @Test
    void normalUserCannotRequestMediaReconciliation() throws Exception {
        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(), UserRole.USER);

        mockMvc.perform(get("/api/admin/media/reconciliation").
                        header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden()).
                andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void previewRejectsPublishedTrack() throws Exception {
        mockMvc.perform(get("/api/admin/tracks/" + publishedTrack.getId() + "/preview-url").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void updateTrackMetadataReturns200WithUpdatedFields() throws Exception {
        String id = pendingTrack.getId().toString();
        mockMvc.perform(put("/api/admin/tracks/" + id).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {
                                  "title": "Updated Title",
                                  "artist": "Updated Artist",
                                  "album": "Updated Album",
                                  "durationSeconds": 240,
                                  "excerptStartSeconds": 30
                                }
                                """)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.title").value("Updated Title")).
                andExpect(jsonPath("$.artist").value("Updated Artist")).
                andExpect(jsonPath("$.album").value("Updated Album")).
                andExpect(jsonPath("$.durationSeconds").value(240)).
                andExpect(jsonPath("$.excerptStartSeconds").value(30));
    }

    @Test
    void updateTrackStoresDifficultyAndStreamingLinks() throws Exception {
        String url = "/api/admin/tracks/" + pendingTrack.getId();
        mockMvc.perform(put(url).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {
                                  "difficulty": 4,
                                  "streamingLinks": {
                                    "deezer": "https://www.deezer.com/track/3135556",
                                    "youtube": "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
                                    "spotify": "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC",
                                    "qobuz": "https://open.qobuz.com/track/123",
                                    "tidal": "https://tidal.com/browse/track/123",
                                    "soundcloud": ""
                                  }
                                }
                                """)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.difficulty").value(4)).
                andExpect(jsonPath("$.streamingLinks.deezer").value("https://www.deezer.com/track/3135556")).
                andExpect(jsonPath("$.streamingLinks.spotify").
                        value("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")).
                andExpect(jsonPath("$.streamingLinks.soundcloud").doesNotExist());

        mockMvc.perform(put(url).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"title": "Renamed"}
                                """)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.difficulty").value(4)).
                andExpect(jsonPath("$.streamingLinks.tidal").value("https://tidal.com/browse/track/123"));

        mockMvc.perform(put(url).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"streamingLinks": {"deezer": "https://www.deezer.com/track/1"}}
                                """)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.streamingLinks.deezer").value("https://www.deezer.com/track/1")).
                andExpect(jsonPath("$.streamingLinks.tidal").doesNotExist());
    }

    @Test
    void updateTrackRejectsOutOfRangeDifficultyAndForeignStreamingLink() throws Exception {
        String url = "/api/admin/tracks/" + pendingTrack.getId();
        mockMvc.perform(put(url).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"difficulty": 6}
                                """)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put(url).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"streamingLinks": {"spotify": "https://evil.example/open.spotify.com"}}
                                """)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void administratorCanCreateListUpdateAndDeleteAliasesWithoutNormalizationLeak()
            throws Exception {
        String aliasesUrl = "/api/admin/tracks/" + pendingTrack.getId() + "/aliases";
        String response = mockMvc.perform(post(aliasesUrl).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"type":"TITLE","value":"  Le Fabuleux Destin  "}
                                """)).
                andExpect(status().isCreated()).
                andExpect(jsonPath("$.type").value("TITLE")).
                andExpect(jsonPath("$.value").value("Le Fabuleux Destin")).
                andExpect(jsonPath("$.normalizedValue").doesNotExist()).
                andReturn().getResponse().getContentAsString();
        String aliasId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get(aliasesUrl).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$[0].id").value(aliasId)).
                andExpect(jsonPath("$[0].normalizedValue").doesNotExist());

        mockMvc.perform(put(aliasesUrl + "/" + aliasId).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"type":"ALBUM","value":"Nouvel alias"}
                                """)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.type").value("ALBUM")).
                andExpect(jsonPath("$.value").value("Nouvel alias"));

        mockMvc.perform(delete(aliasesUrl + "/" + aliasId).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());
        assertThat(aliasRepository.findAll()).isEmpty();
    }

    @Test
    void aliasCrudSupportsEveryApprovedAnswerType() throws Exception {
        String aliasesUrl = "/api/admin/tracks/" + pendingTrack.getId() + "/aliases";
        for (String type : List.of("TITLE", "ARTIST", "COMPOSER", "ALBUM")) {
            mockMvc.perform(post(aliasesUrl).
                            contentType(MediaType.APPLICATION_JSON).
                            header(HttpHeaders.AUTHORIZATION, adminToken).
                            content("{\"type\":\"" + type + "\",\"value\":\"Alias "
                                    + type + "\"}")).
                    andExpect(status().isCreated()).
                    andExpect(jsonPath("$.type").value(type));
        }
        assertThat(aliasRepository.findAll()).hasSize(4);
    }

    @Test
    void blankAndNormalizedDuplicateAliasesAreRejected() throws Exception {
        String aliasesUrl = "/api/admin/tracks/" + pendingTrack.getId() + "/aliases";
        mockMvc.perform(post(aliasesUrl).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"type":"TITLE","value":"My   Alias"}
                                """)).
                andExpect(status().isCreated());

        mockMvc.perform(post(aliasesUrl).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"type":"TITLE","value":"  my alias  "}
                                """)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("ANSWER_ALIAS_DUPLICATE"));

        mockMvc.perform(post(aliasesUrl).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("""
                                {"type":"ARTIST","value":"   "}
                                """)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void normalUserCannotManageAliases() throws Exception {
        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(), UserRole.USER);
        mockMvc.perform(get("/api/admin/tracks/" + pendingTrack.getId() + "/aliases").
                        header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden());
    }

    @Test
    void negativeExcerptOffsetIsRejected() throws Exception {
        mockMvc.perform(put("/api/admin/tracks/" + pendingTrack.getId()).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"excerptStartSeconds\":-1}")).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void publicationAcceptsExactlySixtyPlayableSecondsAfterOffset() throws Exception {
        pendingTrack.setDurationSeconds(90);
        pendingTrack.setExcerptStartSeconds(30);
        trackRepository.saveAndFlush(pendingTrack);

        mockMvc.perform(post("/api/admin/tracks/" + pendingTrack.getId() + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.excerptStartSeconds").value(30));
    }

    @Test
    void publicationRejectsUnknownOrInsufficientDurationAfterOffset() throws Exception {
        pendingTrack.setExcerptStartSeconds(30);
        trackRepository.saveAndFlush(pendingTrack);

        mockMvc.perform(post("/api/admin/tracks/" + pendingTrack.getId() + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("TRACK_EXCERPT_INVALID"));

        pendingTrack.setDurationSeconds(89);
        trackRepository.saveAndFlush(pendingTrack);
        mockMvc.perform(post("/api/admin/tracks/" + pendingTrack.getId() + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("TRACK_EXCERPT_INVALID"));
    }

    @Test
    void publishedTrackCannotLoseItsCanonicalPrimaryTitle() throws Exception {
        mockMvc.perform(put("/api/admin/tracks/" + publishedTrack.getId()).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"title\":\"   \"}")).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("TRACK_METADATA_INCOMPLETE"));

        assertThat(trackRepository.findById(publishedTrack.getId()).orElseThrow().getTitle()).
                isEqualTo("Published Track");
    }

    @Test
    void updateTrackReturns404ForUnknownId() throws Exception {
        mockMvc.perform(put("/api/admin/tracks/" + UUID.randomUUID()).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"title\":\"New Title\"}")).
                andExpect(status().isNotFound());
    }

    @Test
    void deleteTrackReturns204AndRemovesRecord() throws Exception {
        importJobRepository.deleteAll();
        String id = pendingTrack.getId().toString();
        mockMvc.perform(delete("/api/admin/tracks/" + id).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        assertThat(trackRepository.findById(pendingTrack.getId())).isEmpty();
        assertThat(deletionTaskRepository.findByObjectKey("tracks/pending/audio.mp3")).isPresent();
    }

    @Test
    void deleteTrackReturns404ForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/admin/tracks/" + UUID.randomUUID()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }

    @Test
    void validateTrackSetsStatusToPublished() throws Exception {
        String id = pendingTrack.getId().toString();
        mockMvc.perform(post("/api/admin/tracks/" + id + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/admin/tracks/" + id + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(importJobRepository.findAll().getFirst().getStatus()).
                isEqualTo(ImportJobStatus.PUBLISHED);
    }

    @Test
    void validateTrackRejectsIncompletePrimaryMetadata() throws Exception {
        pendingTrack.setTitle(" ");
        trackRepository.saveAndFlush(pendingTrack);

        mockMvc.perform(post("/api/admin/tracks/" + pendingTrack.getId() + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("TRACK_METADATA_INCOMPLETE"));

        assertThat(trackRepository.findById(pendingTrack.getId()).orElseThrow().getStatus()).
                isEqualTo(TrackStatus.PENDING);
    }

    @Test
    void validateTrackReturns404ForUnknownId() throws Exception {
        mockMvc.perform(post("/api/admin/tracks/" + UUID.randomUUID() + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }

    @Test
    void rejectTrackSetsStatusToRejected() throws Exception {
        String id = pendingTrack.getId().toString();
        mockMvc.perform(post("/api/admin/tracks/" + id + "/reject").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/admin/tracks/" + id + "/reject").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("REJECTED"));

        TrackImportJobEntity job = importJobRepository.findAll().getFirst();
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.REJECTED);
        assertThat(trackRepository.findById(pendingTrack.getId()).orElseThrow().
                getAudioObjectKey()).isEqualTo("tracks/pending/audio.mp3");
    }

    @Test
    void rejectingPublishedTrackRemovesItFromPlaylistsAndCompactsOrder() throws Exception {
        mockMvc.perform(post("/api/admin/tracks/" + pendingTrack.getId() + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk());
        PlaylistEntity playlist = playlist("Rejection playlist");
        playlistTrackRepository.saveAll(List.of(
                new PlaylistTrackEntity(playlist, publishedTrack, 0),
                new PlaylistTrackEntity(playlist, pendingTrack, 1)));

        mockMvc.perform(post("/api/admin/tracks/" + pendingTrack.getId() + "/reject").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(playlistTrackRepository.findAllByPlaylistIdOrdered(playlist.getId())).
                singleElement().satisfies(entry -> {
                    assertThat(entry.getTrack().getId()).isEqualTo(publishedTrack.getId());
                    assertThat(entry.getTrackOrder()).isZero();
                });
    }

    @Test
    void deletingPublishedTrackRemovesItFromPlaylistsAndCompactsOrder() throws Exception {
        PlaylistEntity playlist = playlist("Deletion playlist");
        playlistTrackRepository.saveAll(List.of(
                new PlaylistTrackEntity(playlist, publishedTrack, 0),
                new PlaylistTrackEntity(playlist, pendingTrack, 1)));

        mockMvc.perform(delete("/api/admin/tracks/" + publishedTrack.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        assertThat(playlistTrackRepository.findAllByPlaylistIdOrdered(playlist.getId())).
                singleElement().satisfies(entry -> {
                    assertThat(entry.getTrack().getId()).isEqualTo(pendingTrack.getId());
                    assertThat(entry.getTrackOrder()).isZero();
                });
    }

    @Test
    void publicationRejectsImportThatIsNotDone() throws Exception {
        TrackImportJobEntity job = importJobRepository.findAll().getFirst();
        job.setStatus(ImportJobStatus.PROCESSING);
        importJobRepository.saveAndFlush(job);

        mockMvc.perform(post("/api/admin/tracks/" + pendingTrack.getId() + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/tracks/" + pendingTrack.getId() + "/reject").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict());

        assertThat(trackRepository.findById(pendingTrack.getId()).orElseThrow().getStatus()).
                isEqualTo(TrackStatus.PENDING);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private TrackEntity buildTrack(String title, TrackStatus status) {
        TrackEntity track = new TrackEntity();
        track.setTitle(title);
        track.setArtist("Test Artist");
        track.setAudioUrl("https://example.com/audio.mp3");
        track.setStatus(status);
        return track;
    }

    private PlaylistEntity playlist(String name) {
        PlaylistEntity playlist = new PlaylistEntity();
        playlist.setName(name);
        playlist.setCreatedBy(userRepository.findByEmailIgnoreCase("track-admin@example.com").orElseThrow());
        return playlistRepository.save(playlist);
    }
}
