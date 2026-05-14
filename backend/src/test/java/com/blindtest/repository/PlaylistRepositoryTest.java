package com.blindtest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class PlaylistRepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    private UserEntity alice;
    private PlaylistEntity alicePlaylist;

    @BeforeEach
    void setUp() {
        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(buildUser("alice", "alice@example.com"));
        UserEntity bob = userRepository.save(buildUser("bob", "bob@example.com"));

        alicePlaylist = new PlaylistEntity();
        alicePlaylist.setName("Alice's Hits");
        alicePlaylist.setCreatedBy(alice);
        alicePlaylist = playlistRepository.save(alicePlaylist);

        PlaylistEntity bobPlaylist = new PlaylistEntity();
        bobPlaylist.setName("Bob's Mix");
        bobPlaylist.setCreatedBy(bob);
        playlistRepository.save(bobPlaylist);
    }

    @Test
    void findByCreatedByIdReturnsOnlyUserPlaylists() {
        List<PlaylistEntity> results = playlistRepository.findByCreatedById(alice.getId());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Alice's Hits");
    }

    @Test
    void findByCreatedByIdReturnsEmptyWhenUserHasNone() {
        UserEntity charlie = userRepository.save(buildUser("charlie", "charlie@example.com"));
        List<PlaylistEntity> results = playlistRepository.findByCreatedById(charlie.getId());
        assertThat(results).isEmpty();
    }

    @Test
    void playlistTrackOrderIsPreserved() {
        TrackEntity t1 = trackRepository.save(buildTrack("Song A"));
        TrackEntity t2 = trackRepository.save(buildTrack("Song B"));
        TrackEntity t3 = trackRepository.save(buildTrack("Song C"));

        playlistTrackRepository.save(new PlaylistTrackEntity(alicePlaylist, t3, 3));
        playlistTrackRepository.save(new PlaylistTrackEntity(alicePlaylist, t1, 1));
        playlistTrackRepository.save(new PlaylistTrackEntity(alicePlaylist, t2, 2));

        UUID playlistId = alicePlaylist.getId();
        List<PlaylistTrackEntity> ordered =
                playlistTrackRepository.findByIdPlaylistIdOrderByTrackOrderAsc(playlistId);

        assertThat(ordered).hasSize(3);
        assertThat(ordered.get(0).getTrackOrder()).isEqualTo(1);
        assertThat(ordered.get(1).getTrackOrder()).isEqualTo(2);
        assertThat(ordered.get(2).getTrackOrder()).isEqualTo(3);
    }

    private UserEntity buildUser(String username, String email) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hash");
        user.setRole(UserRole.USER);
        return user;
    }

    private TrackEntity buildTrack(String title) {
        TrackEntity track = new TrackEntity();
        track.setTitle(title);
        track.setArtist("Artist");
        track.setAudioUrl("https://example.com/audio.mp3");
        track.setStatus(TrackStatus.PUBLISHED);
        return track;
    }
}
