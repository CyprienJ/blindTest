package com.blindtest.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeVideoUrlTest {

    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    @ParameterizedTest
    @MethodSource("supportedUrls")
    void parsesSupportedFormsAndCanonicalizesThem(String input) {
        YouTubeVideoUrl parsed = YouTubeVideoUrl.parse(input).orElseThrow();

        assertThat(parsed.videoId()).isEqualTo(VIDEO_ID);
        assertThat(parsed.canonicalUrl()).
                isEqualTo("https://www.youtube.com/watch?v=" + VIDEO_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "not-a-url",
        "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://example.com/watch?v=dQw4w9WgXcQ",
        "https://www.youtube.com/playlist?list=dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=too-short",
        "https://www.youtube.com/watch?v=../../etc",
        "https://www.youtube.com.evil.test/watch?v=dQw4w9WgXcQ",
        "https://user@www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://www.youtube.com:8443/watch?v=dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ/extra"
    })
    void rejectsUnsupportedOrUnsafeUrls(String input) {
        assertThat(YouTubeVideoUrl.parse(input)).isEmpty();
    }

    private static Stream<Arguments> supportedUrls() {
        return Stream.of(
                Arguments.of("https://www.youtube.com/watch?v=" + VIDEO_ID),
                Arguments.of("https://youtube.com/watch?v=" + VIDEO_ID + "&t=42"),
                Arguments.of("https://m.youtube.com/watch?t=42&v=" + VIDEO_ID),
                Arguments.of("https://music.youtube.com/watch?v=" + VIDEO_ID),
                Arguments.of("https://youtu.be/" + VIDEO_ID),
                Arguments.of("https://youtu.be/" + VIDEO_ID + "?t=42"),
                Arguments.of("https://www.youtube.com/shorts/" + VIDEO_ID));
    }
}
