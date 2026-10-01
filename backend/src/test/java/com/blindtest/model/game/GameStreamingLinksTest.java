package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameStreamingLinksTest {

    @Test
    void keepsOnlyPresentWebLinksInDisplayOrder() {
        GameStreamingLinks links = GameStreamingLinks.of(
                " https://www.deezer.com/track/1 ",
                null,
                "https://open.spotify.com/track/2",
                "   ",
                "javascript:alert(1)",
                "http://soundcloud.com/artist/track");

        assertThat(links.links()).containsExactly(
                org.assertj.core.api.Assertions.entry("deezer", "https://www.deezer.com/track/1"),
                org.assertj.core.api.Assertions.entry("spotify", "https://open.spotify.com/track/2"),
                org.assertj.core.api.Assertions.entry(
                        "soundcloud", "http://soundcloud.com/artist/track"));
    }

    @Test
    void returnsNoLinksWhenNothingIsAvailable() {
        assertThat(GameStreamingLinks.of(null, null, null, null, null, null)).
                isSameAs(GameStreamingLinks.NONE);
        assertThat(GameStreamingLinks.NONE.links()).isEmpty();
    }
}
