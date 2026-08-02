package com.blindtest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.model.entity.AdminProvisioningOutcome;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.RefreshTokenEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.service.FirstAdminProvisioningResult;
import com.blindtest.service.FirstAdminProvisioningService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(FirstAdminProvisioningService.class)
class PostgresqlSchemaIntegrationTest {

    private static final String LEGACY_SCHEMA = "media03_existing_schema";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;
    @Autowired
    private TrackImportJobRepository importJobRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AdminProvisioningAuditRepository adminProvisioningAuditRepository;
    @Autowired
    private FirstAdminProvisioningService firstAdminProvisioningService;

    @Test
    void flywayMigratesEmptyDatabaseAndHibernateValidatesMappings() {
        List<Integer> installedRanks = jdbcTemplate.queryForList(
                "SELECT installed_rank FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                Integer.class);

        assertThat(installedRanks).containsExactly(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void objectStorageMigrationUpgradesExistingSchemaWithoutBreakingLegacyRows() {
        flywayForLegacySchema(MigrationVersion.fromVersion("6")).migrate();
        UUID legacyTrackId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO media03_existing_schema.tracks (id, title, artist, audio_url, status)
                VALUES (?, 'Legacy track', 'Legacy artist', '/media/legacy.mp3', 'PUBLISHED')
                """, legacyTrackId);

        flywayForLegacySchema(null).migrate();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT audio_url FROM media03_existing_schema.tracks WHERE id = ?
                """, String.class, legacyTrackId)).isEqualTo("/media/legacy.mp3");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT album FROM media03_existing_schema.tracks WHERE id = ?
                """, String.class, legacyTrackId)).isNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT excerpt_start_seconds
                FROM media03_existing_schema.tracks WHERE id = ?
                """, Integer.class, legacyTrackId)).isZero();
        UUID objectTrackId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO media03_existing_schema.tracks
                    (id, title, artist, audio_object_key, audio_checksum, audio_size_bytes, status)
                VALUES (?, 'Object track', 'Object artist', ?, ?, ?, 'PENDING')
                """, objectTrackId, "tracks/object/audio-content.mp3", "a".repeat(64), 1234L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT audio_url FROM media03_existing_schema.tracks WHERE id = ?
                """, String.class, objectTrackId)).isNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT audio_object_key FROM media03_existing_schema.tracks WHERE id = ?
                """, String.class, objectTrackId)).isEqualTo("tracks/object/audio-content.mp3");
        assertThat(jdbcTemplate.queryForList("""
                SELECT installed_rank FROM media03_existing_schema.flyway_schema_history
                WHERE success AND version IS NOT NULL ORDER BY installed_rank
                """, Integer.class)).containsExactly(
                        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    @Test
    void postgresGeneratesUuidWhenDatabaseDefaultIsUsed() {
        UUID id = jdbcTemplate.queryForObject("""
                INSERT INTO users (username, email, password_hash)
                VALUES ('database-uuid', 'database-uuid@example.com', 'hash')
                RETURNING id
                """, UUID.class);

        assertThat(id).isNotNull();
        assertThat(userRepository.findById(id)).isPresent();
    }

    @Test
    void databaseRejectsUnknownUserRole() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users (username, email, password_hash, role)
                VALUES ('bad-role', 'bad-role@example.com', 'hash', 'SUPERUSER')
                """)).
                isInstanceOf(DataIntegrityViolationException.class).
                hasMessageContaining("chk_users_role");
    }

    @Test
    void databaseRejectsUnknownTrackStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO tracks (title, artist, audio_url, status)
                VALUES ('Bad status', 'Artist', '/audio.mp3', 'ARCHIVED')
                """)).
                isInstanceOf(DataIntegrityViolationException.class).
                hasMessageContaining("chk_tracks_status");
    }

    @Test
    void databaseRejectsUnknownImportJobStatus() {
        UserEntity admin = userRepository.saveAndFlush(buildUser("enum-admin"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO track_import_jobs (youtube_url, status, admin_id)
                VALUES ('https://youtu.be/example', 'CANCELLED', ?)
                """, admin.getId())).
                isInstanceOf(DataIntegrityViolationException.class).
                hasMessageContaining("chk_track_import_jobs_status");
    }

    @Test
    void databaseRejectsNegativeExcerptStart() {
        TrackEntity track = trackRepository.saveAndFlush(
                buildTrack("Negative excerpt", TrackStatus.PENDING));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE tracks SET excerpt_start_seconds = -1 WHERE id = ?
                """, track.getId())).
                isInstanceOf(DataIntegrityViolationException.class).
                hasMessageContaining("chk_tracks_excerpt_start_seconds");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseEnforcesNonNegativeUniquePlaylistPositions() {
        UUID adminId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        UUID firstTrackId = UUID.randomUUID();
        UUID secondTrackId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    INSERT INTO users (id, username, email, password_hash, role)
                    VALUES (?, 'ordering-admin', 'ordering-admin@example.com', 'hash', 'ADMIN')
                    """, adminId);
            jdbcTemplate.update("""
                    INSERT INTO playlists (id, name, created_by)
                    VALUES (?, 'Ordering constraints', ?)
                    """, playlistId, adminId);
            jdbcTemplate.update("""
                    INSERT INTO tracks (id, title, artist, audio_url, status)
                    VALUES
                        (?, 'First ordered track', 'Artist', '/first.mp3', 'PUBLISHED'),
                        (?, 'Second ordered track', 'Artist', '/second.mp3', 'PUBLISHED')
                    """, firstTrackId, secondTrackId);
            jdbcTemplate.update("""
                    INSERT INTO playlist_tracks (playlist_id, track_id, "order")
                    VALUES (?, ?, 0)
                    """, playlistId, firstTrackId);

            assertThatThrownBy(() -> jdbcTemplate.update("""
                    INSERT INTO playlist_tracks (playlist_id, track_id, "order")
                    VALUES (?, ?, 0)
                    """, playlistId, secondTrackId)).
                    isInstanceOf(DataIntegrityViolationException.class).
                    hasMessageContaining("uq_playlist_tracks_order");
            assertThatThrownBy(() -> jdbcTemplate.update("""
                    INSERT INTO playlist_tracks (playlist_id, track_id, "order")
                    VALUES (?, ?, -1)
                    """, playlistId, secondTrackId)).
                    isInstanceOf(DataIntegrityViolationException.class).
                    hasMessageContaining("chk_playlist_tracks_order_non_negative");
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", adminId);
            jdbcTemplate.update("DELETE FROM tracks WHERE id IN (?, ?)", firstTrackId, secondTrackId);
        }
    }

    @Test
    void databaseRejectsUnknownAnswerAliasType() {
        TrackEntity track = trackRepository.saveAndFlush(
                buildTrack("Unknown alias type", TrackStatus.PENDING));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO track_answer_aliases
                    (track_id, answer_type, alias_value, normalized_value)
                VALUES (?, 'WORK', 'Alias', 'alias')
                """, track.getId())).
                isInstanceOf(DataIntegrityViolationException.class).
                hasMessageContaining("chk_track_answer_aliases_type");
    }

    @Test
    void databaseRejectsDuplicateNormalizedAliasForSameType() {
        TrackEntity track = trackRepository.saveAndFlush(
                buildTrack("Duplicate alias", TrackStatus.PENDING));
        jdbcTemplate.update("""
                INSERT INTO track_answer_aliases
                    (track_id, answer_type, alias_value, normalized_value)
                VALUES (?, 'TITLE', 'My Alias', 'my alias')
                """, track.getId());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO track_answer_aliases
                    (track_id, answer_type, alias_value, normalized_value)
                VALUES (?, 'TITLE', 'my alias', 'my alias')
                """, track.getId())).
                isInstanceOf(DataIntegrityViolationException.class).
                hasMessageContaining("uq_track_answer_aliases_normalized");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentProvisioningCreatesExactlyOneAdministrator() throws Exception {
        UserEntity first = userRepository.saveAndFlush(buildVerifiedUser("provision-first"));
        UserEntity second = userRepository.saveAndFlush(buildVerifiedUser("provision-second"));
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<FirstAdminProvisioningResult>> futures = new ArrayList<>();
            for (String email : List.of(first.getEmail(), second.getEmail())) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return firstAdminProvisioningService.provision(email);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<AdminProvisioningOutcome> outcomes = new ArrayList<>();
            for (Future<FirstAdminProvisioningResult> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS).outcome());
            }

            assertThat(outcomes).containsExactlyInAnyOrder(
                    AdminProvisioningOutcome.PROMOTED,
                    AdminProvisioningOutcome.ADMIN_ALREADY_EXISTS);
            assertThat(userRepository.findByRole(UserRole.ADMIN)).hasSize(1);
            assertThat(adminProvisioningAuditRepository.findAll()).
                    extracting(audit -> audit.getOutcome()).
                    containsExactlyInAnyOrder(
                            AdminProvisioningOutcome.PROMOTED,
                            AdminProvisioningOutcome.ADMIN_ALREADY_EXISTS);
        } finally {
            executor.shutdownNow();
            adminProvisioningAuditRepository.deleteAll();
            userRepository.deleteAllById(List.of(first.getId(), second.getId()));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRejectsDuplicateActiveImportForSameVideo() {
        UUID adminId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    INSERT INTO users (id, username, email, password_hash, role)
                    VALUES (?, 'duplicate-admin', 'duplicate-admin@example.com', 'hash', 'ADMIN')
                    """, adminId);
            jdbcTemplate.update("""
                    INSERT INTO track_import_jobs
                        (youtube_url, youtube_video_id, status, admin_id)
                    VALUES
                        ('https://www.youtube.com/watch?v=dQw4w9WgXcQ',
                         'dQw4w9WgXcQ', 'QUEUED', ?)
                    """, adminId);

            assertThatThrownBy(() -> jdbcTemplate.update("""
                    INSERT INTO track_import_jobs
                        (youtube_url, youtube_video_id, status, admin_id)
                    VALUES
                        ('https://www.youtube.com/watch?v=dQw4w9WgXcQ',
                         'dQw4w9WgXcQ', 'PROCESSING', ?)
                    """, adminId)).
                    isInstanceOf(DataIntegrityViolationException.class).
                    hasMessageContaining("uq_track_import_jobs_active_video");

            jdbcTemplate.update("""
                    INSERT INTO track_import_jobs
                        (youtube_url, youtube_video_id, status, admin_id)
                    VALUES
                        ('https://www.youtube.com/watch?v=dQw4w9WgXcQ',
                         'dQw4w9WgXcQ', 'FAILED', ?)
                    """, adminId);
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", adminId);
        }
    }

    @Test
    void userDeleteAppliesForeignKeyCascades() {
        UserEntity admin = userRepository.saveAndFlush(buildUser("cascade-admin"));
        TrackEntity track = trackRepository.saveAndFlush(buildTrack("Cascade track", TrackStatus.PUBLISHED));
        PlaylistEntity playlist = buildPlaylist(admin, "Cascade playlist");
        playlist = playlistRepository.saveAndFlush(playlist);
        playlistTrackRepository.saveAndFlush(new PlaylistTrackEntity(playlist, track, 1));

        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setUser(admin);
        refreshToken.setToken("cascade-token");
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshTokenRepository.saveAndFlush(refreshToken);

        TrackImportJobEntity job = buildImportJob(admin, track);
        importJobRepository.saveAndFlush(job);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", admin.getId());

        assertThat(rowCount("refresh_tokens")).isZero();
        assertThat(rowCount("playlists")).isZero();
        assertThat(rowCount("playlist_tracks")).isZero();
        assertThat(rowCount("track_import_jobs")).isZero();
        assertThat(rowCount("tracks")).isOne();
    }

    @Test
    void trackDeleteCascadesMembershipAndClearsImportReference() {
        UserEntity admin = userRepository.saveAndFlush(buildUser("track-delete-admin"));
        TrackEntity track = trackRepository.saveAndFlush(buildTrack("Deleted track", TrackStatus.PENDING));
        PlaylistEntity playlist = playlistRepository.saveAndFlush(buildPlaylist(admin, "Track delete playlist"));
        playlistTrackRepository.saveAndFlush(new PlaylistTrackEntity(playlist, track, 1));
        TrackImportJobEntity job = importJobRepository.saveAndFlush(buildImportJob(admin, track));
        jdbcTemplate.update("""
                INSERT INTO track_answer_aliases
                    (track_id, answer_type, alias_value, normalized_value)
                VALUES (?, 'TITLE', 'Deleted alias', 'deleted alias')
                """, track.getId());

        jdbcTemplate.update("DELETE FROM tracks WHERE id = ?", track.getId());

        assertThat(rowCount("playlist_tracks")).isZero();
        assertThat(rowCount("track_answer_aliases")).isZero();
        assertThat(rowCount("track_import_jobs")).isOne();
        UUID trackId = jdbcTemplate.queryForObject(
                "SELECT track_id FROM track_import_jobs WHERE id = ?", UUID.class, job.getId());
        assertThat(trackId).isNull();
    }

    @Test
    void repositoriesUsePostgresEnumsAndQuotedOrderColumn() {
        UserEntity admin = userRepository.saveAndFlush(buildUser("repository-admin"));
        TrackEntity pending = trackRepository.saveAndFlush(buildTrack("Pending", TrackStatus.PENDING));
        TrackEntity published = trackRepository.saveAndFlush(buildTrack("Published", TrackStatus.PUBLISHED));
        PlaylistEntity playlist = playlistRepository.saveAndFlush(buildPlaylist(admin, "Repository playlist"));
        playlistTrackRepository.saveAndFlush(new PlaylistTrackEntity(playlist, pending, 2));
        playlistTrackRepository.saveAndFlush(new PlaylistTrackEntity(playlist, published, 1));
        TrackImportJobEntity job = importJobRepository.saveAndFlush(buildImportJob(admin, published));

        UUID playlistId = playlist.getId();
        assertThat(playlistRepository.findByCreatedById(admin.getId())).containsExactly(playlist);
        assertThat(trackRepository.findByStatus(TrackStatus.PUBLISHED)).contains(published);
        assertThat(importJobRepository.findByStatus(ImportJobStatus.DONE)).containsExactly(job);
        assertThat(importJobRepository.findByAdminId(admin.getId())).containsExactly(job);
        assertThat(importJobRepository.findByIdWithTrack(job.getId())).contains(job);
        assertThat(playlistTrackRepository.findByIdPlaylistIdOrderByTrackOrderAsc(playlistId)).
                extracting(PlaylistTrackEntity::getTrackOrder).
                containsExactly(1, 2);
        assertThat(playlistTrackRepository.findPublishedByPlaylistIdOrdered(playlistId, TrackStatus.PUBLISHED)).
                extracting(PlaylistTrackEntity::getTrack).
                containsExactly(published);
        assertThat(playlistTrackRepository.countPublishedInPlaylist(playlistId, TrackStatus.PUBLISHED)).
                isOne();
    }

    private int rowCount(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Flyway flywayForLegacySchema(MigrationVersion target) {
        var configuration = Flyway.configure().
                dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).
                defaultSchema(LEGACY_SCHEMA).
                schemas(LEGACY_SCHEMA);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private UserEntity buildUser(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private UserEntity buildVerifiedUser(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(true);
        return user;
    }

    private TrackEntity buildTrack(String title, TrackStatus status) {
        TrackEntity track = new TrackEntity();
        track.setTitle(title);
        track.setArtist("Artist");
        track.setAudioUrl("/audio.mp3");
        track.setStatus(status);
        return track;
    }

    private PlaylistEntity buildPlaylist(UserEntity creator, String name) {
        PlaylistEntity playlist = new PlaylistEntity();
        playlist.setName(name);
        playlist.setCreatedBy(creator);
        return playlist;
    }

    private TrackImportJobEntity buildImportJob(UserEntity admin, TrackEntity track) {
        TrackImportJobEntity job = new TrackImportJobEntity();
        job.setYoutubeUrl("https://youtu.be/example");
        job.setStatus(ImportJobStatus.DONE);
        job.setAdmin(admin);
        job.setTrack(track);
        return job;
    }
}
