package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.blindtest.config.properties.ImportProperties;
import com.blindtest.infra.cover.CoverHttpClient;
import com.blindtest.infra.cover.CoverImageFormat;
import com.blindtest.infra.cover.CoverImageTranscoder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class CoverImageNormalizerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 1, 2, 3, 4, 5, 6, 7, 8};

    @TempDir
    private Path tempDir;

    private final CoverImageTranscoder transcoder = mock(CoverImageTranscoder.class);
    private CoverImageNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new CoverImageNormalizer(transcoder,
                new ImportProperties(tempDir, 1024, Duration.ofMinutes(1), null));
    }

    @Test
    void convertsWithTheDetectedFormatAndCleansTheWorkspace() throws Exception {
        doAnswer(invocation -> Files.write(invocation.getArgument(2), JPEG)).
                when(transcoder).toJpeg(any(), eq(CoverImageFormat.PNG), any());

        assertThat(normalizer.normalize(PNG)).isEqualTo(JPEG);
        assertThat(tempDir.resolve("covers")).isEmptyDirectory();
    }

    @Test
    void rejectsUnsupportedOversizedAndUndecodableImages() throws Exception {
        assertThatThrownBy(() -> normalizer.normalize("<svg/>.......".getBytes())).
                hasMessageContaining("COVER_IMAGE_UNSUPPORTED");
        assertThatThrownBy(() -> normalizer.normalize(new byte[CoverHttpClient.MAX_IMAGE_BYTES + 1])).
                isInstanceOf(ResponseStatusException.class).
                hasMessageContaining("COVER_IMAGE_TOO_LARGE");
        verifyNoInteractions(transcoder);

        doThrow(new IOException("corrupt")).when(transcoder).toJpeg(any(), any(), any());
        assertThatThrownBy(() -> normalizer.normalize(PNG)).
                hasMessageContaining("COVER_IMAGE_UNSUPPORTED");
        assertThat(tempDir.resolve("covers")).isEmptyDirectory();
    }
}
