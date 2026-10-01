package com.blindtest.infra.cover;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CoverImageFormatTest {

    @Test
    void detectsSupportedFormatsFromContentOnly() {
        assertThat(CoverImageFormat.detect(padded(0xff, 0xd8, 0xff, 0xe0))).contains(CoverImageFormat.JPEG);
        assertThat(CoverImageFormat.detect(padded(0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'))).
                contains(CoverImageFormat.PNG);
        assertThat(CoverImageFormat.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).
                contains(CoverImageFormat.WEBP);
    }

    @Test
    void rejectsOtherOrTruncatedContent() {
        assertThat(CoverImageFormat.detect("<svg xmlns='x'/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(CoverImageFormat.detect("GIF89a......".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(CoverImageFormat.detect(new byte[] {(byte) 0xff, (byte) 0xd8})).isEmpty();
        assertThat(CoverImageFormat.detect(null)).isEmpty();
    }

    private static byte[] padded(int... header) {
        byte[] content = Arrays.copyOf(new byte[0], 16);
        for (int index = 0; index < header.length; index++) {
            content[index] = (byte) header[index];
        }
        return content;
    }
}
