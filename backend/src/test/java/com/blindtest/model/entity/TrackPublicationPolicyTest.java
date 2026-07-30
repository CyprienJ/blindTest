package com.blindtest.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrackPublicationPolicyTest {

    @Test
    void completeLegacyTrackCanBePublishedWithoutOptionalSecondaryMetadata() {
        TrackEntity track = requiredMetadata();
        track.setAudioUrl("/media/legacy.mp3");

        assertThat(TrackPublicationPolicy.isComplete(track)).isTrue();
    }

    @Test
    void completeObjectBackedTrackCanBePublished() {
        TrackEntity track = requiredMetadata();
        track.setAudioObjectKey("tracks/id/audio.mp3");
        track.setAudioChecksum("a".repeat(64));
        track.setAudioSizeBytes(1024L);

        assertThat(TrackPublicationPolicy.isComplete(track)).isTrue();
    }

    @Test
    void titleIsAlwaysTheRequiredPrimaryAnswer() {
        TrackEntity track = requiredMetadata();
        track.setTitle(" ");
        track.setAudioUrl("/media/legacy.mp3");

        assertThat(TrackPublicationPolicy.isComplete(track)).isFalse();
    }

    @Test
    void displayArtistAndPlayableMediaAreRequired() {
        TrackEntity track = requiredMetadata();

        assertThat(TrackPublicationPolicy.isComplete(track)).isFalse();

        track.setAudioUrl("/media/legacy.mp3");
        track.setArtist(null);
        assertThat(TrackPublicationPolicy.isComplete(track)).isFalse();
    }

    @Test
    void partialObjectMetadataIsNotPlayable() {
        TrackEntity track = requiredMetadata();
        track.setAudioObjectKey("tracks/id/audio.mp3");
        track.setAudioChecksum("a".repeat(64));

        assertThat(TrackPublicationPolicy.isComplete(track)).isFalse();

        track.setAudioSizeBytes(0L);
        assertThat(TrackPublicationPolicy.isComplete(track)).isFalse();
    }

    private TrackEntity requiredMetadata() {
        TrackEntity track = new TrackEntity();
        track.setTitle("Primary title");
        track.setArtist("Display artist");
        return track;
    }
}
