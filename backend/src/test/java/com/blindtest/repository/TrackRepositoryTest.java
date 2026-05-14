package com.blindtest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class TrackRepositoryTest {

    @Autowired
    private TrackRepository trackRepository;

    private TrackEntity publishedTrack;

    @BeforeEach
    void setUp() {
        trackRepository.deleteAll();

        publishedTrack = buildTrack("Bohemian Rhapsody", "Queen", "Rock", (short) 1970);
        publishedTrack.setStatus(TrackStatus.PUBLISHED);
        publishedTrack = trackRepository.save(publishedTrack);

        TrackEntity pendingTrack = buildTrack("Imagine", "John Lennon", "Pop", (short) 1970);
        trackRepository.save(pendingTrack);

        TrackEntity eighties = buildTrack("Thriller", "Michael Jackson", "Pop", (short) 1980);
        eighties.setStatus(TrackStatus.PUBLISHED);
        trackRepository.save(eighties);
    }

    @Test
    void findByStatusReturnsOnlyMatchingTracks() {
        List<TrackEntity> results = trackRepository.findByStatus(TrackStatus.PUBLISHED);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(TrackEntity::getStatus).containsOnly(TrackStatus.PUBLISHED);
    }

    @Test
    void findByStatusReturnsEmptyWhenNoneMatch() {
        List<TrackEntity> results = trackRepository.findByStatus(TrackStatus.REJECTED);
        assertThat(results).isEmpty();
    }

    @Test
    void findByCategoryReturnsMatchingTracks() {
        List<TrackEntity> results = trackRepository.findByCategory("Pop");
        assertThat(results).hasSize(2);
    }

    @Test
    void findByCategoryReturnsEmptyWhenNoneMatch() {
        List<TrackEntity> results = trackRepository.findByCategory("Jazz");
        assertThat(results).isEmpty();
    }

    @Test
    void findByDecadeReturnsMatchingTracks() {
        List<TrackEntity> results = trackRepository.findByDecade((short) 1970);
        assertThat(results).hasSize(2);
    }

    @Test
    void findByDecadeReturnsEmptyWhenNoneMatch() {
        List<TrackEntity> results = trackRepository.findByDecade((short) 1960);
        assertThat(results).isEmpty();
    }

    @Test
    void saveAndFindByIdRoundTripsAllFields() {
        TrackEntity found = trackRepository.findById(publishedTrack.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Bohemian Rhapsody");
        assertThat(found.getArtist()).isEqualTo("Queen");
        assertThat(found.getStatus()).isEqualTo(TrackStatus.PUBLISHED);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    private TrackEntity buildTrack(String title, String artist, String category, short decade) {
        TrackEntity track = new TrackEntity();
        track.setTitle(title);
        track.setArtist(artist);
        track.setAudioUrl("https://example.com/audio.mp3");
        track.setCategory(category);
        track.setDecade(decade);
        return track;
    }
}
